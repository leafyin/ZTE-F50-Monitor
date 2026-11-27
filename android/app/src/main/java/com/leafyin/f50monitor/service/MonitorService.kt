package com.leafyin.f50monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leafyin.f50monitor.R
import com.leafyin.f50monitor.data.NetworkUtil
import com.leafyin.f50monitor.data.RouterApi
import com.leafyin.f50monitor.domain.Fmt
import com.leafyin.f50monitor.domain.QuotaRepository
import com.leafyin.f50monitor.domain.UsageCalculator
import com.leafyin.f50monitor.ui.MainActivity
import com.leafyin.f50monitor.widget.RateWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 常驻采集。用 connectedDevice 类型的前台服务：
 * 语义上是「连着一台外部设备」，也不会像 dataSync 那样在 Android 15 上被限 6 小时。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var repo: QuotaRepository
    private val calc = UsageCalculator()
    private var lastSampleAt = 0L
    private var migratedNotice = false

    override fun onCreate() {
        super.onCreate()
        repo = QuotaRepository(this)
        createChannel()
        startAsForeground(buildNotification(MonitorState.state.value))
        MonitorState.set { it.copy(running = true) }
        job = startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        MonitorState.set { it.copy(running = false) }
        RateWidgetProvider.push(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 采集循环 ----------------

    private fun startLoop(): Job = scope.launch {
        while (isActive) {
            val interval = repo.intervalSec()
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val ep = NetworkUtil.resolve(this@MonitorService, repo.gatewayOverride())
                NetworkUtil.bind(cm, ep.network)

                val res = RouterApi.fetch(ep.gateway)
                val dt = if (lastSampleAt == 0L) interval.toDouble()
                else ((startedAt - lastSampleAt) / 1000.0).coerceAtLeast(0.2)
                lastSampleAt = startedAt

                onSample(res, ep.gateway, ep.localIp, dt)
            } catch (e: Exception) {
                onError(describe(e))
            }
            delay(interval * 1000L)
        }
    }

    private fun onSample(res: RouterApi.Result, gateway: String, localIp: String?, dt: Double) {
        val snap = res.snapshot
        val out = calc.step(snap, dt)

        val deviceBytes = snap.totalBytes
        val migrated = repo.migrateBaseIfNeeded(deviceBytes)
        if (migrated && !migratedNotice) {
            migratedNotice = true
        }
        if (out.firmwareBytes > 0) repo.addExtra(out.firmwareBytes)
        if (out.counterDelta > 0) repo.addCounter(out.counterDelta)
        if (out.usageBytes > 0) repo.addDaily(out.usageBytes)

        val cfg = repo.config()
        val remaining = repo.remainingGB(deviceBytes)
        val used = (cfg.totalGB - remaining).coerceAtLeast(0.0)

        MonitorState.set { s ->
            s.copy(
                snapshot = snap,
                downBps = out.downBps,
                upBps = out.upBps,
                rateSource = out.rateSource.label,
                usageSource = out.usageSource.label,
                counterDelta = out.counterDelta,
                usageBytes = out.usageBytes,
                extraBytes = cfg.extraBytes,
                counterBytes = cfg.counterBytes,
                remainingGB = remaining,
                usedGB = used,
                totalGB = cfg.totalGB,
                todayBytes = repo.todayBytes(),
                calibrated = cfg.calibrated,
                baseTimeMs = cfg.calibratedAt,
                responseMs = res.elapsedMs,
                error = null,
                lastUpdate = snap.timestamp,
                gateway = gateway,
                localIp = localIp,
                samplesDown = (s.samplesDown + out.downBps).takeLast(60),
                samplesUp = (s.samplesUp + out.upBps).takeLast(60),
                running = true,
            )
        }

        val state = MonitorState.state.value
        notify(buildNotification(state))
        RateWidgetProvider.push(this)
    }

    private fun onError(message: String) {
        MonitorState.set { it.copy(error = message, running = true) }
        val state = MonitorState.state.value
        notify(buildNotification(state))
        RateWidgetProvider.push(this)
    }

    private fun describe(e: Exception): String = when (e) {
        is RouterApi.ApiException -> when (e.kind) {
            RouterApi.Kind.TIMEOUT -> "连接超时（F50 是否在线？）"
            RouterApi.Kind.NO_ROUTE -> "连不上 ${MonitorState.state.value.gateway}"
            RouterApi.Kind.BAD_STATUS -> e.message ?: "设备返回异常"
            RouterApi.Kind.BAD_PAYLOAD -> "设备返回的数据无法解析"
            RouterApi.Kind.UNKNOWN -> e.message ?: "读取失败"
        }
        else -> e.message ?: "读取失败"
    }

    // ---------------- 通知 ----------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "F50 实时状态", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "常驻显示 F50 的速率与剩余流量"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(state: MonitorState.PanelState): Notification {
        val snap = state.snapshot
        val title = if (snap == null || !snap.connected) "未连接 F50" else
            "${state.netLabel} · ${state.carrier} · 信号 ${state.signalBars}/5"

        val text = if (state.error != null) {
            state.error!!
        } else {
            "↓ ${Fmt.rateFull(state.downBps).first}${Fmt.rateFull(state.downBps).second}" +
                "  ↑ ${Fmt.rateFull(state.upBps).first}${Fmt.rateFull(state.upBps).second}" +
                " · 剩余 ${String.format(java.util.Locale.US, "%.2f", state.remainingGB)} GB" +
                " · 今日 ${Fmt.bytes(state.todayBytes)}"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .addAction(0, "暂停监控", pauseIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun notify(notification: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "f50.monitor"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.leafyin.f50monitor.START"
        const val ACTION_PAUSE = "com.leafyin.f50monitor.PAUSE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
