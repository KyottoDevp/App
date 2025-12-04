package com.hannsapp.fpscounter.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.FpsStats
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.data.SystemInfo
import com.hannsapp.fpscounter.utils.Constants

class FpsOverlayService : Service(), FpsMonitorService.FpsDataListener {

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var handler: Handler

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var fpsMonitorService: FpsMonitorService? = null
    private var isServiceBound = false

    private var fpsTextView: TextView? = null
    private var memoryTextView: TextView? = null
    private var cpuTextView: TextView? = null
    private var batteryTextView: TextView? = null
    private var tempTextView: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FpsMonitorService.FpsMonitorBinder
            fpsMonitorService = binder.getService()
            fpsMonitorService?.addListener(this@FpsOverlayService)
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fpsMonitorService?.removeListener(this@FpsOverlayService)
            fpsMonitorService = null
            isServiceBound = false
        }
    }

    private val fpsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FpsMonitorService.ACTION_FPS_UPDATE) {
                val fps = intent.getIntExtra(FpsMonitorService.EXTRA_FPS, 0)
                val cpuUsage = intent.getFloatExtra(FpsMonitorService.EXTRA_CPU_USAGE, 0f)
                val memoryUsed = intent.getLongExtra(FpsMonitorService.EXTRA_MEMORY_USED, 0)
                val memoryTotal = intent.getLongExtra(FpsMonitorService.EXTRA_MEMORY_TOTAL, 0)
                val batteryLevel = intent.getIntExtra(FpsMonitorService.EXTRA_BATTERY_LEVEL, 0)
                val batteryTemp = intent.getFloatExtra(FpsMonitorService.EXTRA_BATTERY_TEMP, 0f)
                val cpuTemp = intent.getFloatExtra(FpsMonitorService.EXTRA_CPU_TEMP, 0f)

                val systemInfo = SystemInfo(
                    cpuUsage = cpuUsage,
                    memoryUsed = memoryUsed * 1024 * 1024,
                    memoryTotal = memoryTotal * 1024 * 1024,
                    batteryLevel = batteryLevel,
                    batteryTemp = batteryTemp,
                    cpuTemp = cpuTemp
                )

                handler.post {
                    updateDisplay(fps, systemInfo)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = HannsApplication.getInstance().preferencesManager
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (canDrawOverlay()) {
                    startForeground(Constants.NOTIFICATION_ID_FPS, createNotification())
                    createOverlay()
                    bindToMonitorService()
                    registerReceiver()
                }
            }
            ACTION_STOP -> {
                removeOverlay()
                unbindFromMonitorService()
                unregisterReceiver()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_CONFIG -> {
                updateOverlayConfig()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        unbindFromMonitorService()
        unregisterReceiver()
        super.onDestroy()
    }

    override fun onFpsUpdate(fps: Int, stats: FpsStats, systemInfo: SystemInfo) {
        handler.post {
            updateDisplay(fps, systemInfo)
        }
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createOverlay() {
        if (overlayView != null) return

        overlayView = createOverlayView()
        layoutParams = createLayoutParams()

        try {
            windowManager.addView(overlayView, layoutParams)
            setupTouchListener()
        } catch (e: Exception) {
            overlayView = null
        }
    }

    private fun createOverlayView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            setBackgroundResource(R.drawable.overlay_background)
            alpha = preferencesManager.overlayOpacity
        }

        val textSize = when (preferencesManager.overlaySize) {
            Constants.OVERLAY_SIZE_SMALL -> 10f
            Constants.OVERLAY_SIZE_MEDIUM -> 12f
            Constants.OVERLAY_SIZE_LARGE -> 14f
            else -> 12f
        }

        val textColor = preferencesManager.overlayColor

        if (preferencesManager.showFps) {
            fpsTextView = createTextView(textSize, textColor, "FPS: --")
            container.addView(fpsTextView)
        }

        if (preferencesManager.showMemory) {
            memoryTextView = createTextView(textSize, textColor, "RAM: --")
            container.addView(memoryTextView)
        }

        if (preferencesManager.showCpu) {
            cpuTextView = createTextView(textSize, textColor, "CPU: --")
            container.addView(cpuTextView)
        }

        if (preferencesManager.showBattery) {
            batteryTextView = createTextView(textSize, textColor, "BAT: --")
            container.addView(batteryTextView)
        }

        if (preferencesManager.showTemp) {
            tempTextView = createTextView(textSize, textColor, "TEMP: --")
            container.addView(tempTextView)
        }

        return container
    }

    private fun createTextView(textSize: Float, textColor: Int, initialText: String): TextView {
        return TextView(this).apply {
            text = initialText
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
            setTextColor(textColor)
            setShadowLayer(2f, 1f, 1f, 0xFF000000.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        applyPosition(params)

        return params
    }

    private fun applyPosition(params: WindowManager.LayoutParams) {
        when (preferencesManager.overlayPosition) {
            Constants.OVERLAY_POSITION_TOP_LEFT -> {
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 20
                params.y = 100
            }
            Constants.OVERLAY_POSITION_TOP_RIGHT -> {
                params.gravity = Gravity.TOP or Gravity.END
                params.x = 20
                params.y = 100
            }
            Constants.OVERLAY_POSITION_BOTTOM_LEFT -> {
                params.gravity = Gravity.BOTTOM or Gravity.START
                params.x = 20
                params.y = 100
            }
            Constants.OVERLAY_POSITION_BOTTOM_RIGHT -> {
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.x = 20
                params.y = 100
            }
            Constants.OVERLAY_POSITION_CENTER_TOP -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = 100
            }
            Constants.OVERLAY_POSITION_CENTER_BOTTOM -> {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = 100
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams?.x = (initialX + deltaX).toInt()
                        layoutParams?.y = (initialY + deltaY).toInt()
                        try {
                            windowManager.updateViewLayout(overlayView, layoutParams)
                        } catch (e: Exception) {
                            // View may have been removed
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Handle click if needed
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View may already be removed
            }
        }
        overlayView = null
        fpsTextView = null
        memoryTextView = null
        cpuTextView = null
        batteryTextView = null
        tempTextView = null
    }

    private fun updateOverlayConfig() {
        removeOverlay()
        if (canDrawOverlay()) {
            createOverlay()
        }
    }

    private fun updateDisplay(fps: Int, systemInfo: SystemInfo) {
        fpsTextView?.text = String.format("FPS: %d", fps)

        memoryTextView?.text = String.format(
            "RAM: %d/%d MB",
            systemInfo.memoryUsedMb,
            systemInfo.memoryTotalMb
        )

        cpuTextView?.text = String.format("CPU: %.1f%%", systemInfo.cpuUsage)

        batteryTextView?.text = String.format(
            "BAT: %d%% %.1f°C",
            systemInfo.batteryLevel,
            systemInfo.batteryTemp
        )

        tempTextView?.text = String.format("TEMP: %.1f°C", systemInfo.cpuTemp)

        updateFpsColor(fps)
    }

    private fun updateFpsColor(fps: Int) {
        val color = when {
            fps >= 55 -> 0xFF4CAF50.toInt() // Green - Good
            fps >= 45 -> 0xFFFFC107.toInt() // Yellow - OK
            fps >= 30 -> 0xFFFF9800.toInt() // Orange - Warning
            else -> 0xFFF44336.toInt()      // Red - Bad
        }

        if (!preferencesManager.showFps) return

        fpsTextView?.setTextColor(color)
    }

    private fun bindToMonitorService() {
        val intent = Intent(this, FpsMonitorService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromMonitorService() {
        if (isServiceBound) {
            fpsMonitorService?.removeListener(this)
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter(FpsMonitorService.ACTION_FPS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fpsUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fpsUpdateReceiver, filter)
        }
    }

    private fun unregisterReceiver() {
        try {
            unregisterReceiver(fpsUpdateReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }

    private fun createNotification(): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopIntent = Intent(this, FpsOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_FPS)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_fps)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_fps, getString(R.string.stop), stopPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "FpsOverlayService"

        const val ACTION_START = "com.hannsapp.fpscounter.action.START_OVERLAY"
        const val ACTION_STOP = "com.hannsapp.fpscounter.action.STOP_OVERLAY"
        const val ACTION_UPDATE_CONFIG = "com.hannsapp.fpscounter.action.UPDATE_OVERLAY_CONFIG"

        fun start(context: Context) {
            val intent = Intent(context, FpsOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FpsOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateConfig(context: Context) {
            val intent = Intent(context, FpsOverlayService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            context.startService(intent)
        }
    }
}
