package com.hannsapp.fpscounter.utils

object Constants {
    const val NOTIFICATION_CHANNEL_FPS = "fps_monitor_channel"
    const val NOTIFICATION_CHANNEL_CONNECTION = "connection_channel"
    const val NOTIFICATION_ID_FPS = 1001
    const val NOTIFICATION_ID_CONNECTION = 1002

    const val PREFS_NAME = "hannsapp_prefs"
    const val PREF_OVERLAY_POSITION = "overlay_position"
    const val PREF_OVERLAY_SIZE = "overlay_size"
    const val PREF_OVERLAY_OPACITY = "overlay_opacity"
    const val PREF_OVERLAY_COLOR = "overlay_color"
    const val PREF_UPDATE_INTERVAL = "update_interval"
    const val PREF_SHOW_FPS = "show_fps"
    const val PREF_SHOW_MEMORY = "show_memory"
    const val PREF_SHOW_CPU = "show_cpu"
    const val PREF_SHOW_BATTERY = "show_battery"
    const val PREF_SHOW_TEMP = "show_temp"
    const val PREF_AUTO_START = "auto_start"
    const val PREF_VIBRATION = "vibration"
    const val PREF_DARK_THEME = "dark_theme"
    const val PREF_MONITORED_APPS = "monitored_apps"
    const val PREF_CONNECTION_TYPE = "connection_type"
    const val PREF_LAST_IP = "last_ip"
    const val PREF_LAST_PORT = "last_port"
    const val PREF_FIRST_RUN = "first_run"

    const val DEFAULT_UPDATE_INTERVAL = 500L
    const val DEFAULT_OVERLAY_OPACITY = 0.8f
    const val DEFAULT_OVERLAY_SIZE = 1
    const val DEFAULT_OVERLAY_POSITION = 0

    const val OVERLAY_POSITION_TOP_LEFT = 0
    const val OVERLAY_POSITION_TOP_RIGHT = 1
    const val OVERLAY_POSITION_BOTTOM_LEFT = 2
    const val OVERLAY_POSITION_BOTTOM_RIGHT = 3
    const val OVERLAY_POSITION_CENTER_TOP = 4
    const val OVERLAY_POSITION_CENTER_BOTTOM = 5

    const val OVERLAY_SIZE_SMALL = 0
    const val OVERLAY_SIZE_MEDIUM = 1
    const val OVERLAY_SIZE_LARGE = 2

    const val CONNECTION_TYPE_NONE = 0
    const val CONNECTION_TYPE_WIFI_DEBUG = 1
    const val CONNECTION_TYPE_SHIZUKU = 2

    const val ADB_DEFAULT_PORT = 5555
    const val ADB_PAIRING_TIMEOUT = 30000L
    const val ADB_CONNECTION_TIMEOUT = 10000L

    const val SHIZUKU_REQUEST_CODE = 100

    const val FPS_SAMPLE_SIZE = 60
    const val FPS_HISTORY_SIZE = 120

    const val PERMISSION_REQUEST_OVERLAY = 1001
    const val PERMISSION_REQUEST_USAGE = 1002
    const val PERMISSION_REQUEST_NOTIFICATION = 1003

    val UPDATE_INTERVALS = listOf(100L, 250L, 500L, 1000L)

    val OVERLAY_COLORS = listOf(
        0xFF1565C0.toInt(),
        0xFF4CAF50.toInt(),
        0xFFFFC107.toInt(),
        0xFFF44336.toInt(),
        0xFF9C27B0.toInt(),
        0xFF00BCD4.toInt(),
        0xFFFFFFFF.toInt()
    )
}
