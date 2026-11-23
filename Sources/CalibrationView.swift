import SwiftUI

/// 套餐校准窗口：填入运营商 App 里的当前剩余量，之后靠设备计数器自动递减
struct CalibrationView: View {

    @ObservedObject var engine: MonitorEngine
    var onClose: () -> Void = {}
    @State private var totalText: String = ""
    @State private var remainingText: String = ""
    @State private var hint: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            Text("套餐校准")
                .font(.system(size: 14, weight: .semibold))

            Text("按运营商 App 里显示的数字填一次。之后 App 用设备的流量计数自动往下扣，随时可以再校准。")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Text("套餐总量").font(.system(size: 12)).frame(width: 62, alignment: .leading)
                TextField("160", text: $totalText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 84)
                Text("GB").font(.system(size: 11)).foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                Text("当前剩余").font(.system(size: 12)).frame(width: 62, alignment: .leading)
                TextField("88.65", text: $remainingText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 84)
                Text("GB").font(.system(size: 11)).foregroundStyle(.secondary)
            }

            if !hint.isEmpty {
                Text(hint).font(.system(size: 10.5)).foregroundStyle(.red)
            }

            Text("校准基准：设备计数 \(Fmt.bytes(engine.snapshot?.totalBytes ?? 0))")
                .font(.system(size: 10))
                .foregroundStyle(.tertiary)

            HStack {
                Spacer()
                Button("取消") { onClose() }
                Button("保存") { save() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(18)
        .frame(width: 320)
        .onAppear {
            totalText = String(format: "%.0f", engine.totalGB)
            remainingText = String(format: "%.2f", engine.remainingGB)
        }
    }

    private func save() {
        guard let total = Double(totalText), total > 0 else {
            hint = "套餐总量需要是大于 0 的数字"
            return
        }
        guard let remaining = Double(remainingText), remaining >= 0 else {
            hint = "剩余量需要是数字"
            return
        }
        if remaining > total {
            hint = "剩余量不能大于套餐总量"
            return
        }
        engine.calibrate(totalGB: total, remainingGB: remaining)
        onClose()
    }
}
