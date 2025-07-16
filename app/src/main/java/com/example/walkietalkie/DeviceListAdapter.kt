package com.example.walkietalkie

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(private val onDeviceClick: (Device) -> Unit) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<Device>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_list_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

    fun addDevice(device: Device) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyDataSetChanged()
        }
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.device_name)
        private val addressTextView: TextView = itemView.findViewById(R.id.device_address)

        fun bind(device: Device) {
            nameTextView.text = device.name
            addressTextView.text = device.address
            itemView.setOnClickListener { onDeviceClick(device) }
        }
    }
}
