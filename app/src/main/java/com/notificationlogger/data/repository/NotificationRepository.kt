package com.notificationlogger.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.notificationlogger.data.database.AppDatabase
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.data.model.*

class NotificationRepository(context: Context) {

    private val db       = AppDatabase.getInstance(context)
    private val dao      = db.notificationDao()
    private val filterDao = db.appFilterDao()

    // ─── Notifications ─────────────────────────────────────────────────────────

    suspend fun insert(log: NotificationLog) = dao.insert(log)
    suspend fun update(log: NotificationLog) = dao.update(log)
    suspend fun getPagedLogs(limit: Int = 50, offset: Int = 0) = dao.getPagedLogs(limit, offset)
    suspend fun getLogsForApp(pkg: String) = dao.getLogsForApp(pkg)
    suspend fun getLogsInRange(from: Long, to: Long) = dao.getLogsInRange(from, to)
    suspend fun searchLogs(query: String) = dao.searchLogs(query)
    suspend fun getAllLogs() = dao.getAllLogs()
    fun getTotalCount(): LiveData<Int> = dao.getTotalCount()

    // ─── Analysis ──────────────────────────────────────────────────────────────

    suspend fun getTopApps(sinceDays: Int = 30): List<NotificationSummary> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getTopApps(since)
    }

    suspend fun getHourlyDistribution(sinceDays: Int = 30): List<HourlyCount> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getHourlyDistribution(since)
    }

    suspend fun getTopicBreakdown(sinceDays: Int = 30): List<TopicCount> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getTopicBreakdown(since)
    }

    suspend fun getDoomScrollEvents(sinceDays: Int = 7, threshold: Int = 5): List<DoomScrollEvent> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getDoomScrollEvents(since, threshold)
    }

    suspend fun getIgnoreRate(pkg: String) = dao.getIgnoreRate(pkg)

    // ─── Maintenance ───────────────────────────────────────────────────────────

    suspend fun deleteOlderThan(cutoffMs: Long) = dao.deleteOlderThan(cutoffMs)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun countOlderThan(cutoffMs: Long) = dao.countOlderThan(cutoffMs)

    // ─── Filters ───────────────────────────────────────────────────────────────

    fun getAllFilters() = filterDao.getAllFilters()

    suspend fun addToBlacklist(pkg: String, appName: String) =
        filterDao.insert(AppFilter(packageName = pkg, appName = appName, filterType = "BLACKLIST"))

    suspend fun addToWhitelist(pkg: String, appName: String) =
        filterDao.insert(AppFilter(packageName = pkg, appName = appName, filterType = "WHITELIST"))

    suspend fun removeFilter(filter: AppFilter) = filterDao.delete(filter)
    suspend fun isBlacklisted(pkg: String) = filterDao.isInFilter(pkg, "BLACKLIST") > 0
    suspend fun getBlacklist() = filterDao.getPackageNamesByType("BLACKLIST")
    suspend fun getWhitelist() = filterDao.getPackageNamesByType("WHITELIST")

    // ─── People ────────────────────────────────────────────────────────────────

    suspend fun getSenderStats(sinceDays: Int, pkg: String? = null): List<SenderStats> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getSenderStats(since, pkg)
    }

    suspend fun getSenderHourlyDist(sender: String, pkg: String, sinceDays: Int): List<HourlyCount> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getSenderHourlyDist(sender, pkg, since)
    }

    suspend fun getSenderTypeBreakdown(sender: String, pkg: String, sinceDays: Int): List<TopicCount> {
        val since = System.currentTimeMillis() - sinceDays * 86_400_000L
        return dao.getSenderTypeBreakdown(sender, pkg, since)
    }

    // ─── Explorer ──────────────────────────────────────────────────────────────

    suspend fun explorerQuery(
        since: Long, pkg: String?, type: String?,
        sender: String?, query: String?, limit: Int, offset: Int
    ) = dao.explorerQuery(since, pkg, type, sender, query, limit, offset)

    suspend fun getDistinctApps() = dao.getDistinctApps()

    suspend fun getDistinctTypes() = dao.getDistinctTypes()
}
