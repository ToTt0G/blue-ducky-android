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

    enum class PlaybackState { IDLE, RUNNING, PAUSED }
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var executionJob: kotlinx.coroutines.Job? = null
    private var currentActions: List<PayloadParser.Action> = emptyList()
    private var currentActionIndex = 0

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

    fun executePayload(interKeyDelayMs: Long = 45L) {
        if (_playbackState.value == PlaybackState.RUNNING) return

        if (_playbackState.value == PlaybackState.IDLE) {
            val script = _scriptText.value
            if (script.isBlank()) return
            currentActions = PayloadParser.parse(script)
            currentActionIndex = 0
            hidManager.setStatusMessage("Executing payload…")
        } else if (_playbackState.value == PlaybackState.PAUSED) {
            hidManager.setStatusMessage("Resuming payload…")
        }

        _playbackState.value = PlaybackState.RUNNING

        executionJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (currentActionIndex < currentActions.size && _playbackState.value == PlaybackState.RUNNING) {
                val action = currentActions[currentActionIndex]
                when (action) {
                    is PayloadParser.Action.KeyPress -> {
                        val reports = PayloadParser.toHidReports(action)
                        for (report in reports) {
                            hidManager.sendSingleReport(report)
                            kotlinx.coroutines.delay(interKeyDelayMs)
                        }
                    }
                    is PayloadParser.Action.Delay -> {
                        kotlinx.coroutines.delay(action.milliseconds)
                    }
                    is PayloadParser.Action.DefaultDelay -> { }
                }
                currentActionIndex++
            }

            if (currentActionIndex >= currentActions.size) {
                _playbackState.value = PlaybackState.IDLE
                hidManager.setStatusMessage("Payload executed successfully.")
                currentActionIndex = 0
            }
        }
    }

    fun pauseExecution() {
        if (_playbackState.value == PlaybackState.RUNNING) {
            _playbackState.value = PlaybackState.PAUSED
            executionJob?.cancel()
            hidManager.sendSingleReport(ByteArray(8)) // Release any stuck keys
            hidManager.setStatusMessage("Execution paused.")
        }
    }

    fun stopExecution() {
        _playbackState.value = PlaybackState.IDLE
        executionJob?.cancel()
        currentActionIndex = 0
        hidManager.sendSingleReport(ByteArray(8)) // Release any stuck keys
        hidManager.setStatusMessage("Execution stopped.")
    }

    override fun onCleared() {
        super.onCleared()
        hidManager.unregister()
    }
}
