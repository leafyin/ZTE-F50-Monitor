package com.leafyin.f50monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：用户装完就不必每次手动点开。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { MonitorService.start(context) }
        }
    }
}
