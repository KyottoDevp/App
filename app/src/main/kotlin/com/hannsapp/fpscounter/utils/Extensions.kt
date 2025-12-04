package com.hannsapp.fpscounter.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.AnimRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.hannsapp.fpscounter.HannsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.vibrate(duration: Long = 50) {
    val prefs = HannsApplication.getInstance().preferencesManager
    if (!prefs.vibrationEnabled) return
    
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}

fun View.fadeIn(duration: Long = 300) {
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .start()
}

fun View.fadeOut(duration: Long = 300, gone: Boolean = true) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            visibility = if (gone) View.GONE else View.INVISIBLE
        }
        .start()
}

fun View.slideInFromRight(duration: Long = 300) {
    translationX = width.toFloat()
    visibility = View.VISIBLE
    animate()
        .translationX(0f)
        .setDuration(duration)
        .start()
}

fun View.slideOutToLeft(duration: Long = 300, gone: Boolean = true) {
    animate()
        .translationX(-width.toFloat())
        .setDuration(duration)
        .withEndAction {
            visibility = if (gone) View.GONE else View.INVISIBLE
            translationX = 0f
        }
        .start()
}

fun View.scaleIn(duration: Long = 300) {
    scaleX = 0f
    scaleY = 0f
    visibility = View.VISIBLE
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .start()
}

fun View.pulse(scale: Float = 1.1f, duration: Long = 150) {
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .setDuration(duration)
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .start()
        }
        .start()
}

fun View.startAnimation(@AnimRes animResId: Int) {
    val animation = AnimationUtils.loadAnimation(context, animResId)
    startAnimation(animation)
}

fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun Long.formatMegabytes(): String {
    val mb = this / (1024 * 1024)
    return "$mb MB"
}

fun Long.formatGigabytes(): String {
    val gb = this / (1024.0 * 1024.0 * 1024.0)
    return DecimalFormat("#,##0.0").format(gb) + " GB"
}

fun Float.formatPercent(): String {
    return DecimalFormat("#,##0.0").format(this) + "%"
}

fun Float.formatTemperature(): String {
    return DecimalFormat("#,##0.0").format(this) + "°C"
}

fun Int.dpToPx(): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()
}

fun Float.dpToPx(): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )
}

@ColorInt
fun getFpsColor(fps: Int): Int {
    return when {
        fps >= 55 -> Color.parseColor("#4CAF50")
        fps >= 45 -> Color.parseColor("#8BC34A")
        fps >= 30 -> Color.parseColor("#FFC107")
        fps >= 20 -> Color.parseColor("#FF9800")
        else -> Color.parseColor("#F44336")
    }
}

@ColorInt
fun getMemoryColor(usagePercent: Float): Int {
    return when {
        usagePercent < 50 -> Color.parseColor("#4CAF50")
        usagePercent < 75 -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#F44336")
    }
}

@ColorInt
fun getCpuColor(usagePercent: Float): Int {
    return when {
        usagePercent < 40 -> Color.parseColor("#4CAF50")
        usagePercent < 70 -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#F44336")
    }
}

@ColorInt
fun getTempColor(temp: Float): Int {
    return when {
        temp < 40 -> Color.parseColor("#4CAF50")
        temp < 50 -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#F44336")
    }
}

fun CoroutineScope.launchDelayed(delayMs: Long, block: suspend () -> Unit) {
    launch(Dispatchers.Main) {
        delay(delayMs)
        block()
    }
}

fun String.isValidIpAddress(): Boolean {
    val ipPattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
    return ipPattern.matches(this)
}

fun String.isValidPort(): Boolean {
    val port = this.toIntOrNull() ?: return false
    return port in 1..65535
}
