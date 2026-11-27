package com.leafyin.f50monitor.service

import com.leafyin.f50monitor.data.RouterSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 进程内唯一的数据总线：Service 往里写，Activity / Widget 读。
 * 单进程即可满足需求，省掉跨进程和数据库。
 */
object MonitorState {

    data class PanelState(
        val snapshot: RouterSnapshot? = null,
        val downBps: Double = 0.0,
        val upBps: Double = 0.0,
        val rateSource: String = "—",
        val usageSource: String = "—",
        val counterDelta: Long = 0,
        val usageBytes: Long = 0,
        val extraBytes: Long = 0,
        val counterBytes: Long = 0,
        val remainingGB: Double = 0.0,
        val usedGB: Double = 0.0,
        val totalGB: Double = 160.0,
        val todayBytes: Long = 0,
        val calibrated: Boolean = false,
        val baseTimeMs: Long = 0,
        val responseMs: Long = 0,
        val error: String? = null,
        val lastUpdate: Long = 0,
        val gateway: String = "192.168.0.1",
        val localIp: String? = null,
        val samplesDown: List<Double> = emptyList(),
        val samplesUp: List<Double> = emptyList(),
        val running: Boolean = false,
    ) {
        val netLabel: String get() = snapshot?.networkType?.ifEmpty { "—" } ?: "—"
        val carrier: String get() = snapshot?.provider?.ifEmpty { "未连接 F50" } ?: "未连接 F50"
        val signalBars: Int get() = snapshot?.signalBars ?: 0
        val deviceCount: Int get() = snapshot?.stations?.size ?: 0
    }

    val state = MutableStateFlow(PanelState())

    fun set(block: (PanelState) -> PanelState) {
        state.value = block(state.value)
    }
}
