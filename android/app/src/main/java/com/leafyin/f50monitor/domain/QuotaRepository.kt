package com.leafyin.f50monitor.domain

import android.content.Context
import org.json.JSONObject

/**
 * 套餐口径：校准时的「剩余」是锚点，之后按固件下行瞬时积分逐秒扣减。
 *
 * 为什么不再用 deviceDelta（monthly 计数器增量）：真机实测这台 MU5120 的
 * monthly_rx_bytes 不跟真实流量（下 136MB 只涨 11MB，空载还匀速漂移），
 * 用它记账会严重少算。计数器差分只保留作诊断，和运营商账单对不上时能对比。
 */
class QuotaRepository(context: Context) {

    private val sp = context.getSharedPreferences("quota.v1", Context.MODE_PRIVATE)

    data class Config(
        val totalGB: Double = 160.0,
        val remainingBaseGB: Double = 160.0,
        val baseDeviceBytes: Long = 0,
        val extraBytes: Long = 0,      // 固件下行积分累计（本月）
        val counterBytes: Long = 0,    // 计数器差分累计（仅诊断）
        val calibrated: Boolean = false,
        val calibratedAt: Long = 0,
    )

    companion object {
        const val DEC = 1_000_000_000L
        const val BIN = 1_073_741_824L
        private const val KEY_CONFIG = "config"
        private const val KEY_DAILY = "daily"
        private const val KEY_UNIT_BIN = "unit_bin"
        private const val KEY_INTERVAL = "interval_sec"
        private const val KEEP_DAYS = 90
    }

    fun unitBytes(): Long = if (sp.getBoolean(KEY_UNIT_BIN, false)) BIN else DEC

    fun setUnitBin(bin: Boolean) = sp.edit().putBoolean(KEY_UNIT_BIN, bin).apply()

    fun gatewayOverride(): String = sp.getString("gateway_override", "") ?: ""

    fun setGatewayOverride(v: String) = sp.edit().putString("gateway_override", v.trim()).apply()

    fun intervalSec(): Int = sp.getInt(KEY_INTERVAL, 2).coerceIn(1, 60)

    fun setIntervalSec(v: Int) = sp.edit().putInt(KEY_INTERVAL, v).apply()

    fun config(): Config {
        val raw = sp.getString(KEY_CONFIG, null) ?: return Config()
        return try {
            val o = JSONObject(raw)
            Config(
                totalGB = o.optDouble("totalGB", 160.0),
                remainingBaseGB = o.optDouble("remainingBaseGB", 160.0),
                baseDeviceBytes = o.optLong("baseDeviceBytes", 0),
                extraBytes = o.optLong("extraBytes", 0),
                counterBytes = o.optLong("counterBytes", 0),
                calibrated = o.optBoolean("calibrated", false),
                calibratedAt = o.optLong("calibratedAt", 0),
            )
        } catch (e: Exception) {
            Config()
        }
    }

    private fun save(c: Config) {
        val o = JSONObject()
            .put("totalGB", c.totalGB)
            .put("remainingBaseGB", c.remainingBaseGB)
            .put("baseDeviceBytes", c.baseDeviceBytes)
            .put("extraBytes", c.extraBytes)
            .put("counterBytes", c.counterBytes)
            .put("calibrated", c.calibrated)
            .put("calibratedAt", c.calibratedAt)
        sp.edit().putString(KEY_CONFIG, o.toString()).apply()
    }

    /** 剩余 GB；未校准时退化为用户填的套餐总量 */
    fun remainingGB(deviceBytes: Long): Double {
        val c = config()
        if (!c.calibrated) return c.remainingBaseGB.coerceAtLeast(0.0)
        val used = c.extraBytes.toDouble() / unitBytes()
        return (c.remainingBaseGB - used).coerceAtLeast(0.0)
    }

    fun deviceDelta(deviceBytes: Long): Long {
        val c = config()
        if (!c.calibrated) return 0
        return (deviceBytes - c.baseDeviceBytes).coerceAtLeast(0)
    }

    fun calibrate(totalGB: Double, remainingGB: Double, deviceBytes: Long) {
        val c = config()
        save(
            c.copy(
                totalGB = totalGB.coerceAtLeast(0.01),
                remainingBaseGB = remainingGB.coerceIn(0.0, totalGB.coerceAtLeast(0.01)),
                baseDeviceBytes = deviceBytes,
                extraBytes = 0,
                counterBytes = 0,
                calibrated = true,
                calibratedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 固件下行瞬时积分，作为本月用量的主账 */
    fun addExtra(bytes: Long) {
        if (bytes <= 0) return
        val c = config()
        save(c.copy(extraBytes = c.extraBytes + bytes))
    }

    /** 计数器差分累计，只做诊断对比，不参与剩余计算 */
    fun addCounter(bytes: Long) {
        if (bytes <= 0) return
        val c = config()
        save(c.copy(counterBytes = c.counterBytes + bytes))
    }

    /** 计数器回退时把基准跟着挪到新起点；返回是否发生了迁移 */
    fun migrateBaseIfNeeded(deviceBytes: Long): Boolean {
        val c = config()
        if (!c.calibrated) return false
        if (deviceBytes >= c.baseDeviceBytes) return false
        save(c.copy(baseDeviceBytes = deviceBytes))
        return true
    }

    // ---- 日用量 ----

    fun addDaily(bytes: Long) {
        if (bytes <= 0) return
        val map = dailyMap()
        val key = Fmt.dayKey()
        map.put(key, map.optLong(key, 0) + bytes)

        // 只留最近 90 天，防止无限膨胀
        val keys = map.keys().asSequence().sorted().toList()
        if (keys.size > KEEP_DAYS) {
            keys.take(keys.size - KEEP_DAYS).forEach { map.remove(it) }
        }
        sp.edit().putString(KEY_DAILY, map.toString()).apply()
    }

    fun todayBytes(): Long = dailyMap().optLong(Fmt.dayKey(), 0)

    private fun dailyMap(): JSONObject {
        val raw = sp.getString(KEY_DAILY, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
