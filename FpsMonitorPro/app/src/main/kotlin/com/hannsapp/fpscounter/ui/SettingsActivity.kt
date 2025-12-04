package com.hannsapp.fpscounter.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.databinding.ActivitySettingsBinding
import com.hannsapp.fpscounter.utils.Constants

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferencesManager: PreferencesManager

    private val updateIntervalLabels = arrayOf(
        "100ms (Muito Rápido)",
        "250ms (Rápido)",
        "500ms (Normal)",
        "1000ms (Lento)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager

        setupToolbar()
        setupGeneralSettings()
        setupOverlaySettings()
        setupConnectionSettings()
        setupAboutSection()
        loadCurrentSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupGeneralSettings() {
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.autoStart = isChecked
            vibrate()
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.vibrationEnabled = isChecked
            if (isChecked) vibrate()
        }

        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.darkTheme = isChecked
            vibrate()
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            updateIntervalLabels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerUpdateInterval.adapter = adapter
        binding.spinnerUpdateInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                preferencesManager.updateInterval = Constants.UPDATE_INTERVALS[position]
                vibrate()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupOverlaySettings() {
        binding.cardOverlaySettings.setOnClickListener {
            try {
                val intent = android.content.Intent(this, OverlayCustomizationActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                // Activity not available
            }
        }
    }

    private fun setupConnectionSettings() {
        binding.cardConnectionSettings.setOnClickListener {
            try {
                val intent = android.content.Intent(this, ConnectionActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                // Activity not available
            }
        }

        updateConnectionStatus()
    }

    private fun updateConnectionStatus() {
        val connectionType = preferencesManager.connectionType
        when (connectionType) {
            Constants.CONNECTION_TYPE_WIFI_DEBUG -> {
                binding.tvConnectionStatus.text = getString(R.string.connected)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                binding.tvConnectionDetails.text = "${preferencesManager.lastIp}:${preferencesManager.lastPort}"
            }
            Constants.CONNECTION_TYPE_SHIZUKU -> {
                binding.tvConnectionStatus.text = getString(R.string.connected)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                binding.tvConnectionDetails.text = getString(R.string.shizuku_title)
            }
            else -> {
                binding.tvConnectionStatus.text = getString(R.string.disconnected)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
                binding.tvConnectionDetails.text = getString(R.string.connection_subtitle)
            }
        }
    }

    private fun setupAboutSection() {
        binding.tvVersionValue.text = getString(R.string.app_version)

        binding.btnResetSettings.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this, R.style.Theme_Hannsapp_Dialog)
            .setTitle(R.string.reset_settings)
            .setMessage(R.string.reset_settings_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                preferencesManager.resetToDefaults()
                loadCurrentSettings()
                vibrate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadCurrentSettings() {
        binding.switchAutoStart.isChecked = preferencesManager.autoStart
        binding.switchVibration.isChecked = preferencesManager.vibrationEnabled
        binding.switchDarkTheme.isChecked = preferencesManager.darkTheme

        val intervalIndex = Constants.UPDATE_INTERVALS.indexOf(preferencesManager.updateInterval)
        if (intervalIndex >= 0) {
            binding.spinnerUpdateInterval.setSelection(intervalIndex)
        }
    }

    private fun vibrate() {
        if (!preferencesManager.vibrationEnabled) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Vibration not available
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
    }
}
