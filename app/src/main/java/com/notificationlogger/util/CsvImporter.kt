package com.notificationlogger.util

import android.content.Context
import android.net.Uri
import com.notificationlogger.data.model.NotificationLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object CsvImporter {

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val errors: List<String>
    )

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Parses a CSV file (same format as CsvExporter output) and returns
     * a list of NotificationLog objects ready to insert.
     *
     * Expected header:
     * id,packageName,appName,event,notificationType,postTime,postTimeFormatted,
     * eventTime,removedTime,title,text,bigText,subText,
     * importance,isHeadsUp,category,topicGroup,removalReason
     */
    fun parse(context: Context, uri: Uri): ImportResult {
        val logs = mutableListOf<NotificationLog>()
        val errors = mutableListOf<String>()
        var skipped = 0
        var lineNum = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val header = reader.readLine() ?: return ImportResult(0, 0, listOf("Empty file"))
                    lineNum = 1

                    // Build column index map from actual header (tolerant of column order)
                    val cols = parseCsvLine(header).mapIndexed { i, name -> name.trim() to i }.toMap()

                    fun col(name: String): Int = cols[name] ?: -1

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNum++
                        val raw = line ?: continue
                        if (raw.isBlank()) continue

                        try {
                            val fields = parseCsvLine(raw)
                            fun get(name: String): String? {
                                val idx = col(name)
                                return if (idx < 0 || idx >= fields.size) null
                                       else fields[idx].trim().ifEmpty { null }
                            }

                            val packageName = get("packageName") ?: run {
                                skipped++; return@use
                            }
                            val appName  = get("appName") ?: packageName
                            val event    = get("event") ?: NotificationLog.EVENT_POSTED
                            val postTime = get("postTime")?.toLongOrNull()
                                ?: get("postTimeFormatted")?.let {
                                    runCatching { DATE_FMT.parse(it)?.time }.getOrNull()
                                }
                                ?: run { skipped++; return@use }

                            logs.add(NotificationLog(
                                id              = 0, // auto-generate new IDs
                                packageName     = packageName,
                                appName         = appName,
                                postTime        = postTime,
                                eventTime       = get("eventTime")?.toLongOrNull() ?: postTime,
                                removedTime     = get("removedTime")?.toLongOrNull(),
                                title           = get("title"),
                                text            = get("text"),
                                bigText         = get("bigText"),
                                subText         = get("subText"),
                                importance      = get("importance")?.toIntOrNull() ?: 0,
                                isHeadsUp       = get("isHeadsUp")?.lowercase() == "true",
                                category        = get("category"),
                                notificationType = get("notificationType") ?: "UNKNOWN",
                                event           = event,
                                removalReason   = get("removalReason")?.toIntOrNull() ?: 0,
                                topicGroup      = get("topicGroup")
                            ))
                        } catch (e: Exception) {
                            errors.add("Line $lineNum: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to open file: ${e.message}")
        }

        return ImportResult(imported = logs.size, skipped = skipped, errors = errors)
    }

    /**
     * Full parse including returning the list — used for the actual DB insert.
     */
    fun parseToLogs(context: Context, uri: Uri): Pair<List<NotificationLog>, ImportResult> {
        val logs = mutableListOf<NotificationLog>()
        val errors = mutableListOf<String>()
        var skipped = 0
        var lineNum = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val header = reader.readLine()
                        ?: return Pair(emptyList(), ImportResult(0, 0, listOf("Empty file")))
                    lineNum = 1
                    val cols = parseCsvLine(header).mapIndexed { i, name -> name.trim() to i }.toMap()
                    fun col(name: String) = cols[name] ?: -1

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lineNum++
                        val raw = line ?: continue
                        if (raw.isBlank()) continue

                        try {
                            val fields = parseCsvLine(raw)
                            fun get(name: String): String? {
                                val idx = col(name)
                                return if (idx < 0 || idx >= fields.size) null
                                       else fields[idx].trim().ifEmpty { null }
                            }

                            val packageName = get("packageName")
                            if (packageName == null) {
                                skipped++
                                continue
                            }
                            val appName  = get("appName") ?: packageName
                            val event    = get("event") ?: NotificationLog.EVENT_POSTED
                            val postTime = get("postTime")?.toLongOrNull()
                                ?: get("postTimeFormatted")?.let {
                                    runCatching { DATE_FMT.parse(it)?.time }.getOrNull()
                                }

                            if (postTime == null) {
                                skipped++
                                continue
                            }

                            logs.add(NotificationLog(
                                id              = 0,
                                packageName     = packageName,
                                appName         = appName,
                                postTime        = postTime,
                                eventTime       = get("eventTime")?.toLongOrNull() ?: postTime,
                                removedTime     = get("removedTime")?.toLongOrNull(),
                                title           = get("title"),
                                text            = get("text"),
                                bigText         = get("bigText"),
                                subText         = get("subText"),
                                importance      = get("importance")?.toIntOrNull() ?: 0,
                                isHeadsUp       = get("isHeadsUp")?.lowercase() == "true",
                                category        = get("category"),
                                notificationType = get("notificationType") ?: "UNKNOWN",
                                event           = event,
                                removalReason   = get("removalReason")?.toIntOrNull() ?: 0,
                                topicGroup      = get("topicGroup")
                            ))
                        } catch (e: Exception) {
                            errors.add("Line $lineNum: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to open file: ${e.message}")
        }

        return Pair(logs, ImportResult(imported = logs.size, skipped = skipped, errors = errors))
    }

    /**
     * RFC 4180-compliant CSV line parser. Handles quoted fields with embedded
     * commas and escaped double-quotes ("").
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++ // escaped quote
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
