package com.riverxu.itemmanager.data.repository

import androidx.sqlite.db.SupportSQLiteDatabase
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.database.FilterShortcutEntity
import com.riverxu.itemmanager.data.local.database.ItemEntity
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.model.DataStats
import com.riverxu.itemmanager.data.model.ItemDetail
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.data.model.MigrationTarget
import com.riverxu.itemmanager.data.model.NameValidation
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.FieldLimits
import com.riverxu.itemmanager.domain.model.FilterShortcut
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetLocations
import com.riverxu.itemmanager.domain.model.PresetTypes
import com.riverxu.itemmanager.util.FilterCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于 Room(SQLite) 的 [ItemRepository] 实现。
 */
class SQLiteItemRepository(
    private val db: AppDatabase,
    private val photoStore: PhotoStore,
) : ItemRepository {

    private val residenceDao get() = db.residenceDao()
    private val locationDao get() = db.locationDao()
    private val typeDao get() = db.typeDao()
    private val itemDao get() = db.itemDao()
    private val shortcutDao get() = db.filterShortcutDao()

    // ---------------- 初始化 ----------------

    override suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (residenceDao.count() == 0) {
            val id = residenceDao.insert(
                ResidenceEntity(name = DEFAULT_RESIDENCE_NAME, createdAt = System.currentTimeMillis()),
            )
            createPresetLocations(id)
        }
        val existingTypes = typeDao.getAll().map { it.name }.toSet()
        PresetTypes.ALL.forEach { name ->
            if (name !in existingTypes) {
                typeDao.insertIgnore(TypeEntity(name = name, isPreset = true))
            }
        }
        // 兜底：确保每个住处都有两个预置位置
        residenceDao.getAll().forEach { createPresetLocations(it.id) }
    }

    private suspend fun createPresetLocations(residenceId: Long) {
        PresetLocations.ALL.forEach { name ->
            locationDao.insertIgnore(
                LocationEntity(
                    residenceId = residenceId,
                    name = name,
                    isPreset = true,
                    usageCount = 0,
                    lastUsedAt = 0L,
                ),
            )
        }
    }

    // ---------------- 物品 ----------------

    override fun observeItems(filter: FilterSpec, sort: CompositeSort): Flow<List<ItemListRow>> =
        itemDao.observeByRawQuery(ItemQueryBuilder.build(filter, sort))

    override suspend fun countItems(filter: FilterSpec): Int =
        itemDao.countByRawQuery(ItemQueryBuilder.buildCount(filter))

    override suspend fun getItem(id: Long): ItemEntity? = itemDao.getById(id)

    override suspend fun getItemDetail(id: Long): ItemDetail? {
        val entity = itemDao.getById(id) ?: return null
        val residence = residenceDao.getById(entity.residenceId)
        val location = locationDao.getById(entity.locationId)
        return ItemDetail(
            item = entity,
            residenceName = residence?.name.orEmpty(),
            locationName = location?.name.orEmpty(),
        )
    }

    override suspend fun addItem(input: ItemInput): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = itemDao.insert(
            ItemEntity(
                residenceId = input.residenceId,
                name = input.name.trim(),
                note = input.note?.trim()?.ifBlank { null },
                sizeCategory = input.sizeCategory,
                type = input.type?.trim()?.ifBlank { null },
                photoPath = input.photoPath,
                purchaseDate = input.purchaseDate,
                expiryDate = input.expiryDate,
                locationId = input.locationId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        touchLocationInternal(input.locationId, now)
        input.type?.trim()?.ifBlank { null }?.let { touchTypeInternal(it, now) }
        id
    }

    override suspend fun updateItem(input: ItemInput) = withContext(Dispatchers.IO) {
        val existing = itemDao.getById(input.id) ?: return@withContext
        val now = System.currentTimeMillis()
        val oldPhoto = existing.photoPath
        val newPhoto = input.photoPath
        itemDao.update(
            existing.copy(
                residenceId = input.residenceId,
                name = input.name.trim(),
                note = input.note?.trim()?.ifBlank { null },
                sizeCategory = input.sizeCategory,
                type = input.type?.trim()?.ifBlank { null },
                photoPath = newPhoto,
                purchaseDate = input.purchaseDate,
                expiryDate = input.expiryDate,
                locationId = input.locationId,
                updatedAt = now,
            ),
        )
        touchLocationInternal(input.locationId, now)
        input.type?.trim()?.ifBlank { null }?.let { touchTypeInternal(it, now) }
        if (oldPhoto != null && oldPhoto != newPhoto) {
            photoStore.deleteQuietly(oldPhoto)
        }
        // 该物品的旧照片若按 id 命名，替换后清理孤立文件
        photoStore.pruneOrphansFor(existing.id, keepPath = newPhoto)
    }

    override suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        val existing = itemDao.getById(id)
        itemDao.deleteById(id)
        photoStore.deleteItemPhotos(id, existing?.photoPath)
    }

    override suspend fun deleteItems(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.forEach { id ->
            val existing = itemDao.getById(id)
            itemDao.deleteById(id)
            photoStore.deleteItemPhotos(id, existing?.photoPath)
        }
    }

    override suspend fun validateItemName(name: String, excludeId: Long): NameValidation {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameValidation.Empty
        if (trimmed.length > FieldLimits.ITEM_NAME_MAX) return NameValidation.TooLong
        val same = itemDao.countSameName(trimmed, excludeId)
        return if (same > 0) NameValidation.Duplicated(same) else NameValidation.Ok
    }

    // ---------------- 住处 ----------------

    override fun observeResidences(): Flow<List<ResidenceEntity>> = residenceDao.observeAll()

    override suspend fun getResidences(): List<ResidenceEntity> = residenceDao.getAll()

    override suspend fun getResidence(id: Long): ResidenceEntity? = residenceDao.getById(id)

    override suspend fun addResidence(name: String): Long = withContext(Dispatchers.IO) {
        val id = residenceDao.insert(
            ResidenceEntity(name = name.trim(), createdAt = System.currentTimeMillis()),
        )
        createPresetLocations(id)
        id
    }

    override suspend fun renameResidence(id: Long, name: String) = withContext(Dispatchers.IO) {
        val existing = residenceDao.getById(id) ?: return@withContext
        residenceDao.update(existing.copy(name = name.trim()))
    }

    override suspend fun deleteResidence(id: Long) = withContext(Dispatchers.IO) {
        val photos = itemDao.getAll().filter { it.residenceId == id }.mapNotNull { it.photoPath }
        residenceDao.deleteById(id)
        photos.forEach { photoStore.deleteQuietly(it) }
    }

    // ---------------- 位置 ----------------

    override fun observeLocations(residenceId: Long): Flow<List<LocationEntity>> =
        locationDao.observeByResidence(residenceId)

    override fun observeLocationsLru(residenceId: Long): Flow<List<LocationEntity>> =
        locationDao.observeByResidenceLru(residenceId).map { list -> list.take(LRU_LIMIT) }

    override suspend fun getLocations(residenceId: Long): List<LocationEntity> =
        locationDao.getByResidence(residenceId)

    override suspend fun getLocation(id: Long): LocationEntity? = locationDao.getById(id)

    override suspend fun findLocationByName(residenceId: Long, name: String): LocationEntity? =
        locationDao.findByName(residenceId, name.trim())

    override suspend fun addLocation(residenceId: Long, name: String): Long =
        withContext(Dispatchers.IO) {
            val existing = locationDao.findByName(residenceId, name.trim())
            if (existing != null) return@withContext existing.id
            locationDao.insert(
                LocationEntity(
                    residenceId = residenceId,
                    name = name.trim(),
                    isPreset = false,
                    usageCount = 0,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }

    override suspend fun renameLocation(id: Long, name: String) = withContext(Dispatchers.IO) {
        val existing = locationDao.getById(id) ?: return@withContext
        locationDao.update(existing.copy(name = name.trim()))
    }

    override suspend fun deleteLocation(id: Long) = withContext(Dispatchers.IO) {
        val existing = locationDao.getById(id) ?: return@withContext
        if (existing.isPreset) return@withContext
        val unsetId = getUnsetLocationId(existing.residenceId)
        if (unsetId != id) {
            itemDao.reassignLocation(id, unsetId, System.currentTimeMillis())
        }
        locationDao.deleteById(id)
    }

    override suspend fun touchLocation(id: Long) = withContext(Dispatchers.IO) {
        touchLocationInternal(id, System.currentTimeMillis())
    }

    override suspend fun getUnsetLocationId(residenceId: Long): Long = withContext(Dispatchers.IO) {
        locationDao.findByName(residenceId, PresetLocations.UNSET)?.id
            ?: locationDao.insert(
                LocationEntity(
                    residenceId = residenceId,
                    name = PresetLocations.UNSET,
                    isPreset = true,
                ),
            )
    }

    private suspend fun touchLocationInternal(id: Long, now: Long) {
        locationDao.touch(id, now)
    }

    // ---------------- 类型 ----------------

    override fun observeTypes(): Flow<List<TypeEntity>> = typeDao.observeAllLru()

    override fun observeTypesLru(): Flow<List<TypeEntity>> =
        typeDao.observeAllLru().map { list -> list.take(LRU_LIMIT) }

    override suspend fun getTypes(): List<TypeEntity> = typeDao.getAllLru()

    override suspend fun addType(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        typeDao.getByName(trimmed)?.id
            ?: typeDao.insert(
                TypeEntity(name = trimmed, isPreset = false, lastUsedAt = System.currentTimeMillis()),
            )
    }

    override suspend fun renameType(id: Long, name: String) = withContext(Dispatchers.IO) {
        val existing = typeDao.getById(id) ?: return@withContext
        val trimmed = name.trim()
        typeDao.update(existing.copy(name = trimmed))
        // 同步更新物品上的类型名称
        db.openHelper.writableDatabase.execSQL(
            "UPDATE item SET type = ?, updatedAt = ? WHERE type = ?",
            arrayOf<Any?>(trimmed, System.currentTimeMillis(), existing.name),
        )
    }

    override suspend fun deleteType(id: Long) = withContext(Dispatchers.IO) {
        val existing = typeDao.getById(id) ?: return@withContext
        if (existing.isPreset) return@withContext
        itemDao.clearType(existing.name, System.currentTimeMillis())
        typeDao.deleteById(id)
    }

    override suspend fun touchType(name: String) = withContext(Dispatchers.IO) {
        touchTypeInternal(name, System.currentTimeMillis())
    }

    private suspend fun touchTypeInternal(name: String, now: Long) {
        typeDao.getByName(name)?.let { typeDao.touch(it.id, now) }
    }

    // ---------------- 筛选快捷入口 ----------------

    override fun observeShortcuts(): Flow<List<FilterShortcut>> =
        shortcutDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addShortcut(
        name: String,
        filter: FilterSpec,
        sort: CompositeSort?,
    ): Long = shortcutDao.insert(
        FilterShortcutEntity(
            name = name.trim(),
            filterConfig = FilterCodec.encode(filter),
            sortConfig = sort?.let { FilterCodec.encodeSort(it) },
        ),
    )

    override suspend fun deleteShortcut(id: Long) = shortcutDao.deleteById(id)

    override suspend fun getShortcutEntity(id: Long): FilterShortcutEntity? =
        shortcutDao.getById(id)

    private fun FilterShortcutEntity.toDomain(): FilterShortcut = FilterShortcut(
        id = id,
        name = name,
        filter = FilterCodec.decode(filterConfig) ?: FilterSpec.EMPTY,
        sort = sortConfig?.let { FilterCodec.decodeSort(it) },
    )

    // ---------------- 搬家模式 ----------------

    override suspend fun migrateItems(
        itemIds: List<Long>,
        targetResidenceId: Long,
        target: MigrationTarget,
    ): Int = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        val unsetId = getUnsetLocationId(targetResidenceId)

        val defaultLocationId = when (target) {
            is MigrationTarget.SpecificLocation -> target.locationId
            MigrationTarget.ToUnset -> unsetId
            MigrationTarget.SameLocationName -> unsetId
        }

        var moved = 0
        itemIds.chunked(SQLITE_VARIABLE_LIMIT).forEach { chunk ->
            when (target) {
                is MigrationTarget.SpecificLocation, MigrationTarget.ToUnset -> {
                    itemDao.migrateItems(chunk, targetResidenceId, defaultLocationId, now)
                }

                MigrationTarget.SameLocationName -> {
                    // 按原位置名分组，在新住处复用或创建同名位置
                    val byLocationName = mutableMapOf<String, MutableList<Long>>()
                    chunk.forEach { itemId ->
                        val item = itemDao.getById(itemId) ?: return@forEach
                        val name = locationDao.getById(item.locationId)?.name
                            ?: PresetLocations.UNSET
                        byLocationName.getOrPut(name) { mutableListOf() }.add(itemId)
                    }
                    byLocationName.forEach { (locationName, ids) ->
                        val targetLocationId =
                            locationDao.findByName(targetResidenceId, locationName)?.id
                                ?: locationDao.insert(
                                    LocationEntity(
                                        residenceId = targetResidenceId,
                                        name = locationName,
                                        isPreset = locationName in PresetLocations.ALL,
                                        lastUsedAt = now,
                                    ),
                                )
                        itemDao.migrateItems(ids, targetResidenceId, targetLocationId, now)
                    }
                }
            }
            moved += chunk.size
        }
        moved
    }

    // ---------------- 统计与维护 ----------------

    override fun observeTotalItemCount(): Flow<Int> = itemDao.observeTotalCount()

    override fun observeResidenceItemCount(residenceId: Long): Flow<Int> =
        itemDao.observeResidenceItemCount(residenceId)

    override suspend fun stats(): DataStats = withContext(Dispatchers.IO) {
        val allItems = itemDao.getAll()
        DataStats(
            itemCount = allItems.size,
            residenceCount = residenceDao.count(),
            locationCount = residenceDao.getAll().sumOf { locationDao.countByResidence(it.id) },
            typeCount = typeDao.count(),
            photoCount = allItems.count { !it.photoPath.isNullOrBlank() },
            photoBytes = photoStore.totalBytes(),
        )
    }

    override suspend fun optimize() = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL("VACUUM")
    }

    // ---------------- 数据库文件 ----------------

    override suspend fun checkpointForExport() {
        withContext(Dispatchers.IO) {
            runCatching {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            }
        }
    }

    override fun databaseFile(): File = db.openHelper.writableDatabase.path?.let { File(it) }
        ?: File(db.openHelper.readableDatabase.path ?: "")

    override suspend fun replaceDatabaseFrom(file: File) = withContext(Dispatchers.IO) {
        SQLiteBridge(db.openHelper.writableDatabase).attachAndReplace(file)
    }

    /** 需要作为可写 SQLiteDatabase 使用的桥接，用于导入时跨库复制。 */
    private class SQLiteBridge(private val database: SupportSQLiteDatabase) {

        fun attachAndReplace(sourceFile: File) {
            val attachAlias = "imported"
            database.execSQL("ATTACH DATABASE ? AS $attachAlias", arrayOf<Any?>(sourceFile.absolutePath))
            try {
                database.beginTransaction()
                try {
                    validateImportedDatabase(attachAlias)
                    val tables = listOf("item", "location", "item_type", "filter_shortcut", "residence")
                    tables.forEach { table ->
                        database.execSQL("DELETE FROM $table")
                    }
                    database.execSQL("INSERT INTO residence SELECT * FROM $attachAlias.residence")
                    database.execSQL("INSERT INTO item_type SELECT * FROM $attachAlias.item_type")
                    database.execSQL("INSERT INTO location SELECT * FROM $attachAlias.location")
                    database.execSQL("INSERT INTO item SELECT * FROM $attachAlias.item")
                    database.execSQL("INSERT INTO filter_shortcut SELECT * FROM $attachAlias.filter_shortcut")
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.execSQL("DETACH DATABASE $attachAlias")
            }
        }

        private fun validateImportedDatabase(alias: String) {
            val required = setOf("residence", "location", "item_type", "item", "filter_shortcut")
            val found = mutableSetOf<String>()
            database.query(
                "SELECT name FROM $alias.sqlite_master WHERE type = 'table'",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    found += cursor.getString(0)
                }
            }
            val missing = required - found
            require(missing.isEmpty()) {
                "备份文件缺少数据表：${missing.joinToString()}"
            }
        }
    }

    companion object {
        const val DEFAULT_RESIDENCE_NAME = "我的住处"
        const val LRU_LIMIT = 20
        private const val SQLITE_VARIABLE_LIMIT = 900
    }
}
