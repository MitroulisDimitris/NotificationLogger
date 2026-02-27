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

class NotificationLoggerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NLService"

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

    // MessagingStyle apps: tracks how many messages already logged per notification key
    private val messagingCounts = mutableMapOf<String, Int>()

    // Regular notifications: tracks last db id per key for removal matching
    private val activeNotifications = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository(applicationContext)
        prefs = PrefsHelper(applicationContext)
        Log.i(TAG, "NotificationLoggerService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "NotificationLoggerService stopped")
    }

    // ─── Posted ────────────────────────────────────────────────────────────────

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
                val appName = getAppName(pkg)
                val isHeadsUp = notification.priority >= Notification.PRIORITY_HIGH ||
                        (notification.flags and Notification.FLAG_HIGH_PRIORITY) != 0

                // Use NotificationCompat (AndroidX) — works across all API levels
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

    /**
     * MessagingStyle: only log messages we haven't seen yet.
     * allMessages contains the full conversation history — we skip everything
     * up to alreadyLogged and only insert the new tail.
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
        val alreadyLogged = messagingCounts[sbn.key] ?: 0
        val newMessages = allMessages.drop(alreadyLogged)

        if (newMessages.isEmpty()) return

        val now = System.currentTimeMillis()
        val conversationTitle = style.conversationTitle?.toString()

        for (msg in newMessages) {
            // NotificationCompat.MessagingStyle.Message exposes .person (AndroidX Person)
            val senderName = msg.person?.name?.toString()

            val msgText = if (logContent) msg.text?.toString() else null
            val msgTime = if (msg.timestamp > 0) msg.timestamp else sbn.postTime

            val title = if (logContent) {
                when {
                    conversationTitle != null && senderName != null -> "$senderName in $conversationTitle"
                    conversationTitle != null -> conversationTitle
                    senderName != null -> senderName
                    else -> null
                }
            } else null

            val topicGroup = AnalysisEngine.classifyTopic(msgText ?: "", pkg)
            val notificationType = AnalysisEngine.detectType(
                title = title,
                text = msgText,
                packageName = pkg,
                androidCategory = sbn.notification.category,
                isMessagingStyle = true
            )

            val log = NotificationLog(
                packageName = pkg,
                appName = appName,
                postTime = msgTime,
                eventTime = now,
                title = title,
                text = msgText,
                bigText = msgText,
                importance = sbn.notification.priority,
                isHeadsUp = isHeadsUp,
                category = sbn.notification.category,
                notificationType = notificationType,
                event = NotificationLog.EVENT_POSTED,
                topicGroup = topicGroup
            )
            val id = repository.insert(log)
            activeNotifications[sbn.key] = id
        }

        messagingCounts[sbn.key] = allMessages.size
    }

    /**
     * Regular notification: skip updates to already-tracked keys (same key = in-place update).
     */
    private suspend fun handleRegularNotification(
        sbn: StatusBarNotification,
        notification: Notification,
        pkg: String,
        appName: String,
        logContent: Boolean,
        isHeadsUp: Boolean
    ) {
        if (sbn.key in activeNotifications) return

        val extras: Bundle = notification.extras ?: Bundle()
        val now = System.currentTimeMillis()

        val title   = if (logContent) extras.getString(Notification.EXTRA_TITLE) else null
        val text    = if (logContent) extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() else null
        val bigText = if (logContent) {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        } else null
        val subText  = if (logContent) extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() else null
        val infoText = if (logContent) extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() else null

        val combined = listOfNotNull(title, text, bigText).joinToString(" ")
        val topicGroup = AnalysisEngine.classifyTopic(combined, pkg)
        val notificationType = AnalysisEngine.detectType(
            title = title,
            text = text ?: bigText,
            packageName = pkg,
            androidCategory = notification.category,
            isMessagingStyle = false
        )

        val log = NotificationLog(
            packageName = pkg,
            appName = appName,
            postTime = sbn.postTime,
            eventTime = now,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            infoText = infoText,
            importance = notification.priority,
            isHeadsUp = isHeadsUp,
            category = notification.category,
            notificationType = notificationType,
            event = NotificationLog.EVENT_POSTED,
            topicGroup = topicGroup
        )

        val id = repository.insert(log)
        activeNotifications[sbn.key] = id
    }

    // ─── Removed ───────────────────────────────────────────────────────────────

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val pkg = sbn.packageName
        activeNotifications.remove(sbn.key)
        messagingCounts.remove(sbn.key)

        val event = when (reason) {
            REASON_CLICK      -> NotificationLog.EVENT_CLICKED
            REASON_CANCEL_ALL,
            REASON_CANCEL     -> NotificationLog.EVENT_DISMISSED
            else              -> NotificationLog.EVENT_APP_CANCEL
        }

        serviceScope.launch {
            try {
                val log = NotificationLog(
                    packageName = pkg,
                    appName = getAppName(pkg),
                    postTime = sbn.postTime,
                    eventTime = System.currentTimeMillis(),
                    event = event,
                    removalReason = reason
                )
                repository.insert(log)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging removal for $pkg", e)
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
