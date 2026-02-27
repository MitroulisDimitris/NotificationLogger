package com.notificationlogger.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.notificationlogger.data.model.NotificationLog
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val FILE_DATE_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun export(context: Context, logs: List<NotificationLog>): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val fileName = "notifications_${FILE_DATE_FMT.format(Date())}.csv"
            val file = File(cacheDir, fileName)

            FileWriter(file).use { writer ->
                // Header row
                writer.appendLine(
                    "id,packageName,appName,event,notificationType,postTime,postTimeFormatted," +
                    "eventTime,removedTime,title,text,bigText,subText," +
                    "importance,isHeadsUp,category,topicGroup,removalReason"
                )

                // Data rows
                for (log in logs) {
                    writer.appendLine(buildRow(log))
                }
            }

            // Create shareable URI via FileProvider
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun buildShareIntent(context: Context, logs: List<NotificationLog>): Intent? {
        val uri = export(context, logs) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Notification Log Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildRow(log: NotificationLog): String {
        return listOf(
            log.id,
            log.packageName.escapeCsv(),
            log.appName.escapeCsv(),
            log.event,
            log.notificationType,
            log.postTime,
            DATE_FMT.format(Date(log.postTime)).escapeCsv(),
            log.eventTime,
            log.removedTime ?: "",
            (log.title ?: "").escapeCsv(),
            (log.text ?: "").escapeCsv(),
            (log.bigText ?: "").escapeCsv(),
            (log.subText ?: "").escapeCsv(),
            log.importance,
            log.isHeadsUp,
            (log.category ?: "").escapeCsv(),
            (log.topicGroup ?: "Other").escapeCsv(),
            log.removalReason
        ).joinToString(",")
    }

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }

    private fun Any.escapeCsv(): String = toString()
}
