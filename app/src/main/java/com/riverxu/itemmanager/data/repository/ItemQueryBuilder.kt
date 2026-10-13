package com.riverxu.itemmanager.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import java.util.Calendar

/**
 * 依据 [FilterSpec] 与 [CompositeSort] 构造物品列表查询。
 *
 * - SELECT 列顺序必须与 [com.riverxu.itemmanager.data.local.database.ItemListRow] 一致
 * - 所有用户输入均作为绑定参数传入，只对 LIKE 通配符做转义，避免 SQL 注入
 */
object ItemQueryBuilder {

    private const val SELECT_COLUMNS = """
        i.id AS id,
        i.name AS name,
        i.note AS note,
        i.sizeCategory AS sizeCategory,
        i.type AS type,
        i.photoPath AS photoPath,
        i.purchaseDate AS purchaseDate,
        i.expiryDate AS expiryDate,
        i.createdAt AS createdAt,
        i.updatedAt AS updatedAt,
        i.residenceId AS residenceId,
        r.name AS residenceName,
        i.locationId AS locationId,
        l.name AS locationName
    """

    private const val FROM_JOIN = """
        FROM item i
        INNER JOIN residence r ON r.id = i.residenceId
        INNER JOIN location l ON l.id = i.locationId
    """

    /**
     * @param limit 分页大小，<=0 表示不分页
     * @param offset 起始偏移
     */
    fun build(
        filter: FilterSpec,
        sort: CompositeSort,
        limit: Int = 0,
        offset: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = buildWhere(filter, now, args)
        val orderBy = buildOrderBy(sort)

        val sql = buildString {
            append("SELECT ").append(SELECT_COLUMNS.trimIndent().prependIndent("")).append('\n')
            append(FROM_JOIN.trimIndent()).append('\n')
            if (where.isNotEmpty()) {
                append("WHERE ").append(where).append('\n')
            }
            append("ORDER BY ").append(orderBy)
            if (limit > 0) {
                append(" LIMIT ").append(limit)
                if (offset > 0) append(" OFFSET ").append(offset)
            }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /** 构造与列表一致的 COUNT 查询，用于统计筛选结果数量。 */
    fun buildCount(filter: FilterSpec, now: Long = System.currentTimeMillis()): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = buildWhere(filter, now, args)
        val sql = buildString {
            append("SELECT COUNT(*) ").append(FROM_JOIN.trimIndent())
            if (where.isNotEmpty()) append(" WHERE ").append(where)
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun buildWhere(filter: FilterSpec, now: Long, args: MutableList<Any?>): String {
        val clauses = mutableListOf<String>()

        val keyword = filter.keyword.trim()
        if (keyword.isNotEmpty()) {
            val pattern = "%" + escapeLike(keyword) + "%"
            clauses += "(i.name LIKE ? ESCAPE '\\' OR IFNULL(i.note, '') LIKE ? ESCAPE '\\')"
            args += pattern
            args += pattern
        }

        filter.residenceId?.let {
            clauses += "i.residenceId = ?"
            args += it
        }

        filter.locationId?.let {
            clauses += "i.locationId = ?"
            args += it
        }

        filter.typeName?.let {
            clauses += "i.type = ?"
            args += it
        }

        filter.sizeCategory?.let {
            clauses += "i.sizeCategory = ?"
            args += it.name
        }

        filter.purchaseFrom?.let {
            clauses += "i.purchaseDate IS NOT NULL AND i.purchaseDate >= ?"
            args += it
        }
        filter.purchaseTo?.let {
            clauses += "i.purchaseDate IS NOT NULL AND i.purchaseDate <= ?"
            args += it
        }

        buildExpirationClause(filter.expiration, now, args)?.let { clauses += it }

        return clauses.joinToString(" AND ")
    }

    private fun buildExpirationClause(
        expiration: ExpirationFilter?,
        now: Long,
        args: MutableList<Any?>,
    ): String? = when (expiration) {
        null, ExpirationFilter.ANY -> null

        ExpirationFilter.HAS_EXPIRY -> "i.expiryDate IS NOT NULL"

        ExpirationFilter.NO_EXPIRY -> "i.expiryDate IS NULL"

        ExpirationFilter.EXPIRED -> {
            args += now
            "i.expiryDate IS NOT NULL AND i.expiryDate < ?"
        }

        ExpirationFilter.WITHIN_7_DAYS -> withinDays(7, now, args)

        ExpirationFilter.WITHIN_30_DAYS -> withinDays(30, now, args)

        ExpirationFilter.WITHIN_90_DAYS -> withinDays(90, now, args)
    }

    private fun withinDays(days: Int, now: Long, args: MutableList<Any?>): String {
        val endOfWindow = startOfDay(now) + days * DAY_MILLIS + (DAY_MILLIS - 1)
        args += now
        args += endOfWindow
        return "i.expiryDate IS NOT NULL AND i.expiryDate >= ? AND i.expiryDate <= ?"
    }

    private fun buildOrderBy(sort: CompositeSort): String {
        val specs = sort.normalized().specs
        val effective = if (specs.isEmpty()) SortField.defaultSort().specs else specs
        val parts = effective.map { spec -> orderClause(spec) }
        // 兜底：以 id 保证排序稳定
        return (parts + "i.id ASC").joinToString(", ")
    }

    private fun orderClause(spec: SortSpec): String {
        val direction = if (spec.ascending) "ASC" else "DESC"
        val column = when (spec.field) {
            SortField.LOCATION -> "l.name"
            SortField.NAME -> "i.name"
            SortField.PURCHASE_DATE -> "i.purchaseDate"
            SortField.EXPIRY_DATE -> "i.expiryDate"
            SortField.CREATED_AT -> "i.createdAt"
            SortField.UPDATED_AT -> "i.updatedAt"
            SortField.SIZE_CATEGORY ->
                "CASE i.sizeCategory WHEN 'FURNITURE' THEN 0 WHEN 'LARGE' THEN 1 ELSE 2 END"
            SortField.TYPE -> "i.type"
            SortField.RESIDENCE -> "r.name"
        }
        // SQLite 中 NULL 最小；用 CASE WHEN 显式控制空值位置
        val nullRank = if (spec.nullsLast) 1 else 0
        return if (spec.field == SortField.SIZE_CATEGORY) {
            "$column $direction"
        } else {
            "CASE WHEN $column IS NULL THEN $nullRank ELSE ${1 - nullRank} END ASC, $column $direction"
        }
    }

    /**
     * 转义 LIKE 通配符，配合 ESCAPE '\' 使用。
     *
     * 只处理 `%` 与 `_`：搜索关键字来自文本框，不会包含反斜杠；
     * 若同时转义反斜杠，输入 `\%` 会变成 `\\\%`，语义与预期不符。
     */
    internal fun escapeLike(value: String): String {
        val sb = StringBuilder(value.length)
        value.forEach { ch ->
            when (ch) {
                '%', '_' -> sb.append('\\').append(ch)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** 供调试/测试查看最终 SQL（不展开参数值）。 */
    @Suppress("unused")
    fun debugSql(query: SupportSQLiteQuery): String = query.sql

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private fun startOfDay(timeMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
