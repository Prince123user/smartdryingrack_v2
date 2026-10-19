package com.example.smartdryingrack.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdryingrack.data.hardware.rest.WifiRackController
import com.example.smartdryingrack.data.repository.DevicePreferences
import com.example.smartdryingrack.data.repository.RackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val rack: RackRepository,
    private val controller: WifiRackController,
    private val preferences: DevicePreferences
) : ViewModel() {
    val connection = rack.connection
    val rackState = rack.rackState
    val sensorData = rack.sensorData
    val address = preferences.address
    val firmware = controller.firmware
    val message = controller.message
    val failedAttempts = controller.failedAttempts
    private val _validation = MutableStateFlow<String?>(null)
    val validation = _validation.asStateFlow()
    fun connect(address: String) {
        try {
            preferences.saveAddress(address)
            _validation.value = null
            reconnect()
        } catch (e: IllegalArgumentException) { _validation.value = e.message }
    }
    fun reconnect() { viewModelScope.launch { rack.connect() } }
    fun disconnect() { viewModelScope.launch { controller.disconnect() } }
}
