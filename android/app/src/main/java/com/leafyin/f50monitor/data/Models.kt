package com.leafyin.f50monitor.data

data class Station(
    val mac: String = "",
    val hostname: String = "",
    val ip: String = "",
) {
    val displayName: String get() = hostname.ifEmpty { mac }
}

/**
 * 一帧设备状态。字段与 F50（MU5120）gofrm 接口一一对应，命名沿用 macOS 版。
 */
data class RouterSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val networkType: String = "",
    val provider: String = "",
    val signalBars: Int = 0,
    val rsrp: Int? = null,
    val pppStatus: String = "",
    val wanIp: String = "",
    val monthlyRX: Long = 0,
    val monthlyTX: Long = 0,
    /** 固件自报瞬时速率，单位字节/秒；部分固件/状态下恒为 0 */
    val realtimeRX: Long = 0,
    val realtimeTX: Long = 0,
    /** 计数器窗口长度（秒），固件从自己的起点算起 */
    val counterSeconds: Int = 0,
    val ssid: String = "",
    val stations: List<Station> = emptyList(),
) {
    val totalBytes: Long get() = monthlyRX + monthlyTX

    val connected: Boolean get() = pppStatus.contains("connected") && networkType.isNotEmpty()
}
