package com.hannsapp.fpscounter.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.FpsStats
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.data.SystemInfo
import com.hannsapp.fpscounter.databinding.ActivityMainBinding
import com.hannsapp.fpscounter.services.FpsMonitorService
import com.hannsapp.fpscounter.services.FpsOverlayService
import com.hannsapp.fpscounter.utils.Constants

class MainActivity : AppCompatActivity(), FpsMonitorService.FpsDataListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    private var fpsMonitorService: FpsMonitorService? = null
    private var isServiceBound = false
    private var isMonitoring = false

    private var currentFpsValue = 0
    private var fpsAnimator: ValueAnimator? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FpsMonitorService.FpsMonitorBinder
            fpsMonitorService = binder.getService()
            fpsMonitorService?.addListener(this@MainActivity)
            isServiceBound = true
            isMonitoring = fpsMonitorService?.isMonitoring() ?: false
            updateMonitoringUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fpsMonitorService?.removeListener(this@MainActivity)
            fpsMonitorService = null
            isServiceBound = false
        }
    }

    private val fpsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FpsMonitorService.ACTION_FPS_UPDATE) {
                val fps = intent.getIntExtra(FpsMonitorService.EXTRA_FPS, 0)
                val avgFps = intent.getFloatExtra(FpsMonitorService.EXTRA_AVG_FPS, 0f)
                val minFps = intent.getIntExtra(FpsMonitorService.EXTRA_MIN_FPS, 0)
                val maxFps = intent.getIntExtra(FpsMonitorService.EXTRA_MAX_FPS, 0)
                val cpuUsage = intent.getFloatExtra(FpsMonitorService.EXTRA_CPU_USAGE, 0f)
                val memoryUsed = intent.getLongExtra(FpsMonitorService.EXTRA_MEMORY_USED, 0)
                val memoryTotal = intent.getLongExtra(FpsMonitorService.EXTRA_MEMORY_TOTAL, 0)
                val batteryLevel = intent.getIntExtra(FpsMonitorService.EXTRA_BATTERY_LEVEL, 0)
                val batteryTemp = intent.getFloatExtra(FpsMonitorService.EXTRA_BATTERY_TEMP, 0f)
                val cpuTemp = intent.getFloatExtra(FpsMonitorService.EXTRA_CPU_TEMP, 0f)

                handler.post {
                    updateFpsDisplay(fps, avgFps, minFps, maxFps)
                    updateSystemInfo(cpuUsage, memoryUsed, memoryTotal, batteryLevel, batteryTemp, cpuTemp)
                }
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    private val usagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager

        setupViews()
        setupListeners()
        setupBottomNavigation()

        if (preferencesManager.isFirstRun) {
            showSplashAnimation()
            preferencesManager.isFirstRun = false
        } else {
            binding.splashOverlay.visibility = View.GONE
        }

        checkPermissions()
        bindToMonitorService()
        updateConnectionStatus()
    }

    override fun onResume() {
        super.onResume()
        registerFpsReceiver()
        checkPermissions()
        updateConnectionStatus()
        if (isServiceBound) {
            isMonitoring = fpsMonitorService?.isMonitoring() ?: false
            updateMonitoringUI()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterFpsReceiver()
    }

    override fun onDestroy() {
        unbindFromMonitorService()
        fpsAnimator?.cancel()
        super.onDestroy()
    }

    override fun onFpsUpdate(fps: Int, stats: FpsStats, systemInfo: SystemInfo) {
        handler.post {
            updateFpsDisplay(fps, stats.averageFps, stats.minFps, stats.maxFps)
            updateSystemInfo(
                systemInfo.cpuUsage,
                systemInfo.memoryUsedMb,
                systemInfo.memoryTotalMb,
                systemInfo.batteryLevel,
                systemInfo.batteryTemp,
                systemInfo.cpuTemp
            )
        }
    }

    private fun setupViews() {
        binding.tvFpsValue.text = "0"
        binding.tvAvgFps.text = "0"
        binding.tvMinFps.text = "0"
        binding.tvMaxFps.text = "0"
        binding.progressMemory.progress = 0
        binding.progressCpu.progress = 0
    }

    private fun setupListeners() {
        binding.fabMonitoring.setOnClickListener {
            toggleMonitoring()
        }

        binding.connectionStatusLayout.setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }

        binding.btnConnection.setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }

        binding.btnOverlaySettings.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.hannsapp.fpscounter.ui.OverlayCustomizationActivity"))
                startActivity(intent)
            } catch (e: Exception) {
                // Activity not found
            }
        }

        binding.btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnGrantUsage.setOnClickListener {
            requestUsageStatsPermission()
        }

        binding.btnGrantNotification.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_apps -> {
                    try {
                        val intent = Intent(this, Class.forName("com.hannsapp.fpscounter.ui.AppSelectionActivity"))
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Activity not found
                    }
                    true
                }
                R.id.nav_settings -> {
                    try {
                        val intent = Intent(this, Class.forName("com.hannsapp.fpscounter.ui.SettingsActivity"))
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Activity not found
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showSplashAnimation() {
        binding.splashOverlay.visibility = View.VISIBLE
        binding.splashOverlay.alpha = 1f

        handler.postDelayed({
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.splashOverlay.visibility = View.GONE
                        animateContentIn()
                    }
                })
                .start()
        }, 1500)
    }

    private fun animateContentIn() {
        val cards = listOf(
            binding.cardFpsDisplay,
            binding.cardSystemInfo,
            binding.cardQuickActions
        )

        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 100f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 100).toLong())
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }

        binding.fabMonitoring.scaleX = 0f
        binding.fabMonitoring.scaleY = 0f
        binding.fabMonitoring.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setStartDelay(400)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            if (checkAllPermissionsGranted()) {
                startMonitoring()
            } else {
                showPermissionsCard()
            }
        }
    }

    private fun startMonitoring() {
        FpsMonitorService.start(this)
        FpsOverlayService.start(this)
        isMonitoring = true
        updateMonitoringUI()
        animateFabChange()
    }

    private fun stopMonitoring() {
        FpsMonitorService.stop(this)
        FpsOverlayService.stop(this)
        isMonitoring = false
        updateMonitoringUI()
        animateFabChange()
    }

    private fun updateMonitoringUI() {
        binding.fabMonitoring.apply {
            if (isMonitoring) {
                text = getString(R.string.stop_monitoring)
                setIconResource(R.drawable.ic_stop)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.error))
            } else {
                text = getString(R.string.start_monitoring)
                setIconResource(R.drawable.ic_play)
                backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.primary)
            }
        }

        binding.tvMonitoringStatus.text = if (isMonitoring) {
            getString(R.string.monitoring_active)
        } else {
            getString(R.string.monitoring_inactive)
        }

        binding.tvMonitoringStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isMonitoring) R.color.success else R.color.text_tertiary
            )
        )
    }

    private fun animateFabChange() {
        binding.fabMonitoring.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.fabMonitoring.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    private fun updateFpsDisplay(fps: Int, avgFps: Float, minFps: Int, maxFps: Int) {
        animateFpsValue(currentFpsValue, fps)
        currentFpsValue = fps

        binding.tvAvgFps.text = String.format("%.1f", avgFps)
        binding.tvMinFps.text = minFps.toString()
        binding.tvMaxFps.text = maxFps.toString()

        val fpsColor = when {
            fps >= 55 -> R.color.fps_excellent
            fps >= 45 -> R.color.fps_good
            fps >= 30 -> R.color.fps_moderate
            fps >= 20 -> R.color.fps_poor
            else -> R.color.fps_critical
        }
        binding.tvFpsValue.setTextColor(ContextCompat.getColor(this, fpsColor))
    }

    private fun animateFpsValue(from: Int, to: Int) {
        fpsAnimator?.cancel()
        fpsAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.tvFpsValue.text = value.toString()
            }
            start()
        }
    }

    private fun updateSystemInfo(
        cpuUsage: Float,
        memoryUsed: Long,
        memoryTotal: Long,
        batteryLevel: Int,
        batteryTemp: Float,
        cpuTemp: Float
    ) {
        val memoryPercent = if (memoryTotal > 0) {
            ((memoryUsed.toFloat() / memoryTotal) * 100).toInt()
        } else 0

        binding.tvMemoryValue.text = String.format("%d / %d MB", memoryUsed, memoryTotal)
        binding.progressMemory.setProgressCompat(memoryPercent, true)

        val cpuPercent = cpuUsage.toInt().coerceIn(0, 100)
        binding.tvCpuValue.text = String.format("%.1f%%", cpuUsage)
        binding.progressCpu.setProgressCompat(cpuPercent, true)

        binding.tvBatteryLevel.text = String.format("%d%%", batteryLevel)
        binding.tvBatteryTemp.text = String.format("%.1f°C", batteryTemp)
        binding.tvCpuTemp.text = String.format("%.1f°C", cpuTemp)

        val memoryColor = when {
            memoryPercent < 60 -> R.color.memory_low
            memoryPercent < 80 -> R.color.memory_medium
            else -> R.color.memory_high
        }
        binding.progressMemory.setIndicatorColor(ContextCompat.getColor(this, memoryColor))

        val cpuColor = when {
            cpuPercent < 50 -> R.color.cpu_cool
            cpuPercent < 75 -> R.color.cpu_warm
            else -> R.color.cpu_hot
        }
        binding.progressCpu.setIndicatorColor(ContextCompat.getColor(this, cpuColor))
    }

    private fun updateConnectionStatus() {
        val connectionType = preferencesManager.connectionType
        val isConnected = connectionType != Constants.CONNECTION_TYPE_NONE

        if (isConnected) {
            binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_connected)
            binding.tvConnectionStatus.text = getString(R.string.connected)
            binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.connected))
        } else {
            binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_disconnected)
            binding.tvConnectionStatus.text = getString(R.string.disconnected)
            binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun checkPermissions() {
        val overlayGranted = hasOverlayPermission()
        val usageGranted = hasUsageStatsPermission()
        val notificationGranted = hasNotificationPermission()

        updatePermissionUI(R.id.permOverlayLayout, binding.ivOverlayStatus, binding.btnGrantOverlay, overlayGranted)
        updatePermissionUI(R.id.permUsageLayout, binding.ivUsageStatus, binding.btnGrantUsage, usageGranted)
        updatePermissionUI(R.id.permNotificationLayout, binding.ivNotificationStatus, binding.btnGrantNotification, notificationGranted)

        val allGranted = overlayGranted && usageGranted && notificationGranted
        binding.cardPermissions.isVisible = !allGranted
    }

    private fun updatePermissionUI(layoutId: Int, statusIcon: android.widget.ImageView, button: View, granted: Boolean) {
        if (granted) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success))
            button.visibility = View.GONE
        } else {
            statusIcon.setImageResource(R.drawable.ic_error)
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.error))
            button.visibility = View.VISIBLE
        }
    }

    private fun showPermissionsCard() {
        if (!binding.cardPermissions.isVisible) {
            binding.cardPermissions.visibility = View.VISIBLE
            binding.cardPermissions.alpha = 0f
            binding.cardPermissions.translationY = -50f
            binding.cardPermissions.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    private fun checkAllPermissionsGranted(): Boolean {
        return hasOverlayPermission() && hasUsageStatsPermission() && hasNotificationPermission()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usagePermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    private fun registerFpsReceiver() {
        val filter = IntentFilter(FpsMonitorService.ACTION_FPS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fpsUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(fpsUpdateReceiver, filter)
        }
    }

    private fun unregisterFpsReceiver() {
        try {
            unregisterReceiver(fpsUpdateReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }
}
