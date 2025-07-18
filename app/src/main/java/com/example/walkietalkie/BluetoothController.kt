package com.example.walkietalkie

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.Key
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class BluetoothController(
    private val adapter: BluetoothAdapter,
    private val onStateChanged: (String) -> Unit,
    private val onAudioDataReceived: (ByteArray) -> Unit,
    private val onTextDataReceived: (String) -> Unit,
    private val onDeviceFound: (BluetoothDevice) -> Unit,
    private val onCallStateChanged: (String) -> Unit
) {
    private val appName = "WalkieTalkie"
    private val appUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var streamJob: Job? = null
    private var socket: BluetoothSocket? = null

    private val bleScanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    private var encryptionKey: Key? = null

    object MessageType {
        const val AUDIO_CHUNK: Byte = 0x01
        const val TEXT_MESSAGE: Byte = 0x02
        const val CALL_REQUEST: Byte = 0x03
        const val CALL_TERMINATE: Byte = 0x04
        const val GROUP_MESSAGE: Byte = 0x05
        const val FILE_TRANSFER: Byte = 0x06
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
        // TODO: Implement secure key exchange
        encryptionKey = EncryptionHelper.generateKey("This is a key123") // Placeholder key

        streamJob = scope.launch(Dispatchers.IO) {
            val dataInputStream = DataInputStream(socket?.inputStream)
            while (isActive) {
                try {
                    val messageType = dataInputStream.readByte()
                    val messageLength = dataInputStream.readInt()
                    if (messageLength > 0) {
                        val messagePayload = ByteArray(messageLength)
                        dataInputStream.readFully(messagePayload)
                        val decryptedPayload = encryptionKey?.let { EncryptionHelper.decrypt(messagePayload, it) }

                        when (messageType) {
                            MessageType.AUDIO_CHUNK -> decryptedPayload?.let { onAudioDataReceived(it) }
                            MessageType.TEXT_MESSAGE -> decryptedPayload?.let { onTextDataReceived(String(it)) }
                            MessageType.CALL_REQUEST -> onCallStateChanged("Incoming call...")
                            MessageType.CALL_TERMINATE -> onCallStateChanged("Call terminated.")
                            // TODO: Handle group messages and file transfers
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) { onStateChanged("Status: Disconnected") }
                    break
                }
            }
        }
    }

    fun sendMessage(messageType: Byte, data: ByteArray, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // TODO: Implement data compression
                val encryptedData = encryptionKey?.let { EncryptionHelper.encrypt(data, it) }
                encryptedData?.let {
                    val dataOutputStream = DataOutputStream(socket?.outputStream)
                    dataOutputStream.writeByte(messageType.toInt())
                    dataOutputStream.writeInt(it.size)
                    dataOutputStream.write(it)
                    dataOutputStream.flush()
                }
            } catch (e: IOException) {
                 withContext(Dispatchers.Main) { onStateChanged("Status: Send failed.") }
            }
        }
    }

    fun startBleScan() {
        if (scanCallback != null) {
            return // Scan already in progress
        }
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(appUuid))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { onDeviceFound(it) }
            }
        }
        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        scanCallback = null
    }

    // Placeholder for secure pairing
    fun securePair(device: BluetoothDevice) {
        // TODO: Implement secure pairing logic
    }

    // Placeholder for mesh networking
    fun forwardMessage(message: ByteArray) {
        // TODO: Implement mesh networking logic
    }

    // Placeholder for connection management
    fun prioritizeConnection(device: BluetoothDevice) {
        // TODO: Implement connection prioritization logic
    }

    // Placeholder for call quality optimization
    fun optimizeCallQuality() {
        // TODO: Implement call quality optimization logic
    }

    // Placeholder for group communication
    fun sendGroupMessage(message: String) {
        // TODO: Implement group communication logic
    }

    // Placeholder for file transfer
    fun sendFile(file: java.io.File) {
        // TODO: Implement file transfer logic
    }

    // Placeholder for self-destructing messages
    fun sendSelfDestructingMessage(message: String, timer: Long) {
        // TODO: Implement self-destructing messages
    }

    // Placeholder for anonymous communication
    fun sendAnonymousMessage(message: String) {
        // TODO: Implement anonymous communication
    }

    // Placeholder for adaptive quality
    fun setAdaptiveQuality(enabled: Boolean) {
        // TODO: Implement adaptive quality logic
    }

    // Placeholder for bandwidth management
    fun setBandwidthLimit(limit: Int) {
        // TODO: Implement bandwidth management logic
    }

    // Placeholder for store-and-forward messaging
    fun storeMessage(message: ByteArray) {
        // TODO: Implement store-and-forward messaging
    }

    // Placeholder for automatic network healing
    fun healNetwork() {
        // TODO: Implement automatic network healing
    }

    // Placeholder for hierarchical network structure
    fun createSubnet(subnetId: String) {
        // TODO: Implement hierarchical network structure
    }

    // Placeholder for load distribution
    fun distributeLoad() {
        // TODO: Implement load distribution
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
