package com.riverxu.itemmanager.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日期与过期状态测试。
 */
class DateUtilsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 12)
    private val now: Long = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `null 购入时间显示为很久`() {
        assertEquals("很久", DateUtils.formatPurchaseDate(null))
    }

    @Test
    fun `具体日期格式为年月日`() {
        assertEquals("2026-10-12", DateUtils.formatDate(now))
    }

    @Test
    fun `剩余天数为正表示尚未过期`() {
        assertEquals(3L, DateUtils.daysUntilExpiry(now + 3 * day, now))
        assertEquals("还有 3 天过期", DateUtils.expiryLabel(now + 3 * day, now))
    }

    @Test
    fun `明天与今天过期文案`() {
        assertEquals("明天过期", DateUtils.expiryLabel(now + day, now))
        assertEquals("今天过期", DateUtils.expiryLabel(now, now))
    }

    @Test
    fun `已过期天数文案`() {
        assertEquals("已过期 1 天", DateUtils.expiryLabel(now - day, now))
        assertEquals(-10L, DateUtils.daysUntilExpiry(now - 10 * day, now))
        assertEquals("已过期 10 天", DateUtils.expiryLabel(now - 10 * day, now))
    }

    @Test
    fun `未设置过期时间没有文案`() {
        assertNull(DateUtils.expiryLabel(null, now))
    }

    @Test
    fun `过期等级划分`() {
        assertEquals(DateUtils.ExpiryLevel.NONE, DateUtils.expiryLevel(null, now))
        assertEquals(DateUtils.ExpiryLevel.EXPIRED, DateUtils.expiryLevel(now - day, now))
        assertEquals(DateUtils.ExpiryLevel.URGENT, DateUtils.expiryLevel(now + 5 * day, now))
        assertEquals(DateUtils.ExpiryLevel.SOON, DateUtils.expiryLevel(now + 20 * day, now))
        assertEquals(DateUtils.ExpiryLevel.SAFE, DateUtils.expiryLevel(now + 120 * day, now))
    }

    @Test
    fun `字节格式化`() {
        assertEquals("512 B", DateUtils.formatBytes(512))
        assertTrue(DateUtils.formatBytes(2048).contains("KB"))
        assertTrue(DateUtils.formatBytes(5L * 1024 * 1024).contains("MB"))
        assertTrue(DateUtils.formatBytes(3L * 1024 * 1024 * 1024).contains("GB"))
    }

    @Test
    fun `备份时间戳格式为14位`() {
        val stamp = DateUtils.backupTimestamp(now)
        assertEquals(15, stamp.length) // yyyyMMdd_HHmmss
        assertTrue(stamp.contains("_"))
    }

    @Test
    fun `当天零点计算`() {
        val start = DateUtils.startOfToday(now + 13 * 60 * 60 * 1000)
        assertEquals(now, start)
    }
}
