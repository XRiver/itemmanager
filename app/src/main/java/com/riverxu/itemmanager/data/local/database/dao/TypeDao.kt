package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.riverxu.itemmanager.data.local.database.TypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TypeDao {

    @Insert
    suspend fun insert(type: TypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(type: TypeEntity): Long

    @Update
    suspend fun update(type: TypeEntity)

    @Query("DELETE FROM item_type WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM item_type ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC, lastUsedAt DESC, usageCount DESC, id ASC")
    fun observeAllLru(): Flow<List<TypeEntity>>

    @Query("SELECT * FROM item_type ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC, lastUsedAt DESC, usageCount DESC, id ASC")
    suspend fun getAllLru(): List<TypeEntity>

    @Query("SELECT * FROM item_type ORDER BY id ASC")
    suspend fun getAll(): List<TypeEntity>

    @Query("SELECT * FROM item_type WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TypeEntity?

    @Query("SELECT * FROM item_type WHERE id = :id")
    suspend fun getById(id: Long): TypeEntity?

    @Query("UPDATE item_type SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM item_type")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM item WHERE type = :name")
    suspend fun itemCountOfType(name: String): Int
}
