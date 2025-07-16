package com.example.walkietalkie

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class BluetoothController(
    private val adapter: BluetoothAdapter,
    private val onStateChanged: (String) -> Unit,
    private val onDataReceived: (ByteArray) -> Unit
) {
    private val appName = "WalkieTalkie"
    private val appUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var streamJob: Job? = null

    private var socket: BluetoothSocket? = null

    fun startServer(scope: CoroutineScope) {
        onStateChanged("Status: Starting server...")
        serverJob = scope.launch(Dispatchers.IO) {
            val serverSocket: BluetoothServerSocket? = adapter.listenUsingRfcommWithServiceRecord(appName, appUuid)
            try {
                withContext(Dispatchers.Main) { onStateChanged("Status: Waiting for connection...") }
                val clientSocket = serverSocket?.accept()
                serverSocket?.close()
                manageConnection(clientSocket, scope)
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { onStateChanged("Status: Server failed.") }
            }
        }
    }

    fun connectToServer(device: BluetoothDevice, scope: CoroutineScope) {
        onStateChanged("Status: Connecting to ${device.name}...")
        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val clientSocket = device.createRfcommSocketToServiceRecord(appUuid)
                clientSocket.connect()
                manageConnection(clientSocket, scope)
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { onStateChanged("Status: Connection failed.") }
            }
        }
    }

    private suspend fun manageConnection(btSocket: BluetoothSocket?, scope: CoroutineScope) {
        this.socket = btSocket
        withContext(Dispatchers.Main) { onStateChanged("Status: Connected") }

        streamJob = scope.launch(Dispatchers.IO) {
            val inputStream = socket?.inputStream
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val bytes = inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        onDataReceived(buffer.copyOf(bytes))
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) { onStateChanged("Status: Disconnected") }
                    break
                }
            }
        }
    }

    fun sendData(data: ByteArray, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                socket?.outputStream?.write(data)
            } catch (e: IOException) {
                 withContext(Dispatchers.Main) { onStateChanged("Status: Send failed.") }
            }
        }
    }

    fun stop() {
        try {
            serverJob?.cancel()
            clientJob?.cancel()
            streamJob?.cancel()
            socket?.close()
            socket = null
            onStateChanged("Status: Disconnected")
        } catch (e: IOException) {
            // Log error
        }
    }
}
