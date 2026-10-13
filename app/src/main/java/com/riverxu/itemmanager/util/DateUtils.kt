package com.riverxu.itemmanager.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 日期与过期状态处理。
 */
object DateUtils {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(zone()).toInstant().toEpochMilli()

    /** 当天 0 点的毫秒时间戳。 */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long = toMillis(toLocalDate(now))

    fun formatDate(millis: Long?): String =
        millis?.let { toLocalDate(it).format(formatter) } ?: "很久"

    /** 购入时间展示：null 表示"很久"。 */
    fun formatPurchaseDate(millis: Long?): String = millis?.let { formatDate(it) } ?: "很久"

    /**
     * 距离过期还剩的天数（按自然日计算）。
     * 负数表示已过期天数。
     */
    fun daysUntilExpiry(expiryMillis: Long, now: Long = System.currentTimeMillis()): Long =
        ChronoUnit.DAYS.between(toLocalDate(now), toLocalDate(expiryMillis))

    /**
     * 过期状态文案，例如"还有 3 天过期"、"已过期 5 天"、"今天过期"。
     */
    fun expiryLabel(expiryMillis: Long?, now: Long = System.currentTimeMillis()): String? {
        if (expiryMillis == null) return null
        val days = daysUntilExpiry(expiryMillis, now)
        return when {
            days > 1 -> "还有 $days 天过期"
            days == 1L -> "明天过期"
            days == 0L -> "今天过期"
            days == -1L -> "已过期 1 天"
            else -> "已过期 ${-days} 天"
        }
    }

    enum class ExpiryLevel { NONE, SAFE, SOON, URGENT, EXPIRED }

    fun expiryLevel(expiryMillis: Long?, now: Long = System.currentTimeMillis()): ExpiryLevel {
        if (expiryMillis == null) return ExpiryLevel.NONE
        val days = daysUntilExpiry(expiryMillis, now)
        return when {
            days < 0 -> ExpiryLevel.EXPIRED
            days <= 7 -> ExpiryLevel.URGENT
            days <= 30 -> ExpiryLevel.SOON
            else -> ExpiryLevel.SAFE
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.CHINA, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.CHINA, "%.1f MB", mb)
        return String.format(java.util.Locale.CHINA, "%.2f GB", mb / 1024.0)
    }

    /** 备份文件名时间戳：20261012_091500 */
    fun backupTimestamp(now: Long = System.currentTimeMillis()): String {
        val dateTime = Instant.ofEpochMilli(now).atZone(zone())
        return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(dateTime)
    }
}
