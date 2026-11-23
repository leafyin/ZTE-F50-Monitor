import Foundation
import Combine

/// 全局状态 + 采样循环。默认 1 秒一次拉取路由器接口。
@MainActor
final class MonitorEngine: ObservableObject {

    static let shared = MonitorEngine()

    // 实时状态
    @Published var snapshot: RouterSnapshot?
    @Published var downBps: Double = 0
    @Published var upBps: Double = 0
    @Published var responseMs: Double = 0
    @Published var errorText: String?
    @Published var lastUpdate: Date?

    // 流量
    @Published var totalGB: Double = 160
    @Published var remainingGB: Double = 88.65
    @Published var usedGB: Double = 0
    @Published var todayBytes: Int64 = 0

    // 本机
    @Published var localIP: String = ""

    // 迷你折线（最近 60 个采样）
    @Published var downSamples: [Double] = []
    @Published var upSamples: [Double] = []

    private var task: Task<Void, Never>?
    private var prevRX: Int64?
    private var prevTX: Int64?
    private var lastSampleAt: Date?
    private var lastLocalIPAt: Date?

    private var interval: TimeInterval {
        let v = UserDefaults.standard.double(forKey: "pollInterval")
        return v > 0.2 ? v : 1.0
    }

    private init() {
        totalGB = QuotaStore.shared.config.totalGB
        remainingGB = QuotaStore.shared.config.remainingBaseGB
    }

    // MARK: - 生命周期

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.pollOnce()
                let sleep = self?.interval ?? 1
                try? await Task.sleep(nanoseconds: UInt64(max(0.2, sleep) * 1_000_000_000))
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }

    // MARK: - 单次采样

    private func pollOnce() async {
        do {
            let result = try await RouterClient.fetch()
            apply(result)
        } catch {
            errorText = describe(error)
        }
    }

    private func apply(_ result: RouterClient.Result) {
        let snap = result.snapshot
        let store = QuotaStore.shared
        store.autoCalibrateIfNeeded(deviceBytes: snap.totalBytes)

        snapshot = snap
        responseMs = result.elapsedMs
        errorText = nil
        lastUpdate = snap.timestamp

        // 速率来源二选一：
        //  1) 固件自报 realtime_rx/tx_thrpt（字节/秒）——即时、准确；
        //  2) monthly 计数器差分——固件不吐瞬时值时兜底。
        // 同一台 MU5120 会随状态切换（断网重连后出现过 realtime 有效、counter 冻结），
        // 所以每次采样都要两句都判断。
        var counterDown: Int64 = 0
        var counterUp: Int64 = 0
        var dt: Double = 0
        if let prx = prevRX, let ptx = prevTX, let last = lastSampleAt {
            dt = snap.timestamp.timeIntervalSince(last)
            if dt >= 0.2 {
                counterDown = max(0, snap.monthlyRX - prx)
                counterUp = max(0, snap.monthlyTX - ptx)
            }
        }

        let rtDown = Double(snap.realtimeRX)
        let rtUp = Double(snap.realtimeTX)
        var usageDelta: Int64 = 0

        if rtDown + rtUp > 0 {
            downBps = rtDown
            upBps = rtUp
            if counterDown + counterUp > 0 {
                usageDelta = counterDown + counterUp        // 计数器还在动，用精确增量
            } else if dt > 0 {
                // 计数器冻结：只能用瞬时值积分。实测 realtime_rx 与本机实际下载 1:1 吻合，
                // 而 realtime_tx 明显偏高（混了链路开销/其它设备噪声），所以估算只采信下行，
                // 并减去空闲基线、只在明显有流量时计入，避免空闲时凭空累加。
                let baseline: Double = 8_000
                if rtDown >= 30_000 {
                    let estimated = Int64((rtDown - baseline) * dt)
                    store.addExtraBytes(estimated)
                    usageDelta = max(0, estimated)
                }
            }
        } else if counterDown + counterUp > 0, dt > 0 {
            // 该固件 realtime 恒为 0：退回计数器差分
            downBps = Double(counterDown) / dt
            upBps = Double(counterUp) / dt
            usageDelta = counterDown + counterUp
        } else {
            downBps = 0
            upBps = 0
        }

        prevRX = snap.monthlyRX
        prevTX = snap.monthlyTX
        lastSampleAt = snap.timestamp

        if usageDelta > 0 {
            store.addUsage(bytes: usageDelta, at: snap.timestamp)
        }

        totalGB = store.config.totalGB
        remainingGB = store.remaining(deviceBytes: snap.totalBytes)
        usedGB = max(0, totalGB - remainingGB)
        todayBytes = store.todayBytes(at: snap.timestamp)

        downSamples.append(downBps)
        upSamples.append(upBps)
        if downSamples.count > 60 { downSamples.removeFirst(downSamples.count - 60) }
        if upSamples.count > 60 { upSamples.removeFirst(upSamples.count - 60) }

        refreshLocalIPIfNeeded()
    }

    // MARK: - 校准

    func calibrate(totalGB: Double, remainingGB: Double) {
        let deviceBytes = snapshot?.totalBytes ?? QuotaStore.shared.config.baseDeviceBytes
        QuotaStore.shared.calibrate(totalGB: totalGB, remainingGB: remainingGB, deviceBytes: deviceBytes)
        self.totalGB = QuotaStore.shared.config.totalGB
        self.remainingGB = QuotaStore.shared.remaining(deviceBytes: deviceBytes)
        self.usedGB = max(0, self.totalGB - self.remainingGB)
    }

    // MARK: - 本机地址

    private func refreshLocalIPIfNeeded() {
        let now = Date()
        if let last = lastLocalIPAt, now.timeIntervalSince(last) < 10 { return }
        lastLocalIPAt = now
        localIP = LocalNetwork.ipv4(interface: "en0") ?? localIP
    }

    private func describe(_ error: Error) -> String {
        if let client = error as? RouterClient.ClientError {
            return client.errorDescription ?? "读取失败"
        }
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain {
            switch ns.code {
            case NSURLErrorTimedOut: return "连接超时（设备是否在线？）"
            case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost:
                return "连不上 192.168.0.1"
            case NSURLErrorNotConnectedToInternet: return "未连接到 F50"
            default: return ns.localizedDescription
            }
        }
        return ns.localizedDescription
    }
}

// MARK: - 本地网卡地址

enum LocalNetwork {

    static func ipv4(interface target: String) -> String? {
        var addrs: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addrs) == 0, let first = addrs else { return nil }
        defer { freeifaddrs(addrs) }

        var ptr: UnsafeMutablePointer<ifaddrs>? = first
        while let current = ptr {
            let addr = current.pointee
            if addr.ifa_addr?.pointee.sa_family == UInt8(AF_INET) {
                let name = String(cString: addr.ifa_name)
                if name == target {
                    // 取出 IPv4 地址（ifa_addr 实际指向 sockaddr_in）
                    var sinAddr = UnsafeRawPointer(addr.ifa_addr!)
                        .assumingMemoryBound(to: sockaddr_in.self).pointee.sin_addr
                    var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                    let ok = inet_ntop(AF_INET, &sinAddr, &buffer, UInt32(INET_ADDRSTRLEN))
                    if ok != nil { return String(cString: buffer) }
                }
            }
            ptr = addr.ifa_next
        }
        return nil
    }
}
