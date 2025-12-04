package com.hannsapp.fpscounter.services

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.data.ConnectionStatus
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuService private constructor(private val context: Context) {

    private val preferencesManager: PreferencesManager = HannsApplication.getInstance().preferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isShizukuAvailableAtomic = AtomicBoolean(false)
    private val hasShizukuPermissionAtomic = AtomicBoolean(false)
    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<ShizukuListener, Boolean>())
    private var userService: IShizukuUserService? = null
    private var isUserServiceBound = false

    interface ShizukuListener {
        fun onShizukuAvailabilityChanged(available: Boolean)
        fun onShizukuPermissionResult(granted: Boolean)
        fun onConnectionStatusChanged(status: ConnectionStatus)
    }

    interface IShizukuUserService {
        fun executeCommand(command: String): String
        fun destroy()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        isShizukuAvailableAtomic.set(true)
        checkPermission()
        notifyAvailabilityChanged(true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isShizukuAvailableAtomic.set(false)
        hasShizukuPermissionAtomic.set(false)
        notifyAvailabilityChanged(false)
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == Constants.SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            hasShizukuPermissionAtomic.set(granted)
            notifyPermissionResult(granted)
            if (granted) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_SHIZUKU
                notifyConnectionStatus(
                    ConnectionStatus(
                        isConnected = true,
                        connectionType = Constants.CONNECTION_TYPE_SHIZUKU
                    )
                )
            }
        }
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isUserServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            isUserServiceBound = false
        }
    }

    fun initialize() {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            if (Shizuku.pingBinder()) {
                isShizukuAvailableAtomic.set(true)
                checkPermission()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            unbindUserService()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        listeners.clear()
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean = isShizukuAvailableAtomic.get()

    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuAvailableAtomic.get()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                hasShizukuPermissionAtomic.set(true)
                true
            } else {
                hasShizukuPermissionAtomic.set(false)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkPermission() {
        try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            hasShizukuPermissionAtomic.set(granted)
            if (granted) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_SHIZUKU
            }
        } catch (e: Exception) {
            hasShizukuPermissionAtomic.set(false)
        }
    }

    fun requestPermission() {
        try {
            if (!isShizukuAvailableAtomic.get()) {
                notifyPermissionResult(false)
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                hasShizukuPermissionAtomic.set(true)
                notifyPermissionResult(true)
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // Rationale logic handled by UI
            }
            Shizuku.requestPermission(Constants.SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            notifyPermissionResult(false)
        }
    }

    fun getConnectionStatus(): ConnectionStatus {
        val available = isShizukuAvailableAtomic.get() && hasShizukuPermissionAtomic.get()
        return ConnectionStatus(
            isConnected = available,
            connectionType = if (available) Constants.CONNECTION_TYPE_SHIZUKU else Constants.CONNECTION_TYPE_NONE
        )
    }

    fun executeCommand(command: String, callback: (Boolean, String) -> Unit) {
        if (!isShizukuAvailableAtomic.get() || !hasShizukuPermissionAtomic.get()) {
            callback(false, "Shizuku unavailable")
            return
        }
        serviceScope.launch {
            try {
                val result = executePrivilegedCommand(command)
                withContext(Dispatchers.Main) {
                    callback(result.first, result.second)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun executePrivilegedCommand(command: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val cmdArray = arrayOf("sh", "-c", command)
                val process = Shizuku.newProcess(cmdArray, null, null)
                
                val outputDeferred = async(Dispatchers.IO) {
                    readStreamFully(process.inputStream)
                }
                val errorDeferred = async(Dispatchers.IO) {
                    readStreamFully(process.errorStream)
                }

                val exitCode = process.waitFor()
                val output = outputDeferred.await()
                val errorOutput = errorDeferred.await()

                if (exitCode == 0) {
                    Pair(true, output.trim())
                } else {
                    Pair(false, errorOutput.ifEmpty { output }.trim())
                }
            } catch (e: Exception) {
                Pair(false, e.message ?: "Execution failed")
            }
        }
    }

    private fun readStreamFully(inputStream: InputStream): String {
        return try {
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun executeShellCommand(command: String): String {
        if (!isShizukuAvailableAtomic.get() || !hasShizukuPermissionAtomic.get()) {
            return ""
        }
        return try {
            val cmdArray = arrayOf("sh", "-c", command)
            val process = Shizuku.newProcess(cmdArray, null, null)
            
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val output = reader.readText()
                process.waitFor()
                output.trim()
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getSurfaceFlingerFps(): Int {
        return try {
            val output = executeShellCommand("service call SurfaceFlinger 1013")
            parseFpsFromSurfaceFlinger(output)
        } catch (e: Exception) {
            0
        }
    }

    fun getFrameStats(packageName: String): String {
        return try {
            executeShellCommand("dumpsys gfxinfo $packageName framestats")
        } catch (e: Exception) {
            ""
        }
    }

    fun getTopActivity(): String {
        return try {
            val output = executeShellCommand("dumpsys activity activities | grep mResumedActivity")
            parseTopActivity(output)
        } catch (e: Exception) {
            ""
        }
    }

    fun getRunningApps(): List<String> {
        return try {
            val output = executeShellCommand("dumpsys activity recents | grep 'Recent #'")
            parseRunningApps(output)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFpsFromSurfaceFlinger(output: String): Int {
        return try {
            val hexPattern = Regex("Result:\\s*Parcel\\(([0-9a-fA-F]+)\\s+([0-9a-fA-F]+).*\\)")
            val match = hexPattern.find(output)
            if (match != null && match.groupValues.size >= 3) {
                val hexString = match.groupValues[2]
                val longValue = hexString.toLongOrNull(16) ?: 0L
                (longValue and 0xFFFFFFFF).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun parseTopActivity(output: String): String {
        return try {
            val componentPattern = Regex("([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)")
            val match = componentPattern.find(output)
            match?.groupValues?.getOrNull(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseRunningApps(output: String): List<String> {
        return try {
            val packagePattern = Regex("A=([a-zA-Z0-9_.]+)")
            packagePattern.findAll(output).mapNotNull { it.groupValues.getOrNull(1) }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun unbindUserService() {
        if (isUserServiceBound) {
            try {
                Shizuku.unbindUserService(
                    Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, ShizukuUserServiceImpl::class.java.name)
                    ),
                    userServiceConnection,
                    false
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isUserServiceBound = false
        }
    }

    fun addListener(listener: ShizukuListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ShizukuListener) {
        listeners.remove(listener)
    }

    private fun notifyAvailabilityChanged(available: Boolean) {
        handler.post {
            for (listener in listeners) {
                try {
                    listener.onShizukuAvailabilityChanged(available)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun notifyPermissionResult(granted: Boolean) {
        handler.post {
            for (listener in listeners) {
                try {
                    listener.onShizukuPermissionResult(granted)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun notifyConnectionStatus(status: ConnectionStatus) {
        handler.post {
            for (listener in listeners) {
                try {
                    listener.onConnectionStatusChanged(status)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    class ShizukuUserServiceImpl : IShizukuUserService {
        override fun executeCommand(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    val output = reader.readText()
                    process.waitFor()
                    output
                }
            } catch (e: Exception) {
                ""
            }
        }

        override fun destroy() {}
    }

    companion object {
        @Volatile
        private var instance: ShizukuService? = null

        fun getInstance(context: Context): ShizukuService {
            return instance ?: synchronized(this) {
                instance ?: ShizukuService(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getShizukuVersion(context: Context): Int {
            return try {
                if (Shizuku.pingBinder()) Shizuku.getVersion() else -1
            } catch (e: Exception) {
                -1
            }
        }

        fun isPreV11(): Boolean {
            return try {
                if (Shizuku.pingBinder()) Shizuku.getVersion() < 11 else true
            } catch (e: Exception) {
                true
            }
        }
    }
}
