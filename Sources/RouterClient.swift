import Foundation

/// 直接走 F50 本地管理页的 goform 接口读取状态。
/// 该接口在 MU5120（F50_FLYMODEM_ZYV1.0.0B09）上免登录可读，
/// 只要求请求头带 Referer（设备用它做 CSRF 校验）——网页端自己也是这么做的。
struct RouterClient {

    static let host = "192.168.0.1"

    private static let fields: [String] = [
        "network_type",
        "network_provider",
        "signalbar",
        "Z5g_rsrp",
        "ppp_status",
        "modem_main_state",
        "wan_ipaddr",
        "monthly_rx_bytes",
        "monthly_tx_bytes",
        "realtime_rx_thrpt",
        "realtime_tx_thrpt",
        "monthly_time",
        "wifi_onoff_state",
        "wifi_chip1_ssid1_ssid",
        "station_list",
        "cr_version",
        "web_version",
        "hardware_version"
    ]

    struct Result {
        var snapshot: RouterSnapshot
        var elapsedMs: Double
    }

    enum ClientError: LocalizedError {
        case badURL
        case badStatus(Int)
        case badPayload

        var errorDescription: String? {
            switch self {
            case .badURL: return "接口地址无效"
            case .badStatus(let code): return "设备返回 HTTP \(code)"
            case .badPayload: return "设备返回的数据无法解析"
            }
        }
    }

    static var endpoint: URL? {
        var comps = URLComponents()
        comps.scheme = "http"
        comps.host = host
        comps.path = "/goform/goform_get_cmd_process"
        comps.queryItems = [
            URLQueryItem(name: "isTest", value: "false"),
            URLQueryItem(name: "multi_data", value: "1"),
            URLQueryItem(name: "cmd", value: fields.joined(separator: ","))
        ]
        return comps.url
    }

    static func fetch() async throws -> Result {
        guard let url = endpoint else { throw ClientError.badURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 4
        request.setValue("http://192.168.0.1/index.html", forHTTPHeaderField: "Referer")
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        request.cachePolicy = .reloadIgnoringLocalCacheData

        let started = CFAbsoluteTimeGetCurrent()
        let (data, response) = try await URLSession.shared.data(for: request)
        let elapsed = (CFAbsoluteTimeGetCurrent() - started) * 1000

        if let http = response as? HTTPURLResponse, http.statusCode != 200 {
            throw ClientError.badStatus(http.statusCode)
        }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ClientError.badPayload
        }

        return Result(snapshot: parse(json), elapsedMs: elapsed)
    }

    // MARK: - 解析

    private static func parse(_ dict: [String: Any]) -> RouterSnapshot {
        var snap = RouterSnapshot()
        snap.timestamp = Date()
        snap.networkType = string(dict["network_type"])
        snap.provider = string(dict["network_provider"])
        snap.signalBars = Int(int(dict["signalbar"]) ?? 0)
        snap.rsrp = int(dict["Z5g_rsrp"]).map { Int($0) }
        snap.pppStatus = string(dict["ppp_status"])
        snap.wanIP = string(dict["wan_ipaddr"])
        snap.monthlyRX = int(dict["monthly_rx_bytes"]) ?? 0
        snap.monthlyTX = int(dict["monthly_tx_bytes"]) ?? 0
        snap.realtimeRX = int(dict["realtime_rx_thrpt"]) ?? 0
        snap.realtimeTX = int(dict["realtime_tx_thrpt"]) ?? 0
        snap.counterSeconds = Int(int(dict["monthly_time"]) ?? 0)
        snap.wifiOn = string(dict["wifi_onoff_state"]) == "1"
        snap.ssid = string(dict["wifi_chip1_ssid1_ssid"])
        snap.firmware = string(dict["web_version"]).isEmpty
            ? string(dict["cr_version"]) : string(dict["web_version"])
        snap.hardware = string(dict["hardware_version"])
        snap.stations = stations(dict["station_list"])
        return snap
    }

    private static func string(_ any: Any?) -> String {
        if let s = any as? String { return s }
        if let n = any as? NSNumber { return n.stringValue }
        return ""
    }

    private static func int(_ any: Any?) -> Int64? {
        if let n = any as? NSNumber { return n.int64Value }
        if let s = any as? String { return Int64(s) }
        return nil
    }

    private static func stations(_ any: Any?) -> [Station] {
        var raw: [[String: Any]] = []
        if let arr = any as? [[String: Any]] {
            raw = arr
        } else if let s = any as? String, let data = s.data(using: .utf8),
                  let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            raw = arr
        }
        return raw.map {
            Station(mac: string($0["mac_addr"]),
                    hostname: string($0["hostname"]),
                    ip: string($0["ip_addr"]))
        }
        .filter { !$0.mac.isEmpty || !$0.ip.isEmpty }
    }
}
