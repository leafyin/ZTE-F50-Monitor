package com.leafyin.f50monitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.leafyin.f50monitor.R
import com.leafyin.f50monitor.domain.Fmt
import com.leafyin.f50monitor.service.MonitorState
import com.leafyin.f50monitor.ui.MainActivity
import java.util.Locale

/**
 * 桌面小组件只负责显示，刷新由前台服务主动 push——
 * AppWidgetProvider 自己的 updatePeriodMillis 最小 30 分钟，做不了实时。
 */
class RateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    companion object {

        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RateWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context, MonitorState.state.value))
        }

        fun buildViews(context: Context, s: MonitorState.PanelState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_rate)

            if (!s.running) {
                views.setTextViewText(R.id.widgetNet, "—")
                views.setTextViewText(R.id.widgetCarrier, "已暂停 · 点击恢复")
                views.setTextViewText(R.id.widgetDown, "↓ 0B")
                views.setTextViewText(R.id.widgetUp, "↑ 0B")
                views.setTextViewText(R.id.widgetFoot, "打开 F50 监控继续采集")
            } else if (s.snapshot == null) {
                views.setTextViewText(R.id.widgetNet, "5G")
                views.setTextViewText(R.id.widgetCarrier, s.error ?: "等待 F50 数据")
                views.setTextViewText(R.id.widgetDown, "↓ —")
                views.setTextViewText(R.id.widgetUp, "↑ —")
                views.setTextViewText(R.id.widgetFoot, "网关 ${s.gateway}")
            } else {
                views.setTextViewText(R.id.widgetNet, s.netLabel)
                views.setTextViewText(R.id.widgetCarrier, "F50 · ${s.carrier}")
                views.setTextViewText(R.id.widgetDown, "↓ ${Fmt.rateShort(s.downBps)}")
                views.setTextViewText(R.id.widgetUp, "↑ ${Fmt.rateShort(s.upBps)}")
                views.setTextViewText(
                    R.id.widgetFoot,
                    "剩余 ${String.format(Locale.US, "%.2f", s.remainingGB)} GB · 今日 ${Fmt.bytes(s.todayBytes)}"
                )
            }

            views.setOnClickPendingIntent(R.id.widgetRoot, openApp(context))
            return views
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
