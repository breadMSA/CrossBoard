package com.bread.crossboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bread.crossboard.R
import com.bread.crossboard.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: MainViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        setupListeners()
        observeViewModel()
    }
    
    private fun setupListeners() {
        binding.saveSettingsButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.settingsState.collectLatest { state ->
                updateUI(state)
            }
        }
    }
    
    private fun updateUI(state: MainViewModel.SettingsState) {
        binding.serverPortEditText.setText(state.serverPort.toString())
        binding.deviceNameEditText.setText(state.deviceName)
        binding.autoStartSwitch.isChecked = state.autoStartOnBoot
        binding.notificationsSwitch.isChecked = state.showNotifications
    }
    
    private fun saveSettings() {
        val serverPortText = binding.serverPortEditText.text.toString()
        val deviceName = binding.deviceNameEditText.text.toString()
        
        if (serverPortText.isBlank() || deviceName.isBlank()) {
            Toast.makeText(requireContext(), R.string.settings_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }
        
        val serverPort = serverPortText.toIntOrNull()
        if (serverPort == null || serverPort < 1024 || serverPort > 65535) {
            Toast.makeText(requireContext(), R.string.settings_invalid_port, Toast.LENGTH_SHORT).show()
            return
        }
        
        val settings = MainViewModel.SettingsState(
            serverPort = serverPort,
            deviceName = deviceName,
            autoStartOnBoot = binding.autoStartSwitch.isChecked,
            showNotifications = binding.notificationsSwitch.isChecked
        )
        
        viewModel.saveSettings(settings)
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 