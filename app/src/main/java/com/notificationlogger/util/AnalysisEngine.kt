package com.notificationlogger.util

import com.notificationlogger.data.model.NotificationType

object AnalysisEngine {

    // ─── Topic Classification ──────────────────────────────────────────────────

    private val TOPIC_KEYWORDS = mapOf(
        "Work" to listOf(
            "meeting", "deadline", "task", "project", "pr", "pull request",
            "jira", "ticket", "standup", "review", "deploy", "build", "error",
            "alert", "server", "jenkins", "github", "gitlab", "slack", "teams",
            "zoom", "calendar", "invite", "conference", "report", "email"
        ),
        "Shopping" to listOf(
            "order", "shipped", "delivery", "package", "tracking", "discount",
            "sale", "offer", "deal", "coupon", "cart", "purchase", "payment",
            "receipt", "refund", "amazon", "ebay", "etsy", "checkout", "price"
        ),
        "Social" to listOf(
            "liked", "commented", "mentioned", "tagged", "followed", "friend",
            "message", "dm", "story", "reel", "post", "share", "reaction",
            "reply", "photo", "video", "live", "stream", "invite", "group"
        ),
        "News" to listOf(
            "breaking", "update", "news", "latest", "report", "announced",
            "government", "election", "policy", "economy", "market", "stock",
            "weather", "sports", "score", "match", "game", "result"
        ),
        "Finance" to listOf(
            "transaction", "deposit", "withdrawal", "balance", "transfer",
            "bank", "credit", "debit", "invoice", "subscription", "bill",
            "payment", "overdue", "crypto", "wallet", "spending"
        ),
        "Health" to listOf(
            "reminder", "steps", "calories", "workout", "sleep", "heart rate",
            "medication", "prescription", "appointment", "doctor", "health",
            "activity", "exercise", "water", "weight"
        )
    )

    private val SOCIAL_PACKAGES = setOf(
        "com.facebook.orca", "com.instagram.android", "com.discord",
        "com.viber.voip", "com.whatsapp", "com.snapchat.android",
        "com.twitter.android", "com.reddit.frontpage", "com.tiktok.android"
    )

    fun classifyTopic(text: String, packageName: String = ""): String {
        if (packageName in SOCIAL_PACKAGES) return "Social"
        if (text.isBlank()) return "Other"
        val lower = text.lowercase()
        var bestTopic = "Other"
        var bestScore = 0
        for ((topic, keywords) in TOPIC_KEYWORDS) {
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) { bestScore = score; bestTopic = topic }
        }
        return bestTopic
    }

    // ─── Notification Type Detection ───────────────────────────────────────────
    //
    // Called for every posted notification.
    // Priority: explicit MessagingStyle path → keyword matching on title+text → fallback.
    //
    // For MessagingStyle notifications the service passes isMessagingStyle=true
    // and the raw text of a single message. For regular notifications it passes
    // the notification title + text concatenated.

    fun detectType(
        title: String?,
        text: String?,
        packageName: String,
        androidCategory: String?,       // Notification.category from the Android API
        isMessagingStyle: Boolean = false
    ): String {
        val t  = title?.lowercase().orEmpty()
        val tx = text?.lowercase().orEmpty()
        val combined = "$t $tx".trim()

        // ── 1. Call notifications ──────────────────────────────────────────────
        if (androidCategory == "call" || combined.contains("incoming call") ||
            combined.contains("calling") && !combined.contains("missed"))
            return NotificationType.INCOMING_CALL

        if (combined.containsAny("missed call", "missed video call", "missed voice call",
                "you missed a call"))
            return NotificationType.MISSED_CALL

        if (combined.containsAny("call ended", "call duration"))
            return NotificationType.CALL_ENDED

        // ── 2. Reactions (check BEFORE generic message — reactions have no long text) ──
        // IMPORTANT: only classify as REACTION if there is explicit reaction phrasing.
        // Emoji-only messages (e.g. "❤️" or "😂😂") are still messages, not reactions.
        val hasReactionPhrase = combined.containsAny(
            "reacted to", "reacted with", "liked your", "loved your",
            "laughed at", "emphasized", "questioned your",
            "reacted to your message", "reaction to"
        )
        if (hasReactionPhrase) return NotificationType.REACTION

        // ── 3. Story interactions ──────────────────────────────────────────────
        if (combined.containsAny("reacted to your story", "replied to your story",
                "mentioned you in their story", "viewed your story"))
            return when {
                combined.contains("react") -> NotificationType.STORY_REACTION
                else                       -> NotificationType.STORY_MENTION
            }

        if (combined.containsAny("mentioned you in a story", "added you to their story"))
            return NotificationType.STORY_MENTION

        // ── 4. Post/reel/media interactions ───────────────────────────────────
        if (combined.containsAny("commented on your reel", "replied to your reel"))
            return NotificationType.REEL_COMMENT

        if (combined.containsAny("liked your reel", "reacted to your reel"))
            return NotificationType.REACTION

        if (combined.containsAny("liked your post", "liked your photo",
                "liked your video", "reacted to your post"))
            return NotificationType.POST_LIKE

        if (combined.containsAny("commented on your post", "commented on your photo",
                "replied to your comment", "also commented on"))
            return NotificationType.POST_COMMENT

        if (combined.containsAny("tagged you in", "tagged you in a post",
                "tagged you in a photo", "mentioned you in a post"))
            return NotificationType.TAGGED_IN_POST

        if (combined.containsAny("sent you a reel", "shared a reel",
                "check out this reel", "shared a short"))
            return NotificationType.REEL_SHARED

        if (combined.containsAny("sent a photo", "sent you a photo",
                "shared a photo", "sent an image", "📷", "🖼️"))
            return NotificationType.PHOTO_SHARED

        if (combined.containsAny("sent a video", "sent you a video",
                "shared a video", "📹", "🎥"))
            return NotificationType.VIDEO_SHARED

        if (combined.containsAny("is live", "started a live", "went live", "live now"))
            return NotificationType.LIVE_STREAM

        if (combined.containsAny("started following you", "sent you a friend request",
                "accepted your friend request", "wants to connect", "new follower"))
            return when {
                combined.contains("friend request") -> NotificationType.FRIEND_REQUEST
                else                               -> NotificationType.FOLLOW
            }

        if (combined.containsAny("mentioned you", "mentioned you in", "@"))
            return NotificationType.MENTION

        // ── 5. Stickers / GIFs / Voice ────────────────────────────────────────
        if (combined.containsAny("sent a sticker", "🎭", "(sticker)"))
            return NotificationType.STICKER

        if (combined.containsAny("sent a gif", "sent an animated", "(gif)"))
            return NotificationType.GIF

        if (combined.containsAny("sent a voice message", "sent an audio message",
                "🎤", "🎙️", "voice message", "audio message"))
            return NotificationType.VOICE_MESSAGE

        if (combined.containsAny("sent a video message", "video message"))
            return NotificationType.VIDEO_MESSAGE

        // ── 6. Direct / Group messages ────────────────────────────────────────
        // MessagingStyle with a conversation title → group chat
        if (isMessagingStyle) {
            // if the title contains a group indicator or multiple names
            val hasGroupIndicator = t.contains(",") || t.contains("group") ||
                    t.contains("channel") || t.contains("server") ||
                    t.contains("chat") && !packageName.contains("whatsapp")
            return if (hasGroupIndicator) NotificationType.GROUP_MESSAGE
            else NotificationType.MESSAGE
        }

        if (androidCategory == "msg" || androidCategory == "msg_response")
            return NotificationType.MESSAGE

        // ── 7. Email ──────────────────────────────────────────────────────────
        if (androidCategory == "email" ||
            combined.containsAny("sent you an email", "new email", "new message in"))
            return NotificationType.EMAIL

        // ── 8. Promotional / News / Alerts ────────────────────────────────────
        if (combined.containsAny("sale", "off", "discount", "deal", "offer", "promo",
                "coupon", "limited time", "% off"))
            return NotificationType.PROMOTION

        if (combined.containsAny("breaking", "news", "update", "alert", "warning",
                "critical", "urgent"))
            return if (combined.containsAny("breaking", "news")) NotificationType.NEWS
            else NotificationType.ALERT

        if (combined.containsAny("reminder", "don't forget", "scheduled", "upcoming"))
            return NotificationType.REMINDER

        if (combined.containsAny("download complete", "downloading", "install",
                "update available"))
            return NotificationType.DOWNLOAD

        // ── 9. System ─────────────────────────────────────────────────────────
        if (packageName.startsWith("android") || packageName.startsWith("com.android") ||
            packageName.startsWith("com.google.android.gms"))
            return NotificationType.SYSTEM

        return NotificationType.UNKNOWN
    }

    // ─── Sentiment Scoring ─────────────────────────────────────────────────────

    private val POSITIVE_WORDS = setOf(
        "great", "awesome", "love", "excellent", "amazing", "congrats",
        "congratulations", "win", "won", "success", "happy", "good", "best",
        "free", "deal", "save", "bonus", "reward", "gift", "thanks"
    )
    private val NEGATIVE_WORDS = setOf(
        "fail", "error", "warning", "critical", "urgent", "problem", "issue",
        "overdue", "missing", "declined", "blocked", "suspicious", "alert",
        "virus", "hack", "scam", "danger", "bad", "wrong", "lost", "missed"
    )

    fun scoreSentiment(text: String): Float {
        if (text.isBlank()) return 0f
        val lower = text.lowercase()
        val words = lower.split(Regex("\\W+"))
        val pos = words.count { it in POSITIVE_WORDS }
        val neg = words.count { it in NEGATIVE_WORDS }
        val total = pos + neg
        return if (total == 0) 0f else (pos - neg).toFloat() / total.toFloat()
    }

    // ─── Burst / Doomscroll Detection ─────────────────────────────────────────

    fun detectBursts(
        timestamps: List<Long>,
        windowMs: Long = 2 * 60 * 1000L,
        threshold: Int = 5
    ): List<Pair<Long, Int>> {
        if (timestamps.size < threshold) return emptyList()
        val sorted = timestamps.sorted()
        val result = mutableListOf<Pair<Long, Int>>()
        var i = 0
        while (i < sorted.size) {
            val windowEnd = sorted[i] + windowMs
            var j = i
            while (j < sorted.size && sorted[j] <= windowEnd) j++
            val count = j - i
            if (count >= threshold) result.add(Pair(sorted[i], count))
            i++
        }
        return result
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private fun String.containsAny(vararg tokens: String): Boolean =
        tokens.any { this.contains(it, ignoreCase = true) }
}
