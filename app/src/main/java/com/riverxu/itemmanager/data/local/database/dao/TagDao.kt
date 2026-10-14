package com.riverxu.itemmanager.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.riverxu.itemmanager.data.local.database.ItemTagCrossRef
import com.riverxu.itemmanager.data.local.database.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    // ---------------- 标签本身 ----------------

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tag ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC, lastUsedAt DESC, usageCount DESC, id ASC")
    fun observeAllLru(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag ORDER BY CASE WHEN lastUsedAt = 0 THEN 1 ELSE 0 END ASC, lastUsedAt DESC, usageCount DESC, id ASC")
    suspend fun getAllLru(): List<TagEntity>

    @Query("SELECT * FROM tag ORDER BY id ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("UPDATE tag SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM tag")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM item_tag WHERE tagId = :tagId")
    suspend fun itemCountOfTag(tagId: Long): Int

    // ---------------- 物品 ↔ 标签 关联 ----------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<ItemTagCrossRef>)

    @Query("DELETE FROM item_tag WHERE itemId = :itemId")
    suspend fun deleteRefsForItem(itemId: Long)

    @Query(
        """
        SELECT t.name FROM item_tag it
        INNER JOIN tag t ON t.id = it.tagId
        WHERE it.itemId = :itemId
        ORDER BY t.name ASC
        """,
    )
    suspend fun getTagNamesForItem(itemId: Long): List<String>

    @Query(
        """
        SELECT t.name FROM item_tag it
        INNER JOIN tag t ON t.id = it.tagId
        WHERE it.itemId = :itemId
        ORDER BY t.name ASC
        """,
    )
    fun observeTagNamesForItem(itemId: Long): Flow<List<String>>

    @Query("SELECT id FROM tag WHERE name IN (:names)")
    suspend fun findIdsByNames(names: List<String>): List<Long>
}
