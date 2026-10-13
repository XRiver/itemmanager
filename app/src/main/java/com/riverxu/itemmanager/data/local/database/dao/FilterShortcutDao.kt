package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.riverxu.itemmanager.data.local.database.FilterShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterShortcutDao {

    @Insert
    suspend fun insert(shortcut: FilterShortcutEntity): Long

    @Update
    suspend fun update(shortcut: FilterShortcutEntity)

    @Delete
    suspend fun delete(shortcut: FilterShortcutEntity)

    @Query("DELETE FROM filter_shortcut WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM filter_shortcut ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<FilterShortcutEntity>>

    @Query("SELECT * FROM filter_shortcut WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): FilterShortcutEntity?

    @Query("SELECT * FROM filter_shortcut WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FilterShortcutEntity?

    @Query("SELECT COUNT(*) FROM filter_shortcut")
    suspend fun count(): Int
}
