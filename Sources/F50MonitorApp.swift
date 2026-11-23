import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        MonitorEngine.shared.start()
        StatusItemController.shared.start()
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    func applicationWillTerminate(_ notification: Notification) {
        MonitorEngine.shared.stop()
        StatusItemController.shared.stop()
    }
}

/// 纯 AppKit 生命周期：不声明任何 SwiftUI Scene，
/// 启动时不会自动弹出窗口。界面仍是 SwiftUI，由 NSStatusItem 的 popover 和 NSWindow 承载。
@main
enum F50MonitorMain {

    private static var appDelegate: AppDelegate?

    @MainActor
    static func main() {
        let app = NSApplication.shared
        let delegate = AppDelegate()
        appDelegate = delegate
        app.delegate = delegate
        app.setActivationPolicy(.accessory)
        app.run()
    }
}
