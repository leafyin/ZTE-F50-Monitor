package com.leafyin.f50monitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 校准：按运营商 App 显示的「本月剩余」重填。
 * 确认后以当前设备计数器为新基准，并清空冻结期的估算账。
 */
@Composable
fun CalibrationDialog(
    totalDefault: Double,
    remainDefault: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    var total by remember { mutableStateOf(String.format(Locale.US, "%.2f", totalDefault)) }
    var remain by remember { mutableStateOf(String.format(Locale.US, "%.2f", remainDefault)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColors.Card,
        titleContentColor = PanelColors.Text,
        textContentColor = PanelColors.Dim,
        title = { Text("校准套餐", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "按运营商 App 显示的剩余重填。确认后从当前时刻重新起算，剩余按固件积分逐秒扣减。",
                    color = PanelColors.Dim,
                    fontSize = 11.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it },
                    label = { Text("套餐总量 GB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remain,
                    onValueChange = { remain = it },
                    label = { Text("当前剩余 GB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = total.toDoubleOrNull() ?: 0.0
                val r = remain.toDoubleOrNull() ?: 0.0
                if (t > 0) onConfirm(t, r.coerceIn(0.0, t))
            }) { Text("确认校准", color = PanelColors.Blue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF8B97A7)) }
        },
    )
}
