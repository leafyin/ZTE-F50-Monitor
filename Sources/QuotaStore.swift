import Foundation

// MARK: - 套餐模型
// 设备自带的“当月流量”是从它自己的计数起点算的，和运营商账单口径对不上。
// 所以口径是：已用 = 校准时的剩余量 - 校准后设备计数器的增量。

struct QuotaConfig: Codable {
    var totalGB: Double = 160
    var remainingBaseGB: Double = 88.65
    var baseDeviceBytes: Int64 = 0
    var calibratedAt: Date = Date()
    var isCalibrated: Bool = false
    /// 设备计数器冻结期间（固件不再累加 monthly 字节）靠固件瞬时速率估算出来的用量。
    /// 用 Optional 是为了能解码旧版本存下的 JSON（缺这个键也不会整体解码失败）。
    var extraBytes: Int64? = nil
}

final class QuotaStore {

    static let shared = QuotaStore()

    private let configKey = "quota.config.v1"
    private let usageKey = "quota.dailyUsage.v1"

    private(set) var config: QuotaConfig
    private var dailyUsage: [String: Int64]

    private let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private init() {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: configKey),
           let decoded = try? JSONDecoder().decode(QuotaConfig.self, from: data) {
            config = decoded
        } else {
            config = QuotaConfig()
        }
        dailyUsage = (defaults.dictionary(forKey: usageKey) as? [String: Int64]) ?? [:]
    }

    // MARK: 计算

    /// 依当前设备计数器推算套餐剩余（GB）
    func remaining(deviceBytes: Int64) -> Double {
        guard config.isCalibrated else { return config.remainingBaseGB }
        var base = config.baseDeviceBytes
        // 计数器被重置（变小）时，把基准跟着挪到新起点，避免出现负增长
        if deviceBytes < base { base = deviceBytes }
        let usedSinceBase = Double(deviceBytes - base) / 1_000_000_000
        let extra = Double(config.extraBytes ?? 0) / 1_000_000_000
        return max(0, config.remainingBaseGB - usedSinceBase - extra)
    }

    // MARK: 写入

    func calibrate(totalGB: Double, remainingGB: Double, deviceBytes: Int64, at date: Date = Date()) {
        config.totalGB = max(0.01, totalGB)
        config.remainingBaseGB = max(0, min(remainingGB, totalGB))
        config.baseDeviceBytes = deviceBytes
        config.calibratedAt = date
        config.isCalibrated = true
        config.extraBytes = 0
        saveConfig()
    }

    /// 首次拿到数据时，用用户给定的“当前剩余”自动建立基准
    func autoCalibrateIfNeeded(deviceBytes: Int64) {
        guard !config.isCalibrated else { return }
        calibrate(totalGB: config.totalGB,
                  remainingGB: config.remainingBaseGB,
                  deviceBytes: deviceBytes)
    }

    /// 把冻结期间的估算账清零（重新校准或需要重置时用）
    func resetExtra() {
        config.extraBytes = 0
        saveConfig()
    }

    /// 计数器冻结时，用固件瞬时速率累加出来的用量
    func addExtraBytes(_ bytes: Int64) {
        guard bytes > 0 else { return }
        config.extraBytes = (config.extraBytes ?? 0) + bytes
        saveConfig()
    }

    func addUsage(bytes: Int64, at date: Date = Date()) {
        guard bytes > 0 else { return }
        let key = dayFormatter.string(from: date)
        dailyUsage[key, default: 0] += bytes
        // 只保留最近 90 天，防止无限膨胀
        if dailyUsage.count > 90 {
            let sorted = dailyUsage.keys.sorted()
            for old in sorted.prefix(dailyUsage.count - 90) { dailyUsage.removeValue(forKey: old) }
        }
        UserDefaults.standard.set(dailyUsage, forKey: usageKey)
    }

    func todayBytes(at date: Date = Date()) -> Int64 {
        dailyUsage[dayFormatter.string(from: date)] ?? 0
    }

    // MARK: 持久化

    private func saveConfig() {
        guard let data = try? JSONEncoder().encode(config) else { return }
        UserDefaults.standard.set(data, forKey: configKey)
    }
}
