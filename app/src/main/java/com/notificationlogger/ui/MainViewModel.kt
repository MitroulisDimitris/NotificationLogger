package com.notificationlogger.ui

import android.app.Application
import androidx.lifecycle.*
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.data.model.*
import com.notificationlogger.data.repository.NotificationRepository
import com.notificationlogger.util.Event
import kotlinx.coroutines.launch

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

    private val _topApps       = MutableLiveData<List<NotificationSummary>>()
    val topApps: LiveData<List<NotificationSummary>> = _topApps

    private val _hourlyDist    = MutableLiveData<List<HourlyCount>>()
    val hourlyDist: LiveData<List<HourlyCount>> = _hourlyDist

    private val _topicBreakdown = MutableLiveData<List<TopicCount>>()
    val topicBreakdown: LiveData<List<TopicCount>> = _topicBreakdown

    private val _doomScrollEvents = MutableLiveData<List<DoomScrollEvent>>()
    val doomScrollEvents: LiveData<List<DoomScrollEvent>> = _doomScrollEvents

    fun loadAnalysis(sinceDays: Int = 30) = viewModelScope.launch {
        _topApps.postValue(repo.getTopApps(sinceDays))
        _hourlyDist.postValue(repo.getHourlyDistribution(sinceDays))
        _topicBreakdown.postValue(repo.getTopicBreakdown(sinceDays))
        _doomScrollEvents.postValue(repo.getDoomScrollEvents(7))
    }

    // ─── Export ────────────────────────────────────────────────────────────────

    private val _exportEvent = MutableLiveData<Event<List<NotificationLog>>>()
    val exportEvent: LiveData<Event<List<NotificationLog>>> = _exportEvent

    fun loadAllForExport() = viewModelScope.launch {
        _exportEvent.postValue(Event(repo.getAllLogs()))
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
        _senderStats.postValue(repo.getSenderStats(sinceDays, pkg))
    }

    // Per-sender drill-down (used by PersonDetailBottomSheet)
    private val _senderHourly = MutableLiveData<List<HourlyCount>>()
    val senderHourly: LiveData<List<HourlyCount>> = _senderHourly

    private val _senderTypes = MutableLiveData<List<TopicCount>>()
    val senderTypes: LiveData<List<TopicCount>> = _senderTypes

    fun loadSenderDetail(sender: String, pkg: String, sinceDays: Int = 90) = viewModelScope.launch {
        _senderHourly.postValue(repo.getSenderHourlyDist(sender, pkg, sinceDays))
        _senderTypes.postValue(repo.getSenderTypeBreakdown(sender, pkg, sinceDays))
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
            since      = since,
            pkg        = filter.appPackage,
            type       = filter.notificationType,
            sender     = filter.sender,
            query      = filter.textQuery,
            limit      = EXPLORER_PAGE,
            offset     = explorerOffset
        )
        _explorerRows.postValue(
            if (loadMore) ((_explorerRows.value.orEmpty()) + page).toList() else page
        )
        explorerOffset += page.size
    }

    // Filter option pickers
    private val _availableApps = MutableLiveData<List<AppEntry>>()
    val availableApps: LiveData<List<AppEntry>> = _availableApps

    private val _availableTypes = MutableLiveData<List<TypeEntry>>()
    val availableTypes: LiveData<List<TypeEntry>> = _availableTypes

    fun loadFilterOptions() = viewModelScope.launch {
        _availableApps.postValue(repo.getDistinctApps())
        _availableTypes.postValue(repo.getDistinctTypes())
    }
}
