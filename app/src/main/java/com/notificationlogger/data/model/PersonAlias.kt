package com.notificationlogger.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores non-destructive name alias groups.
 * Multiple raw sender names can share the same canonicalName.
 * The original data is never modified — aliases are resolved at query time.
 *
 * Example:
 *   rawName = "Alex 🔥",         canonicalName = "Alex"
 *   rawName = "Alex",            canonicalName = "Alex"
 *   rawName = "Alex (work)",     canonicalName = "Alex"
 */
@Entity(
    tableName = "person_aliases",
    indices = [Index(value = ["rawName"], unique = true)]
)
data class PersonAlias(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawName: String,        // exactly as it appears in notification title
    val canonicalName: String,  // the display name to group under
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Projection used in the People list after alias resolution.
 * Same structure as SenderStats but `sender` is the canonical name.
 */
data class ResolvedSenderStats(
    val sender: String,         // canonical name (after alias resolution)
    val rawNames: String,       // comma-joined list of raw names in this group
    val packageName: String,
    val appName: String,
    val messageCount: Int,
    val lastSeen: Long,
    val peakHour: Int,
    val topType: String,
    // Statistics for schedule detection
    val stdDevHour: Double,     // std deviation of activity hours (low = very scheduled)
    val meanHour: Double        // mean activity hour
)

/** Per-day-of-week counts for day pattern chart */
data class DayOfWeekCount(
    val dayOfWeek: Int,  // 1=Sunday, 2=Monday … 7=Saturday (SQLite strftime %w = 0=Sun)
    val count: Int
)

/** Per-hour per-day-of-week for the detailed heatmap */
data class HourDayCount(
    val hour: Int,       // 0-23
    val dayOfWeek: Int,  // 0=Sun … 6=Sat
    val count: Int
)
