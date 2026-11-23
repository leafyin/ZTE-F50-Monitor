import AppKit
import SwiftUI

/// 套餐校准窗口。用 AppKit 自己管一个 NSWindow：
/// - 不在启动时自动弹（不声明任何 SwiftUI 场景）
/// - 关闭窗口不会结束整个 App
@MainActor
final class CalibrationWindowController: NSObject, NSWindowDelegate {

    static let shared = CalibrationWindowController()

    private var window: NSWindow?

    func show() {
        if let window {
            NSApp.activate(ignoringOtherApps: true)
            window.makeKeyAndOrderFront(nil)
            return
        }

        let root = CalibrationView(engine: MonitorEngine.shared) { [weak self] in
            self?.close()
        }
        let hosting = NSHostingController(rootView: root)
        let win = NSWindow(contentViewController: hosting)
        win.title = "套餐校准"
        win.styleMask = [.titled, .closable]
        win.isReleasedWhenClosed = false
        win.delegate = self
        win.setContentSize(hosting.view.fittingSize)
        win.center()
        window = win

        NSApp.activate(ignoringOtherApps: true)
        win.makeKeyAndOrderFront(nil)
    }

    func close() {
        window?.close()
    }

    func windowWillClose(_ notification: Notification) {
        window = nil
    }
}
