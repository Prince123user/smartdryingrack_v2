package com.example.smartdryingrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.smartdryingrack.core.navigation.SmartDryingRackRoot
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.smartdryingrack.data.hardware.rest.WifiRackController

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var rackConnection: WifiRackController

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { rackConnection.resumeRequestedConnection() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmartDryingRackRoot() }
    }
}
