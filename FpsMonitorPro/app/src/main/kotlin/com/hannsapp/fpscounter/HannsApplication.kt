package com.hannsapp.fpscounter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.utils.Constants

class HannsApplication : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fpsChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_FPS,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val connectionChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Connection status notifications"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(fpsChannel)
            notificationManager.createNotificationChannel(connectionChannel)
        }
    }

    companion object {
        @Volatile
        private var instance: HannsApplication? = null

        fun getInstance(): HannsApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }

        fun getAppContext(): Context {
            return getInstance().applicationContext
        }
    }
}
