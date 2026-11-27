package com.leafyin.f50monitor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 一套配色。深/浅两套只换数值，UI 代码不分支。 */
data class Palette(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val border: Color,
    val borderStrong: Color,
    val grid: Color,
    val text: Color,
    val dim: Color,
    val dim2: Color,
    val green: Color,
    val orange: Color,
    val blue: Color,
    val red: Color,
    val chipBg: Color,
    val chipSelBg: Color,
    val chipText: Color,
    val btn: Color,
    val onBtn: Color,
)

/** 暗色：比原来的纯黑提亮两档，卡片和背景拉开层次，长时间看不发闷。 */
val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF12161C),
    card = Color(0xFF1B222B),
    border = Color(0x1FFFFFFF),
    borderStrong = Color(0x3DFFFFFF),
    grid = Color(0x14FFFFFF),
    text = Color(0xFFE9EEF5),
    dim = Color(0xFF9AA7B8),
    dim2 = Color(0xFF74808F),
    green = Color(0xFF3ED598),
    orange = Color(0xFFF5A45C),
    blue = Color(0xFF6FAEFF),
    red = Color(0xFFFF7B7B),
    chipBg = Color(0x12FFFFFF),
    chipSelBg = Color(0x335AA0FF),
    chipText = Color(0xFFD9E8FF),
    btn = Color(0xFF2E7CF6),
    onBtn = Color(0xFFFFFFFF),
)

/** 浅色：白卡片浮在浅灰底上，绿色/橙色压深保证对比度。 */
val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFF3F5F9),
    card = Color(0xFFFFFFFF),
    border = Color(0x14101828),
    borderStrong = Color(0x2E101828),
    grid = Color(0x12000000),
    text = Color(0xFF12171E),
    dim = Color(0xFF5C6878),
    dim2 = Color(0xFF8B95A4),
    green = Color(0xFF0E9A62),
    orange = Color(0xFFC2740F),
    blue = Color(0xFF2A6BE0),
    red = Color(0xFFD04343),
    chipBg = Color(0x0F101828),
    chipSelBg = Color(0x1F2A6BE0),
    chipText = Color(0xFF1B49C4),
    btn = Color(0xFF1D6FE8),
    onBtn = Color(0xFFFFFFFF),
)

/**
 * 全局当前配色。用 mutableStateOf 包一层，切深浅色时 Compose 会自动重组，
 * Canvas 里直接读也能拿到新值（老代码里 40 多个 PanelColors.xxx 不用改）。
 */
object PanelColors {
    private var current by mutableStateOf(DarkPalette)

    fun use(p: Palette) {
        current = p
    }

    val isDark: Boolean get() = current.isDark
    val Bg: Color get() = current.bg
    val Card: Color get() = current.card
    val Border: Color get() = current.border
    val BorderStrong: Color get() = current.borderStrong
    val Grid: Color get() = current.grid
    val Text: Color get() = current.text
    val Dim: Color get() = current.dim
    val Dim2: Color get() = current.dim2
    val Green: Color get() = current.green
    val Orange: Color get() = current.orange
    val Blue: Color get() = current.blue
    val Red: Color get() = current.red
    val ChipBg: Color get() = current.chipBg
    val ChipSelBg: Color get() = current.chipSelBg
    val ChipText: Color get() = current.chipText
    val Btn: Color get() = current.btn
    val OnBtn: Color get() = current.onBtn
}
