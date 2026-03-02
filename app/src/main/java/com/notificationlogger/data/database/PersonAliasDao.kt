package com.notificationlogger.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.notificationlogger.data.model.PersonAlias

@Dao
interface PersonAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: PersonAlias)

    @Delete
    suspend fun delete(alias: PersonAlias)

    @Query("SELECT * FROM person_aliases ORDER BY canonicalName ASC")
    fun getAllAliases(): LiveData<List<PersonAlias>>

    @Query("SELECT * FROM person_aliases ORDER BY canonicalName ASC")
    suspend fun getAllAliasesSuspend(): List<PersonAlias>

    @Query("SELECT canonicalName FROM person_aliases WHERE rawName = :rawName")
    suspend fun resolveAlias(rawName: String): String?

    @Query("SELECT * FROM person_aliases WHERE canonicalName = :canonical ORDER BY rawName ASC")
    suspend fun getAliasesForCanonical(canonical: String): List<PersonAlias>

    @Query("DELETE FROM person_aliases WHERE canonicalName = :canonical")
    suspend fun deleteGroup(canonical: String)

    @Query("SELECT DISTINCT canonicalName FROM person_aliases ORDER BY canonicalName ASC")
    suspend fun getCanonicalNames(): List<String>
}
