# 🔔 Notification Logger

A privacy-first Android app that captures, stores, and analyzes every notification on your device — entirely offline. No internet permission, no ads, no cloud sync. All data lives in a local SQLite database only you can access.

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Setup](#setup)
- [Navigation](#navigation)
- [Features](#features)
  - [Home Dashboard](#-home-dashboard)
  - [Explorer](#-explorer)
  - [People](#-people)
  - [Analysis](#-analysis)
  - [Settings](#-settings)
- [Notification Capture](#notification-capture)
- [Notification Type Detection](#notification-type-detection)
- [Topic Classification](#topic-classification)
- [CSV Export](#csv-export)
- [Privacy & Security](#privacy--security)
- [Database Schema](#database-schema)
- [Project Structure](#project-structure)
- [Dependencies](#dependencies)

---

## Overview

Notification Logger runs a background `NotificationListenerService` that intercepts every notification posted to your Android device and writes a structured record to a local Room (SQLite) database. You can then explore the data through five built-in screens, filter and search like a spreadsheet, view per-person stats with charts, and export everything to CSV for deeper analysis in Python, Excel, or R.

---

## Requirements

| Item | Requirement |
|------|-------------|
| Android version | 8.0 (API 26) or higher |
| Target SDK | 34 (Android 14) |
| Permission required | Notification Listener Access (granted manually in system settings) |
| Internet | **Not required — not requested** |

---

## Setup

1. Install the APK on your device
2. Open the app — it will prompt you to grant Notification Listener access
3. Go to **Settings → Apps → Special App Access → Notification Access** → enable **Notification Logger**
4. (Recommended) Go to **Settings → Battery → Battery Optimization** → set Notification Logger to **Don't optimize**
5. Return to the app — logging begins immediately

> **Samsung devices:** Also disable *Sleeping apps* for Notification Logger  
> **Xiaomi devices:** Enable *Autostart* for Notification Logger

---

## Navigation

The app uses a bottom navigation bar with five tabs:

| Tab | Icon | Purpose |
|-----|------|---------|
| **Home** | 🏠 | Dashboard summary of recent activity |
| **Explorer** | 🔍 | Filterable spreadsheet-style data table |
| **People** | 👥 | Per-sender stats and activity charts |
| **Analysis** | 📊 | Aggregate stats, heatmaps, topic breakdown |
| **Settings** | ⚙️ | Privacy controls, app filters, data management |

---

## Features

### 🏠 Home Dashboard

A quick-glance overview of your notification activity.

- **Total notification count** across all time
- **Top 5 apps** by volume over the last 30 days
- **Peak hour** — the hour of day you receive the most notifications
- **Top topic group** — the most common subject category
- **Doomscroll alerts** — number of burst events detected in the last 7 days (5+ notifications from one app within 2 minutes)

---

### 🔍 Explorer

A spreadsheet-style, fully filterable view of every logged notification. Designed for quick lookups and targeted searches.

**Columns displayed:**

| Column | Content |
|--------|---------|
| TIME | Date and time (MM/dd HH:mm) |
| APP | App name |
| FROM | Sender name (notification title) |
| MESSAGE | Notification text (up to 80 characters) |
| TYPE | Color-coded notification type badge |

**Filter controls:**

- **Full-text search** — searches title, text, and expanded (bigText) content simultaneously
- **Sender filter** — substring match on the sender/title field
- **App dropdown** — dynamically populated from apps actually present in your data
- **Type dropdown** — dynamically populated from notification types present in your data
- **Time range** — 7 days / 30 days / 90 days / All time
- **Clear button** — resets all filters at once

**Performance:**
- Loads 60 rows at a time with automatic infinite scroll (loads next page when you reach the bottom)
- Type badge colors: blue = messages, orange = reactions, green = calls, purple = media (photos/videos/reels), pink = likes and post interactions

---

### 👥 People

Per-sender intelligence. Shows everyone who has sent you 2 or more notifications, sorted by total count.

**People list columns:**
- Color-coded avatar with name initial (color is deterministic — same person always gets the same color)
- Name and source app
- Total notification count
- Peak activity hour
- Last seen timestamp
- Most common notification type from that person

**Search:** Filter by name or app name in real time.

**Time range:** 7 days / 30 days / 90 days / All time.

**Tap any person** to open a detail bottom sheet containing:

- Header with avatar, app, total count, peak hour, last seen, top type
- **Hourly activity bar chart** — 24 vertical bars (one per hour), peak hour highlighted in green. Shows when this specific person tends to message you.
- **Notification type breakdown chart** — horizontal bar chart showing the split between MESSAGE, REACTION, PHOTO_SHARED, VOICE_MESSAGE, etc. for that person specifically.

---

### 📊 Analysis

Aggregate statistics with configurable time ranges (7 / 14 / 30 / 90 days).

- **Top apps ranking** — sorted by notification volume
- **Hourly heatmap** — ASCII bar chart showing which hours of day are busiest
- **Topic breakdown** — pie-style breakdown of Work / Social / Shopping / News / Finance / Health / Other
- **Doomscroll bursts** — list of detected burst events (5+ notifications from one app within a 2-minute window), showing app, time, and count
- **CSV export button** — exports entire database to a CSV file and opens the Android share sheet

---

### ⚙️ Settings

**Privacy controls:**
- **Log notification content toggle** — when off, only metadata is recorded (app, timestamp, event type). Title, text, and message body are never written to the database. Useful for sensitive apps.
- **Biometric / PIN lock toggle** — requires fingerprint or device PIN to open the app. Disabled automatically if the device has no enrolled biometrics.

**Data management:**
- All logs are kept indefinitely until you choose to delete them
- **Delete ALL Logs** button — permanently wipes the entire database after confirmation

**App filters:**
- **Blacklist** — add an app by package name to completely exclude it from logging (e.g. banking apps, 2FA apps, password managers)
- **Whitelist** — when any whitelist entry exists, *only* whitelisted apps are logged (everything else is ignored)
- Existing filters shown in a list with type badge and remove button
- Blacklist and whitelist are mutually exclusive in effect — whitelist mode takes priority when non-empty

---

## Notification Capture

The `NotificationLoggerService` extends Android's `NotificationListenerService` and handles two distinct notification formats:

### MessagingStyle notifications (WhatsApp, Instagram, Discord, Viber, Telegram, Snapchat, Messenger, Twitter DMs, Reddit, TikTok)

These apps bundle the entire conversation history inside a single notification that gets *updated* each time a new message arrives. The service:

1. Extracts `NotificationCompat.MessagingStyle` from the notification
2. Tracks how many messages have already been logged per notification key (`messagingCounts` map)
3. On each update, only logs the **new messages** (`allMessages.drop(alreadyLogged)`)
4. Each message gets its own database row with sender name, message text, and precise timestamp
5. Group chats vs direct messages are distinguished by the presence of a conversation title

This prevents the duplicate-counting bug where sending 3 messages would create entries for "1 message", "2 messages", "3 messages" instead of three individual message rows.

### Regular notifications (all other apps)

1. Checks if the notification key is already tracked (i.e. an in-place update like a progress bar)
2. If already tracked → **skip** (avoids duplicate entries for the same notification being refreshed)
3. If new → extracts title, text, bigText, subText, importance, category, and heads-up status
4. Writes a single POSTED record

### Removal tracking

When any notification is dismissed or acted upon, a second record is written with the appropriate event type:

| Reason | Event recorded |
|--------|---------------|
| User tapped the notification | `CLICKED` |
| User swiped it away | `DISMISSED` |
| App cancelled it programmatically | `APP_CANCEL` |

Both tracking maps (`messagingCounts` and `activeNotifications`) are cleaned up on removal to prevent memory growth.

---

## Notification Type Detection

Every logged notification is classified into one of 30+ fine-grained types by `AnalysisEngine.detectType()`. Detection runs in priority order to avoid misclassification:

| Priority | Category | Types |
|----------|----------|-------|
| 1 | **Calls** | `INCOMING_CALL`, `MISSED_CALL`, `CALL_ENDED` |
| 2 | **Reactions** | `REACTION` — checked before messages because reactions have no body text |
| 3 | **Story interactions** | `STORY_REACTION`, `STORY_MENTION` |
| 4 | **Post / reel interactions** | `POST_LIKE`, `POST_COMMENT`, `REEL_COMMENT`, `TAGGED_IN_POST`, `REEL_SHARED`, `LIVE_STREAM` |
| 5 | **Media messages** | `PHOTO_SHARED`, `VIDEO_SHARED` |
| 6 | **Social actions** | `FOLLOW`, `FRIEND_REQUEST`, `MENTION` |
| 7 | **Rich media messages** | `STICKER`, `GIF`, `VOICE_MESSAGE`, `VIDEO_MESSAGE` |
| 8 | **Text messages** | `MESSAGE` (direct), `GROUP_MESSAGE` (in group/channel) |
| 9 | **Email** | `EMAIL` |
| 10 | **Utility** | `PROMOTION`, `NEWS`, `ALERT`, `REMINDER`, `DOWNLOAD` |
| 11 | **System** | `SYSTEM` (com.android.* packages) |
| 12 | **Fallback** | `UNKNOWN` |

Detection uses keyword matching on the notification title and text, the Android `notification.category` field, and the `isMessagingStyle` flag passed from the service.

---

## Topic Classification

Separate from notification type, each notification is also assigned a **topic group** based on keyword matching across title + text + bigText:

| Topic | Example keywords |
|-------|-----------------|
| **Work** | meeting, deadline, pull request, jira, deploy, github, zoom |
| **Shopping** | shipped, delivery, order, discount, coupon, receipt |
| **Social** | liked, commented, tagged, story, reel, dm, reaction |
| **News** | breaking, election, market, weather, score |
| **Finance** | transaction, balance, invoice, crypto, bank |
| **Health** | steps, workout, medication, appointment, calories |
| **Other** | anything that doesn't match the above |

Social media apps (WhatsApp, Instagram, Discord, etc.) are always assigned **Social** regardless of content.

---

## CSV Export

Triggered from the Analysis tab. Exports all records to a timestamped CSV file in app cache, then opens the Android share sheet so you can send it to any app (Files, Google Drive, email, etc.).

**CSV columns:**

| Column | Description |
|--------|-------------|
| `id` | Auto-incremented database ID |
| `packageName` | App package name (e.g. `com.whatsapp`) |
| `appName` | Human-readable app name |
| `event` | `POSTED` / `DISMISSED` / `CLICKED` / `APP_CANCEL` |
| `notificationType` | Fine-grained type (e.g. `REACTION`, `VOICE_MESSAGE`) |
| `postTime` | Unix timestamp in milliseconds |
| `postTimeFormatted` | Human-readable (`yyyy-MM-dd HH:mm:ss`) |
| `eventTime` | When the service recorded the event |
| `removedTime` | When the notification was removed (nullable) |
| `title` | Notification title / sender name |
| `text` | Short notification text |
| `bigText` | Expanded text / full message body |
| `subText` | Sub-text field |
| `importance` | Android importance level integer |
| `isHeadsUp` | Whether it appeared as a heads-up popup |
| `category` | Android notification category string |
| `topicGroup` | Work / Social / Shopping / News / Finance / Health / Other |
| `removalReason` | Android REASON_* constant integer |

**Python analysis example:**
```python
import pandas as pd

df = pd.read_csv('notifications_20250227_143022.csv')

# Who messages you most
df[df['event'] == 'POSTED'].groupby('title').size().sort_values(ascending=False).head(10)

# What time does a specific person message you?
person = df[(df['title'] == 'Alice') & (df['event'] == 'POSTED')]
person['hour'] = pd.to_datetime(person['postTime'], unit='ms').dt.hour
person.groupby('hour').size().plot(kind='bar', title='Alice activity by hour')

# Reaction vs message ratio per app
types = df[df['event'] == 'POSTED'].groupby(['appName', 'notificationType']).size().unstack(fill_value=0)

# Ignore rate (dismissed without clicking)
posted    = df[df['event'] == 'POSTED'].groupby('packageName').size()
dismissed = df[df['event'] == 'DISMISSED'].groupby('packageName').size()
ignore_rate = (dismissed / posted).sort_values(ascending=False)
```

---

## Privacy & Security

| Feature | Detail |
|---------|--------|
| **No internet permission** | `INTERNET` is not declared in the manifest — data cannot leave the device by any network path |
| **Local storage only** | All data is stored in `/data/data/com.notificationlogger/databases/` — accessible only to the app |
| **No cloud backup** | `android:allowBackup="false"` in manifest prevents Android backup from copying the database |
| **Biometric gate** | Optional fingerprint/PIN lock on app open |
| **Content logging toggle** | Disable to log only metadata (app name, timestamp, event type) — message text is never written |
| **Blacklist** | Exclude any app entirely (recommended: banking apps, 2FA apps, password managers) |
| **Whitelist mode** | Log only specific apps — everything else is silently ignored |

> **Recommended apps to blacklist immediately:**  
> Google Authenticator (`com.google.android.apps.authenticator2`), Authy (`com.authy.authy`), your banking app, and any password manager.

---

## Database Schema

**Table: `notification_logs`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER PK | Auto-increment |
| `packageName` | TEXT | App package name (indexed) |
| `appName` | TEXT | Display name |
| `postTime` | INTEGER | Unix ms, when notification arrived (indexed) |
| `eventTime` | INTEGER | Unix ms, when service recorded it |
| `removedTime` | INTEGER? | Unix ms, when removed |
| `title` | TEXT? | Notification title (null if content logging off) |
| `text` | TEXT? | Short text |
| `bigText` | TEXT? | Expanded text / full message |
| `subText` | TEXT? | Sub-text |
| `infoText` | TEXT? | Info text |
| `importance` | INTEGER | Android importance level |
| `isHeadsUp` | INTEGER | 1 if appeared as heads-up |
| `category` | TEXT? | Android category string |
| `notificationType` | TEXT | Fine-grained type (indexed) |
| `event` | TEXT | POSTED / DISMISSED / CLICKED / APP_CANCEL (indexed) |
| `removalReason` | INTEGER | Android REASON_* constant |
| `topicGroup` | TEXT? | Work / Social / Shopping / News / Finance / Health / Other |

**Table: `app_filter`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER PK | Auto-increment |
| `packageName` | TEXT UNIQUE | App package name |
| `appName` | TEXT | Display name |
| `filterType` | TEXT | `BLACKLIST` or `WHITELIST` |
| `addedAt` | INTEGER | Unix ms when added |

**Database version:** 2  
**Migration v1→v2:** Adds `notificationType` column with default `'UNKNOWN'`

---

## Project Structure

```
app/src/main/java/com/notificationlogger/
│
├── MainActivity.kt                    # Entry point, biometric gate, nav setup
│
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt             # Room database, migrations
│   │   ├── NotificationDao.kt         # All SQL queries
│   │   └── AppFilterDao.kt            # Filter (blacklist/whitelist) queries
│   ├── model/
│   │   ├── NotificationLog.kt         # Main entity + NotificationType constants
│   │   └── SenderStats.kt             # Query projections (SenderStats, ExplorerRow, etc.)
│   └── repository/
│       └── NotificationRepository.kt  # Data access layer
│
├── service/
│   └── NotificationLoggerService.kt   # Core NotificationListenerService
│
├── ui/
│   ├── MainViewModel.kt               # Shared ViewModel for all fragments
│   ├── dashboard/DashboardFragment.kt
│   ├── explorer/ExplorerFragment.kt
│   ├── logs/LogsFragment.kt
│   ├── people/
│   │   ├── PeopleFragment.kt
│   │   └── PersonDetailBottomSheet.kt
│   ├── analysis/AnalysisFragment.kt
│   └── settings/SettingsFragment.kt
│
├── util/
│   ├── AnalysisEngine.kt              # Topic classification + type detection + burst detection
│   ├── BiometricHelper.kt             # Biometric/PIN authentication wrapper
│   ├── CsvExporter.kt                 # CSV generation and share intent
│   ├── Event.kt                       # Single-fire LiveData wrapper
│   ├── PrefsHelper.kt                 # SharedPreferences wrapper
│   └── RetentionWorker.kt             # WorkManager job (available but not auto-scheduled)
│
└── widget/
    └── BarChartView.kt                # Custom bar chart (vertical + horizontal, no library)
```

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.room` | 2.6.1 | SQLite ORM with annotation processing |
| `androidx.navigation` | 2.7.6 | Fragment navigation and back stack |
| `androidx.lifecycle` | 2.7.0 | ViewModel, LiveData, coroutine scopes |
| `kotlinx.coroutines` | 1.7.3 | Async database and service operations |
| `androidx.biometric` | 1.1.0 | Fingerprint / PIN authentication |
| `androidx.work` | 2.9.0 | WorkManager (available for scheduled tasks) |
| `androidx.recyclerview` | 1.3.2 | Scrollable lists throughout the app |
| `com.google.android.material` | 1.11.0 | BottomSheet, Material components |
| `androidx.core:core-ktx` | 1.12.0 | Kotlin Android extensions |
