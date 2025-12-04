package com.hannsapp.fpscounter.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.data.ConnectionStatus
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.utils.Constants
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class AdbConnectionService : Service() {

    private val binder = AdbConnectionBinder()
    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var connectionJob: Job? = null
    private var isConnected = false
    private var currentIp = ""
    private var currentPort = Constants.ADB_DEFAULT_PORT
    
    private val listeners = mutableSetOf<ConnectionListener>()

    interface ConnectionListener {
        fun onConnectionStatusChanged(status: ConnectionStatus)
        fun onPairingResult(success: Boolean, message: String)
        fun onConnectionResult(success: Boolean, message: String)
    }

    inner class AdbConnectionBinder : Binder() {
        fun getService(): AdbConnectionService = this@AdbConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = HannsApplication.getInstance().preferencesManager
        currentIp = preferencesManager.lastIp
        currentPort = preferencesManager.lastPort
        initializeShell()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAIR -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                val code = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: return START_NOT_STICKY
                pair(ip, port, code)
            }
            ACTION_CONNECT -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, Constants.ADB_DEFAULT_PORT)
                connect(ip, port)
            }
            ACTION_DISCONNECT -> {
                disconnect()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        super.onDestroy()
    }

    private fun initializeShell() {
        if (!Shell.isAppGrantedRoot()) {
            Log.d(TAG, "Root access not available, using standard shell")
        }
    }

    fun pair(ip: String, port: Int, code: String) {
        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            try {
                notifyListeners(ConnectionStatus(
                    isConnected = false,
                    connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG,
                    ipAddress = ip,
                    port = port,
                    errorMessage = "Pairing..."
                ))

                val result = withTimeoutOrNull(Constants.ADB_PAIRING_TIMEOUT) {
                    executePairCommand(ip, port, code)
                }

                val success = result == true
                val message = if (success) {
                    "Pairing successful"
                } else {
                    "Pairing failed or timed out"
                }

                handler.post {
                    listeners.forEach { it.onPairingResult(success, message) }
                }

                if (success) {
                    preferencesManager.lastIp = ip
                    preferencesManager.lastPort = Constants.ADB_DEFAULT_PORT
                }

            } catch (e: Exception) {
                Log.e(TAG, "Pairing error", e)
                handler.post {
                    listeners.forEach { it.onPairingResult(false, e.message ?: "Unknown error") }
                }
            }
        }
    }

    fun connect(ip: String, port: Int) {
        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            try {
                notifyListeners(ConnectionStatus(
                    isConnected = false,
                    connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG,
                    ipAddress = ip,
                    port = port,
                    errorMessage = "Connecting..."
                ))

                val reachable = isHostReachable(ip, port)
                if (!reachable) {
                    val status = ConnectionStatus(
                        isConnected = false,
                        connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG,
                        ipAddress = ip,
                        port = port,
                        errorMessage = "Host not reachable"
                    )
                    notifyListeners(status)
                    handler.post {
                        listeners.forEach { it.onConnectionResult(false, "Host not reachable") }
                    }
                    return@launch
                }

                val result = withTimeoutOrNull(Constants.ADB_CONNECTION_TIMEOUT) {
                    executeConnectCommand(ip, port)
                }

                val success = result == true
                isConnected = success
                currentIp = if (success) ip else ""
                currentPort = if (success) port else Constants.ADB_DEFAULT_PORT

                if (success) {
                    preferencesManager.lastIp = ip
                    preferencesManager.lastPort = port
                    preferencesManager.connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG
                }

                val status = ConnectionStatus(
                    isConnected = success,
                    connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG,
                    ipAddress = ip,
                    port = port,
                    errorMessage = if (success) null else "Connection failed"
                )
                notifyListeners(status)

                val message = if (success) "Connected successfully" else "Connection failed"
                handler.post {
                    listeners.forEach { it.onConnectionResult(success, message) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                isConnected = false
                val status = ConnectionStatus(
                    isConnected = false,
                    connectionType = Constants.CONNECTION_TYPE_WIFI_DEBUG,
                    ipAddress = ip,
                    port = port,
                    errorMessage = e.message
                )
                notifyListeners(status)
                handler.post {
                    listeners.forEach { it.onConnectionResult(false, e.message ?: "Unknown error") }
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            try {
                if (currentIp.isNotEmpty()) {
                    executeDisconnectCommand(currentIp, currentPort)
                }

                isConnected = false
                currentIp = ""
                currentPort = Constants.ADB_DEFAULT_PORT
                preferencesManager.connectionType = Constants.CONNECTION_TYPE_NONE

                val status = ConnectionStatus(
                    isConnected = false,
                    connectionType = Constants.CONNECTION_TYPE_NONE
                )
                notifyListeners(status)

                handler.post {
                    listeners.forEach { it.onConnectionResult(true, "Disconnected") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
            }
        }
    }

    fun isConnected(): Boolean = isConnected

    fun getConnectionStatus(): ConnectionStatus {
        return ConnectionStatus(
            isConnected = isConnected,
            connectionType = if (isConnected) Constants.CONNECTION_TYPE_WIFI_DEBUG else Constants.CONNECTION_TYPE_NONE,
            ipAddress = currentIp,
            port = currentPort
        )
    }

    fun addListener(listener: ConnectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }

    private suspend fun isHostReachable(ip: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 3000)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun executePairCommand(ip: String, port: Int, code: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (Shell.isAppGrantedRoot() == true) {
                    executeRootPairCommand(ip, port, code)
                } else {
                    executeShellPairCommand(ip, port, code)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pair command failed", e)
                false
            }
        }
    }

    private fun executeRootPairCommand(ip: String, port: Int, code: String): Boolean {
        return try {
            val command = "echo '$code' | adb pair $ip:$port"
            val result = Shell.cmd(command).exec()
            result.isSuccess && result.out.any { 
                it.contains("Successfully paired", ignoreCase = true) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root pair command failed", e)
            false
        }
    }

    private fun executeShellPairCommand(ip: String, port: Int, code: String): Boolean {
        return try {
            val process = ProcessBuilder()
                .command("sh", "-c", "echo '$code' | adb pair $ip:$port")
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()

            val exitCode = process.waitFor()
            val outputStr = output.toString()

            exitCode == 0 && outputStr.contains("Successfully paired", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Shell pair command failed", e)
            false
        }
    }

    private suspend fun executeConnectCommand(ip: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (Shell.isAppGrantedRoot() == true) {
                    executeRootConnectCommand(ip, port)
                } else {
                    executeShellConnectCommand(ip, port)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connect command failed", e)
                false
            }
        }
    }

    private fun executeRootConnectCommand(ip: String, port: Int): Boolean {
        return try {
            val result = Shell.cmd("adb connect $ip:$port").exec()
            result.isSuccess && result.out.any { 
                it.contains("connected", ignoreCase = true) && 
                !it.contains("unable", ignoreCase = true) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root connect command failed", e)
            false
        }
    }

    private fun executeShellConnectCommand(ip: String, port: Int): Boolean {
        return try {
            val process = ProcessBuilder()
                .command("adb", "connect", "$ip:$port")
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()

            val exitCode = process.waitFor()
            val outputStr = output.toString()

            exitCode == 0 && 
            outputStr.contains("connected", ignoreCase = true) && 
            !outputStr.contains("unable", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Shell connect command failed", e)
            false
        }
    }

    private suspend fun executeDisconnectCommand(ip: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (Shell.isAppGrantedRoot() == true) {
                    val result = Shell.cmd("adb disconnect $ip:$port").exec()
                    result.isSuccess
                } else {
                    val process = ProcessBuilder()
                        .command("adb", "disconnect", "$ip:$port")
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor() == 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect command failed", e)
                false
            }
        }
    }

    fun executeAdbCommand(command: String, callback: (Boolean, String) -> Unit) {
        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (Shell.isAppGrantedRoot() == true) {
                        val shellResult = Shell.cmd("adb $command").exec()
                        Pair(shellResult.isSuccess, shellResult.out.joinToString("\n"))
                    } else {
                        val process = ProcessBuilder()
                            .command("adb", *command.split(" ").toTypedArray())
                            .redirectErrorStream(true)
                            .start()

                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            output.append(line).append("\n")
                        }
                        reader.close()

                        val exitCode = process.waitFor()
                        Pair(exitCode == 0, output.toString())
                    }
                }

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

    fun executeShellCommand(command: String): String {
        return try {
            if (Shell.isAppGrantedRoot() == true) {
                val result = Shell.cmd(command).exec()
                result.out.joinToString("\n")
            } else {
                val process = ProcessBuilder()
                    .command("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                reader.close()
                process.waitFor()
                output.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            ""
        }
    }

    private fun notifyListeners(status: ConnectionStatus) {
        handler.post {
            listeners.forEach { it.onConnectionStatusChanged(status) }
        }
    }

    companion object {
        private const val TAG = "AdbConnectionService"

        const val ACTION_PAIR = "com.hannsapp.fpscounter.action.ADB_PAIR"
        const val ACTION_CONNECT = "com.hannsapp.fpscounter.action.ADB_CONNECT"
        const val ACTION_DISCONNECT = "com.hannsapp.fpscounter.action.ADB_DISCONNECT"

        const val EXTRA_IP = "extra_ip"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PAIRING_CODE = "extra_pairing_code"

        fun pair(context: Context, ip: String, port: Int, code: String) {
            val intent = Intent(context, AdbConnectionService::class.java).apply {
                action = ACTION_PAIR
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PAIRING_CODE, code)
            }
            context.startService(intent)
        }

        fun connect(context: Context, ip: String, port: Int) {
            val intent = Intent(context, AdbConnectionService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_PORT, port)
            }
            context.startService(intent)
        }

        fun disconnect(context: Context) {
            val intent = Intent(context, AdbConnectionService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
