package com.riverxu.itemmanager.data.model

import com.riverxu.itemmanager.domain.model.SizeCategory

/**
 * 新增/编辑物品的输入参数。
 *
 * 不含录入时间与最近更新位置时间：这两个字段由仓储层根据业务规则自动维护。
 */
data class ItemInput(
    val id: Long = 0L,
    val residenceId: Long,
    val name: String,
    val note: String? = null,
    val sizeCategory: SizeCategory = SizeCategory.SMALL,
    val type: String? = null,
    /** 多选标签；新建物品时默认为空 */
    val tags: List<String> = emptyList(),
    val photoPath: String? = null,
    val purchaseDate: Long? = null,
    val expiryDate: Long? = null,
    val locationId: Long,
)

/**
 * 物品详情（含关联名称与标签）。
 */
data class ItemDetail(
    val item: com.riverxu.itemmanager.data.local.database.ItemEntity,
    val residenceName: String,
    val locationName: String,
    val tags: List<String> = emptyList(),
)

/**
 * 轻量统计，用于设置页与搬家页。
 */
data class DataStats(
    val itemCount: Int,
    val residenceCount: Int,
    val locationCount: Int,
    val typeCount: Int,
    val tagCount: Int,
    val photoCount: Int,
    val photoBytes: Long,
)

/**
 * 同名物品校验结果。
 */
sealed interface NameValidation {
    data object Empty : NameValidation
    data object TooLong : NameValidation
    data class Duplicated(val count: Int) : NameValidation
    data object Ok : NameValidation
    data class Invalid(val message: String) : NameValidation
}

/**
 * 导出结果。
 */
data class ExportSummary(
    val itemCount: Int,
    val residenceCount: Int,
    val photoCount: Int,
    val totalBytes: Long,
    val fileName: String,
)

/**
 * 导入结果。
 */
data class ImportSummary(
    val itemCount: Int,
    val residenceCount: Int,
    val photoCount: Int,
    val renamedResidences: List<String>,
)

/**
 * 迁移方式。
 */
sealed interface MigrationTarget {
    /** 指定新住处的某个具体位置 */
    data class SpecificLocation(val locationId: Long) : MigrationTarget

    /** 同位迁移：在新住处创建/复用同名位置 */
    data object SameLocationName : MigrationTarget

    /** 灵活迁移：落到新住处的"未设置" */
    data object ToUnset : MigrationTarget
}
