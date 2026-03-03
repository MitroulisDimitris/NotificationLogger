package com.notificationlogger.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.notificationlogger.data.database.AppDatabase
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.data.model.*

class NotificationRepository(context: Context) {

    private val db        = AppDatabase.getInstance(context)
    private val dao       = db.notificationDao()
    private val filterDao = db.appFilterDao()
    private val aliasDao  = db.personAliasDao()

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

    suspend fun getTopApps(sinceDays: Int = 30) =
        dao.getTopApps(System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getHourlyDistribution(sinceDays: Int = 30) =
        dao.getHourlyDistribution(System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getTopicBreakdown(sinceDays: Int = 30) =
        dao.getTopicBreakdown(System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getDoomScrollEvents(sinceDays: Int = 7, threshold: Int = 5) =
        dao.getDoomScrollEvents(System.currentTimeMillis() - sinceDays * 86_400_000L, threshold)

    suspend fun getIgnoreRate(pkg: String) = dao.getIgnoreRate(pkg)

    // ─── Day patterns ──────────────────────────────────────────────────────────

    suspend fun getDayOfWeekDist(sinceDays: Int, pkg: String? = null, sender: String? = null) =
        dao.getDayOfWeekDist(System.currentTimeMillis() - sinceDays * 86_400_000L, pkg, sender)

    suspend fun getHourDayHeatmap(sinceDays: Int, pkg: String? = null, sender: String? = null) =
        dao.getHourDayHeatmap(System.currentTimeMillis() - sinceDays * 86_400_000L, pkg, sender)

    suspend fun getSenderHourStats(sender: String, pkg: String, sinceDays: Int) =
        dao.getSenderHourStats(sender, pkg, System.currentTimeMillis() - sinceDays * 86_400_000L)

    // ─── Maintenance ───────────────────────────────────────────────────────────

    suspend fun deleteOlderThan(cutoffMs: Long) = dao.deleteOlderThan(cutoffMs)
    suspend fun deleteAll() = dao.deleteAllLogs()
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

    // ─── Person aliases ────────────────────────────────────────────────────────

    fun getAllAliases(): LiveData<List<PersonAlias>> = aliasDao.getAllAliases()
    suspend fun getAllAliasesSuspend(): List<PersonAlias> = aliasDao.getAllAliasesSuspend()
    suspend fun addAlias(rawName: String, canonicalName: String) =
        aliasDao.insert(PersonAlias(rawName = rawName, canonicalName = canonicalName))
    suspend fun deleteAlias(alias: PersonAlias) = aliasDao.delete(alias)
    suspend fun deleteAliasGroup(canonical: String) = aliasDao.deleteGroup(canonical)
    suspend fun getCanonicalNames() = aliasDao.getCanonicalNames()
    suspend fun getAliasesForCanonical(canonical: String) = aliasDao.getAliasesForCanonical(canonical)

    // ─── People ────────────────────────────────────────────────────────────────

    suspend fun getSenderStats(sinceDays: Int, pkg: String? = null): List<SenderStats> =
        dao.getSenderStats(System.currentTimeMillis() - sinceDays * 86_400_000L, pkg)

    suspend fun getSenderHourlyDist(sender: String, pkg: String, sinceDays: Int) =
        dao.getSenderHourlyDist(sender, pkg, System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getSenderTypeBreakdown(sender: String, pkg: String, sinceDays: Int) =
        dao.getSenderTypeBreakdown(sender, pkg, System.currentTimeMillis() - sinceDays * 86_400_000L)

    /**
     * Resolves a canonical name to all its raw names.
     * If no aliases exist for this name, returns listOf(canonicalName) so
     * single-name persons work identically.
     */
    suspend fun resolveToRawNames(canonicalName: String): List<String> {
        val aliases = aliasDao.getAliasesForCanonical(canonicalName)
        return if (aliases.isEmpty()) listOf(canonicalName)
               else aliases.map { it.rawName }
    }

    suspend fun getSenderHourlyDistMulti(names: List<String>, sinceDays: Int) =
        dao.getSenderHourlyDistMulti(names, System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getSenderTypeBreakdownMulti(names: List<String>, sinceDays: Int) =
        dao.getSenderTypeBreakdownMulti(names, System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getDayOfWeekDistMulti(names: List<String>, sinceDays: Int) =
        dao.getDayOfWeekDistMulti(names, System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getHourDayHeatmapMulti(names: List<String>, sinceDays: Int) =
        dao.getHourDayHeatmapMulti(names, System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getSenderHourStatsMulti(names: List<String>, sinceDays: Int) =
        dao.getSenderHourStatsMulti(names, System.currentTimeMillis() - sinceDays * 86_400_000L)

    // ─── Explorer ──────────────────────────────────────────────────────────────

    suspend fun explorerQuery(
        since: Long, pkg: String?, type: String?,
        sender: String?, query: String?, limit: Int, offset: Int
    ) = dao.explorerQuery(since, pkg, type, sender, query, limit, offset)

    suspend fun getDistinctSenders(sinceDays: Int = 90) =
        dao.getDistinctSenders(System.currentTimeMillis() - sinceDays * 86_400_000L)

    suspend fun getDistinctApps() = dao.getDistinctApps()
    suspend fun getDistinctTypes() = dao.getDistinctTypes()

    // ─── Import ────────────────────────────────────────────────────────────────

    suspend fun replaceAllWithImport(logs: List<NotificationLog>) {
        dao.deleteAllLogs()
        dao.insertAll(logs)
    }
}
