package com.leafyin.f50monitor.domain

import com.leafyin.f50monitor.data.RouterSnapshot

/**
 * 真机（魅族 21 Pro + MU5120，2026-11-27）实测后调整过的口径：
 *
 * 1) monthly_rx_bytes / monthly_tx_bytes 在这台固件上【不是】真实流量计数器——
 *    下载 136MB，六分钟内计数器只涨 11MB，且空载时仍以 30–100KB/s 匀速漂移，
 *    不能用来记账。macOS 版把计数器差分当精确值的假设在真机上不成立。
 * 2) realtime_rx_thrpt 实测可用：下载 10MB 的积分落在 6.5–9.6MB（0.6–0.9x），
 *    高速段（6.8MB/s）积分 127MB / 实际 136MB（0.93x），方向与量级都对。
 * 3) realtime_tx_thrpt 噪声大（空载都能报 100KB/s），所以记账仍然只采信下行。
 *
 * 因此用量改为「固件瞬时下行积分」为主，计数器差分只作诊断；
 * 固件瞬时全为 0 的机型才退回计数器差分。
 * 采样间隔过大（息屏/被系统冻结后恢复）时跳过积分，避免一个大 dt 放大成巨量。
 */
class UsageCalculator(
    private val baselineBps: Double = 4_000.0,
    private val deadzoneBps: Double = 10_000.0,
    private val maxIntegrateDtSec: Double = 5.0,
) {

    enum class RateSource(val label: String) {
        REALTIME("固件瞬时"),
        COUNTER("计数器差分"),
        NONE("无数据"),
    }

    enum class UsageSource(val label: String) {
        FIRMWARE("固件积分"),
        COUNTER("计数器差分"),
        ESTIMATE_SKIPPED("积分跳过"),
        NONE("无"),
    }

    data class Output(
        val downBps: Double,
        val upBps: Double,
        val rateSource: RateSource,
        val usageSource: UsageSource,
        val usageBytes: Long,
        val counterDelta: Long,
        val firmwareBytes: Long,
    )

    private var prevRX: Long? = null
    private var prevTX: Long? = null

    fun reset() {
        prevRX = null
        prevTX = null
    }

    fun step(s: RouterSnapshot, dtSec: Double): Output {
        var counterDown = 0L
        var counterUp = 0L
        prevRX?.let { counterDown = (s.monthlyRX - it).coerceAtLeast(0) }
        prevTX?.let { counterUp = (s.monthlyTX - it).coerceAtLeast(0) }
        val counterDelta = counterDown + counterUp

        val rtDown = s.realtimeRX.toDouble()
        val rtUp = s.realtimeTX.toDouble()

        var down = 0.0
        var up = 0.0
        var usage = 0L
        var rateSource = RateSource.NONE
        var usageSource = UsageSource.NONE
        var estimated = 0L

        if (rtDown + rtUp > 0) {
            // 速率：固件自报瞬时值
            down = rtDown
            up = rtUp
            rateSource = RateSource.REALTIME

            // 用量：固件下行积分（扣基线、设死区，只在上行噪声之外算真实下行）
            if (dtSec > maxIntegrateDtSec) {
                usageSource = UsageSource.ESTIMATE_SKIPPED
            } else if (rtDown >= deadzoneBps) {
                estimated = ((rtDown - baselineBps) * dtSec).toLong()
                if (estimated > 0) {
                    usage = estimated
                    usageSource = UsageSource.FIRMWARE
                }
            }
        } else if (counterDelta > 0 && dtSec > 0) {
            // 源 2：固件不吐瞬时值，退回计数器差分
            down = counterDown / dtSec
            up = counterUp / dtSec
            rateSource = RateSource.COUNTER
            usage = counterDelta
            usageSource = UsageSource.COUNTER
        }

        prevRX = s.monthlyRX
        prevTX = s.monthlyTX

        return Output(down, up, rateSource, usageSource, usage, counterDelta, estimated)
    }
}
