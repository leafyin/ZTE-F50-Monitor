package com.leafyin.f50monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leafyin.f50monitor.domain.Fmt
import com.leafyin.f50monitor.service.MonitorState
import java.util.Locale

private val numStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PanelScreen(
    state: MonitorState.PanelState,
    intervalSec: Int,
    unitBin: Boolean,
    gatewayOverride: String,
    onCalibrate: () -> Unit,
    onOpenAdmin: () -> Unit,
    onToggleRun: () -> Unit,
    onInterval: (Int) -> Unit,
    onUnit: (Boolean) -> Unit,
    onGateway: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PanelColors.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        TopBar(onCalibrate)
        Spacer(Modifier.height(6.dp))

        StatusCard(state)
        Spacer(Modifier.height(10.dp))
        RateCard(state)
        Spacer(Modifier.height(10.dp))
        QuotaCard(state, onCalibrate)
        Spacer(Modifier.height(10.dp))
        DevicesCard(state)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onOpenAdmin,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PanelColors.BorderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PanelColors.Text),
            ) { Text("打开管理页", fontSize = 13.sp) }

            Button(
                onClick = onToggleRun,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanelColors.Btn, contentColor = PanelColors.OnBtn),
            ) { Text(if (state.running) "暂停监控" else "开始监控", fontSize = 13.sp, color = Color.White) }
        }

        Spacer(Modifier.height(14.dp))
        SettingsRow(intervalSec, unitBin, gatewayOverride, onInterval, onUnit, onGateway)
        Spacer(Modifier.height(10.dp))

        if (state.error != null) {
            Text(
                "⚠ ${state.error}",
                color = PanelColors.Orange,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Text(
            buildString {
                append("网关 ${state.gateway}")
                if (state.responseMs > 0) append(" · 接口 ${state.responseMs} ms")
                if (state.lastUpdate > 0) append(" · 更新 ${Fmt.time(state.lastUpdate)}")
                append(" · 采样 ${intervalSec}s")
            },
            color = PanelColors.Dim2,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun TopBar(onCalibrate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("F50 监控", color = PanelColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onCalibrate) {
            Text("校准套餐", color = PanelColors.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelColors.Card)
            .border(1.dp, PanelColors.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun StatusCard(state: MonitorState.PanelState) {
    PanelCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.carrier, color = PanelColors.Text, fontSize = 13.sp)
                Spacer(Modifier.width(9.dp))
                Badge(state.netLabel)
            }
            SignalBars(state.signalBars)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Meta("RSRP", state.snapshot?.rsrp?.let { "$it dBm" } ?: "—")
            Meta("接口响应", if (state.responseMs > 0) "${state.responseMs} ms" else "—")
            Meta("在线设备", "${state.deviceCount} 台")
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PanelColors.Green.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, color = PanelColors.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignalBars(bars: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((5 + i * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < bars) PanelColors.Green else PanelColors.BorderStrong),
            )
        }
    }
}

@Composable
private fun Meta(key: String, value: String) {
    Column {
        Text(key, color = PanelColors.Dim2, fontSize = 10.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = PanelColors.Text, fontSize = 12.5.sp, style = numStyle)
    }
}

@Composable
private fun RateCard(state: MonitorState.PanelState) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("实时速率", color = PanelColors.Dim, fontSize = 12.sp)
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PanelColors.ChipBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(state.rateSource, color = PanelColors.Dim2, fontSize = 10.5.sp)
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.Bottom) {
            RateBlock("↓", state.downBps, PanelColors.Green)
            RateBlock("↑", state.upBps, PanelColors.Orange)
        }

        Spacer(Modifier.height(8.dp))
        Sparkline(state.samplesDown, state.samplesUp, Modifier.fillMaxWidth().height(76.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("近 60 次采样", color = PanelColors.Dim2, fontSize = 10.5.sp)
            val peak = maxOf(
                1_000.0,
                state.samplesDown.maxOrNull() ?: 0.0,
                state.samplesUp.maxOrNull() ?: 0.0,
            )
            Text("峰值 ${Fmt.bytes(peak.toLong())}/s", color = PanelColors.Dim2, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun RateBlock(arrow: String, bps: Double, color: Color) {
    val (num, unit) = Fmt.rateFull(bps)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(arrow, color = color, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Text(num, color = color, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, style = numStyle)
        Spacer(Modifier.width(3.dp))
        Text(unit, color = color.copy(alpha = 0.72f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun Sparkline(down: List<Double>, up: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val gridColor = PanelColors.Grid
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        val n = down.size
        if (n < 2) return@Canvas
        val rawMax = maxOf(down.maxOrNull() ?: 0.0, up.maxOrNull() ?: 0.0)
        val maxV = maxOf(1_000.0, rawMax * 1.2)
        val bottom = h - 8.dp.toPx()
        fun x(i: Int) = w * i / (n - 1).toFloat()
        fun y(v: Double) = bottom - (v / maxV).toFloat() * (h - 16.dp.toPx())

        // 下行填充
        val area = Path()
        area.moveTo(x(0), y(down[0]))
        for (i in 1 until n) area.lineTo(x(i), y(down[i]))
        area.lineTo(w, h)
        area.lineTo(0f, h)
        area.close()
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(PanelColors.Green.copy(alpha = 0.18f), Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )

        for (i in 1 until n) {
            drawLine(PanelColors.Green, Offset(x(i - 1), y(down[i - 1])), Offset(x(i), y(down[i])), 1.6.dp.toPx())
        }
        if (up.size >= 2) {
            for (i in 1 until up.size) {
                drawLine(
                    PanelColors.Orange.copy(alpha = 0.85f),
                    Offset(x(i - 1), y(up[i - 1])),
                    Offset(x(i), y(up[i])),
                    1.2.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun QuotaCard(state: MonitorState.PanelState, onCalibrate: () -> Unit) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本月流量", color = PanelColors.Dim, fontSize = 12.sp)
            Text(
                "校准基准",
                color = PanelColors.Blue,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onCalibrate() },
            )
        }
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(Locale.US, "%.2f", state.remainingGB),
                color = PanelColors.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, style = numStyle,
            )
            Spacer(Modifier.width(5.dp))
            Text("GB 剩余", color = PanelColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        Spacer(Modifier.height(9.dp))
        val fraction = if (state.totalGB > 0) (state.usedGB / state.totalGB).toFloat() else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PanelColors.ChipBg),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(PanelColors.Btn, PanelColors.Green))),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("共 ${state.totalGB.toInt()} GB", color = PanelColors.Dim, fontSize = 11.5.sp)
            Text(
                "已用 ${String.format(Locale.US, "%.2f", state.usedGB)} GB",
                color = PanelColors.Dim, fontSize = 11.5.sp,
            )
        }

        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelColors.Border))
        Spacer(Modifier.height(11.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日用量 ${Fmt.bytes(state.todayBytes)}", color = PanelColors.Dim, fontSize = 11.5.sp)
            Text(
                if (state.calibrated && state.baseTimeMs > 0) "基准 ${Fmt.time(state.baseTimeMs)}" else "未校准",
                color = PanelColors.Dim2, fontSize = 11.5.sp,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (state.extraBytes > 0) "固件积分账 ${Fmt.bytes(state.extraBytes)} · 路由器计数 ${Fmt.bytes(state.counterBytes)}"
            else "固件积分账 0 B · 路由器计数 ${Fmt.bytes(state.counterBytes)}",
            color = if (state.extraBytes > 0) PanelColors.Orange else PanelColors.Dim2,
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun DevicesCard(state: MonitorState.PanelState) {
    val stations = state.snapshot?.stations ?: emptyList()
    PanelCard {
        Text("在线设备", color = PanelColors.Dim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (stations.isEmpty()) {
            Text("暂无设备", color = PanelColors.Dim2, fontSize = 12.sp)
        } else {
            stations.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PanelColors.ChipBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            st.displayName.take(1).uppercase(),
                            color = PanelColors.Dim, fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(st.displayName, color = PanelColors.Text, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text(st.ip, color = PanelColors.Dim2, fontSize = 11.sp, style = numStyle)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelColors.Border).padding(vertical = 0.dp))
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本机 IP", color = PanelColors.Dim2, fontSize = 11.5.sp)
            Text(state.localIp ?: "—", color = PanelColors.Dim2, fontSize = 11.5.sp, style = numStyle)
        }
    }
}

@Composable
private fun SettingsRow(
    intervalSec: Int,
    unitBin: Boolean,
    gatewayOverride: String,
    onInterval: (Int) -> Unit,
    onUnit: (Boolean) -> Unit,
    onGateway: (String) -> Unit,
) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("采样间隔", color = PanelColors.Dim, fontSize = 11.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 5, 10).forEach { v ->
                    SegButton("${v}s", intervalSec == v) { onInterval(v) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("GB 口径", color = PanelColors.Dim, fontSize = 11.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegButton("1e9", !unitBin) { onUnit(false) }
                SegButton("1024³", unitBin) { onUnit(true) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("网关", color = PanelColors.Dim, fontSize = 11.5.sp)
                Text("留空则自动取当前 Wi-Fi 网关", color = PanelColors.Dim2, fontSize = 10.sp)
            }
            OutlinedTextField(
                value = gatewayOverride,
                onValueChange = onGateway,
                singleLine = true,
                placeholder = { Text("自动", color = PanelColors.Dim2, fontSize = 11.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = PanelColors.Text),
                modifier = Modifier.width(140.dp),
            )
        }
    }
}

@Composable
private fun SegButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PanelColors.ChipSelBg else PanelColors.ChipBg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = if (selected) PanelColors.ChipText else PanelColors.Dim,
            fontSize = 11.sp,
        )
    }
}
