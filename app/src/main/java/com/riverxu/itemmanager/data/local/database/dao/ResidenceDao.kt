package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResidenceDao {

    @Insert
    suspend fun insert(residence: ResidenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(residence: ResidenceEntity): Long

    @Update
    suspend fun update(residence: ResidenceEntity)

    @Query("DELETE FROM residence WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM residence ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ResidenceEntity>>

    @Query("SELECT * FROM residence ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ResidenceEntity>

    @Query("SELECT * FROM residence WHERE id = :id")
    suspend fun getById(id: Long): ResidenceEntity?

    @Query("SELECT * FROM residence WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ResidenceEntity?

    @Query("SELECT COUNT(*) FROM residence")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM residence WHERE name = :name AND id != :excludeId")
    suspend fun countSameName(name: String, excludeId: Long): Int
}
