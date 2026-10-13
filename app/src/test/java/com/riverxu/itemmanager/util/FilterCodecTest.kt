package com.riverxu.itemmanager.util

import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 筛选快捷入口的 JSON 编解码测试：需容忍缺字段与非法内容。
 *
 * 使用 Robolectric 是因为实现依赖 Android 平台的 `org.json`。
 */
@RunWith(RobolectricTestRunner::class)
class FilterCodecTest {

    @Test
    fun `筛选条件往返一致`() {
        val original = FilterSpec(
            keyword = "螺丝刀",
            residenceId = 2L,
            locationId = 5L,
            typeName = "电子产品",
            sizeCategory = SizeCategory.SMALL,
            expiration = ExpirationFilter.WITHIN_30_DAYS,
            purchaseFrom = 1_000L,
            purchaseTo = 2_000L,
        )
        val decoded = FilterCodec.decode(FilterCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `空筛选条件可往返`() {
        val decoded = FilterCodec.decode(FilterCodec.encode(FilterSpec.EMPTY))
        assertEquals(FilterSpec.EMPTY, decoded)
        assertTrue(decoded!!.isEmpty)
    }

    @Test
    fun `非法 JSON 返回 null 而不抛异常`() {
        assertNull(FilterCodec.decode("{ 这不是 JSON"))
        assertNull(FilterCodec.decode(""))
        assertNull(FilterCodec.decode(null))
    }

    @Test
    fun `缺失字段使用默认值`() {
        val decoded = FilterCodec.decode("""{"keyword":"书"}""")
        assertEquals("书", decoded?.keyword)
        assertNull(decoded?.residenceId)
        assertNull(decoded?.expiration)
        assertNull(decoded?.sizeCategory)
    }

    @Test
    fun `未知枚举值被忽略`() {
        val decoded = FilterCodec.decode("""{"sizeCategory":"HUGE","expiration":"SOMEDAY"}""")
        assertNull(decoded?.sizeCategory)
        assertNull(decoded?.expiration)
    }

    @Test
    fun `排序往返一致且最多三项`() {
        val sort = CompositeSort(
            listOf(
                SortSpec(SortField.LOCATION, ascending = true),
                SortSpec(SortField.EXPIRY_DATE, ascending = false, nullsLast = false),
            ),
        )
        val decoded = FilterCodec.decodeSort(FilterCodec.encodeSort(sort))
        assertEquals(sort.normalized(), decoded)
    }

    @Test
    fun `排序字段保留空值位置设置`() {
        val sort = CompositeSort(listOf(SortSpec(SortField.TYPE, ascending = false, nullsLast = false)))
        val decoded = FilterCodec.decodeSort(FilterCodec.encodeSort(sort))
        assertEquals(false, decoded?.specs?.first()?.nullsLast)
        assertEquals(false, decoded?.specs?.first()?.ascending)
    }

    @Test
    fun `非法排序 JSON 返回 null`() {
        assertNull(FilterCodec.decodeSort("not-json"))
        assertNull(FilterCodec.decodeSort(null))
    }

    @Test
    fun `含特殊字符的关键字可安全往返`() {
        val original = FilterSpec(keyword = "100% 的 \"螺丝刀\" \\ 备注")
        val decoded = FilterCodec.decode(FilterCodec.encode(original))
        assertEquals(original.keyword, decoded?.keyword)
    }
}
