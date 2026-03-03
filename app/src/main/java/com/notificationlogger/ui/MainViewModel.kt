package com.notificationlogger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.data.model.*
import com.notificationlogger.data.repository.NotificationRepository
import com.notificationlogger.util.CsvImporter
import com.notificationlogger.util.Event
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NotificationRepository(application)

    val totalCount: LiveData<Int> = repo.getTotalCount()

    // ─── Logs ──────────────────────────────────────────────────────────────────

    private val _logs = MutableLiveData<List<NotificationLog>>()
    val logs: LiveData<List<NotificationLog>> = _logs
    private var logsOffset = 0
    private val PAGE = 50

    fun loadLogs(reset: Boolean = false) = viewModelScope.launch {
        if (reset) logsOffset = 0
        val page = repo.getPagedLogs(PAGE, logsOffset)
        _logs.postValue(if (reset) page else ((_logs.value.orEmpty()) + page).toList())
        logsOffset += page.size
    }

    fun searchLogs(query: String) = viewModelScope.launch {
        _logs.postValue(repo.searchLogs(query))
    }

    // ─── Dashboard Analysis ────────────────────────────────────────────────────

    private val _topApps        = MutableLiveData<List<NotificationSummary>>()
    val topApps: LiveData<List<NotificationSummary>> = _topApps

    private val _hourlyDist     = MutableLiveData<List<HourlyCount>>()
    val hourlyDist: LiveData<List<HourlyCount>> = _hourlyDist

    private val _topicBreakdown = MutableLiveData<List<TopicCount>>()
    val topicBreakdown: LiveData<List<TopicCount>> = _topicBreakdown

    private val _doomScrollEvents = MutableLiveData<List<DoomScrollEvent>>()
    val doomScrollEvents: LiveData<List<DoomScrollEvent>> = _doomScrollEvents

    // Day-of-week distribution (global)
    private val _dayOfWeekDist  = MutableLiveData<List<DayOfWeekCount>>()
    val dayOfWeekDist: LiveData<List<DayOfWeekCount>> = _dayOfWeekDist

    // Hour × day-of-week heatmap (global)
    private val _hourDayHeatmap = MutableLiveData<List<HourDayCount>>()
    val hourDayHeatmap: LiveData<List<HourDayCount>> = _hourDayHeatmap

    fun loadAnalysis(sinceDays: Int = 30) = viewModelScope.launch {
        _topApps.postValue(repo.getTopApps(sinceDays))
        _hourlyDist.postValue(repo.getHourlyDistribution(sinceDays))
        _topicBreakdown.postValue(repo.getTopicBreakdown(sinceDays))
        _doomScrollEvents.postValue(repo.getDoomScrollEvents(7))
        _dayOfWeekDist.postValue(repo.getDayOfWeekDist(sinceDays))
        _hourDayHeatmap.postValue(repo.getHourDayHeatmap(sinceDays))
    }

    // ─── Export ────────────────────────────────────────────────────────────────

    private val _exportEvent = MutableLiveData<Event<List<NotificationLog>>>()
    val exportEvent: LiveData<Event<List<NotificationLog>>> = _exportEvent

    fun loadAllForExport() = viewModelScope.launch {
        _exportEvent.postValue(Event(repo.getAllLogs()))
    }

    // ─── Import ────────────────────────────────────────────────────────────────

    private val _importResult = MutableLiveData<Event<CsvImporter.ImportResult>>()
    val importResult: LiveData<Event<CsvImporter.ImportResult>> = _importResult

    fun importFromCsv(uri: Uri) = viewModelScope.launch {
        val (logs, result) = CsvImporter.parseToLogs(getApplication(), uri)
        if (logs.isNotEmpty()) {
            repo.replaceAllWithImport(logs)
        }
        _importResult.postValue(Event(result))
    }

    // ─── Filters ───────────────────────────────────────────────────────────────

    val allFilters = repo.getAllFilters()

    fun addToBlacklist(pkg: String, appName: String) = viewModelScope.launch {
        repo.addToBlacklist(pkg, appName)
    }
    fun addToWhitelist(pkg: String, appName: String) = viewModelScope.launch {
        repo.addToWhitelist(pkg, appName)
    }
    fun removeFilter(filter: AppFilter) = viewModelScope.launch {
        repo.removeFilter(filter)
    }

    // ─── Maintenance ───────────────────────────────────────────────────────────

    fun deleteAll() = viewModelScope.launch {
        repo.deleteAll()
        loadLogs(reset = true)
    }

    // ─── People / Senders ──────────────────────────────────────────────────────

    private val _senderStats = MutableLiveData<List<SenderStats>>()
    val senderStats: LiveData<List<SenderStats>> = _senderStats

    fun loadSenderStats(sinceDays: Int = 30, pkg: String? = null) = viewModelScope.launch {
        val raw     = repo.getSenderStats(sinceDays, pkg)
        val aliases = repo.getAllAliasesSuspend()
        // Build rawName → canonicalName lookup
        val aliasMap = aliases.associate { it.rawName to it.canonicalName }

        // Group raw stats by resolved (canonical) name
        val grouped = mutableMapOf<String, MutableList<SenderStats>>()
        for (stat in raw) {
            val key = aliasMap[stat.sender] ?: stat.sender
            grouped.getOrPut(key) { mutableListOf() }.add(stat)
        }

        // Merge each group into a single SenderStats row
        val merged = grouped.map { (canonicalName, stats) ->
            if (stats.size == 1) {
                // No merge needed, but update display name if aliased
                stats[0].copy(sender = canonicalName)
            } else {
                SenderStats(
                    sender       = canonicalName,
                    packageName  = stats.maxByOrNull { it.messageCount }!!.packageName,
                    appName      = stats.maxByOrNull { it.messageCount }!!.appName,
                    messageCount = stats.sumOf { it.messageCount },
                    lastSeen     = stats.maxOf { it.lastSeen },
                    peakHour     = stats.maxByOrNull { it.messageCount }!!.peakHour,
                    topType      = stats.maxByOrNull { it.messageCount }!!.topType
                )
            }
        }.sortedByDescending { it.messageCount }

        _senderStats.postValue(merged)
    }

    // Distinct sender names for the alias picker dialog
    private val _distinctSenders = MutableLiveData<List<String>>()
    val distinctSenders: LiveData<List<String>> = _distinctSenders

    fun loadDistinctSenders(sinceDays: Int = 90) = viewModelScope.launch {
        val names = repo.getDistinctSenders(sinceDays).map { it.sender }
        _distinctSenders.postValue(names)
    }

    // Per-sender drill-down
    private val _senderHourly = MutableLiveData<List<HourlyCount>>()
    val senderHourly: LiveData<List<HourlyCount>> = _senderHourly

    private val _senderTypes = MutableLiveData<List<TopicCount>>()
    val senderTypes: LiveData<List<TopicCount>> = _senderTypes

    // Per-sender schedule stats (mean + stddev)
    private val _senderScheduleStats = MutableLiveData<SenderScheduleStats?>()
    val senderScheduleStats: LiveData<SenderScheduleStats?> = _senderScheduleStats

    // Per-sender day-of-week chart
    private val _senderDayOfWeek = MutableLiveData<List<DayOfWeekCount>>()
    val senderDayOfWeek: LiveData<List<DayOfWeekCount>> = _senderDayOfWeek

    // Per-sender hour×day heatmap
    private val _senderHourDay = MutableLiveData<List<HourDayCount>>()
    val senderHourDay: LiveData<List<HourDayCount>> = _senderHourDay

    fun loadSenderDetail(sender: String, pkg: String, sinceDays: Int = 90) = viewModelScope.launch {
        // Resolve canonical name → all raw names (includes itself if no aliases)
        val rawNames = repo.resolveToRawNames(sender)

        _senderHourly.postValue(repo.getSenderHourlyDistMulti(rawNames, sinceDays))
        _senderTypes.postValue(repo.getSenderTypeBreakdownMulti(rawNames, sinceDays))
        _senderDayOfWeek.postValue(repo.getDayOfWeekDistMulti(rawNames, sinceDays))
        _senderHourDay.postValue(repo.getHourDayHeatmapMulti(rawNames, sinceDays))

        val raw = repo.getSenderHourStatsMulti(rawNames, sinceDays)
        _senderScheduleStats.postValue(
            if (raw != null && raw.cnt > 1) {
                val mean     = raw.hourSum / raw.cnt
                val variance = (raw.hourSumSq / raw.cnt) - (mean * mean)
                val stdDev   = sqrt(variance.coerceAtLeast(0.0))
                SenderScheduleStats(mean = mean, stdDev = stdDev, sampleCount = raw.cnt)
            } else null
        )
    }

    // ─── Aliases ───────────────────────────────────────────────────────────────

    val allAliases: LiveData<List<PersonAlias>> = repo.getAllAliases()

    fun addAlias(rawName: String, canonicalName: String) = viewModelScope.launch {
        repo.addAlias(rawName, canonicalName)
    }
    fun removeAlias(alias: PersonAlias) = viewModelScope.launch {
        repo.deleteAlias(alias)
    }
    fun removeAliasGroup(canonical: String) = viewModelScope.launch {
        repo.deleteAliasGroup(canonical)
    }

    // ─── Explorer ──────────────────────────────────────────────────────────────

    private val _explorerRows = MutableLiveData<List<ExplorerRow>>()
    val explorerRows: LiveData<List<ExplorerRow>> = _explorerRows
    private var explorerOffset = 0
    private val EXPLORER_PAGE = 60

    fun loadExplorer(filter: ExplorerFilter, loadMore: Boolean = false) = viewModelScope.launch {
        if (!loadMore) explorerOffset = 0
        val since = System.currentTimeMillis() - filter.sinceDays * 86_400_000L
        val page = repo.explorerQuery(
            since  = since,
            pkg    = filter.appPackage,
            type   = filter.notificationType,
            sender = filter.sender,
            query  = filter.textQuery,
            limit  = EXPLORER_PAGE,
            offset = explorerOffset
        )
        _explorerRows.postValue(
            if (loadMore) ((_explorerRows.value.orEmpty()) + page).toList() else page
        )
        explorerOffset += page.size
    }

    private val _availableApps  = MutableLiveData<List<AppEntry>>()
    val availableApps: LiveData<List<AppEntry>> = _availableApps

    private val _availableTypes = MutableLiveData<List<TypeEntry>>()
    val availableTypes: LiveData<List<TypeEntry>> = _availableTypes

    fun loadFilterOptions() = viewModelScope.launch {
        _availableApps.postValue(repo.getDistinctApps())
        _availableTypes.postValue(repo.getDistinctTypes())
    }
}

/** Mean + stddev of a sender's activity hours — passed to the detail sheet */
data class SenderScheduleStats(
    val mean: Double,
    val stdDev: Double,
    val sampleCount: Int
)
