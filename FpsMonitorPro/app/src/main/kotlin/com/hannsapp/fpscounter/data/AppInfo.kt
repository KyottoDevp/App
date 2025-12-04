package com.hannsapp.fpscounter.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    var isMonitored: Boolean = false,
    val versionName: String = "",
    val versionCode: Long = 0,
    val installedDate: Long = 0,
    val lastUpdated: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}

data class FpsData(
    val timestamp: Long,
    val fps: Int,
    val frameTime: Float
)

data class SystemInfo(
    val cpuUsage: Float = 0f,
    val memoryUsed: Long = 0L,
    val memoryTotal: Long = 0L,
    val memoryAvailable: Long = 0L,
    val batteryLevel: Int = 0,
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f,
    val gpuUsage: Float = 0f,
    val screenRefreshRate: Float = 0f
) {
    val memoryUsagePercent: Float
        get() = if (memoryTotal > 0) (memoryUsed.toFloat() / memoryTotal) * 100f else 0f

    val memoryUsedMb: Long
        get() = memoryUsed / (1024 * 1024)

    val memoryTotalMb: Long
        get() = memoryTotal / (1024 * 1024)

    val memoryAvailableMb: Long
        get() = memoryAvailable / (1024 * 1024)
}

data class FpsStats(
    val currentFps: Int = 0,
    val averageFps: Float = 0f,
    val minFps: Int = 0,
    val maxFps: Int = 0,
    val fpsHistory: List<Int> = emptyList(),
    val frameTimeHistory: List<Float> = emptyList()
) {
    val isStable: Boolean
        get() = maxFps - minFps <= 10

    val stabilityPercent: Float
        get() {
            if (maxFps == 0) return 100f
            return (1f - ((maxFps - minFps).toFloat() / maxFps)) * 100f
        }
}

data class ConnectionStatus(
    val isConnected: Boolean = false,
    val connectionType: Int = 0,
    val deviceName: String = "",
    val ipAddress: String = "",
    val port: Int = 0,
    val errorMessage: String? = null
)

data class OverlayConfig(
    val position: Int = 0,
    val size: Int = 1,
    val opacity: Float = 0.8f,
    val color: Int = 0xFF1565C0.toInt(),
    val showFps: Boolean = true,
    val showMemory: Boolean = true,
    val showCpu: Boolean = false,
    val showBattery: Boolean = false,
    val showTemp: Boolean = false
)
