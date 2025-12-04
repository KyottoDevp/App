package com.hannsapp.fpscounter.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import androidx.core.app.NotificationCompat
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.FpsData
import com.hannsapp.fpscounter.data.FpsStats
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.data.SystemInfo
import com.hannsapp.fpscounter.utils.Constants

class FpsMonitorService : Service(), Choreographer.FrameCallback {

    private val binder = FpsMonitorBinder()
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var systemInfoCollector: SystemInfoCollector
    private lateinit var handler: Handler
    private lateinit var choreographer: Choreographer

    private var isMonitoring = false
    private var lastFrameTimeNanos: Long = 0
    private var frameCount = 0
    private var lastFpsUpdateTime: Long = 0

    private val fpsHistory = mutableListOf<Int>()
    private val frameTimeHistory = mutableListOf<Float>()
    private var currentFps = 0
    private var minFps = Int.MAX_VALUE
    private var maxFps = 0
    private var totalFps = 0L
    private var fpsReadings = 0L

    private val listeners = mutableSetOf<FpsDataListener>()

    private var currentSystemInfo: SystemInfo = SystemInfo()

    interface FpsDataListener {
        fun onFpsUpdate(fps: Int, stats: FpsStats, systemInfo: SystemInfo)
    }

    inner class FpsMonitorBinder : Binder() {
        fun getService(): FpsMonitorService = this@FpsMonitorService
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = HannsApplication.getInstance().preferencesManager
        systemInfoCollector = SystemInfoCollector(this)
        handler = Handler(Looper.getMainLooper())
        choreographer = Choreographer.getInstance()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    fun startMonitoring() {
        if (isMonitoring) return

        startForeground(Constants.NOTIFICATION_ID_FPS, createNotification())
        isMonitoring = true
        lastFrameTimeNanos = 0
        frameCount = 0
        lastFpsUpdateTime = System.currentTimeMillis()
        resetStats()
        choreographer.postFrameCallback(this)
        startSystemInfoCollection()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        choreographer.removeFrameCallback(this)
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isMonitoring) return

        if (lastFrameTimeNanos != 0L) {
            val frameDeltaNanos = frameTimeNanos - lastFrameTimeNanos
            val frameTimeMs = frameDeltaNanos / 1_000_000f
            frameCount++

            if (frameTimeHistory.size >= Constants.FPS_SAMPLE_SIZE) {
                frameTimeHistory.removeAt(0)
            }
            frameTimeHistory.add(frameTimeMs)

            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastFpsUpdateTime

            if (elapsedTime >= preferencesManager.updateInterval) {
                currentFps = ((frameCount * 1000L) / elapsedTime).toInt()
                frameCount = 0
                lastFpsUpdateTime = currentTime

                updateStats(currentFps)
                notifyListeners()
                updateNotification()
            }
        }

        lastFrameTimeNanos = frameTimeNanos
        choreographer.postFrameCallback(this)
    }

    private fun updateStats(fps: Int) {
        if (fpsHistory.size >= Constants.FPS_HISTORY_SIZE) {
            fpsHistory.removeAt(0)
        }
        fpsHistory.add(fps)

        if (fps > 0) {
            if (fps < minFps) minFps = fps
            if (fps > maxFps) maxFps = fps
            totalFps += fps
            fpsReadings++
        }
    }

    private fun resetStats() {
        fpsHistory.clear()
        frameTimeHistory.clear()
        currentFps = 0
        minFps = Int.MAX_VALUE
        maxFps = 0
        totalFps = 0L
        fpsReadings = 0L
    }

    private fun startSystemInfoCollection() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                currentSystemInfo = systemInfoCollector.collect()
                handler.postDelayed(this, preferencesManager.updateInterval)
            }
        }
        handler.post(updateRunnable)
    }

    private fun notifyListeners() {
        val stats = getCurrentStats()
        listeners.forEach { listener ->
            try {
                listener.onFpsUpdate(currentFps, stats, currentSystemInfo)
            } catch (e: Exception) {
                // Listener threw exception, continue with others
            }
        }

        val intent = Intent(ACTION_FPS_UPDATE).apply {
            putExtra(EXTRA_FPS, currentFps)
            putExtra(EXTRA_AVG_FPS, stats.averageFps)
            putExtra(EXTRA_MIN_FPS, stats.minFps)
            putExtra(EXTRA_MAX_FPS, stats.maxFps)
            putExtra(EXTRA_CPU_USAGE, currentSystemInfo.cpuUsage)
            putExtra(EXTRA_MEMORY_USED, currentSystemInfo.memoryUsedMb)
            putExtra(EXTRA_MEMORY_TOTAL, currentSystemInfo.memoryTotalMb)
            putExtra(EXTRA_BATTERY_LEVEL, currentSystemInfo.batteryLevel)
            putExtra(EXTRA_BATTERY_TEMP, currentSystemInfo.batteryTemp)
            putExtra(EXTRA_CPU_TEMP, currentSystemInfo.cpuTemp)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    fun addListener(listener: FpsDataListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: FpsDataListener) {
        listeners.remove(listener)
    }

    fun getCurrentFps(): Int = currentFps

    fun getCurrentStats(): FpsStats {
        val avgFps = if (fpsReadings > 0) totalFps.toFloat() / fpsReadings else 0f
        val effectiveMinFps = if (minFps == Int.MAX_VALUE) 0 else minFps

        return FpsStats(
            currentFps = currentFps,
            averageFps = avgFps,
            minFps = effectiveMinFps,
            maxFps = maxFps,
            fpsHistory = fpsHistory.toList(),
            frameTimeHistory = frameTimeHistory.toList()
        )
    }

    fun getCurrentSystemInfo(): SystemInfo = currentSystemInfo

    fun getFpsData(): FpsData {
        val avgFrameTime = if (frameTimeHistory.isNotEmpty()) {
            frameTimeHistory.average().toFloat()
        } else {
            0f
        }
        return FpsData(
            timestamp = System.currentTimeMillis(),
            fps = currentFps,
            frameTime = avgFrameTime
        )
    }

    fun isMonitoring(): Boolean = isMonitoring

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopIntent = Intent(this, FpsMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_FPS)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_fps, currentFps))
            .setSmallIcon(R.drawable.ic_fps)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_fps, getString(R.string.stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID_FPS, notification)
    }

    companion object {
        private const val TAG = "FpsMonitorService"

        const val ACTION_START = "com.hannsapp.fpscounter.action.START_MONITORING"
        const val ACTION_STOP = "com.hannsapp.fpscounter.action.STOP_MONITORING"
        const val ACTION_FPS_UPDATE = "com.hannsapp.fpscounter.action.FPS_UPDATE"

        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_AVG_FPS = "extra_avg_fps"
        const val EXTRA_MIN_FPS = "extra_min_fps"
        const val EXTRA_MAX_FPS = "extra_max_fps"
        const val EXTRA_CPU_USAGE = "extra_cpu_usage"
        const val EXTRA_MEMORY_USED = "extra_memory_used"
        const val EXTRA_MEMORY_TOTAL = "extra_memory_total"
        const val EXTRA_BATTERY_LEVEL = "extra_battery_level"
        const val EXTRA_BATTERY_TEMP = "extra_battery_temp"
        const val EXTRA_CPU_TEMP = "extra_cpu_temp"

        fun start(context: Context) {
            val intent = Intent(context, FpsMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FpsMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
