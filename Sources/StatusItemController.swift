import AppKit
import Combine
import SwiftUI

/// 菜单栏显示用原生 NSStatusItem + 自己绘制的位图，绕开 macOS 对
/// SwiftUI 自定义 label 的降级（自定义 label 可能被压成「一行纯文字」，颜色和换行丢失）。
///
/// 显示内容：左侧网络类型大字，右侧上下两行速率
///   5G  ↓64K
///       ↑19K
@MainActor
final class StatusItemController: NSObject, NSPopoverDelegate {

    static let shared = StatusItemController()

    private var statusItem: NSStatusItem?
    private var popover: NSPopover?
    private var cancellables: Set<AnyCancellable> = []
    private var ignoreToggleUntil: Date = .distantPast
    private var cachedBgIsDark: Bool = false
    private var lastBgProbe: Date = .distantPast

    // MARK: - 生命周期

    func start() {
        guard statusItem == nil else { return }

        let item = NSStatusBar.system.statusItem(withLength: 100)
        item.button?.imagePosition = NSControl.ImagePosition.imageOnly
        item.button?.target = self
        item.button?.action = #selector(handleClick)
        statusItem = item

        let pop = NSPopover()
        pop.behavior = .transient
        pop.animates = true
        pop.delegate = self
        pop.contentViewController = NSHostingController(
            rootView: PanelView(engine: MonitorEngine.shared)
        )
        popover = pop

        // 数据每秒更新一次 → 每秒重绘一次
        let engine = MonitorEngine.shared
        Publishers.MergeMany(
            engine.$snapshot.map { _ in () }.eraseToAnyPublisher(),
            engine.$downBps.map { _ in () }.eraseToAnyPublisher(),
            engine.$upBps.map { _ in () }.eraseToAnyPublisher(),
            engine.$errorText.map { _ in () }.eraseToAnyPublisher()
        )
        .receive(on: RunLoop.main)
        .sink { [weak self] in self?.refresh() }
        .store(in: &cancellables)

        refresh()
    }

    func stop() {
        cancellables.removeAll()
        if let statusItem { NSStatusBar.system.removeStatusItem(statusItem) }
        statusItem = nil
    }

    // MARK: - 点击展开面板

    @objc private func handleClick() {
        guard let popover, let button = statusItem?.button else { return }
        if popover.isShown {
            popover.performClose(nil)
            ignoreToggleUntil = Date().addingTimeInterval(0.28)
            return
        }
        guard Date() > ignoreToggleUntil else { return }
        popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        popover.contentViewController?.view.window?.makeKey()
        NSApp.activate(ignoringOtherApps: true)
    }

    func popoverDidClose(_ notification: Notification) {
        ignoreToggleUntil = Date().addingTimeInterval(0.28)
    }

    // MARK: - 绘制

    private func refresh() {
        guard let button = statusItem?.button else { return }
        let image = render()
        button.image = image
        statusItem?.length = image.size.width + 2
        if let dumpPath = ProcessInfo.processInfo.environment["F50_DUMP_LABEL"] {
            writePNG(image, to: dumpPath)
        }
    }

    /// 菜单栏底色由壁纸明暗决定（不是 NSApp.effectiveAppearance），
    /// 所以取壁纸顶部区域的平均亮度来判断该用白字还是黑字。
    private func backgroundIsDark() -> Bool {
        if let forced = ProcessInfo.processInfo.environment["F50_APPEARANCE"] {
            return forced == "dark"
        }
        if Date().timeIntervalSince(lastBgProbe) < 30 {
            return cachedBgIsDark
        }
        lastBgProbe = Date()

        if let dark = probeWallpaperDark() {
            cachedBgIsDark = dark
        } else {
            cachedBgIsDark = NSApp.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        }
        return cachedBgIsDark
    }

    /// 壁纸顶部一条（菜单栏所在高度）的平均亮度，低于阈值视为深色
    private func probeWallpaperDark() -> Bool? {
        guard let screen = NSScreen.main,
              let url = NSWorkspace.shared.desktopImageURL(for: screen),
              let image = NSImage(contentsOf: url) else { return nil }

        let w = 48, h = 48
        guard let rep = NSBitmapImageRep(
            bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h,
            bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
            isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0
        ), let ctx = NSGraphicsContext(bitmapImageRep: rep) else { return nil }

        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = ctx
        image.draw(in: NSRect(x: 0, y: 0, width: w, height: h))
        NSGraphicsContext.restoreGraphicsState()

        // 位图原点在左下角，菜单栏对应最上面几行
        var sum = 0.0
        var count = 0
        for y in (h - 7)..<h {
            for x in 0..<w {
                guard let c = rep.colorAt(x: x, y: y) else { continue }
                sum += 0.2126 * c.redComponent + 0.7152 * c.greenComponent + 0.0722 * c.blueComponent
                count += 1
            }
        }
        guard count > 0 else { return nil }
        return (sum / Double(count)) < 0.45
    }

    private func palette() -> (primary: NSColor, dim: NSColor, down: NSColor, up: NSColor) {
        if backgroundIsDark() {
            return (
                NSColor(srgbRed: 1, green: 1, blue: 1, alpha: 1),
                NSColor(srgbRed: 1, green: 1, blue: 1, alpha: 0.6),
                NSColor(srgbRed: 0.42, green: 0.74, blue: 1.00, alpha: 1),
                NSColor(srgbRed: 1.00, green: 0.78, blue: 0.38, alpha: 1)
            )
        }
        return (
            NSColor(srgbRed: 0, green: 0, blue: 0, alpha: 1),
            NSColor(srgbRed: 0, green: 0, blue: 0, alpha: 0.45),
            NSColor(srgbRed: 0.04, green: 0.36, blue: 0.82, alpha: 1),
            NSColor(srgbRed: 0.70, green: 0.30, blue: 0.00, alpha: 1)
        )
    }

    private func render() -> NSImage {
        let engine = MonitorEngine.shared
        let colors = palette()
        let rowFont = NSFont.monospacedSystemFont(ofSize: 9.0, weight: .semibold)
        let arrowFont = NSFont.monospacedSystemFont(ofSize: 9.0, weight: .bold)
        // 左侧网络类型：字号拉到与右侧两行总高度一致
        let bigFont = NSFont.monospacedSystemFont(ofSize: 16.0, weight: .bold)

        let offline = engine.errorText != nil
        let rawType = offline ? "离线" : (engine.snapshot?.networkType ?? "")
        let typeText = rawType.isEmpty ? "…" : rawType
        let showRate = UserDefaults.standard.object(forKey: "showRateInMenuBar") as? Bool ?? true

        func seg(_ text: String, _ color: NSColor, _ f: NSFont) -> NSAttributedString {
            NSAttributedString(string: text, attributes: [.font: f, .foregroundColor: color])
        }

        let big = seg(typeText, offline ? colors.dim : colors.primary, bigFont)
        let bigSize = big.size()

        let row1 = NSMutableAttributedString()
        let row2 = NSMutableAttributedString()
        var rowWidth: CGFloat = 0
        var indent: CGFloat = 0
        if showRate && !offline {
            row1.append(seg("↓", colors.down, arrowFont))
            row1.append(seg(Fmt.compactRate(engine.downBps), colors.down, rowFont))
            row2.append(seg("↑", colors.up, arrowFont))
            row2.append(seg(Fmt.compactRate(engine.upBps), colors.up, rowFont))
            rowWidth = max(row1.size().width, row2.size().width)
            // 让两行的速率数字对齐：第二行按箭头宽度微调
            indent = seg("↓", colors.down, arrowFont).size().width
                - seg("↑", colors.up, arrowFont).size().width
        }

        let leftPad: CGFloat = 2
        let gap: CGFloat = 6
        let rightPad: CGFloat = 3
        let width = ceil(leftPad + bigSize.width + (rowWidth > 0 ? gap + rowWidth : 0) + rightPad)
        let height: CGFloat = 22

        // 用位图上下文直接画，不依赖 lockFocus / 屏幕上下文
        guard let bitmap = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: Int(width * 2),
            pixelsHigh: Int(height * 2),
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else {
            return NSImage(size: NSSize(width: width, height: height))
        }
        // 先设定尺寸，再建上下文，否则绘制坐标会按像素而非点计算
        bitmap.size = NSSize(width: width, height: height)
        guard let ctx = NSGraphicsContext(bitmapImageRep: bitmap) else {
            return NSImage(size: NSSize(width: width, height: height))
        }

        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = ctx
        ctx.cgContext.clear(CGRect(x: 0, y: 0, width: width * 2, height: height * 2))

        // 文字先画一圈对比色描边，再画本体：壁纸杂色时也能看清
        let halo = backgroundIsDark()
            ? NSColor(srgbRed: 0, green: 0, blue: 0, alpha: 0.45)
            : NSColor(srgbRed: 1, green: 1, blue: 1, alpha: 0.38)

        func stroked(_ text: NSAttributedString, in rect: NSRect) {
            let outlined = NSMutableAttributedString(attributedString: text)
            outlined.addAttribute(.foregroundColor, value: halo,
                                  range: NSRange(location: 0, length: outlined.length))
            for offset in [NSPoint(x: 0, y: -1), NSPoint(x: 0, y: 1),
                           NSPoint(x: -1, y: 0), NSPoint(x: 1, y: 0)] {
                outlined.draw(in: rect.offsetBy(dx: offset.x, dy: offset.y))
            }
            text.draw(in: rect)
        }

        // 左侧大字，垂直居中
        let bigY = (height - bigSize.height) / 2
        stroked(big, in: NSRect(x: leftPad, y: bigY, width: bigSize.width + 1, height: bigSize.height))

        // 右侧两行
        let textX = leftPad + bigSize.width + gap
        if rowWidth > 0 {
            stroked(row1, in: NSRect(x: textX, y: 10.5, width: rowWidth + 2, height: 11))
            stroked(row2, in: NSRect(x: textX + indent, y: 0.5, width: rowWidth + 2, height: 11))
        }

        NSGraphicsContext.restoreGraphicsState()

        let image = NSImage(size: NSSize(width: width, height: height))
        image.addRepresentation(bitmap)
        return image
    }

    private func writePNG(_ image: NSImage, to path: String) {
        var target = image
        if let bg = ProcessInfo.processInfo.environment["F50_DUMP_BG"] {
            let color = bg == "dark"
                ? NSColor(srgbRed: 0.13, green: 0.13, blue: 0.14, alpha: 1)
                : NSColor(srgbRed: 0.94, green: 0.94, blue: 0.94, alpha: 1)
            let composed = NSImage(size: image.size)
            composed.lockFocus()
            color.setFill()
            NSRect(origin: .zero, size: image.size).fill()
            image.draw(at: .zero, from: NSRect(origin: .zero, size: image.size), operation: .sourceOver, fraction: 1)
            composed.unlockFocus()
            target = composed
        }
        guard let tiff = target.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let data = rep.representation(using: .png, properties: [:]) else { return }
        try? data.write(to: URL(fileURLWithPath: path))
    }
}
