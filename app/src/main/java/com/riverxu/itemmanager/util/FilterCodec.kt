package com.riverxu.itemmanager.util

import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 筛选条件与排序配置的 JSON 编解码，用于筛选快捷入口的持久化。
 * 使用 JSON 而非序列化框架，避免额外依赖并容忍旧数据缺字段。
 */
object FilterCodec {

    fun encode(filter: FilterSpec): String = JSONObject().apply {
        put("keyword", filter.keyword)
        filter.residenceId?.let { put("residenceId", it) }
        filter.locationId?.let { put("locationId", it) }
        filter.typeName?.let { put("typeName", it) }
        filter.sizeCategory?.let { put("sizeCategory", it.name) }
        filter.expiration?.let { put("expiration", it.name) }
        filter.purchaseFrom?.let { put("purchaseFrom", it) }
        filter.purchaseTo?.let { put("purchaseTo", it) }
        if (filter.tags.isNotEmpty()) {
            put("tags", JSONArray().apply { filter.tags.sorted().forEach { put(it) } })
        }
    }.toString()

    fun decode(json: String?): FilterSpec? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            FilterSpec(
                keyword = obj.optString("keyword", ""),
                residenceId = obj.optLongOrNull("residenceId"),
                locationId = obj.optLongOrNull("locationId"),
                typeName = obj.optStringOrNull("typeName"),
                sizeCategory = obj.optStringOrNull("sizeCategory")
                    ?.let { SizeCategory.fromNameOrNull(it) },
                expiration = obj.optStringOrNull("expiration")
                    ?.let { name -> ExpirationFilter.entries.firstOrNull { it.name == name } },
                purchaseFrom = obj.optLongOrNull("purchaseFrom"),
                purchaseTo = obj.optLongOrNull("purchaseTo"),
                tags = obj.optJSONArray("tags")
                    ?.let { array ->
                        buildSet {
                            for (i in 0 until array.length()) {
                                array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    .orEmpty(),
            )
        }.getOrNull()
    }

    fun encodeSort(sort: CompositeSort): String = JSONObject().apply {
        put(
            "specs",
            org.json.JSONArray().apply {
                sort.normalized().specs.forEach { spec ->
                    put(
                        JSONObject()
                            .put("field", spec.field.name)
                            .put("ascending", spec.ascending)
                            .put("nullsLast", spec.nullsLast),
                    )
                }
            },
        )
    }.toString()

    fun decodeSort(json: String?): CompositeSort? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val array = JSONObject(json).optJSONArray("specs") ?: return null
            val specs = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val field = SortField.fromNameOrNull(obj.optString("field")) ?: continue
                    add(
                        SortSpec(
                            field = field,
                            ascending = obj.optBoolean("ascending", true),
                            nullsLast = obj.optBoolean("nullsLast", true),
                        ),
                    )
                }
            }
            CompositeSort(specs).normalized()
        }.getOrNull()
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
