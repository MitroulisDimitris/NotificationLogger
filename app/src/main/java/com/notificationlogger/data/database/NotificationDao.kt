package com.notificationlogger.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.notificationlogger.data.model.*

@Dao
interface NotificationDao {

    // ─── INSERT ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NotificationLog): Long

    @Update
    suspend fun update(log: NotificationLog)

    // ─── BASIC LOG QUERIES ─────────────────────────────────────────────────────

    @Query("SELECT * FROM notification_logs ORDER BY postTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedLogs(limit: Int, offset: Int): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE packageName = :pkg ORDER BY postTime DESC")
    suspend fun getLogsForApp(pkg: String): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE postTime >= :from AND postTime <= :to ORDER BY postTime DESC")
    suspend fun getLogsInRange(from: Long, to: Long): List<NotificationLog>

    @Query("""
        SELECT * FROM notification_logs
        WHERE (title LIKE '%' || :q || '%' OR text LIKE '%' || :q || '%')
        ORDER BY postTime DESC LIMIT 200
    """)
    suspend fun searchLogs(q: String): List<NotificationLog>

    @Query("SELECT * FROM notification_logs ORDER BY postTime DESC")
    suspend fun getAllLogs(): List<NotificationLog>

    @Query("SELECT COUNT(*) FROM notification_logs")
    fun getTotalCount(): LiveData<Int>

    // ─── ANALYSIS ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT packageName, appName, COUNT(*) as count, MAX(postTime) as lastSeen
        FROM notification_logs
        WHERE event = 'POSTED' AND postTime >= :since
        GROUP BY packageName
        ORDER BY count DESC
        LIMIT :topN
    """)
    suspend fun getTopApps(since: Long, topN: Int = 20): List<NotificationSummary>

    @Query("""
        SELECT CAST(strftime('%H', datetime(postTime/1000, 'unixepoch', 'localtime')) AS INTEGER) as hour,
               COUNT(*) as count
        FROM notification_logs
        WHERE event = 'POSTED' AND postTime >= :since
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getHourlyDistribution(since: Long): List<HourlyCount>

    @Query("""
        SELECT topicGroup, COUNT(*) as count
        FROM notification_logs
        WHERE event = 'POSTED' AND topicGroup IS NOT NULL AND postTime >= :since
        GROUP BY topicGroup
        ORDER BY count DESC
    """)
    suspend fun getTopicBreakdown(since: Long): List<TopicCount>

    @Query("""
        SELECT packageName, appName, postTime as windowStart, COUNT(*) as burstCount
        FROM notification_logs
        WHERE event = 'POSTED' AND postTime >= :since
        GROUP BY packageName, CAST(postTime / (2 * 60 * 1000) AS INTEGER)
        HAVING burstCount >= :threshold
        ORDER BY burstCount DESC
        LIMIT 50
    """)
    suspend fun getDoomScrollEvents(since: Long, threshold: Int = 5): List<DoomScrollEvent>

    @Query("""
        SELECT COUNT(*) * 1.0 / NULLIF(
            (SELECT COUNT(*) FROM notification_logs WHERE packageName = :pkg AND event = 'POSTED'), 0)
        FROM notification_logs
        WHERE packageName = :pkg AND event = 'DISMISSED'
    """)
    suspend fun getIgnoreRate(pkg: String): Double?

    // ─── MAINTENANCE ───────────────────────────────────────────────────────────

    @Query("DELETE FROM notification_logs WHERE postTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM notification_logs WHERE packageName = :pkg")
    suspend fun deleteLogsForApp(pkg: String)

    @Query("DELETE FROM notification_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notification_logs WHERE postTime < :cutoff")
    suspend fun countOlderThan(cutoff: Long): Int

    // ─── PEOPLE / SENDER QUERIES ───────────────────────────────────────────────

    @Query("""
        SELECT
            title as sender,
            packageName,
            appName,
            COUNT(*) as messageCount,
            MAX(postTime) as lastSeen,
            CAST(strftime('%H', datetime(
                (SELECT n2.postTime FROM notification_logs n2
                 WHERE n2.title = n1.title AND n2.packageName = n1.packageName
                   AND n2.event = 'POSTED'
                 GROUP BY strftime('%H', datetime(n2.postTime/1000,'unixepoch','localtime'))
                 ORDER BY COUNT(*) DESC LIMIT 1
                )/1000,'unixepoch','localtime')) AS INTEGER) as peakHour,
            notificationType as topType
        FROM notification_logs n1
        WHERE event = 'POSTED'
          AND title IS NOT NULL AND title != ''
          AND postTime >= :since
          AND (:pkg IS NULL OR packageName = :pkg)
        GROUP BY title, packageName
        HAVING messageCount >= 2
        ORDER BY messageCount DESC
        LIMIT 200
    """)
    suspend fun getSenderStats(since: Long, pkg: String? = null): List<SenderStats>

    @Query("""
        SELECT
            CAST(strftime('%H', datetime(postTime/1000,'unixepoch','localtime')) AS INTEGER) as hour,
            COUNT(*) as count
        FROM notification_logs
        WHERE event = 'POSTED'
          AND title = :sender
          AND packageName = :pkg
          AND postTime >= :since
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getSenderHourlyDist(sender: String, pkg: String, since: Long): List<HourlyCount>

    @Query("""
        SELECT notificationType as topicGroup, COUNT(*) as count
        FROM notification_logs
        WHERE event = 'POSTED'
          AND title = :sender
          AND packageName = :pkg
          AND postTime >= :since
        GROUP BY notificationType
        ORDER BY count DESC
    """)
    suspend fun getSenderTypeBreakdown(sender: String, pkg: String, since: Long): List<TopicCount>

    // ─── EXPLORER ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT id, postTime, appName, packageName, title as sender, text, notificationType, topicGroup
        FROM notification_logs
        WHERE event = 'POSTED'
          AND postTime >= :since
          AND (:pkg IS NULL OR packageName = :pkg)
          AND (:type IS NULL OR notificationType = :type)
          AND (:sender IS NULL OR title LIKE '%' || :sender || '%')
          AND (:query IS NULL OR title LIKE '%' || :query || '%'
                              OR text LIKE '%' || :query || '%'
                              OR bigText LIKE '%' || :query || '%')
        ORDER BY postTime DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun explorerQuery(
        since: Long,
        pkg: String?,
        type: String?,
        sender: String?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<ExplorerRow>

    @Query("SELECT DISTINCT packageName, appName FROM notification_logs WHERE event='POSTED' ORDER BY appName ASC")
    suspend fun getDistinctApps(): List<AppEntry>

    @Query("SELECT DISTINCT notificationType FROM notification_logs WHERE event='POSTED' ORDER BY notificationType ASC")
    suspend fun getDistinctTypes(): List<TypeEntry>
}
