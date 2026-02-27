package com.notificationlogger.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notificationlogger.data.model.NotificationLog

@Database(
    entities = [NotificationLog::class, AppFilter::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun appFilterDao(): AppFilterDao

    companion object {
        private const val DB_NAME = "notification_logger.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notification_logs ADD COLUMN notificationType TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_logs_notificationType ON notification_logs(notificationType)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
