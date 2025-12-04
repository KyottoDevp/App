package com.hannsapp.fpscounter.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.services.FpsMonitorService
import com.hannsapp.fpscounter.services.FpsOverlayService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefsManager = HannsApplication.getInstance().preferencesManager
            
            if (prefsManager.autoStart) {
                startFpsMonitoring(context)
            }
        }
    }

    private fun startFpsMonitoring(context: Context) {
        val monitorIntent = Intent(context, FpsMonitorService::class.java)
        val overlayIntent = Intent(context, FpsOverlayService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(monitorIntent)
            context.startForegroundService(overlayIntent)
        } else {
            context.startService(monitorIntent)
            context.startService(overlayIntent)
        }
    }
}
