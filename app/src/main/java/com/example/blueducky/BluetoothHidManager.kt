package com.example.blueducky

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "BtHidManager"

/** HID Report ID for the keyboard report.  Must match the descriptor below. */
private const val KEYBOARD_REPORT_ID: Byte = 0x01

/**
 * Standard HID descriptor for a Boot-compatible Keyboard.
 * This is the minimal descriptor accepted by all major operating systems.
 */
private val HID_KEYBOARD_DESCRIPTOR = byteArrayOf(
    0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
    0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
    0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
    0x85.toByte(), 0x01.toByte(),  //   Report ID (1)
    // --- Modifier Keys ---
    0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
    0x19.toByte(), 0xE0.toByte(),  //   Usage Minimum (224) = Left Control
    0x29.toByte(), 0xE7.toByte(),  //   Usage Maximum (231) = Right GUI
    0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
    0x25.toByte(), 0x01.toByte(),  //   Logical Maximum (1)
    0x75.toByte(), 0x01.toByte(),  //   Report Size (1)
    0x95.toByte(), 0x08.toByte(),  //   Report Count (8)
    0x81.toByte(), 0x02.toByte(),  //   Input (Data, Variable, Absolute) = 8 modifier bits
    // --- Reserved byte ---
    0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
    0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
    0x81.toByte(), 0x01.toByte(),  //   Input (Constant) = reserved byte
    // --- Key Array (6 simultaneous keys) ---
    0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
    0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
    0x29.toByte(), 0x73.toByte(),  //   Usage Maximum (115)
    0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
    0x25.toByte(), 0x73.toByte(),  //   Logical Maximum (115)
    0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
    0x95.toByte(), 0x06.toByte(),  //   Report Count (6)
    0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array, Absolute) = 6 key slots
    0xC0.toByte()                  // End Collection
)

/** Represents the connection state of the HID device profile. */
enum class HidConnectionState {
    IDLE,          // Profile not yet registered
    REGISTERED,    // Registered with the OS, waiting for a host to connect
    CONNECTED,     // A host device is connected
    DISCONNECTED,  // Was connected, now disconnected
    ERROR
}

/**
 * Manages the lifecycle of the [BluetoothHidDevice] profile.
 *
 * Usage:
 * 1. Call [register] to open the HID profile and advertise as a keyboard.
 * 2. Observe [connectionState] and wait for [HidConnectionState.CONNECTED].
 * 3. Call [sendKeyReports] with the list of HID byte arrays from [PayloadParser].
 * 4. Call [unregister] on cleanup.
 */
@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(HidConnectionState.IDLE)
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap 'Connect' to begin.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // -------------------------------------------------------------------------
    // SDP settings — Presents this device to paired hosts as a standard keyboard
    // -------------------------------------------------------------------------
    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        /* name */        "BlueDucky Keyboard",
        /* description */ "Bluetooth HID Keyboard",
        /* provider */    "BlueDucky",
        /* subclass */    BluetoothHidDevice.SUBCLASS1_KEYBOARD,
        /* descriptors */ HID_KEYBOARD_DESCRIPTOR
    )

    // -------------------------------------------------------------------------
    // HID Device Callback
    // -------------------------------------------------------------------------
    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                Log.i(TAG, "HID App registered with SDP record.")
                _connectionState.value = HidConnectionState.REGISTERED
                _statusMessage.value = "Registered. Waiting for host to connect…"
            } else {
                Log.w(TAG, "HID App unregistered.")
                _connectionState.value = HidConnectionState.IDLE
                _statusMessage.value = "HID app unregistered."
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    Log.i(TAG, "Host connected: ${device.name ?: device.address}")
                    _connectionState.value = HidConnectionState.CONNECTED
                    _statusMessage.value = "Connected to: ${device.name ?: device.address}"
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedHost = null
                    Log.i(TAG, "Host disconnected.")
                    _connectionState.value = HidConnectionState.DISCONNECTED
                    _statusMessage.value = "Host disconnected."
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _statusMessage.value = "Connecting to ${device.name ?: device.address}…"
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // Return an empty report for GET_REPORT requests (host polling)
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Acknowledge received SET_REPORT (e.g. LED state for CapsLock)
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    // -------------------------------------------------------------------------
    // Profile ServiceListener
    // -------------------------------------------------------------------------
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.i(TAG, "HID Device profile connected. Registering app…")
                hidDevice?.registerApp(
                    sdpSettings,
                    null,   // QoS settings (null = default)
                    null,   // QoS settings (null = default)
                    mainHandler::post,
                    hidCallback
                )
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.w(TAG, "HID Device profile service disconnected.")
            hidDevice = null
            _connectionState.value = HidConnectionState.ERROR
            _statusMessage.value = "Profile service disconnected."
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens the HID Device profile proxy and registers this app as a keyboard.
     * Requires [android.Manifest.permission.BLUETOOTH_CONNECT].
     */
    fun register() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth is not enabled."
            _connectionState.value = HidConnectionState.ERROR
            return
        }
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    /**
     * Unregisters the HID app and closes the profile proxy.
     * Call this in [androidx.activity.ComponentActivity.onDestroy].
     */
    fun unregister() {
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        _connectionState.value = HidConnectionState.IDLE
        _statusMessage.value = "Tap 'Connect' to begin."
    }

    /**
     * Explicitly connect to a known [device], prompting the HID profile
     * to initiate the connection. Useful when the host doesn't auto-connect.
     */
    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice ?: run {
            _statusMessage.value = "Not registered yet. Tap 'Connect' first."
            return
        }
        _statusMessage.value = "Connecting to ${device.name ?: device.address}…"
        hid.connect(device)
    }

    /**
     * Send a sequence of raw 8-byte HID keyboard reports to the connected host.
     *
     * @param reports   Ordered list of 8-byte arrays (key-down + key-up pairs from [PayloadParser]).
     * @param delayMs   Inter-report delay in milliseconds (default 20 ms feels natural).
     * @param onDone    Callback invoked on the main thread when all reports have been sent.
     */
    fun sendKeyReports(
        reports: List<ByteArray>,
        delayMs: Long = 20L,
        onDone: () -> Unit = {}
    ) {
        val hid = hidDevice
        val host = connectedHost
        if (hid == null || host == null) {
            _statusMessage.value = "No host connected. Cannot send reports."
            return
        }

        _statusMessage.value = "Executing payload…"

        // Run on a background thread to avoid blocking the UI, but send via handler
        Thread {
            for (report in reports) {
                val success = hid.sendReport(host, KEYBOARD_REPORT_ID.toInt(), report)
                if (!success) {
                    Log.w(TAG, "sendReport failed for report: ${report.toHex()}")
                }
                Thread.sleep(delayMs)
            }
            mainHandler.post {
                _statusMessage.value = "Payload executed successfully."
                onDone()
            }
        }.start()
    }

    /** Convenience: is a host currently connected? */
    val isConnected: Boolean
        get() = _connectionState.value == HidConnectionState.CONNECTED

    /** Returns the list of currently paired (bonded) Bluetooth devices. */
    fun getPairedDevices(): Set<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices ?: emptySet()
}

// --- Extension ---
private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
