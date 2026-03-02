package com.notificationlogger.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notificationlogger.data.model.NotificationLog
import com.notificationlogger.data.model.PersonAlias

@Database(
    entities = [NotificationLog::class, AppFilter::class, PersonAlias::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun appFilterDao(): AppFilterDao
    abstract fun personAliasDao(): PersonAliasDao

    companion object {
        private const val DB_NAME = "notification_logger.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notification_logs ADD COLUMN notificationType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_logs_notificationType ON notification_logs(notificationType)")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create the table without the inline UNIQUE constraint
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `person_aliases` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `rawName` TEXT NOT NULL,
                `canonicalName` TEXT NOT NULL,
                `addedAt` INTEGER NOT NULL
            )
        """.trimIndent()
                )

                // 2. Create the UNIQUE index explicitly with the exact name Room expects
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_person_aliases_rawName` ON `person_aliases` (`rawName`)")
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }

