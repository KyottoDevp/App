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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class ShizukuService private constructor(private val context: Context) {

    private val preferencesManager: PreferencesManager = HannsApplication.getInstance().preferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isShizukuAvailable = false
    private var hasShizukuPermission = false
    
    private val listeners = mutableSetOf<ShizukuListener>()
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
        isShizukuAvailable = true
        checkPermission()
        notifyAvailabilityChanged(true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        isShizukuAvailable = false
        hasShizukuPermission = false
        notifyAvailabilityChanged(false)
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == Constants.SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            hasShizukuPermission = granted
            Log.d(TAG, "Shizuku permission result: $granted")
            notifyPermissionResult(granted)
            
            if (granted) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_SHIZUKU
                notifyConnectionStatus(ConnectionStatus(
                    isConnected = true,
                    connectionType = Constants.CONNECTION_TYPE_SHIZUKU
                ))
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
                isShizukuAvailable = true
                checkPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku", e)
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            unbindUserService()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
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

    fun isShizukuAvailable(): Boolean = isShizukuAvailable

    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuAvailable) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun checkPermission() {
        try {
            hasShizukuPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (hasShizukuPermission) {
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_SHIZUKU
            }
        } catch (e: Exception) {
            hasShizukuPermission = false
        }
    }

    fun requestPermission() {
        try {
            if (!isShizukuAvailable) {
                notifyPermissionResult(false)
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                hasShizukuPermission = true
                notifyPermissionResult(true)
                return
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.d(TAG, "Should show permission rationale")
            }

            Shizuku.requestPermission(Constants.SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request permission", e)
            notifyPermissionResult(false)
        }
    }

    fun getConnectionStatus(): ConnectionStatus {
        val available = isShizukuAvailable && hasShizukuPermission
        return ConnectionStatus(
            isConnected = available,
            connectionType = if (available) Constants.CONNECTION_TYPE_SHIZUKU else Constants.CONNECTION_TYPE_NONE
        )
    }

    fun executeCommand(command: String, callback: (Boolean, String) -> Unit) {
        if (!isShizukuAvailable || !hasShizukuPermission) {
            callback(false, "Shizuku not available or permission not granted")
            return
        }

        serviceScope.launch {
            try {
                val result = executePrivilegedCommand(command)
                handler.post {
                    callback(result.first, result.second)
                }
            } catch (e: Exception) {
                handler.post {
                    callback(false, e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun executePrivilegedCommand(command: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                reader.close()

                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                errorReader.close()

                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    Pair(true, output.toString().trim())
                } else {
                    Pair(false, errorOutput.toString().ifEmpty { output.toString() }.trim())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed", e)
                Pair(false, e.message ?: "Execution failed")
            }
        }
    }

    fun executeShellCommand(command: String): String {
        if (!isShizukuAvailable || !hasShizukuPermission) {
            return ""
        }

        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            ""
        }
    }

    fun getSurfaceFlingerFps(): Int {
        return try {
            val output = executeShellCommand("service call SurfaceFlinger 1013")
            parseFpsFromSurfaceFlinger(output)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SurfaceFlinger FPS", e)
            0
        }
    }

    fun getFrameStats(packageName: String): String {
        return try {
            executeShellCommand("dumpsys gfxinfo $packageName framestats")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get frame stats", e)
            ""
        }
    }

    fun getTopActivity(): String {
        return try {
            val output = executeShellCommand("dumpsys activity activities | grep mResumedActivity")
            parseTopActivity(output)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get top activity", e)
            ""
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
            val regex = Regex("Result:\\s*Parcel\\(([0-9a-f]+)\\s+([0-9a-f]+).*\\)")
            val match = regex.find(output)
            if (match != null) {
                val value = match.groupValues[2].toLong(16)
                (value and 0xFFFFFFFF).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun parseTopActivity(output: String): String {
        return try {
            val regex = Regex("([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)")
            val match = regex.find(output)
            match?.groupValues?.getOrNull(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseRunningApps(output: String): List<String> {
        return try {
            val regex = Regex("A=([a-zA-Z0-9_.]+)")
            regex.findAll(output).map { it.groupValues[1] }.toList()
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
            listeners.forEach { it.onShizukuAvailabilityChanged(available) }
        }
    }

    private fun notifyPermissionResult(granted: Boolean) {
        handler.post {
            listeners.forEach { it.onShizukuPermissionResult(granted) }
        }
    }

    private fun notifyConnectionStatus(status: ConnectionStatus) {
        handler.post {
            listeners.forEach { it.onConnectionStatusChanged(status) }
        }
    }

    class ShizukuUserServiceImpl : IShizukuUserService {
        override fun executeCommand(command: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                reader.close()
                process.waitFor()
                output.toString()
            } catch (e: Exception) {
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

        fun getShizukuVersion(context: Context): Int {
            return try {
                Shizuku.getVersion()
            } catch (e: Exception) {
                -1
            }
        }

        fun isPreV11(): Boolean {
            return try {
                Shizuku.getVersion() < 11
            } catch (e: Exception) {
                true
            }
        }
    }
}
