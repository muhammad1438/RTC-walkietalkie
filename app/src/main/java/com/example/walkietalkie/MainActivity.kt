package com.example.walkietalkie

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 101
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private lateinit var bluetoothController: BluetoothController
    private lateinit var audioHandler: AudioHandler

    private lateinit var scanButton: Button
    private lateinit var pushToTalkButton: Button
    private lateinit var statusIndicator: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var deviceListAdapter: DeviceListAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanButton = findViewById(R.id.scan_button)
        pushToTalkButton = findViewById(R.id.push_to_talk_button)
        statusIndicator = findViewById(R.id.status_indicator)
        devicesRecyclerView = findViewById(R.id.devices_recycler_view)

        deviceListAdapter = DeviceListAdapter { device ->
            bluetoothController.connectToServer(device.address)
        }
        devicesRecyclerView.adapter = deviceListAdapter
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            init()
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                init()
            } else {
                // Permissions not granted, handle appropriately
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        bluetoothController = BluetoothController(this, lifecycleScope)
        audioHandler = AudioHandler(this, lifecycleScope)

        bluetoothController.onDeviceFound = { device ->
            deviceListAdapter.addDevice(device)
        }

        bluetoothController.onStateChanged = { state ->
            runOnUiThread {
                statusIndicator.text = state
            }
        }

        bluetoothController.onConnected = { socket ->
            audioHandler.startPlaying(socket.inputStream)
        }

        scanButton.setOnClickListener {
            deviceListAdapter.clearDevices()
            bluetoothController.startDiscovery()
        }

        pushToTalkButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bluetoothController.onConnected = { socket ->
                        audioHandler.startRecording(socket.outputStream)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    audioHandler.stopRecording()
                }
            }
            true
        }

        lifecycleScope.launchWhenStarted {
            bluetoothController.startServer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothController.close()
        audioHandler.stopRecording()
        audioHandler.stopPlaying()
    }
}
