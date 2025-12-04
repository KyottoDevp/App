package com.hannsapp.fpscounter.services

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.data.ConnectionStatus
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.InputStream
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
        Log.d(TAG, "Shizuku binder received")
        isShizukuAvailableAtomic.set(true)
        checkPermission()
        notifyAvailabilityChanged(true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        isShizukuAvailableAtomic.set(false)
        hasShizukuPermissionAtomic.set(false)
        notifyAvailabilityChanged(false)
        notifyConnectionStatus(
            ConnectionStatus(
                isConnected = false,
                connectionType = Constants.CONNECTION_TYPE_NONE
            )
        )
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == Constants.SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            hasShizukuPermissionAtomic.set(granted)
            Log.d(TAG, "Shizuku permission result: $granted")
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
            Log.d(TAG, "User service connected")
            isUserServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "User service disconnected")
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
            } else {
                isShizukuAvailableAtomic.set(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku listeners", e)
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            unbindUserService()
        } catch (e: Exception) {
            Log.e(TAG, "Error during Shizuku service cleanup", e)
        }
        listeners.clear()
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            val packageName = "moe.shizuku.privileged.api"
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
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
            if (!isShizukuAvailableAtomic.get()) {
                false
            } else {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                hasShizukuPermissionAtomic.set(granted)
                granted
            }
        } catch (e: Exception) {
            hasShizukuPermissionAtomic.set(false)
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
            Log.e(TAG, "Failed to check Shizuku permission", e)
        }
    }

    fun requestPermission() {
        try {
            if (!isShizukuAvailableAtomic.get()) {
                Log.w(TAG, "Requesting permission when Shizuku is not available.")
                notifyPermissionResult(false)
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                hasShizukuPermissionAtomic.set(true)
                notifyPermissionResult(true)
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.d(TAG, "Should show request permission rationale for Shizuku")
            }
            Shizuku.requestPermission(Constants.SHIZUKU_REQUEST_CODE)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to request Shizuku permission, is the activity running?", e)
            notifyPermissionResult(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            notifyPermissionResult(false)
        }
    }

    fun getConnectionStatus(): ConnectionStatus {
        val connected = isShizukuAvailableAtomic.get() && hasShizukuPermissionAtomic.get()
        return ConnectionStatus(
            isConnected = connected,
            connectionType = if (connected) Constants.CONNECTION_TYPE_SHIZUKU else Constants.CONNECTION_TYPE_NONE
        )
    }

    fun executeCommand(command: String, callback: (Boolean, String) -> Unit) {
        if (!isShizukuAvailableAtomic.get() || !hasShizukuPermissionAtomic.get()) {
            callback(false, "Shizuku is not available or permission not granted")
            return
        }
        serviceScope.launch {
            try {
                val result = executePrivilegedCommand(command)
                withContext(Dispatchers.Main) {
                    callback(result.first, result.second)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command execution scope failed", e)
                withContext(Dispatchers.Main) {
                    callback(false, e.message ?: "Unknown error during command execution")
                }
            }
        }
    }

    private suspend fun executePrivilegedCommand(command: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val binder = Shizuku.getBinder()
                if (binder == null) {
                    throw RemoteException("Shizuku binder is null")
                }

                val cmdArray = arrayOf("sh", "-c", command)
                process = ShizukuBinderWrapper(binder).newProcess(cmdArray, null, null)

                val outputDeferred = async { readStreamFully(process.inputStream) }
                val errorDeferred = async { readStreamFully(process.errorStream) }

                val exitCode = process.waitFor()
                val output = outputDeferred.await()
                val errorOutput = errorDeferred.await()

                if (exitCode == 0) {
                    Pair(true, output.trim())
                } else {
                    val errorMessage = errorOutput.ifEmpty { output }.trim()
                    Log.w(TAG, "Command '$command' failed with exit code $exitCode: $errorMessage")
                    Pair(false, errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Privileged command execution failed for: $command", e)
                Pair(false, e.message ?: "Execution failed with an exception")
            } finally {
                process?.destroy()
            }
        }
    }

    private fun readStreamFully(inputStream: InputStream): String {
        return try {
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read stream", e)
            ""
        }
    }

    fun executeShellCommand(command: String): String {
        if (!isShizukuAvailableAtomic.get() || !hasShizukuPermissionAtomic.get()) {
            return ""
        }
        var process: Process? = null
        return try {
            val binder = Shizuku.getBinder()
            if (binder == null) {
                return ""
            }

            val cmdArray = arrayOf("sh", "-c", command)
            process = ShizukuBinderWrapper(binder).newProcess(cmdArray, null, null)

            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed for: $command", e)
            ""
        } finally {
            process?.destroy()
        }
    }

    fun getSurfaceFlingerFps(): Int {
        try {
            val output = executeShellCommand("service call SurfaceFlinger 1013")
            if (output.isNotBlank()) {
                return parseFpsFromSurfaceFlinger(output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SurfaceFlinger FPS", e)
        }
        return 0
    }

    fun getFrameStats(packageName: String): String {
        try {
            return executeShellCommand("dumpsys gfxinfo $packageName framestats")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get frame stats for $packageName", e)
            return ""
        }
    }

    fun getTopActivity(): String {
        try {
            val output = executeShellCommand("dumpsys activity activities | grep mResumedActivity")
            return parseTopActivity(output)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get top activity", e)
            return ""
        }
    }

    fun getRunningApps(): List<String> {
        return try {
            val output = executeShellCommand("dumpsys activity recents | grep 'Recent #'")
            parseRunningApps(output)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running apps", e)
            emptyList()
        }
    }

    private fun parseFpsFromSurfaceFlinger(output: String): Int {
        return try {
            val hexPattern = Regex("Result:\\s*Parcel\\(.+\\s'([0-9a-fA-F]+)'.*\\)")
            val match = hexPattern.find(output)
            if (match != null && match.groupValues.size > 1) {
                val hexString = match.groupValues[1]
                val longValue = hexString.toLongOrNull(16) ?: 0L
                (longValue and 0xFFFFFFFF).toInt()
            } else {
                0
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Failed to parse hex string from SurfaceFlinger output", e)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse FPS from SurfaceFlinger", e)
            0
        }
    }

    private fun parseTopActivity(output: String): String {
        return try {
            val componentPattern = Regex("mResumedActivity:\\s*ActivityRecord\\{.*\\s([^/ ]+)/([^ ]+)\\s.*}")
            val match = componentPattern.find(output)
            match?.groupValues?.getOrNull(1) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse top activity", e)
            ""
        }
    }

    private fun parseRunningApps(output: String): List<String> {
        return try {
            val packagePattern = Regex("A=([a-zA-Z0-9_.]+)")
            packagePattern.findAll(output).mapNotNull { it.groupValues.getOrNull(1) }.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse running apps", e)
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
                Log.e(TAG, "Failed to unbind user service", e)
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
                    Log.e(TAG, "Listener failed during onShizukuAvailabilityChanged", e)
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
                    Log.e(TAG, "Listener failed during onShizukuPermissionResult", e)
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
                    Log.e(TAG, "Listener failed during onConnectionStatusChanged", e)
                }
            }
        }
    }

    class ShizukuUserServiceImpl : IShizukuUserService {
        override fun executeCommand(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                process.waitFor()
                output
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute command in user service", e)
                ""
            }
        }

        override fun destroy() {}
    }

    companion object {
        private const val TAG = "ShizukuService"

        @Volatile
        private var instance: ShizukuService? = null

        fun getInstance(context: Context): ShizukuService {
            return instance ?: synchronized(this) {
                instance ?: ShizukuService(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getShizukuVersion(): Int {
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
