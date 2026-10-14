package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.riverxu.itemmanager.data.local.database.ItemEntity
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    /**
     * 复合排序 + 动态筛选查询。[query] 由 [com.riverxu.itemmanager.data.repository.ItemQueryBuilder] 构造，
     * 其 SELECT 列表必须与 [ItemListRow] 字段顺序一致。
     */
    @RawQuery(observedEntities = [ItemEntity::class, LocationEntity::class, ResidenceEntity::class])
    fun observeByRawQuery(query: SupportSQLiteQuery): Flow<List<ItemListRow>>

    @RawQuery(observedEntities = [ItemEntity::class, LocationEntity::class, ResidenceEntity::class])
    suspend fun countByRawQuery(query: SupportSQLiteQuery): Int

    @Query("SELECT COUNT(*) FROM item WHERE name = :name AND id != :excludeId")
    suspend fun countSameName(name: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM item WHERE residenceId = :residenceId")
    fun observeResidenceItemCount(residenceId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM item")
    fun observeTotalCount(): Flow<Int>

    @Query("UPDATE item SET locationId = :targetLocationId, updatedAt = :now WHERE locationId = :fromLocationId")
    suspend fun reassignLocation(fromLocationId: Long, targetLocationId: Long, now: Long)

    @Query("UPDATE item SET type = NULL, updatedAt = :now WHERE type = :typeName")
    suspend fun clearType(typeName: String, now: Long)

    @Query("UPDATE item SET locationId = :locationId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun updateLocationForItems(ids: List<Long>, locationId: Long, now: Long)

    /**
     * 搬家迁移：位置与住处都变了，因此同时推进「最近更新位置时间」。
     */
    @Query(
        "UPDATE item SET residenceId = :residenceId, locationId = :locationId, " +
            "updatedAt = :now, locationUpdatedAt = :now WHERE id IN (:ids)",
    )
    suspend fun migrateItems(ids: List<Long>, residenceId: Long, locationId: Long, now: Long)

    @Query("UPDATE item SET photoPath = NULL WHERE id = :id")
    suspend fun clearPhoto(id: Long)

    @Query("SELECT * FROM item")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT COALESCE(MAX(id), 0) FROM item")
    suspend fun maxId(): Long
}
