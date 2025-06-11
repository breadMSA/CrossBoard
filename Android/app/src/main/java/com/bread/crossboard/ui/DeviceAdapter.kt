package com.bread.crossboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bread.crossboard.databinding.ItemDeviceBinding
import com.bread.crossboard.network.NetworkManager

class DeviceAdapter(
    private val onSyncClick: (NetworkManager.DeviceInfo) -> Unit
) : ListAdapter<NetworkManager.DeviceInfo, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: NetworkManager.DeviceInfo) {
            binding.deviceNameText.text = device.deviceName
            binding.deviceIpText.text = "IP: ${device.ipAddress}"
            
            binding.syncButton.setOnClickListener {
                onSyncClick(device)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<NetworkManager.DeviceInfo>() {
        override fun areItemsTheSame(oldItem: NetworkManager.DeviceInfo, newItem: NetworkManager.DeviceInfo): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(oldItem: NetworkManager.DeviceInfo, newItem: NetworkManager.DeviceInfo): Boolean {
            return oldItem.deviceId == newItem.deviceId && 
                   oldItem.deviceName == newItem.deviceName &&
                   oldItem.ipAddress == newItem.ipAddress
        }
    }
} 