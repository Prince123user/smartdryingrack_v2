package com.example.smartdryingrack.feature.lighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartdryingrack.data.hardware.rest.LightingApi
import com.example.smartdryingrack.data.hardware.rest.WifiRackController
import com.example.smartdryingrack.data.hardware.rest.WifiTransport
import com.example.smartdryingrack.data.repository.LightingRepository
import com.example.smartdryingrack.domain.model.LightingZone
import com.example.smartdryingrack.domain.model.Rgb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HouseLightsViewModel @Inject constructor(private val rack: WifiRackController, transport: WifiTransport) : ViewModel(), LightingActions {
    private val repository = LightingRepository(LightingApi(transport::openConnection), viewModelScope)
    private var active = false
    val state = repository.state
    init {
        viewModelScope.launch {
            rack.requestedSession.collect { if (active) repository.open(it?.address) }
        }
    }
    fun activate() { active = true; repository.open(rack.requestedSession.value?.address) }
    fun deactivate() { active = false; repository.stop() }
    override fun refresh() = repository.refresh()
    override fun power(zone: LightingZone, enabled: Boolean) = repository.power(zone, enabled)
    override fun brightness(zone: LightingZone, percent: Int) = repository.brightness(zone, percent)
    override fun color(zone: LightingZone, rgb: Rgb) = repository.color(zone, rgb)
    override fun retry(zone: LightingZone) = repository.retry(zone)
    override fun discard(zone: LightingZone) = repository.discard(zone)
}
