package com.example.smartdryingrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdryingrack.data.repository.RackRepository
import com.example.smartdryingrack.data.repository.SettingsRepository
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.SensorData
import com.example.smartdryingrack.domain.model.WeatherData
import com.example.smartdryingrack.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.CommandSource
import com.example.smartdryingrack.domain.model.CommandResult

data class HomeUiState(
    val sensorData: SensorData? = null,
    val rackState: RackState = RackState.UNKNOWN,
    val rackMode: RackMode = RackMode.MANUAL,
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val rainConfirmed: Boolean = false,
    val weather: WeatherData? = null,
    val dryThreshold: Int = 90
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val rack: RackRepository,
    weather: WeatherRepository,
    settings: SettingsRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun setAutoMode(enabled: Boolean) { rack.setMode(if (enabled) RackMode.AUTO else RackMode.MANUAL) }

    fun retract() { viewModelScope.launch {
        _message.value = when (rack.send(RackAction.RETRACT, CommandSource.MANUAL)) {
            CommandResult.Accepted -> "Retraction accepted. Waiting for telemetry."
            is CommandResult.Rejected -> "Retraction unavailable. Check connection, mode and rack status in Controls."
        }
    } }

    private val base = combine(
        rack.sensorData, rack.rackState, rack.mode, rack.connection, rack.rainConfirmed
    ) { sensor, state, mode, conn, rain ->
        HomeUiState(sensorData = sensor, rackState = state, rackMode = mode, connection = conn, rainConfirmed = rain)
    }

    val uiState: StateFlow<HomeUiState> = combine(base, weather.forecast, settings.settings) { b, w, s ->
        b.copy(weather = w, dryThreshold = s.dryThresholdPercent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
