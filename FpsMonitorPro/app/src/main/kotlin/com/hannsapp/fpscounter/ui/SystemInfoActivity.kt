package com.hannsapp.fpscounter.ui

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.databinding.ActivitySystemInfoBinding
import com.hannsapp.fpscounter.services.SystemInfoCollector

class SystemInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemInfoBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var systemInfoCollector: SystemInfoCollector

    private val handler = Handler(Looper.getMainLooper())
    private var isUpdating = false

    private val cpuHistory = mutableListOf<Float>()
    private val memoryHistory = mutableListOf<Float>()
    private val maxHistorySize = 60

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isUpdating) {
                updateSystemInfo()
                handler.postDelayed(this, preferencesManager.updateInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager
        systemInfoCollector = SystemInfoCollector(this)

        setupToolbar()
        setupCharts()
        setupDeviceInfo()
        updateSystemInfo()
    }

    override fun onResume() {
        super.onResume()
        startUpdating()
    }

    override fun onPause() {
        super.onPause()
        stopUpdating()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupCharts() {
        setupChart(binding.chartCpu, R.color.chart_line_1)
        setupChart(binding.chartMemory, R.color.chart_line_2)
    }

    private fun setupChart(chart: LineChart, lineColorRes: Int) {
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setDrawBorders(false)
            setViewPortOffsets(0f, 0f, 0f, 0f)
            setNoDataText("")

            xAxis.apply {
                isEnabled = false
                setDrawGridLines(false)
                position = XAxis.XAxisPosition.BOTTOM
            }

            axisLeft.apply {
                isEnabled = false
                setDrawGridLines(false)
                axisMinimum = 0f
                axisMaximum = 100f
            }

            axisRight.isEnabled = false

            animateX(500)
        }
    }

    private fun setupDeviceInfo() {
        binding.tvDeviceModel.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        binding.tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        val (_, total, _) = systemInfoCollector.getMemoryInfo()
        binding.tvTotalRam.text = formatBytes(total)

        try {
            val cpuInfo = Runtime.getRuntime().availableProcessors()
            binding.tvCpuCores.text = "$cpuInfo cores"
        } catch (e: Exception) {
            binding.tvCpuCores.text = "N/A"
        }
    }

    private fun updateSystemInfo() {
        val systemInfo = systemInfoCollector.collect()

        binding.tvCpuValue.text = String.format("%.1f%%", systemInfo.cpuUsage)
        updateCpuProgress(systemInfo.cpuUsage)

        val memoryPercent = systemInfo.memoryUsagePercent
        binding.tvMemoryValue.text = String.format("%.1f%%", memoryPercent)
        binding.tvMemoryDetails.text = String.format(
            "%s / %s",
            formatBytes(systemInfo.memoryUsed),
            formatBytes(systemInfo.memoryTotal)
        )
        updateMemoryProgress(memoryPercent)

        binding.tvBatteryLevel.text = "${systemInfo.batteryLevel}%"
        binding.tvBatteryTemp.text = String.format("%.1f°C", systemInfo.batteryTemp)
        binding.progressBattery.progress = systemInfo.batteryLevel

        val batteryColor = when {
            systemInfo.batteryLevel >= 60 -> R.color.success
            systemInfo.batteryLevel >= 30 -> R.color.warning
            else -> R.color.error
        }
        binding.progressBattery.setIndicatorColor(ContextCompat.getColor(this, batteryColor))

        binding.tvCpuTemp.text = if (systemInfo.cpuTemp > 0) {
            String.format("%.1f°C", systemInfo.cpuTemp)
        } else {
            "N/A"
        }

        binding.tvRefreshRate.text = String.format("%.0f Hz", systemInfo.screenRefreshRate)

        cpuHistory.add(systemInfo.cpuUsage)
        if (cpuHistory.size > maxHistorySize) {
            cpuHistory.removeAt(0)
        }
        updateChart(binding.chartCpu, cpuHistory, R.color.chart_line_1)

        memoryHistory.add(memoryPercent)
        if (memoryHistory.size > maxHistorySize) {
            memoryHistory.removeAt(0)
        }
        updateChart(binding.chartMemory, memoryHistory, R.color.chart_line_2)
    }

    private fun updateCpuProgress(usage: Float) {
        val progress = usage.toInt().coerceIn(0, 100)
        binding.progressCpu.setProgressCompat(progress, true)

        val color = when {
            progress < 50 -> R.color.cpu_cool
            progress < 75 -> R.color.cpu_warm
            else -> R.color.cpu_hot
        }
        binding.progressCpu.setIndicatorColor(ContextCompat.getColor(this, color))
    }

    private fun updateMemoryProgress(percent: Float) {
        val progress = percent.toInt().coerceIn(0, 100)
        binding.progressMemory.setProgressCompat(progress, true)

        val color = when {
            progress < 60 -> R.color.memory_low
            progress < 80 -> R.color.memory_medium
            else -> R.color.memory_high
        }
        binding.progressMemory.setIndicatorColor(ContextCompat.getColor(this, color))
    }

    private fun updateChart(chart: LineChart, data: List<Float>, colorRes: Int) {
        val entries = data.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, "").apply {
            color = ContextCompat.getColor(this@SystemInfoActivity, colorRes)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@SystemInfoActivity, colorRes)
            fillAlpha = 50
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun startUpdating() {
        isUpdating = true
        handler.post(updateRunnable)
    }

    private fun stopUpdating() {
        isUpdating = false
        handler.removeCallbacks(updateRunnable)
    }
}
