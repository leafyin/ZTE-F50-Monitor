import SwiftUI
import AppKit

struct PanelView: View {

    @ObservedObject var engine: MonitorEngine
    @AppStorage("showRateInMenuBar") private var showRateInMenuBar = true

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            header
            rateSection
            Divider().opacity(0.5)
            quotaSection
            Divider().opacity(0.5)
            deviceSection
            Divider().opacity(0.5)
            footer
        }
        .padding(14)
        .frame(width: 336)
    }

    // MARK: 顶部

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            Text(engine.snapshot?.provider.isEmpty == false ? engine.snapshot!.provider : "中兴 F50")
                .font(.system(size: 11.5, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Text(engine.snapshot?.networkLabel ?? "…")
                .font(.system(size: 10, weight: .bold))
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.secondary.opacity(0.16))
                )

            SignalBars(bars: engine.snapshot?.signalBars ?? 0,
                       color: engine.errorText == nil ? Theme.signal : .secondary)

            if let rsrp = engine.snapshot?.rsrp {
                Text("\(rsrp) dBm")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if engine.errorText != nil {
                Text("离线")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.red)
            } else {
                Text(String(format: "%.0f ms", engine.responseMs))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    // MARK: 速率

    private var rateSection: some View {
        VStack(spacing: 6) {
            rateRow(title: "下行", arrow: "↓", value: engine.downBps, samples: engine.downSamples, color: Theme.down)
            rateRow(title: "上行", arrow: "↑", value: engine.upBps, samples: engine.upSamples, color: Theme.up)
        }
    }

    private func rateRow(title: String, arrow: String, value: Double, samples: [Double], color: Color) -> some View {
        HStack(alignment: .center, spacing: 7) {
            Text(title)
                .font(.system(size: 10.5))
                .foregroundStyle(.secondary)
                .frame(width: 26, alignment: .leading)

            Text(arrow)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(color)

            Text(rateText(value))
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                .foregroundStyle(color)
                .frame(width: 86, alignment: .leading)

            MiniSparkline(samples: samples, color: color)
                .frame(height: 16)
        }
    }

    private func rateText(_ bps: Double) -> String {
        if bps < 1000 { return String(format: "%.0fB/s", bps) }
        let kb = bps / 1000
        if kb < 1000 { return String(format: "%.1fK/s", kb) }
        let mb = kb / 1000
        if mb < 100 { return String(format: "%.2fM/s", mb) }
        return String(format: "%.0fM/s", mb)
    }

    // MARK: 套餐

    private var quotaSection: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(alignment: .firstTextBaseline) {
                Text("本月剩余")
                    .font(.system(size: 10.5))
                    .foregroundStyle(.secondary)

                Text(Fmt.gb(engine.remainingGB))
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(quotaColor)

                Text("/ \(String(format: "%.0f", engine.totalGB)) GB")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)

                Spacer()

                Text("今日 \(Fmt.bytes(engine.todayBytes))")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.secondary.opacity(0.16))
                    RoundedRectangle(cornerRadius: 3)
                        .fill(quotaColor)
                        .frame(width: max(2, geo.size.width * quotaFraction))
                }
            }
            .frame(height: 6)

            Text("已用 \(Fmt.gb(engine.usedGB)) · 校准基准 \(Fmt.gb(QuotaStore.shared.config.remainingBaseGB)) @ \(Fmt.shortDate(QuotaStore.shared.config.calibratedAt))")
                .font(.system(size: 9.5))
                .foregroundStyle(.tertiary)
        }
    }

    private var quotaFraction: Double {
        guard engine.totalGB > 0 else { return 0 }
        return min(1, max(0, engine.usedGB / engine.totalGB))
    }

    private var quotaColor: Color {
        let left = engine.remainingGB
        if left < 10 { return .red }
        if left < 30 { return .orange }
        return Theme.signal
    }

    // MARK: 设备

    private var deviceSection: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text("在线设备")
                    .font(.system(size: 10.5))
                    .foregroundStyle(.secondary)
                Text("\(engine.snapshot?.stations.count ?? 0)")
                    .font(.system(size: 10.5, weight: .semibold))
                Spacer()
                if !engine.localIP.isEmpty {
                    Text("本机 \(engine.localIP)")
                        .font(.system(size: 9.5, design: .monospaced))
                        .foregroundStyle(.tertiary)
                }
            }

            if let stations = engine.snapshot?.stations, !stations.isEmpty {
                ForEach(stations) { station in
                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color.secondary.opacity(0.45))
                            .frame(width: 5, height: 5)
                        Text(station.displayName)
                            .font(.system(size: 10.5))
                            .lineLimit(1)
                        Spacer()
                        Text(station.ip)
                            .font(.system(size: 9.5, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                Text("暂无设备信息")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)
            }
        }
    }

    // MARK: 底部

    private var footer: some View {
        HStack(spacing: 6) {
            Button("管理页") {
                if let url = URL(string: "http://192.168.0.1") {
                    NSWorkspace.shared.open(url)
                }
            }
            Button("校准套餐") {
                CalibrationWindowController.shared.show()
            }
            Toggle("速率", isOn: $showRateInMenuBar)
                .toggleStyle(.switch)
                .controlSize(.mini)
                .font(.system(size: 9.5))
                .foregroundStyle(.secondary)
            Spacer()
            if let last = engine.lastUpdate {
                Text(Fmt.time(last))
                    .font(.system(size: 9.5, design: .monospaced))
                    .foregroundStyle(.tertiary)
            }
            Button("退出") { NSApp.terminate(nil) }
        }
        .buttonStyle(FooterButtonStyle())
    }
}

// MARK: - 小组件

struct SignalBars: View {
    let bars: Int
    let color: Color

    var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<5, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1)
                    .fill(index < bars ? color : Color.secondary.opacity(0.25))
                    .frame(width: 3, height: 4 + CGFloat(index) * 2.4)
            }
        }
    }
}

struct MiniSparkline: View {
    let samples: [Double]
    let color: Color

    var body: some View {
        GeometryReader { geo in
            let values = normalized
            Path { path in
                guard values.count > 1 else { return }
                let step = geo.size.width / CGFloat(values.count - 1)
                for (index, value) in values.enumerated() {
                    let point = CGPoint(x: CGFloat(index) * step,
                                        y: geo.size.height * (1 - CGFloat(value)))
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
            }
            .stroke(color.opacity(0.85), lineWidth: 1)
        }
    }

    /// 用 sqrt 压一下尖峰，避免一根柱子顶到头
    private var normalized: [Double] {
        let tail = Array(samples.suffix(40))
        let peak = tail.max() ?? 0
        guard peak > 1 else { return tail.map { _ in 0 } }
        return tail.map { sqrt($0 / peak) }
    }
}

struct FooterButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 10))
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(Color.secondary.opacity(configuration.isPressed ? 0.26 : 0.13))
            )
            .foregroundStyle(.primary)
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - 配色

/// 下行蓝、上行橙，按深浅色外观各调明度，保证两种菜单栏底色下都够对比
enum Theme {

    static let down = Color(nsColor: NSColor(name: nil) { appearance in
        isDark(appearance)
            ? NSColor(srgbRed: 0.36, green: 0.70, blue: 1.00, alpha: 1)
            : NSColor(srgbRed: 0.04, green: 0.36, blue: 0.82, alpha: 1)
    })

    static let up = Color(nsColor: NSColor(name: nil) { appearance in
        isDark(appearance)
            ? NSColor(srgbRed: 1.00, green: 0.74, blue: 0.32, alpha: 1)
            : NSColor(srgbRed: 0.70, green: 0.30, blue: 0.00, alpha: 1)
    })

    static let signal = Color(nsColor: NSColor(name: nil) { appearance in
        isDark(appearance)
            ? NSColor(srgbRed: 0.36, green: 0.86, blue: 0.45, alpha: 1)
            : NSColor(srgbRed: 0.05, green: 0.55, blue: 0.18, alpha: 1)
    })

    private static func isDark(_ appearance: NSAppearance) -> Bool {
        appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
    }
}
