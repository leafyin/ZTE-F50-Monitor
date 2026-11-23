import Foundation

struct Station: Identifiable, Hashable {
    var mac: String = ""
    var hostname: String = ""
    var ip: String = ""
    var id: String { mac }
    var displayName: String { hostname.isEmpty ? mac : hostname }
}

struct RouterSnapshot {
    var timestamp: Date = Date()
    var networkType: String = ""
    var provider: String = ""
    var signalBars: Int = 0
    var rsrp: Int?
    var pppStatus: String = ""
    var wanIP: String = ""
    var monthlyRX: Int64 = 0
    var monthlyTX: Int64 = 0
    // 固件自报瞬时速率（字节/秒）。部分状态下恒为 0，需要和计数器差分互为兜底。
    var realtimeRX: Int64 = 0
    var realtimeTX: Int64 = 0
    var counterSeconds: Int = 0
    var wifiOn: Bool = false
    var ssid: String = ""
    var stations: [Station] = []
    var firmware: String = ""
    var hardware: String = ""

    var totalBytes: Int64 { monthlyRX + monthlyTX }

    var connected: Bool {
        pppStatus.contains("connected") && !networkType.isEmpty
    }

    var networkLabel: String {
        networkType.isEmpty ? "—" : networkType
    }

    /// month counter 起点（设备侧只统计这一段时间）
    var counterStart: Date? {
        guard counterSeconds > 0 else { return nil }
        return timestamp.addingTimeInterval(-Double(counterSeconds))
    }
}

enum Fmt {

    /// 菜单栏用的紧凑速率：1.2M / 340K / 12B
    static func compactRate(_ bps: Double) -> String {
        if bps < 1000 { return String(format: "%.0fB", bps) }
        let kb = bps / 1000
        if kb < 1000 { return String(format: "%.0fK", kb) }
        let mb = kb / 1000
        if mb < 10 { return String(format: "%.1fM", mb) }
        return String(format: "%.0fM", mb)
    }

    static func bytes(_ value: Int64) -> String {
        let gb = Double(value) / 1_000_000_000
        if gb >= 1 { return String(format: "%.2f GB", gb) }
        let mb = Double(value) / 1_000_000
        if mb >= 1 { return String(format: "%.0f MB", mb) }
        let kb = Double(value) / 1_000
        return String(format: "%.0f KB", kb)
    }

    static func gb(_ value: Double) -> String {
        if value < 1 { return String(format: "%.0f MB", value * 1000) }
        return String(format: "%.2f GB", value)
    }

    static func time(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: date)
    }

    static func shortDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "M月d日"
        return f.string(from: date)
    }
}
