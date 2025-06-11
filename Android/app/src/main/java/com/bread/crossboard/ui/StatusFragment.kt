package com.bread.crossboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bread.crossboard.CrossBoardApplication
import com.bread.crossboard.MainActivity
import com.bread.crossboard.R
import com.bread.crossboard.databinding.FragmentStatusBinding
import com.bread.crossboard.network.NetworkManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatusFragment : Fragment() {
    
    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceAdapter: DeviceAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        setupDevicesList()
        setupListeners()
        observeViewModel()
        displayIpAddresses()
    }
    
    private fun setupDevicesList() {
        deviceAdapter = DeviceAdapter { device ->
            onDeviceSyncClick(device)
        }
        
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun onDeviceSyncClick(device: NetworkManager.DeviceInfo) {
        viewModel.syncWithDevice(device)
    }
    
    private fun setupListeners() {
        binding.startServiceButton.setOnClickListener {
            viewModel.startService()
        }
        
        binding.stopServiceButton.setOnClickListener {
            viewModel.stopService()
        }
        
        binding.scanNetworkButton.setOnClickListener {
            viewModel.scanNetwork()
        }
        
        binding.connectButton?.setOnClickListener {
            val ipAddress = binding.ipAddressEditText?.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                viewModel.testDirectConnection(ipAddress)
            } else {
                Toast.makeText(requireContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Add TCP connection button
        binding.tcpConnectButton?.setOnClickListener {
            val ipAddress = binding.ipAddressEditText?.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                viewModel.sendClipboardViaTcp(ipAddress)
            } else {
                Toast.makeText(requireContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUI(state)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectedDevices.collect { devices ->
                updateDevicesList(devices)
            }
        }
    }
    
    private fun updateDevicesList(devices: List<NetworkManager.DeviceInfo>) {
        deviceAdapter.submitList(devices)
        
        // Show/hide views based on whether devices are available
        if (devices.isEmpty()) {
            binding.devicesRecyclerView.visibility = View.GONE
            binding.noDevicesText.visibility = View.VISIBLE
            binding.noDevicesText.text = "No Windows PCs found on your network.\nMake sure the CrossBoard app is running on your PC."
        } else {
            binding.devicesRecyclerView.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.GONE
            
            // Also update the count text to be more specific
            binding.devicesCountText.text = "${devices.size} Windows PC(s) found"
        }
    }
    
    private fun updateUI(state: MainViewModel.UiState) {
        // Update service status text with color
        binding.serviceStatusText.text = if (state.isServiceRunning) {
            binding.serviceStatusText.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            "Running"
        } else {
            binding.serviceStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            "Stopped"
        }
        
        // Update connection status text with color
        binding.connectionStatusText.text = state.connectionStatus
        binding.connectionStatusText.setTextColor(
            if (state.connectionStatus.equals("Connected", ignoreCase = true)) {
                resources.getColor(android.R.color.holo_green_dark)
            } else {
                resources.getColor(android.R.color.holo_red_dark)
            }
        )
        
        binding.devicesCountText.text = if (state.connectedDevices > 0) {
            "${state.connectedDevices} device(s) found"
        } else {
            "No devices connected"
        }
        
        // Update button states
        binding.startServiceButton.isEnabled = !state.isServiceRunning
        binding.stopServiceButton.isEnabled = state.isServiceRunning
        binding.scanNetworkButton.isEnabled = state.isServiceRunning
        
        // Always enable the connect button for direct connections
        binding.connectButton?.isEnabled = true
        binding.tcpConnectButton?.isEnabled = true
    }
    
    private fun displayIpAddresses() {
        val networkManager = (requireActivity().application as CrossBoardApplication).networkManager
        val ipAddresses = networkManager.getAllLocalIpAddresses()
        
        if (ipAddresses.isNotEmpty()) {
            binding.ipAddressesText.text = ipAddresses.joinToString("\n")
        } else {
            binding.ipAddressesText.text = "No IP addresses found. Make sure you're connected to a network."
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 