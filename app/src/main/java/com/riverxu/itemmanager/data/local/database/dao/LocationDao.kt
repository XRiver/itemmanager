package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.riverxu.itemmanager.data.local.database.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Query("DELETE FROM location WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM location WHERE residenceId = :residenceId ORDER BY id ASC")
    fun observeByResidence(residenceId: Long): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location WHERE residenceId = :residenceId ORDER BY id ASC")
    suspend fun getByResidence(residenceId: Long): List<LocationEntity>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Query("SELECT * FROM location WHERE residenceId = :residenceId AND name = :name LIMIT 1")
    suspend fun getByName(residenceId: Long, name: String): LocationEntity?

    @Query("SELECT * FROM location WHERE residenceId = :residenceId AND name = :name LIMIT 1")
    suspend fun findByName(residenceId: Long, name: String): LocationEntity?

    /** LRU 排序：最近使用时间降序，其次使用次数降序。 */
    @Query(
        """
        SELECT * FROM location
        WHERE residenceId = :residenceId
        ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC,
                 lastUsedAt DESC,
                 usageCount DESC,
                 id ASC
        """,
    )
    fun observeByResidenceLru(residenceId: Long): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location WHERE residenceId = :residenceId ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC, lastUsedAt DESC, usageCount DESC, id ASC")
    suspend fun getByResidenceLru(residenceId: Long): List<LocationEntity>

    @Query("UPDATE location SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM location WHERE residenceId = :residenceId")
    suspend fun countByResidence(residenceId: Long): Int

    @Query("SELECT COUNT(*) FROM location WHERE residenceId = :residenceId AND name = :name AND id != :excludeId")
    suspend fun countSameName(residenceId: Long, name: String, excludeId: Long): Int
}
