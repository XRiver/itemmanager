package com.riverxu.itemmanager.data.repository

import com.riverxu.itemmanager.data.local.database.FilterShortcutEntity
import com.riverxu.itemmanager.data.local.database.ItemEntity
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.model.DataStats
import com.riverxu.itemmanager.data.model.ImportSummary
import com.riverxu.itemmanager.data.model.ItemDetail
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.data.model.MigrationTarget
import com.riverxu.itemmanager.data.model.NameValidation
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.FilterShortcut
import com.riverxu.itemmanager.domain.model.FilterSpec
import kotlinx.coroutines.flow.Flow

/**
 * 数据访问抽象。第一版由 [SQLiteItemRepository] 实现；后续可替换为云端实现（如 OneDriveItemRepository）。
 */
interface ItemRepository {

    // ---------- 初始化 ----------

    /** 首次启动时写入预置数据（默认住处、预置位置、预置类型），可重复调用。 */
    suspend fun ensureSeeded()

    // ---------- 物品 ----------

    fun observeItems(filter: FilterSpec, sort: CompositeSort): Flow<List<ItemListRow>>

    suspend fun countItems(filter: FilterSpec): Int

    suspend fun getItem(id: Long): ItemEntity?

    suspend fun getItemDetail(id: Long): ItemDetail?

    suspend fun addItem(input: ItemInput): Long

    suspend fun updateItem(input: ItemInput)

    suspend fun deleteItem(id: Long)

    suspend fun deleteItems(ids: List<Long>)

    /** 名称校验：空、超长、同名数量提示。 */
    suspend fun validateItemName(name: String, excludeId: Long = 0L): NameValidation

    // ---------- 住处 ----------

    fun observeResidences(): Flow<List<ResidenceEntity>>

    suspend fun getResidences(): List<ResidenceEntity>

    suspend fun getResidence(id: Long): ResidenceEntity?

    suspend fun addResidence(name: String): Long

    suspend fun renameResidence(id: Long, name: String)

    /** 删除住处，级联删除其物品、位置。 */
    suspend fun deleteResidence(id: Long)

    // ---------- 位置 ----------

    fun observeLocations(residenceId: Long): Flow<List<LocationEntity>>

    /** LRU 排序的位置列表（最近使用在前，保留最近 20 条）。 */
    fun observeLocationsLru(residenceId: Long): Flow<List<LocationEntity>>

    suspend fun getLocations(residenceId: Long): List<LocationEntity>

    suspend fun getLocation(id: Long): LocationEntity?

    suspend fun findLocationByName(residenceId: Long, name: String): LocationEntity?

    suspend fun addLocation(residenceId: Long, name: String): Long

    suspend fun renameLocation(id: Long, name: String)

    /** 删除位置；使用该位置的物品自动改为"未设置"。 */
    suspend fun deleteLocation(id: Long)

    suspend fun touchLocation(id: Long)

    /** 取得住处下"未设置"位置（必要时创建）。 */
    suspend fun getUnsetLocationId(residenceId: Long): Long

    // ---------- 类型 ----------

    fun observeTypes(): Flow<List<TypeEntity>>

    /** LRU 排序的类型列表。 */
    fun observeTypesLru(): Flow<List<TypeEntity>>

    suspend fun getTypes(): List<TypeEntity>

    suspend fun addType(name: String): Long

    suspend fun renameType(id: Long, name: String)

    /** 删除类型；使用该类型的物品 type 置空。 */
    suspend fun deleteType(id: Long)

    suspend fun touchType(name: String)

    // ---------- 筛选快捷入口 ----------

    fun observeShortcuts(): Flow<List<FilterShortcut>>

    suspend fun addShortcut(name: String, filter: FilterSpec, sort: CompositeSort?): Long

    suspend fun deleteShortcut(id: Long)

    suspend fun getShortcutEntity(id: Long): FilterShortcutEntity?

    // ---------- 搬家模式 ----------

    /**
     * 批量迁移物品到新住处。
     * @return 实际迁移的物品数量
     */
    suspend fun migrateItems(
        itemIds: List<Long>,
        targetResidenceId: Long,
        target: MigrationTarget,
    ): Int

    // ---------- 统计与维护 ----------

    fun observeTotalItemCount(): Flow<Int>

    fun observeResidenceItemCount(residenceId: Long): Flow<Int>

    suspend fun stats(): DataStats

    suspend fun optimize()

    // ---------- 导入导出的数据库快照 ----------

    /** 导出前把 WAL 内容合并进主库文件，保证备份文件完整。 */
    suspend fun checkpointForExport()

    /** 供导出使用的数据库文件。 */
    fun databaseFile(): java.io.File

    /** 导入完成后用新库文件替换当前数据库。 */
    suspend fun replaceDatabaseFrom(file: java.io.File)
}
