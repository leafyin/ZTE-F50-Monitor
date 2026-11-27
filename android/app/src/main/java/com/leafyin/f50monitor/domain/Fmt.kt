package com.leafyin.f50monitor.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 格式化规则与 macOS 版一致：十进制 1 KB = 1000 B。 */
object Fmt {

    fun bytes(b: Long): String = when {
        b < 1_000 -> "$b B"
        b < 1_000_000 -> "${b / 1_000} KB"
        b < 1_000_000_000L -> String.format(Locale.US, "%.1f MB", b / 1_000_000.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1_000_000_000.0)
    }

    fun gb(v: Double): String =
        if (v < 1) "${(v * 1000).toInt()} MB" else String.format(Locale.US, "%.2f GB", v)

    /** 返回 (数值, 单位)，用在速率大字上 */
    fun rateFull(bps: Double): Pair<String, String> = when {
        bps < 1_000 -> "${bps.toInt()}" to "B/s"
        bps < 1_000_000 -> String.format(Locale.US, "%.1f", bps / 1_000.0) to "KB/s"
        else -> String.format(Locale.US, "%.2f", bps / 1_000_000.0) to "MB/s"
    }

    /** 通知/小组件用的紧凑写法，固定宽度不跳动 */
    fun rateShort(bps: Double): String = when {
        bps < 1_000 -> "${bps.toInt()}B"
        bps < 1_000_000 -> "${(bps / 1_000).toInt()}K"
        else -> {
            val mb = bps / 1_000_000.0
            if (mb < 10) String.format(Locale.US, "%.1fM", mb) else "${mb.toInt()}M"
        }
    }

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    fun dayKey(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}
