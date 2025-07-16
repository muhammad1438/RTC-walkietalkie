package com.example.walkietalkie

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("MissingPermission") // Permissions are checked and handled
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var pttButton: Button
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    // Bluetooth & Audio
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private lateinit var bluetoothController: BluetoothController
    private lateinit var audioHandler: AudioHandler
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var chatAdapter: ChatAdapter
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // --- Activity Lifecycle & Permissions ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupUI()
        requestAppPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(discoveryReceiver)
        bluetoothController.stop()
        audioHandler.release()
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            initialize()
        } else {
            Toast.makeText(this, "Permissions required for app to function.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestAppPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.RECORD_AUDIO)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initialize()
        }
    }

    // --- App Initialization ---

    private fun initialize() {
        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth not supported on this device"
            return
        }

        // Init Handlers
        audioHandler = AudioHandler(lifecycleScope) { data ->
            bluetoothController.sendMessage(BluetoothController.MessageType.AUDIO_CHUNK, data, lifecycleScope)
        }
        bluetoothController = BluetoothController(bluetoothAdapter!!, ::updateStatus, { data ->
            audioHandler.playAudio(data)
        }, { text ->
            runOnUiThread {
                chatAdapter.addMessage("Friend: $text")
            }
        })

        // Setup RecyclerViews
        deviceListAdapter = DeviceListAdapter(discoveredDevices) { device ->
            bluetoothController.connectToServer(device, lifecycleScope)
        }
        devicesRecyclerView.adapter = deviceListAdapter
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter()
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)


        // Start listening for connections immediately
        bluetoothController.startServer(lifecycleScope)

        // Register broadcast receiver for device discovery
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryReceiver, filter)
    }

    // --- UI Setup & Listeners ---

    private fun setupUI() {
        statusText = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        pttButton = findViewById(R.id.push_to_talk_button)
        devicesRecyclerView = findViewById(R.id.devices_recycler_view)
        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)

        scanButton.setOnClickListener {
            discoveredDevices.clear()
            deviceListAdapter.notifyDataSetChanged()
            bluetoothAdapter?.startDiscovery()
            updateStatus("Status: Scanning...")
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotEmpty()) {
                bluetoothController.sendMessage(
                    BluetoothController.MessageType.TEXT_MESSAGE,
                    message.toByteArray(),
                    lifecycleScope
                )
                chatAdapter.addMessage("Me: $message")
                messageInput.text.clear()
            }
        }

        pttButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    audioHandler.startRecording()
                    true // Consume event
                }
                MotionEvent.ACTION_UP -> {
                    audioHandler.stopRecording()
                    true // Consume event
                }
                else -> false
            }
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        if (message == "Status: Connected") {
            pttButton.isEnabled = true
            scanButton.visibility = View.GONE
            devicesRecyclerView.visibility = View.GONE
            chatRecyclerView.visibility = View.VISIBLE
        } else {
            pttButton.isEnabled = false
            scanButton.visibility = View.VISIBLE
            devicesRecyclerView.visibility = View.VISIBLE
            chatRecyclerView.visibility = View.GONE
        }
    }

    // --- Bluetooth Discovery ---

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && device.name != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                    deviceListAdapter.notifyItemInserted(discoveredDevices.size - 1)
                }
            }
        }
    }
}
