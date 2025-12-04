package com.hannsapp.fpscounter.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.databinding.ActivityConnectionBinding
import com.hannsapp.fpscounter.utils.Constants
import java.util.regex.Pattern

class ConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectionBinding
    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())

    private var isWifiExpanded = false
    private var isShizukuExpanded = false
    private var isConnecting = false
    private var isPairing = false

    private val ipPattern = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager

        setupToolbar()
        setupListeners()
        loadSavedConnectionInfo()
        updateConnectionState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.cardWifiDebug.setOnClickListener {
            toggleWifiDebugSection()
        }

        binding.cardShizuku.setOnClickListener {
            toggleShizukuSection()
        }

        binding.btnPair.setOnClickListener {
            startPairing()
        }

        binding.btnConnectWifi.setOnClickListener {
            connectWifi()
        }

        binding.btnConnectShizuku.setOnClickListener {
            connectShizuku()
        }

        binding.btnDisconnect.setOnClickListener {
            disconnect()
        }

        binding.etIpAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateIpAddress()
            }
        })

        binding.etPort.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePort()
            }
        })

        binding.etPairingCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePairingCode()
            }
        })
    }

    private fun loadSavedConnectionInfo() {
        binding.etIpAddress.setText(preferencesManager.lastIp)
        binding.etPort.setText(preferencesManager.lastPort.toString())
    }

    private fun toggleWifiDebugSection() {
        isWifiExpanded = !isWifiExpanded
        animateExpansion(binding.wifiDebugDetails, isWifiExpanded)
        animateRotation(binding.ivWifiExpand, isWifiExpanded)

        if (isWifiExpanded && isShizukuExpanded) {
            isShizukuExpanded = false
            animateExpansion(binding.shizukuDetails, false)
            animateRotation(binding.ivShizukuExpand, false)
        }
    }

    private fun toggleShizukuSection() {
        isShizukuExpanded = !isShizukuExpanded
        animateExpansion(binding.shizukuDetails, isShizukuExpanded)
        animateRotation(binding.ivShizukuExpand, isShizukuExpanded)

        if (isShizukuExpanded && isWifiExpanded) {
            isWifiExpanded = false
            animateExpansion(binding.wifiDebugDetails, false)
            animateRotation(binding.ivWifiExpand, false)
        }
    }

    private fun animateExpansion(view: View, expand: Boolean) {
        if (expand) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = -20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        } else {
            view.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.visibility = View.GONE
                        view.animate().setListener(null)
                    }
                })
                .start()
        }
    }

    private fun animateRotation(view: View, expand: Boolean) {
        val rotation = if (expand) 90f else 0f
        ObjectAnimator.ofFloat(view, View.ROTATION, rotation).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun validateIpAddress(): Boolean {
        val ip = binding.etIpAddress.text?.toString() ?: ""
        val isValid = ip.isEmpty() || ipPattern.matcher(ip).matches()

        if (!isValid) {
            binding.tilIpAddress.error = getString(R.string.error_generic)
        } else {
            binding.tilIpAddress.error = null
        }

        return isValid && ip.isNotEmpty()
    }

    private fun validatePort(): Boolean {
        val portStr = binding.etPort.text?.toString() ?: ""
        val port = portStr.toIntOrNull()
        val isValid = port != null && port in 1..65535

        if (!isValid && portStr.isNotEmpty()) {
            binding.tilPort.error = getString(R.string.error_generic)
        } else {
            binding.tilPort.error = null
        }

        return isValid
    }

    private fun validatePairingCode(): Boolean {
        val code = binding.etPairingCode.text?.toString() ?: ""
        val isValid = code.isEmpty() || code.length >= 6

        if (!isValid) {
            binding.tilPairingCode.error = getString(R.string.error_generic)
        } else {
            binding.tilPairingCode.error = null
        }

        return code.length >= 6
    }

    private fun startPairing() {
        if (!validateIpAddress() || !validatePort() || !validatePairingCode()) {
            showWifiStatus(false, getString(R.string.error_generic))
            return
        }

        isPairing = true
        updateWifiButtons()
        showWifiProgress(true)
        showWifiStatus(null, getString(R.string.connecting))

        val ip = binding.etIpAddress.text?.toString() ?: ""
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: Constants.ADB_DEFAULT_PORT
        val pairingCode = binding.etPairingCode.text?.toString() ?: ""

        preferencesManager.lastIp = ip
        preferencesManager.lastPort = port

        handler.postDelayed({
            isPairing = false
            updateWifiButtons()
            showWifiProgress(false)

            val success = simulatePairing(ip, port, pairingCode)
            if (success) {
                showWifiStatus(true, getString(R.string.connection_success))
            } else {
                showWifiStatus(false, getString(R.string.connection_failed))
            }
        }, 2000)
    }

    private fun connectWifi() {
        if (!validateIpAddress() || !validatePort()) {
            showWifiStatus(false, getString(R.string.error_generic))
            return
        }

        isConnecting = true
        updateWifiButtons()
        showWifiProgress(true)
        showWifiStatus(null, getString(R.string.connecting))

        val ip = binding.etIpAddress.text?.toString() ?: ""
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: Constants.ADB_DEFAULT_PORT

        preferencesManager.lastIp = ip
        preferencesManager.lastPort = port

        handler.postDelayed({
            isConnecting = false
            updateWifiButtons()
            showWifiProgress(false)

            val success = simulateConnection(ip, port)
            if (success) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG
                showWifiStatus(true, getString(R.string.connected))
                updateConnectionState()
                animateConnectionSuccess()
            } else {
                showWifiStatus(false, getString(R.string.connection_failed))
            }
        }, 2000)
    }

    private fun connectShizuku() {
        isConnecting = true
        updateShizukuButtons()
        showShizukuProgress(true)
        showShizukuStatus(null, getString(R.string.connecting))

        handler.postDelayed({
            isConnecting = false
            updateShizukuButtons()
            showShizukuProgress(false)

            val success = checkShizukuAvailable()
            if (success) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_SHIZUKU
                showShizukuStatus(true, getString(R.string.connected))
                updateConnectionState()
                animateConnectionSuccess()
            } else {
                showShizukuStatus(false, getString(R.string.error_shizuku_not_running))
            }
        }, 1500)
    }

    private fun disconnect() {
        preferencesManager.connectionType = Constants.CONNECTION_TYPE_NONE
        updateConnectionState()
        showWifiStatus(null, "")
        showShizukuStatus(false, getString(R.string.disconnected))
        binding.wifiStatusLayout.visibility = View.GONE
    }

    private fun updateWifiButtons() {
        binding.btnPair.isEnabled = !isPairing && !isConnecting
        binding.btnConnectWifi.isEnabled = !isPairing && !isConnecting
    }

    private fun updateShizukuButtons() {
        binding.btnConnectShizuku.isEnabled = !isConnecting
    }

    private fun showWifiProgress(show: Boolean) {
        binding.wifiStatusLayout.visibility = View.VISIBLE
        binding.progressWifi.isVisible = show
        binding.ivWifiStatus.isVisible = !show
    }

    private fun showShizukuProgress(show: Boolean) {
        binding.progressShizuku.isVisible = show
        binding.ivShizukuStatus.isVisible = !show
    }

    private fun showWifiStatus(success: Boolean?, message: String) {
        binding.wifiStatusLayout.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvWifiStatus.text = message

        when (success) {
            true -> {
                binding.ivWifiStatus.setImageResource(R.drawable.ic_check_circle)
                binding.ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.success))
                binding.tvWifiStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            }
            false -> {
                binding.ivWifiStatus.setImageResource(R.drawable.ic_error)
                binding.ivWifiStatus.setColorFilter(ContextCompat.getColor(this, R.color.error))
                binding.tvWifiStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
            null -> {
                binding.tvWifiStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun showShizukuStatus(success: Boolean?, message: String) {
        binding.tvShizukuStatus.text = message

        when (success) {
            true -> {
                binding.ivShizukuStatus.setImageResource(R.drawable.ic_check_circle)
                binding.ivShizukuStatus.setColorFilter(ContextCompat.getColor(this, R.color.success))
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
            }
            false -> {
                binding.ivShizukuStatus.setImageResource(R.drawable.ic_error)
                binding.ivShizukuStatus.setColorFilter(ContextCompat.getColor(this, R.color.error))
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
            null -> {
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun updateConnectionState() {
        val connectionType = preferencesManager.connectionType
        val isConnected = connectionType != Constants.CONNECTION_TYPE_NONE

        binding.cardConnectionInfo.isVisible = isConnected

        if (isConnected) {
            val details = when (connectionType) {
                Constants.CONNECTION_TYPE_WIFI_DEBUG -> {
                    "${preferencesManager.lastIp}:${preferencesManager.lastPort}"
                }
                Constants.CONNECTION_TYPE_SHIZUKU -> {
                    getString(R.string.shizuku_title)
                }
                else -> ""
            }
            binding.tvConnectionDetails.text = details
        }
    }

    private fun animateConnectionSuccess() {
        binding.cardConnectionInfo.alpha = 0f
        binding.cardConnectionInfo.translationY = 50f
        binding.cardConnectionInfo.visibility = View.VISIBLE
        binding.cardConnectionInfo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun simulatePairing(ip: String, port: Int, code: String): Boolean {
        return ip.isNotEmpty() && port > 0 && code.length >= 6
    }

    private fun simulateConnection(ip: String, port: Int): Boolean {
        return ip.isNotEmpty() && port > 0
    }

    private fun checkShizukuAvailable(): Boolean {
        return try {
            val packageManager = packageManager
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
