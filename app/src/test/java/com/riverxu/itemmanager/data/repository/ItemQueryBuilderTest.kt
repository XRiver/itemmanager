package com.riverxu.itemmanager.data.repository

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查询构造测试：复合排序、筛选条件、LIKE 转义。
 */
class ItemQueryBuilderTest {

    private val now = 1_700_000_000_000L

    /** 每个绑定参数对应 SQL 中的一个 `?` 占位符。 */
    private fun placeholderCount(sql: String): Int = sql.count { it == '?' }

    /** 通过 SupportSQLiteQuery.bindTo 取出绑定参数（顺序与 SQL 中 `?` 一致）。 */
    private fun bindArgs(query: SupportSQLiteQuery): List<Any?> {
        val recorded = mutableListOf<Any?>()
        val program = object : SupportSQLiteProgram {
            override fun bindNull(index: Int) {
                recorded += null
            }

            override fun bindLong(index: Int, value: Long) {
                recorded += value
            }

            override fun bindDouble(index: Int, value: Double) {
                recorded += value
            }

            override fun bindString(index: Int, value: String) {
                recorded += value
            }

            override fun bindBlob(index: Int, value: ByteArray) {
                recorded += value
            }

            override fun clearBindings() = recorded.clear()

            override fun close() = Unit
        }
        query.bindTo(program)
        return recorded
    }
    @Test
    fun `默认排序包含位置与名称且以 id 兜底`() {
        val sql = ItemQueryBuilder.build(FilterSpec.EMPTY, SortField.defaultSort(), now = now).sql
        assertTrue(sql.contains("ORDER BY"))
        assertTrue(sql.contains("l.name ASC"))
        assertTrue(sql.contains("i.id ASC"))
    }

    @Test
    fun `最多三个排序字段`() {
        val sort = CompositeSort(
            listOf(
                SortSpec(SortField.LOCATION),
                SortSpec(SortField.PURCHASE_DATE, ascending = false),
                SortSpec(SortField.NAME),
                SortSpec(SortField.TYPE),
            ),
        )
        val sql = ItemQueryBuilder.build(FilterSpec.EMPTY, sort, now = now).sql
        assertTrue(sql.contains("l.name ASC"))
        assertTrue(sql.contains("i.purchaseDate DESC"))
        assertTrue(sql.contains("i.name ASC"))
        assertFalse("第四个字段应被截断", sql.contains("i.type ASC,"))
    }

    @Test
    fun `可空字段默认空值排最后`() {
        val sort = CompositeSort(listOf(SortSpec(SortField.EXPIRY_DATE, nullsLast = true)))
        val sql = ItemQueryBuilder.build(FilterSpec.EMPTY, sort, now = now).sql
        assertTrue(sql.contains("CASE WHEN i.expiryDate IS NULL THEN 1 ELSE 0 END ASC"))
    }

    @Test
    fun `关键字同时匹配名称与注释`() {
        val query = ItemQueryBuilder.build(FilterSpec(keyword = "螺丝刀"), SortField.defaultSort(), now = now)
        assertTrue(query.sql.contains("i.name LIKE ?"))
        assertTrue(query.sql.contains("IFNULL(i.note, '') LIKE ?"))
        val args = bindArgs(query)
        assertEquals(2, args.size)
        assertEquals("%螺丝刀%", args[0])
    }

    @Test
    fun `LIKE 通配符被转义`() {
        // 反斜杠
        val slash = "\\"
        assertEquals("100${slash}%", ItemQueryBuilder.escapeLike("100%"))
        assertEquals("a${slash}_b", ItemQueryBuilder.escapeLike("a_b"))
        assertEquals("普通", ItemQueryBuilder.escapeLike("普通"))
        // 反斜杠本身不作为通配符处理（关键字来自文本输入，不含反斜杠）
        assertEquals("100${slash}${slash}%", ItemQueryBuilder.escapeLike("100${slash}%"))
        assertEquals("plain text", ItemQueryBuilder.escapeLike("plain text"))
        // 转义后的模式在 LIKE 中按字面量匹配
        assertTrue(ItemQueryBuilder.escapeLike("50%").endsWith("\\%"))
    }

    @Test
    fun `筛选条件组合生成对应参数`() {
        val filter = FilterSpec(
            keyword = "书",
            residenceId = 3L,
            locationId = 7L,
            typeName = "书籍",
            sizeCategory = SizeCategory.SMALL,
            expiration = ExpirationFilter.EXPIRED,
        )
        val query = ItemQueryBuilder.build(filter, SortField.defaultSort(), now = now)
        val args = bindArgs(query)
        assertTrue(query.sql.contains("i.residenceId = ?"))
        assertTrue(query.sql.contains("i.locationId = ?"))
        assertTrue(query.sql.contains("i.type = ?"))
        assertTrue(query.sql.contains("i.sizeCategory = ?"))
        assertTrue(query.sql.contains("i.expiryDate < ?"))
        assertEquals("参数个数应与占位符一致", placeholderCount(query.sql), args.size)
        assertEquals(7, args.size)
        // 顺序：keyword(2) → residence → location → type → sizeCategory → expired(now)
        assertEquals("%书%", args[0])
        assertEquals("%书%", args[1])
        assertEquals(3L, args[2])
        assertEquals(7L, args[3])
        assertEquals("书籍", args[4])
        assertEquals("SMALL", args[5])
        assertEquals(now, args[6])
    }

    @Test
    fun `未设置过期时间筛选`() {
        val query = ItemQueryBuilder.build(
            FilterSpec(expiration = ExpirationFilter.NO_EXPIRY),
            SortField.defaultSort(),
            now = now,
        )
        assertTrue(query.sql.contains("i.expiryDate IS NULL"))
        assertEquals(0, bindArgs(query).size)
    }

    @Test
    fun `分页参数生效`() {
        val query = ItemQueryBuilder.build(FilterSpec.EMPTY, SortField.defaultSort(), limit = 50, offset = 100, now = now)
        assertTrue(query.sql.endsWith("LIMIT 50 OFFSET 100"))
    }

    @Test
    fun `计数查询不含排序`() {
        val query = ItemQueryBuilder.buildCount(FilterSpec(residenceId = 1L), now = now)
        assertTrue(query.sql.startsWith("SELECT COUNT(*)"))
        assertFalse(query.sql.contains("ORDER BY"))
    }

    @Test
    fun `排序切换方向`() {
        val toggled = SortField.defaultSort().toggled(SortField.LOCATION)
        val locationSpec = toggled.specs.first { it.field == SortField.LOCATION }
        assertFalse("默认升序切换后应变为降序", locationSpec.ascending)
    }

    @Test
    fun `新增排序字段追加到末尾`() {
        val sort = CompositeSort(listOf(SortSpec(SortField.NAME))).withSpec(SortSpec(SortField.TYPE))
        assertEquals(listOf(SortField.NAME, SortField.TYPE), sort.specs.map { it.field })
    }

    @Test
    fun `日期筛选起点为当天零点之后`() {
        val end = ItemQueryBuilder.build(
            FilterSpec(purchaseFrom = 1_000L, purchaseTo = 2_000L),
            SortField.defaultSort(),
            now = now,
        )
        assertTrue(end.sql.contains("i.purchaseDate IS NOT NULL AND i.purchaseDate >= ?"))
        assertTrue(end.sql.contains("i.purchaseDate IS NOT NULL AND i.purchaseDate <= ?"))
    }

    @Test
    fun `七天内过期窗口按自然日计算`() {
        val query = ItemQueryBuilder.build(
            FilterSpec(expiration = ExpirationFilter.WITHIN_7_DAYS),
            SortField.defaultSort(),
            now = now,
        )
        val args = bindArgs(query)
        assertEquals(2, args.size)
        assertEquals(now, args[0])
        val windowEnd = args[1] as Long
        assertTrue("窗口结束时间应晚于当前时间", windowEnd > now)
        assertTrue("窗口约为 7 天", windowEnd - now < 8L * 24 * 60 * 60 * 1000)
    }
}
