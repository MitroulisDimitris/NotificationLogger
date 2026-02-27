package com.notificationlogger.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "notification_logs",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["postTime"]),
        Index(value = ["event"]),
        Index(value = ["notificationType"])
    ]
)
data class NotificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // App info
    val packageName: String,
    val appName: String,

    // Timing
    val postTime: Long,            // Unix timestamp (ms) when notification arrived
    val eventTime: Long,           // Unix timestamp (ms) when we recorded it
    val removedTime: Long? = null, // When it was dismissed/clicked

    // Content (null if privacy mode is on)
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,   // Expanded content (messages, emails, etc.)
    val subText: String? = null,
    val infoText: String? = null,

    // Classification
    val importance: Int = 0,
    val isHeadsUp: Boolean = false,
    val category: String? = null,  // Android notification category (msg, email, social…)

    /**
     * Fine-grained content type — what kind of notification is this?
     * See NotificationType constants below.
     */
    val notificationType: String = NotificationType.UNKNOWN,

    // Removal info
    val event: String = EVENT_POSTED,
    val removalReason: Int = 0,

    // Keyword grouping
    val topicGroup: String? = null
) {
    companion object {
        const val EVENT_POSTED     = "POSTED"
        const val EVENT_DISMISSED  = "DISMISSED"
        const val EVENT_CLICKED    = "CLICKED"
        const val EVENT_APP_CANCEL = "APP_CANCEL"
    }
}

/**
 * Fine-grained notification content types.
 * Stored as strings in the DB so they're human-readable in CSV exports.
 */
object NotificationType {
    // ── Messaging ────────────────────────────────────────────────────────────
    const val MESSAGE          = "MESSAGE"           // plain text message
    const val GROUP_MESSAGE    = "GROUP_MESSAGE"     // message in a group/channel
    const val REPLY            = "REPLY"             // someone replied to your message
    const val REACTION         = "REACTION"          // emoji/like reaction to a message
    const val STICKER          = "STICKER"           // sticker sent
    const val GIF              = "GIF"               // GIF sent
    const val VOICE_MESSAGE    = "VOICE_MESSAGE"     // voice/audio message
    const val VIDEO_MESSAGE    = "VIDEO_MESSAGE"     // video clip in chat

    // ── Media / Social ───────────────────────────────────────────────────────
    const val PHOTO_SHARED     = "PHOTO_SHARED"      // photo sent to you / tagged
    const val VIDEO_SHARED     = "VIDEO_SHARED"      // video shared with you
    const val REEL_SHARED      = "REEL_SHARED"       // reel/short shared
    const val STORY_MENTION    = "STORY_MENTION"     // mentioned in a story
    const val STORY_REACTION   = "STORY_REACTION"    // reaction to your story
    const val POST_LIKE        = "POST_LIKE"         // like on your post
    const val POST_COMMENT     = "POST_COMMENT"      // comment on your post
    const val TAGGED_IN_POST   = "TAGGED_IN_POST"    // tagged in a post/photo
    const val LIVE_STREAM      = "LIVE_STREAM"       // someone went live
    const val REEL_COMMENT     = "REEL_COMMENT"      // comment on a reel

    // ── Social actions ───────────────────────────────────────────────────────
    const val FOLLOW           = "FOLLOW"            // new follower / friend request
    const val MENTION          = "MENTION"           // @mentioned somewhere
    const val FRIEND_REQUEST   = "FRIEND_REQUEST"

    // ── Calls ────────────────────────────────────────────────────────────────
    const val INCOMING_CALL    = "INCOMING_CALL"
    const val MISSED_CALL      = "MISSED_CALL"
    const val CALL_ENDED       = "CALL_ENDED"

    // ── System / App ─────────────────────────────────────────────────────────
    const val EMAIL            = "EMAIL"
    const val REMINDER         = "REMINDER"
    const val ALERT            = "ALERT"             // warning, error, system alert
    const val PROMOTION        = "PROMOTION"         // marketing / promotional
    const val NEWS             = "NEWS"
    const val DOWNLOAD         = "DOWNLOAD"          // download progress/complete
    const val SYSTEM           = "SYSTEM"            // OS-level notification

    const val UNKNOWN          = "UNKNOWN"
}

// ── Lightweight projections for analysis queries ──────────────────────────────

data class NotificationSummary(
    val packageName: String,
    val appName: String,
    val count: Int,
    val lastSeen: Long
)

data class HourlyCount(
    val hour: Int,
    val count: Int
)

data class TopicCount(
    val topicGroup: String,
    val count: Int
)

data class DoomScrollEvent(
    val packageName: String,
    val appName: String,
    val windowStart: Long,
    val burstCount: Int
)
