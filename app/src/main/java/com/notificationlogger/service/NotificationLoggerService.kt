package com.notificationlogger.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.notificationlogger.data.model.NotificationLog
import com.notificationlogger.data.repository.NotificationRepository
import com.notificationlogger.util.AnalysisEngine
import com.notificationlogger.util.PrefsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotificationLoggerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NLService"

        // How long (ms) a dedup fingerprint is kept in the seen-set.
        // Any duplicate arriving within this window is dropped.
        private const val DEDUP_WINDOW_MS = 5_000L

        val MESSENGER_PACKAGES = setOf(
            "com.facebook.orca",
            "com.instagram.android",
            "com.discord",
            "com.viber.voip",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.tiktok.android",
        )
    }

    private lateinit var repository: NotificationRepository
    private lateinit var prefs: PrefsHelper
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Dedup state ─────────────────────────────────────────────────────────────
    //
    // All three maps are only ever touched inside `dedupMutex`. Because
    // onNotificationPosted can fire on the main thread while coroutines run on
    // IO threads, we need a coroutine-safe lock around every read-modify-write.
    //
    // The mutex is the key fix: previously two coroutines could both read
    // messagingCounts[key] = 0 before either wrote the updated count back,
    // resulting in every message being logged twice.

    private val dedupMutex = Mutex()

    // MessagingStyle: how many messages we've already logged for a given key
    private val messagingCounts = mutableMapOf<String, Int>()

    // Regular notifications: tracks which sbn.key values are currently live
    // (posted but not yet removed), so we skip in-place updates
    private val activeNotifications = mutableMapOf<String, Long>()

    // Fine-grained dedup: fingerprint = "pkg|title|text|msgTimestamp"
    // Maps fingerprint -> System.currentTimeMillis() when it was first seen.
    // Any identical fingerprint arriving within DEDUP_WINDOW_MS is dropped.
    // Entries are pruned on every call to keep memory bounded.
    private val seenFingerprints = mutableMapOf<String, Long>()

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository(applicationContext)
        prefs      = PrefsHelper(applicationContext)
        Log.i(TAG, "NotificationLoggerService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "NotificationLoggerService stopped")
    }

    // ── Posted ───────────────────────────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return

        serviceScope.launch {
            try {
                if (repository.isBlacklisted(pkg)) return@launch
                val whitelist = repository.getWhitelist()
                if (whitelist.isNotEmpty() && pkg !in whitelist) return@launch

                val notification = sbn.notification
                val logContent = prefs.isContentLoggingEnabled()
                val appName    = getAppName(pkg)
                val isHeadsUp  = notification.priority >= Notification.PRIORITY_HIGH ||
                                 (notification.flags and Notification.FLAG_HIGH_PRIORITY) != 0

                val style = NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(notification)

                if (style != null) {
                    handleMessagingStyle(sbn, style, pkg, appName, logContent, isHeadsUp)
                } else {
                    handleRegularNotification(sbn, notification, pkg, appName, logContent, isHeadsUp)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error logging notification from $pkg", e)
            }
        }
    }

    // ── MessagingStyle ───────────────────────────────────────────────────────────

    /**
     * MessagingStyle notifications carry the full conversation history on every
     * update. We track how many messages we've already logged per sbn.key and
     * only insert the tail.
     *
     * The entire read-check-write is inside dedupMutex so that rapid duplicate
     * firings (the most common cause of doubled rows) cannot both see
     * alreadyLogged=0 and race to insert the same messages.
     */
    private suspend fun handleMessagingStyle(
        sbn: StatusBarNotification,
        style: NotificationCompat.MessagingStyle,
        pkg: String,
        appName: String,
        logContent: Boolean,
        isHeadsUp: Boolean
    ) {
        val allMessages = style.messages
        val now = System.currentTimeMillis()
        val conversationTitle = style.conversationTitle?.toString()

        // Prune stale fingerprints while we already hold the lock below
        dedupMutex.withLock {
            pruneFingerprints(now)

            val alreadyLogged = messagingCounts[sbn.key] ?: 0
            val newMessages   = allMessages.drop(alreadyLogged)
            if (newMessages.isEmpty()) return

            for (msg in newMessages) {
                val senderName = msg.person?.name?.toString()
                val msgText    = if (logContent) msg.text?.toString() else null
                val msgTime    = if (msg.timestamp > 0) msg.timestamp else sbn.postTime

                val title = if (logContent) {
                    when {
                        conversationTitle != null && senderName != null ->
                            "$senderName in $conversationTitle"
                        conversationTitle != null -> conversationTitle
                        senderName != null        -> senderName
                        else                      -> null
                    }
                } else null

                // Per-message fingerprint dedup (catches duplicate fires
                // that slip past the messagingCounts check)
                val fingerprint = "$pkg|${title.orEmpty()}|${msgText.orEmpty()}|$msgTime"
                if (seenFingerprints.containsKey(fingerprint)) continue
                seenFingerprints[fingerprint] = now

                val topicGroup = AnalysisEngine.classifyTopic(msgText ?: "", pkg)
                val notificationType = AnalysisEngine.detectType(
                    title             = title,
                    text              = msgText,
                    packageName       = pkg,
                    androidCategory   = sbn.notification.category,
                    isMessagingStyle  = true
                )

                val log = NotificationLog(
                    packageName      = pkg,
                    appName          = appName,
                    postTime         = msgTime,
                    eventTime        = now,
                    title            = title,
                    text             = msgText,
                    bigText          = msgText,
                    importance       = sbn.notification.priority,
                    isHeadsUp        = isHeadsUp,
                    category         = sbn.notification.category,
                    notificationType = notificationType,
                    event            = NotificationLog.EVENT_POSTED,
                    topicGroup       = topicGroup
                )
                val id = repository.insert(log)
                activeNotifications[sbn.key] = id
            }

            // Update count INSIDE the lock so the next call sees the correct value
            messagingCounts[sbn.key] = allMessages.size
        }
    }

    // ── Regular notifications ────────────────────────────────────────────────────

    /**
     * Non-MessagingStyle: skip if this key is already active (in-place update),
     * AND check the fingerprint set for rapid re-fires of the same content.
     */
    private suspend fun handleRegularNotification(
        sbn: StatusBarNotification,
        notification: Notification,
        pkg: String,
        appName: String,
        logContent: Boolean,
        isHeadsUp: Boolean
    ) {
        val extras  = notification.extras ?: Bundle()
        val title   = if (logContent) extras.getString(Notification.EXTRA_TITLE) else null
        val text    = if (logContent) extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() else null
        val bigText = if (logContent) {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        } else null
        val subText  = if (logContent) extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() else null
        val infoText = if (logContent) extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() else null
        val now      = System.currentTimeMillis()

        dedupMutex.withLock {
            pruneFingerprints(now)

            // Skip in-place updates (same key already tracked)
            if (sbn.key in activeNotifications) return

            // Fingerprint: key + postTime is sufficient for regular notifications
            // (sbn.key already encodes pkg + notification id + tag + user)
            val fingerprint = "${sbn.key}|${sbn.postTime}"
            if (seenFingerprints.containsKey(fingerprint)) return
            seenFingerprints[fingerprint] = now

            val combined        = listOfNotNull(title, text, bigText).joinToString(" ")
            val topicGroup      = AnalysisEngine.classifyTopic(combined, pkg)
            val notificationType = AnalysisEngine.detectType(
                title            = title,
                text             = text ?: bigText,
                packageName      = pkg,
                androidCategory  = notification.category,
                isMessagingStyle = false
            )

            val log = NotificationLog(
                packageName      = pkg,
                appName          = appName,
                postTime         = sbn.postTime,
                eventTime        = now,
                title            = title,
                text             = text,
                bigText          = bigText,
                subText          = subText,
                infoText         = infoText,
                importance       = notification.priority,
                isHeadsUp        = isHeadsUp,
                category         = notification.category,
                notificationType = notificationType,
                event            = NotificationLog.EVENT_POSTED,
                topicGroup       = topicGroup
            )

            val id = repository.insert(log)
            activeNotifications[sbn.key] = id
        }
    }

    // ── Removed ───────────────────────────────────────────────────────────────────

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        serviceScope.launch {
            dedupMutex.withLock {
                activeNotifications.remove(sbn.key)
                messagingCounts.remove(sbn.key)
                // Fingerprints are time-expired, not key-expired — leave them
            }

            val event = when (reason) {
                REASON_CLICK                  -> NotificationLog.EVENT_CLICKED
                REASON_CANCEL_ALL, REASON_CANCEL -> NotificationLog.EVENT_DISMISSED
                else                          -> NotificationLog.EVENT_APP_CANCEL
            }

            try {
                val log = NotificationLog(
                    packageName   = sbn.packageName,
                    appName       = getAppName(sbn.packageName),
                    postTime      = sbn.postTime,
                    eventTime     = System.currentTimeMillis(),
                    event         = event,
                    removalReason = reason
                )
                repository.insert(log)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging removal for ${sbn.packageName}", e)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Remove fingerprints older than DEDUP_WINDOW_MS. Call inside dedupMutex. */
    private fun pruneFingerprints(now: Long) {
        val cutoff = now - DEDUP_WINDOW_MS
        val iter   = seenFingerprints.iterator()
        while (iter.hasNext()) {
            if (iter.next().value < cutoff) iter.remove()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
