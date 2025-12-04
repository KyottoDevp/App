package com.hannsapp.fpscounter.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.hannsapp.fpscounter.data.SystemInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile

class SystemInfoCollector(private val context: Context) {

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0

    fun collect(): SystemInfo {
        return SystemInfo(
            cpuUsage = getCpuUsage(),
            memoryUsed = getMemoryUsed(),
            memoryTotal = getMemoryTotal(),
            memoryAvailable = getMemoryAvailable(),
            batteryLevel = getBatteryLevel(),
            batteryTemp = getBatteryTemperature(),
            cpuTemp = getCpuTemperature(),
            gpuUsage = getGpuUsage(),
            screenRefreshRate = getScreenRefreshRate()
        )
    }

    fun getMemoryInfo(): Triple<Long, Long, Long> {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem
        val available = memInfo.availMem
        val used = total - available
        return Triple(used, total, available)
    }

    private fun getMemoryUsed(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem - memInfo.availMem
    }

    private fun getMemoryTotal(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    private fun getMemoryAvailable(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    fun getCpuUsage(): Float {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val parts = load.split("\\s+".toRegex())
            if (parts.size < 5) return 0f

            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
            val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
            val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L

            val total = user + nice + system + idle + iowait + irq + softirq
            val idleTime = idle + iowait

            if (lastCpuTotal == 0L) {
                lastCpuTotal = total
                lastCpuIdle = idleTime
                return 0f
            }

            val totalDiff = total - lastCpuTotal
            val idleDiff = idleTime - lastCpuIdle

            lastCpuTotal = total
            lastCpuIdle = idleTime

            if (totalDiff == 0L) return 0f

            return ((totalDiff - idleDiff).toFloat() / totalDiff) * 100f
        } catch (e: Exception) {
            return 0f
        }
    }

    fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun getBatteryTemperature(): Float {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10f
        } catch (e: Exception) {
            0f
        }
    }

    fun getCpuTemperature(): Float {
        val thermalZones = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/class/hwmon/hwmon1/temp1_input"
        )

        for (path in thermalZones) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val reader = BufferedReader(FileReader(file))
                    val temp = reader.readLine()?.trim()?.toFloatOrNull()
                    reader.close()
                    if (temp != null) {
                        return if (temp > 1000) temp / 1000f else if (temp > 100) temp / 10f else temp
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return 0f
    }

    private fun getGpuUsage(): Float {
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/misc/mali0/device/utilization"
        )

        for (path in gpuPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val reader = BufferedReader(FileReader(file))
                    val line = reader.readLine()?.trim()
                    reader.close()
                    if (line != null) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val busy = parts[0].toFloatOrNull() ?: 0f
                            val total = parts[1].toFloatOrNull() ?: 1f
                            if (total > 0) return (busy / total) * 100f
                        } else {
                            return line.toFloatOrNull() ?: 0f
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return 0f
    }

    private fun getScreenRefreshRate(): Float {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = context.display
                display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.refreshRate ?: 60f
            }
        } catch (e: Exception) {
            60f
        }
    }

    fun isLowMemory(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    fun getMemoryThreshold(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.threshold
    }

    companion object {
        private const val TAG = "SystemInfoCollector"
    }
}
