package com.hannsapp.fpscounter.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.data.PreferencesManager

class FpsAccessibilityService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentForegroundPackage: String = ""
    private var lastWindowChangeTime: Long = 0
    
    private val listeners = mutableSetOf<ForegroundAppListener>()
    
    private var fpsMonitorService: FpsMonitorService? = null
    private var isServiceBound = false

    interface ForegroundAppListener {
        fun onForegroundAppChanged(packageName: String, className: String)
        fun onWindowContentChanged(packageName: String)
    }

    private val monitorServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? FpsMonitorService.FpsMonitorBinder
            fpsMonitorService = binder?.getService()
            isServiceBound = true
            Log.d(TAG, "FpsMonitorService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fpsMonitorService = null
            isServiceBound = false
            Log.d(TAG, "FpsMonitorService disconnected")
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = HannsApplication.getInstance().preferencesManager
        instance = this
        Log.d(TAG, "FpsAccessibilityService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }
        
        serviceInfo = info
        
        bindToFpsMonitorService()
        
        Log.d(TAG, "FpsAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handleWindowsChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""
        
        if (packageName == "com.android.systemui") return
        
        if (packageName != currentForegroundPackage) {
            currentForegroundPackage = packageName
            lastWindowChangeTime = System.currentTimeMillis()
            
            Log.d(TAG, "Foreground app changed: $packageName / $className")
            
            notifyForegroundAppChanged(packageName, className)
            
            if (preferencesManager.isAppMonitored(packageName)) {
                onMonitoredAppDetected(packageName)
            }
            
            sendBroadcast(Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_CLASS_NAME, className)
                setPackage(this@FpsAccessibilityService.packageName)
            })
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        if (packageName == currentForegroundPackage) {
            notifyWindowContentChanged(packageName)
        }
    }

    private fun handleWindowsChanged(event: AccessibilityEvent) {
        try {
            val windows = windows
            for (window in windows) {
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = window.root
                    val packageName = root?.packageName?.toString()
                    if (packageName != null && packageName != currentForegroundPackage) {
                        if (packageName != "com.android.systemui") {
                            Log.d(TAG, "Window changed to: $packageName")
                        }
                    }
                    root?.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling windows changed", e)
        }
    }

    private fun onMonitoredAppDetected(packageName: String) {
        Log.d(TAG, "Monitored app detected: $packageName")
        
        if (preferencesManager.autoStart && fpsMonitorService?.isMonitoring() != true) {
            handler.postDelayed({
                if (currentForegroundPackage == packageName) {
                    FpsMonitorService.start(this)
                    FpsOverlayService.start(this)
                }
            }, 500)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "FpsAccessibilityService interrupted")
    }

    override fun onDestroy() {
        unbindFromFpsMonitorService()
        instance = null
        listeners.clear()
        super.onDestroy()
        Log.d(TAG, "FpsAccessibilityService destroyed")
    }

    private fun bindToFpsMonitorService() {
        if (!isServiceBound) {
            val intent = Intent(this, FpsMonitorService::class.java)
            bindService(intent, monitorServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindFromFpsMonitorService() {
        if (isServiceBound) {
            unbindService(monitorServiceConnection)
            isServiceBound = false
        }
    }

    fun getCurrentForegroundPackage(): String = currentForegroundPackage

    fun getLastWindowChangeTime(): Long = lastWindowChangeTime

    fun isMonitoredAppInForeground(): Boolean {
        return preferencesManager.isAppMonitored(currentForegroundPackage)
    }

    fun addListener(listener: ForegroundAppListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ForegroundAppListener) {
        listeners.remove(listener)
    }

    private fun notifyForegroundAppChanged(packageName: String, className: String) {
        handler.post {
            listeners.forEach { listener ->
                try {
                    listener.onForegroundAppChanged(packageName, className)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }

    private fun notifyWindowContentChanged(packageName: String) {
        handler.post {
            listeners.forEach { listener ->
                try {
                    listener.onWindowContentChanged(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "FpsAccessibilityService"
        
        const val ACTION_FOREGROUND_APP_CHANGED = "com.hannsapp.fpscounter.action.FOREGROUND_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CLASS_NAME = "extra_class_name"

        @Volatile
        private var instance: FpsAccessibilityService? = null

        fun getInstance(): FpsAccessibilityService? = instance

        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, FpsAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }

        fun isServiceRunning(): Boolean {
            return instance != null
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun getForegroundPackage(): String {
            return instance?.getCurrentForegroundPackage() ?: ""
        }
    }
}
