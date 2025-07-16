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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class BluetoothController(
    private val adapter: BluetoothAdapter,
    private val onStateChanged: (String) -> Unit,
    private val onAudioDataReceived: (ByteArray) -> Unit // MODIFIED: Renamed for clarity
) {
    private val appName = "WalkieTalkie"
    private val appUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var streamJob: Job? = null
    private var socket: BluetoothSocket? = null

    // ADDED: A simple protocol definition for different message types.
    // This is the first step towards integrating with a more complex protocol like bitchat.
    object MessageType {
        const val AUDIO_CHUNK: Byte = 0x01
        // Future types could include:
        // const val TEXT_MESSAGE: Byte = 0x02
        // const val CALL_REQUEST: Byte = 0x03
    }

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

        // MODIFIED: Switched to DataInputStream to handle a structured message protocol (Type-Length-Payload).
        // This is more robust than streaming raw bytes and is necessary for bitchat integration.
        streamJob = scope.launch(Dispatchers.IO) {
            val dataInputStream = DataInputStream(socket?.inputStream)
            while (isActive) {
                try {
                    // Read message structure: [TYPE: 1 byte][LENGTH: 4 bytes][PAYLOAD: N bytes]
                    val messageType = dataInputStream.readByte()
                    val messageLength = dataInputStream.readInt()
                    if (messageLength > 0) {
                        val messagePayload = ByteArray(messageLength)
                        dataInputStream.readFully(messagePayload)

                        when (messageType) {
                            MessageType.AUDIO_CHUNK -> {
                                onAudioDataReceived(messagePayload)
                            }
                            // A 'when' block allows for easily handling other message types in the future.
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) { onStateChanged("Status: Disconnected") }
                    break
                }
            }
        }
    }

    // MODIFIED: Renamed from sendData and updated to send structured messages.
    fun sendAudioData(data: ByteArray, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // Use DataOutputStream to easily write primitive types and byte arrays.
                val dataOutputStream = DataOutputStream(socket?.outputStream)
                // Write the message structure
                dataOutputStream.writeByte(MessageType.AUDIO_CHUNK.toInt())
                dataOutputStream.writeInt(data.size)
                dataOutputStream.write(data)
                dataOutputStream.flush() // Ensure the data is sent immediately.
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
