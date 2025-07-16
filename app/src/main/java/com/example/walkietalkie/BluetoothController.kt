package com.example.walkietalkie

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

private const val NAME = "WalkieTalkie"
private val MY_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

data class Device(val name: String, val address: String)

class BluetoothController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null

    var onDeviceFound: ((Device) -> Unit)? = null
    var onStateChanged: ((String) -> Unit)? = null
    var onConnected: ((BluetoothSocket) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        onDeviceFound?.invoke(Device(it.name ?: "Unknown", it.address))
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothAdapter?.cancelDiscovery()
        context.unregisterReceiver(receiver)
    }

    fun startServer() {
        onStateChanged?.invoke("Listening for connections...")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@launch
                }
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
                val socket = serverSocket?.accept()
                socket?.let {
                    clientSocket = it
                    onStateChanged?.invoke("Connected")
                    onConnected?.invoke(it)
                    serverSocket?.close()
                }
            } catch (e: IOException) {
                onStateChanged?.invoke("Could not start server")
            }
        }
    }

    fun connectToServer(address: String) {
        onStateChanged?.invoke("Connecting...")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@launch
                }
                val device = bluetoothAdapter?.getRemoteDevice(address)
                clientSocket = device?.createRfcommSocketToServiceRecord(MY_UUID)
                clientSocket?.connect()
                onStateChanged?.invoke("Connected")
                clientSocket?.let { onConnected?.invoke(it) }
            } catch (e: IOException) {
                onStateChanged?.invoke("Connection failed")
            }
        }
    }

    fun close() {
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
