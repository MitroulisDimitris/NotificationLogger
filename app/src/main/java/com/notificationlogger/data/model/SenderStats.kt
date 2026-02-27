package com.notificationlogger.data.model

/**
 * Returned by the per-sender aggregation query.
 * Field names must exactly match SQL column aliases.
 */
data class SenderStats(
    val sender: String,
    val packageName: String,
    val appName: String,
    val messageCount: Int,
    val lastSeen: Long,
    val peakHour: Int,
    val topType: String
)

/**
 * One row in the Explorer table view.
 * Field names must exactly match SQL column aliases.
 */
data class ExplorerRow(
    val id: Long,
    val postTime: Long,
    val appName: String,
    val packageName: String,
    val sender: String?,
    val text: String?,
    val notificationType: String,
    val topicGroup: String?
)

/**
 * Filter state passed from ExplorerFragment to ViewModel.
 * Not a Room type — just a plain Kotlin data class.
 */
data class ExplorerFilter(
    val appPackage: String? = null,
    val notificationType: String? = null,
    val sender: String? = null,
    val textQuery: String? = null,
    val sinceDays: Int = 30
)

/**
 * Projection for the app-picker spinner.
 * Field names match the SQL column names exactly.
 */
data class AppEntry(
    val packageName: String,
    val appName: String
)

/**
 * Projection for the type-picker spinner.
 * Field name matches the SQL column name exactly.
 */
data class TypeEntry(
    val notificationType: String
)
