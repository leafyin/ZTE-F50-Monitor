package com.leafyin.f50monitor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.leafyin.f50monitor.domain.QuotaRepository
import com.leafyin.f50monitor.service.MonitorService
import com.leafyin.f50monitor.service.MonitorState
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响前台服务，只是通知不可见 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 深浅色跟随系统：状态栏/导航栏图标颜色必须和我们的背景一致，
        // 否则系统浅色 + 深色面板时会把状态栏图标画成深色，看起来像「状态栏不见了」。
        val nightMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (nightMode) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (nightMode) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
        )
        PanelColors.use(if (nightMode) DarkPalette else LightPalette)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        MonitorService.start(this)

        val repo = QuotaRepository(this)

        setContent {
            val state by MonitorState.state.collectAsState()
            var interval by remember { mutableIntStateOf(repo.intervalSec()) }
            var gateway by remember { mutableStateOf(repo.gatewayOverride()) }
            var unitBin by remember { mutableStateOf(repo.unitBytes() == QuotaRepository.BIN) }
            var showCalibration by remember { mutableStateOf(false) }

            val dark = isSystemInDarkTheme()
            val palette = if (dark) DarkPalette else LightPalette
            SideEffect { PanelColors.use(palette) }

            MaterialTheme(
                colorScheme = if (dark) {
                    darkColorScheme(
                        primary = palette.blue,
                        background = palette.bg,
                        surface = palette.card,
                        onSurface = palette.text,
                        onBackground = palette.text,
                    )
                } else {
                    lightColorScheme(
                        primary = palette.blue,
                        background = palette.bg,
                        surface = palette.card,
                        onSurface = palette.text,
                        onBackground = palette.text,
                    )
                }
            ) {
                Box(Modifier.fillMaxSize().background(PanelColors.Bg)) {
                    PanelScreen(
                        state = state,
                        intervalSec = interval,
                        unitBin = unitBin,
                        gatewayOverride = gateway,
                        onCalibrate = { showCalibration = true },
                        onOpenAdmin = { openAdminPage(state.gateway) },
                        onToggleRun = {
                            if (state.running) MonitorService.stop(this@MainActivity) else MonitorService.start(this@MainActivity)
                        },
                        onInterval = {
                            interval = it
                            repo.setIntervalSec(it)
                        },
                        onUnit = {
                            unitBin = it
                            repo.setUnitBin(it)
                        },
                        onGateway = {
                            gateway = it
                            repo.setGatewayOverride(it)
                        },
                    )

                    if (showCalibration) {
                        CalibrationDialog(
                            totalDefault = state.totalGB,
                            remainDefault = state.remainingGB,
                            onDismiss = { showCalibration = false },
                            onConfirm = { total, remain ->
                                val device = state.snapshot?.totalBytes ?: 0L
                                repo.calibrate(total, remain, device)
                                showCalibration = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun openAdminPage(gateway: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$gateway/index.html")))
        }
    }
}
