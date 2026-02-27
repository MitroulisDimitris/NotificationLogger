package com.notificationlogger.data.database

import androidx.room.*
import androidx.lifecycle.LiveData

@Entity(tableName = "app_filter", indices = [Index(value = ["packageName"], unique = true)])
data class AppFilter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val filterType: String,  // "BLACKLIST" or "WHITELIST"
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppFilterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: AppFilter)

    @Delete
    suspend fun delete(filter: AppFilter)

    @Query("SELECT * FROM app_filter ORDER BY appName ASC")
    fun getAllFilters(): LiveData<List<AppFilter>>

    @Query("SELECT * FROM app_filter WHERE filterType = :type ORDER BY appName ASC")
    fun getFiltersByType(type: String): LiveData<List<AppFilter>>

    @Query("SELECT packageName FROM app_filter WHERE filterType = :type")
    suspend fun getPackageNamesByType(type: String): List<String>

    @Query("SELECT COUNT(*) FROM app_filter WHERE packageName = :pkg AND filterType = :type")
    suspend fun isInFilter(pkg: String, type: String): Int
}
