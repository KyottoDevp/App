package com.hannsapp.fpscounter.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hannsapp.fpscounter.utils.Constants

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    var overlayPosition: Int
        get() = prefs.getInt(Constants.PREF_OVERLAY_POSITION, Constants.DEFAULT_OVERLAY_POSITION)
        set(value) = prefs.edit().putInt(Constants.PREF_OVERLAY_POSITION, value).apply()

    var overlaySize: Int
        get() = prefs.getInt(Constants.PREF_OVERLAY_SIZE, Constants.DEFAULT_OVERLAY_SIZE)
        set(value) = prefs.edit().putInt(Constants.PREF_OVERLAY_SIZE, value).apply()

    var overlayOpacity: Float
        get() = prefs.getFloat(Constants.PREF_OVERLAY_OPACITY, Constants.DEFAULT_OVERLAY_OPACITY)
        set(value) = prefs.edit().putFloat(Constants.PREF_OVERLAY_OPACITY, value).apply()

    var overlayColor: Int
        get() = prefs.getInt(Constants.PREF_OVERLAY_COLOR, Constants.OVERLAY_COLORS[0])
        set(value) = prefs.edit().putInt(Constants.PREF_OVERLAY_COLOR, value).apply()

    var updateInterval: Long
        get() = prefs.getLong(Constants.PREF_UPDATE_INTERVAL, Constants.DEFAULT_UPDATE_INTERVAL)
        set(value) = prefs.edit().putLong(Constants.PREF_UPDATE_INTERVAL, value).apply()

    var showFps: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_FPS, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_FPS, value).apply()

    var showMemory: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_MEMORY, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_MEMORY, value).apply()

    var showCpu: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_CPU, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_CPU, value).apply()

    var showBattery: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_BATTERY, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_BATTERY, value).apply()

    var showTemp: Boolean
        get() = prefs.getBoolean(Constants.PREF_SHOW_TEMP, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_SHOW_TEMP, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(Constants.PREF_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_AUTO_START, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_VIBRATION, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(Constants.PREF_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_DARK_THEME, value).apply()

    var connectionType: Int
        get() = prefs.getInt(Constants.PREF_CONNECTION_TYPE, Constants.CONNECTION_TYPE_NONE)
        set(value) = prefs.edit().putInt(Constants.PREF_CONNECTION_TYPE, value).apply()

    var lastIp: String
        get() = prefs.getString(Constants.PREF_LAST_IP, "") ?: ""
        set(value) = prefs.edit().putString(Constants.PREF_LAST_IP, value).apply()

    var lastPort: Int
        get() = prefs.getInt(Constants.PREF_LAST_PORT, Constants.ADB_DEFAULT_PORT)
        set(value) = prefs.edit().putInt(Constants.PREF_LAST_PORT, value).apply()

    var isFirstRun: Boolean
        get() = prefs.getBoolean(Constants.PREF_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_FIRST_RUN, value).apply()

    var monitoredApps: Set<String>
        get() {
            val json = prefs.getString(Constants.PREF_MONITORED_APPS, "[]") ?: "[]"
            val type = object : TypeToken<Set<String>>() {}.type
            return try {
                gson.fromJson(json, type) ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(Constants.PREF_MONITORED_APPS, json).apply()
        }

    fun addMonitoredApp(packageName: String) {
        val current = monitoredApps.toMutableSet()
        current.add(packageName)
        monitoredApps = current
    }

    fun removeMonitoredApp(packageName: String) {
        val current = monitoredApps.toMutableSet()
        current.remove(packageName)
        monitoredApps = current
    }

    fun isAppMonitored(packageName: String): Boolean {
        return monitoredApps.contains(packageName)
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    fun registerOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
