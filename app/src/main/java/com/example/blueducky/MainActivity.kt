package com.example.blueducky

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.blueducky.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlueDuckyTheme {
                BlueDuckyApp(viewModel = viewModel)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueDuckyApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val connectionState by viewModel.connectionState.collectAsState()
    val statusMessage   by viewModel.statusMessage.collectAsState()
    val scriptText      by viewModel.scriptText.collectAsState()
    val isExecuting     by viewModel.isExecuting.collectAsState()

    // ── Device picker dialog state ───────────────────────────────────────────
    var showDevicePicker by remember { mutableStateOf(false) }
    var pairedDevices    by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // ── Bluetooth permission launcher ────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.registerHid()
        } else {
            Toast.makeText(context, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show()
        }
    }

    // ── File open launcher (SAF) ─────────────────────────────────────────────
    val fileOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val text = context.contentResolver.openInputStream(it)
                    ?.bufferedReader()?.readText() ?: ""
                viewModel.onScriptTextChange(text)
                Toast.makeText(context, "Payload loaded.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── File save launcher (SAF — CreateDocument) ────────────────────────────
    val fileSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                    writer.write(scriptText)
                }
                Toast.makeText(context, "Payload saved.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Helper: request BT permissions if needed, then action ───────────────
    fun requireBtPermissions(onGranted: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onGranted() else permissionLauncher.launch(missing.toTypedArray())
    }

    // ── Execute pulse animation ──────────────────────────────────────────────
    val executePulse by animateFloatAsState(
        targetValue = if (isExecuting) 0.92f else 1f,
        label = "execute_pulse"
    )

    // ────────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🦆 ",
                            fontSize = 22.sp
                        )
                        Text(
                            text = "BlueDucky",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = DuckYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DuckSurface,
                    titleContentColor = DuckOnSurface
                )
            )
        },
        containerColor = DuckBackground
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Status card ──────────────────────────────────────────────────
            StatusCard(state = connectionState, message = statusMessage)

            // ── Connect button ───────────────────────────────────────────────
            Button(
                onClick = {
                    requireBtPermissions {
                        if (connectionState == HidConnectionState.IDLE ||
                            connectionState == HidConnectionState.ERROR
                        ) {
                            viewModel.registerHid()
                        } else {
                            // Already registered — show paired devices
                            pairedDevices = viewModel.getPairedDevices()
                            showDevicePicker = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DuckYellow)
            ) {
                Icon(
                    imageVector = if (connectionState == HidConnectionState.CONNECTED)
                        Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Color(0xFF1A1A00)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (connectionState) {
                        HidConnectionState.IDLE, HidConnectionState.ERROR -> "Enable & Register Keyboard"
                        HidConnectionState.REGISTERED  -> "Select Host Device"
                        HidConnectionState.CONNECTED   -> "Connected — Change Device"
                        HidConnectionState.DISCONNECTED -> "Reconnect"
                    },
                    color = Color(0xFF1A1A00),
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Editor card ──────────────────────────────────────────────────
            EditorCard(
                text          = scriptText,
                onTextChange  = viewModel::onScriptTextChange,
                onLoadClick   = { fileOpenLauncher.launch("text/*") },
                onSaveClick   = { fileSaveLauncher.launch("payload.txt") },
                modifier      = Modifier.weight(1f)
            )

            // ── Execute button ───────────────────────────────────────────────
            Button(
                onClick = { viewModel.executePayload() },
                enabled = !isExecuting && connectionState == HidConnectionState.CONNECTED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { scaleX = executePulse; scaleY = executePulse },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DuckGreen,
                    disabledContainerColor = DuckGreen.copy(alpha = 0.3f)
                )
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF003314),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Executing…", color = Color(0xFF003314), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF003314))
                    Spacer(Modifier.width(8.dp))
                    Text("Execute Payload", color = Color(0xFF003314), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // ── Device picker bottom sheet ───────────────────────────────────────────
    if (showDevicePicker) {
        DevicePickerDialog(
            devices  = pairedDevices,
            onSelect = { device ->
                viewModel.connectToDevice(device)
                showDevicePicker = false
            },
            onDismiss = { showDevicePicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatusCard(state: HidConnectionState, message: String) {
    val (icon, tint, label) = when (state) {
        HidConnectionState.IDLE         -> Triple(Icons.Default.BluetoothDisabled,  DuckOnSurface.copy(alpha = 0.5f), "Idle")
        HidConnectionState.REGISTERED   -> Triple(Icons.Default.Bluetooth,          DuckYellow,  "Registered")
        HidConnectionState.CONNECTED    -> Triple(Icons.Default.BluetoothConnected, DuckGreen,   "Connected")
        HidConnectionState.DISCONNECTED -> Triple(Icons.Default.BluetoothDisabled,  DuckError,   "Disconnected")
        HidConnectionState.ERROR        -> Triple(Icons.Default.Error,              DuckError,   "Error")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DuckSurface)
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(message, color = DuckOnSurface.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Editor Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EditorCard(
    text: String,
    onTextChange: (String) -> Unit,
    onLoadClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DuckSurface)
            .padding(12.dp)
    ) {
        // Toolbar row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DuckyScript Editor",
                color = DuckOnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onLoadClick) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Load", tint = DuckYellow)
                }
                IconButton(onClick = onSaveClick) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = DuckYellow)
                }
                IconButton(onClick = { onTextChange("") }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = DuckError)
                }
            }
        }

        HorizontalDivider(color = DuckOnSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 6.dp))

        // Scrollable text field
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            placeholder = {
                Text(
                    "Enter DuckyScript here…\n\nExample:\nSTRING Hello World\nENTER\nDELAY 500\nGUI r",
                    color = DuckOnSurface.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = DuckOnSurface
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = DuckYellow,
                unfocusedBorderColor = DuckOnSurface.copy(alpha = 0.2f),
                cursorColor          = DuckYellow,
                focusedContainerColor   = DuckSurface2,
                unfocusedContainerColor = DuckSurface2
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Device Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerDialog(
    devices: List<BluetoothDevice>,
    onSelect: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = DuckSurface,
        title = {
            Text("Select Paired Device", color = DuckYellow, fontWeight = FontWeight.Bold)
        },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "No paired devices found.\nPair your phone with a host PC via Bluetooth Settings first.",
                    color = DuckOnSurface,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { device ->
                        @SuppressLint("MissingPermission")
                        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DuckSurface2)
                                .clickable { onSelect(device) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = DuckYellow, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(name, color = DuckOnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(device.address, color = DuckOnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DuckYellow)
            }
        }
    )
}
