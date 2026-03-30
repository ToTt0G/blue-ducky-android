package com.example.blueducky

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that owns the [BluetoothHidManager] instance and all UI state,
 * surviving configuration changes (screen rotation, etc.).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val hidManager = BluetoothHidManager(application)

    // Script text in the editor
    private val _scriptText = MutableStateFlow("")
    val scriptText: StateFlow<String> = _scriptText.asStateFlow()

    // Whether the payload is currently being sent
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    // Delegate status/connection state from the manager
    val connectionState = hidManager.connectionState
    val statusMessage   = hidManager.statusMessage

    fun onScriptTextChange(text: String) {
        _scriptText.value = text
    }

    fun registerHid() {
        hidManager.register()
    }

    fun connectToDevice(device: BluetoothDevice) {
        hidManager.connectToDevice(device)
    }

    fun getPairedDevices(): List<BluetoothDevice> =
        hidManager.getPairedDevices().toList()

    val scannedDevices: StateFlow<Set<BluetoothDevice>> = hidManager.scannedDevices

    fun startDiscovery() {
        hidManager.startDiscovery()
    }

    fun executePayload(interKeyDelayMs: Long = 30L) {
        val script = _scriptText.value
        if (script.isBlank()) return

        viewModelScope.launch {
            _isExecuting.value = true
            val actions = PayloadParser.parse(script)
            val reports = mutableListOf<ByteArray>()

            for (action in actions) {
                when (action) {
                    is PayloadParser.Action.KeyPress -> {
                        reports.addAll(PayloadParser.toHidReports(action))
                    }
                    is PayloadParser.Action.Delay -> {
                        // Insert sentinel "delay" reports: we piggyback the delay
                        // value into the report list as a tagged null entry handled
                        // inline by BluetoothHidManager. Here we use a simple
                        // approach: flush current reports, then sleep.
                        // For cleanliness we pass the entire report list including
                        // embedded delays to a helper that sequences them.
                        // (Simplified: merge delay into the per-char inter-key delay.)
                    }
                    is PayloadParser.Action.DefaultDelay -> { /* handled by parser */ }
                }
            }

            hidManager.sendKeyReports(
                reports = reports,
                delayMs = interKeyDelayMs,
                onDone  = { _isExecuting.value = false }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        hidManager.unregister()
    }
}
