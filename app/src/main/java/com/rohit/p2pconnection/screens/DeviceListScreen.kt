package com.rohit.p2pconnection.screens



import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.rohit.p2pconnection.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.IOException
import java.io.DataOutputStream
import java.util.UUID


val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun DeviceListScreen(navController: NavHostController, bluetoothAdapter: BluetoothAdapter) {
    val context = LocalContext.current
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }
    // Track selected devices for both paired and discovered devices
    val selectedPairedDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val selectedDiscoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }

    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var currentSendingDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var sendingProgress by remember { mutableStateOf(0f) }
    var sendResult by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isScanning by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickedFileUri = uri
    }

    // Calculate total selected devices (selected paired + selected discovered)
    val totalSelectedDevices = selectedPairedDevices.size + selectedDiscoveredDevices.size

    // Load paired devices
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pairedDevices.clear()
            pairedDevices.addAll(bluetoothAdapter.bondedDevices)
        }
    }

    // Broadcast receiver for discovered devices
    val receiver = rememberUpdatedState(
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            if (!discoveredDevices.contains(it) && !pairedDevices.contains(it)) {
                                discoveredDevices.add(it)
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning = false
                        Toast.makeText(context, "Discovery finished", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Register receiver
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver.value, filter)

        onDispose {
            context.unregisterReceiver(receiver.value)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        discoveredDevices.clear()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) { return }
        bluetoothAdapter.cancelDiscovery()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) { return }
        isScanning = true
        bluetoothAdapter.startDiscovery()
        Toast.makeText(context, "Discovery started...", Toast.LENGTH_SHORT).show()
    }

    fun sendToAllSelectedDevices() {
        // Create a combined list of all devices to send to
        val devicesToSendTo = mutableListOf<BluetoothDevice>()

        // Add only selected paired devices
        devicesToSendTo.addAll(selectedPairedDevices)

        // Add selected discovered devices
        devicesToSendTo.addAll(selectedDiscoveredDevices)

        if (devicesToSendTo.isEmpty() || pickedFileUri == null) return

        isSending = true
        sendResult = emptyMap()
        sendingProgress = 0f

        // Create a coroutine scope that will be cancelled when the composable is disposed
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            devicesToSendTo.forEachIndexed { index, device ->
                withContext(Dispatchers.Main) {
                    currentSendingDevice = device
                    sendingProgress = index.toFloat() / devicesToSendTo.size
                }

                var success = false
                try {
                    // Use suspendCancellableCoroutine to convert callback to suspend function
                    success = suspendCancellableCoroutine { continuation ->
                        sendFileOverBluetooth(context, device, pickedFileUri!!) { result ->
                            continuation.resume(result)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothTransfer", "Error sending to ${device.name}", e)
                }

                // Update the result map
                withContext(Dispatchers.Main) {
                    val deviceIdentifier = device.name ?: device.address
                    sendResult = sendResult + (deviceIdentifier to success)
                }
            }

            withContext(Dispatchers.Main) {
                isSending = false
                currentSendingDevice = null
                sendingProgress = 1f
            }
        }
    }

    // New function to send to all paired devices and selected discovered devices
    fun sendToAllPairedAndSelectedDiscovered() {
        // Create a combined list of all devices to send to
        val devicesToSendTo = mutableListOf<BluetoothDevice>()

        // Add ALL paired devices
        devicesToSendTo.addAll(pairedDevices)

        // Add only selected discovered devices
        devicesToSendTo.addAll(selectedDiscoveredDevices)

        if (devicesToSendTo.isEmpty() || pickedFileUri == null) return

        isSending = true
        sendResult = emptyMap()
        sendingProgress = 0f

        // Create a coroutine scope that will be cancelled when the composable is disposed
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            devicesToSendTo.forEachIndexed { index, device ->
                withContext(Dispatchers.Main) {
                    currentSendingDevice = device
                    sendingProgress = index.toFloat() / devicesToSendTo.size
                }

                var success = false
                try {
                    // Use suspendCancellableCoroutine to convert callback to suspend function
                    success = suspendCancellableCoroutine { continuation ->
                        sendFileOverBluetooth(context, device, pickedFileUri!!) { result ->
                            continuation.resume(result)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothTransfer", "Error sending to ${device.name}", e)
                }

                // Update the result map
                withContext(Dispatchers.Main) {
                    val deviceIdentifier = device.name ?: device.address
                    sendResult = sendResult + (deviceIdentifier to success)
                }
            }

            withContext(Dispatchers.Main) {
                isSending = false
                currentSendingDevice = null
                sendingProgress = 1f
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth File Transfer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { navController.navigate("receive") }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_download),
                            contentDescription = "Receive Files",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File Selection Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "File Selection",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        pickedFileUri?.let { uri ->
                            FileInfoCard(uri, context)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_attachment),
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pickedFileUri?.let { "Change File" } ?: "Select a File")
                        }
                    }
                }
            }

            // Selected devices chip group (only if any selected)
            item {
                if (selectedPairedDevices.isNotEmpty() || selectedDiscoveredDevices.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Selected Devices ($totalSelectedDevices)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Show paired devices first
                                selectedPairedDevices.forEach { device ->
                                    DeviceChip(
                                        device = device,
                                        isPaired = true,
                                        onRemove = { selectedPairedDevices.remove(device) }
                                    )
                                }

                                // Then show discovered devices
                                selectedDiscoveredDevices.forEach { device ->
                                    DeviceChip(
                                        device = device,
                                        isPaired = false,
                                        onRemove = { selectedDiscoveredDevices.remove(device) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Transfer Controls Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Transfer Controls",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                                    }.also {
                                        startDiscovery()
                                    })
                                },
                                enabled = !isScanning
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (isScanning) "Scanning..." else "Scan Devices")
                            }

                            ElevatedButton(
                                onClick = { sendToAllSelectedDevices() },
                                enabled = totalSelectedDevices > 0 && pickedFileUri != null && !isSending,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isSending) "Sending..." else "Send to Selected (${totalSelectedDevices})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // New button to send to all paired devices + selected discovered
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { sendToAllPairedAndSelectedDiscovered() },
                            enabled = pickedFileUri != null && !isSending && (pairedDevices.isNotEmpty() || selectedDiscoveredDevices.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send to All Paired\nSelected Discovered Devices (${selectedDiscoveredDevices.size})",
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Show progress when sending to multiple devices
                        AnimatedVisibility(visible = isSending) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                LinearProgressIndicator(
                                    progress = { sendingProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sending to: ${currentSendingDevice?.name ?: "Device"} (${(sendingProgress * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Show transfer results
                        AnimatedVisibility(
                            visible = sendResult.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                Text(
                                    "Transfer Results",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        sendResult.forEach { (device, success) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = if (success)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = device,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text(
                                                    text = if (success) "Success" else "Failed",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (success)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Paired Devices Section (now selectable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Paired Devices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${pairedDevices.size} devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pairedDevices.isEmpty()) {
                    EmptyDevicesList(false, "No paired devices")
                }
            }

            // Paired Devices list (now selectable)
            items(pairedDevices) { device ->
                SelectableDeviceItem(
                    device = device,
                    isSelected = selectedPairedDevices.contains(device),
                    isPaired = true,
                    onSelectionChanged = { selected ->
                        if (selected) {
                            if (!selectedPairedDevices.contains(device)) {
                                selectedPairedDevices.add(device)
                            }
                        } else {
                            selectedPairedDevices.remove(device)
                        }
                    }
                )
            }

            // Available Devices Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Available Devices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${discoveredDevices.size} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (discoveredDevices.isEmpty()) {
                    EmptyDevicesList(isScanning, "No devices found")
                }
            }

            // Discovered Devices list (selectable)
            items(discoveredDevices) { device ->
                SelectableDeviceItem(
                    device = device,
                    isSelected = selectedDiscoveredDevices.contains(device),
                    isPaired = false,
                    onSelectionChanged = { selected ->
                        if (selected) {
                            if (!selectedDiscoveredDevices.contains(device)) {
                                selectedDiscoveredDevices.add(device)
                            }
                        } else {
                            selectedDiscoveredDevices.remove(device)
                        }
                    }
                )
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { navController.navigate("receive") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_download),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Receive Screen")
                }
            }
        }
    }
}

// Component for selected device chips
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun DeviceChip(device: BluetoothDevice, isPaired: Boolean, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isPaired)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp,
            if (isPaired)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.labelMedium,
                color = if (isPaired)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = if (isPaired)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

// Unified component for both paired and discovered devices
@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
@Composable
fun SelectableDeviceItem(
    device: BluetoothDevice,
    isSelected: Boolean,
    isPaired: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelectionChanged(!isSelected) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                if (isPaired) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                tint = if (isSelected)
                    if (isPaired) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name ?: "Unnamed Device",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected)
                            if (isPaired) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    if (isPaired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            modifier = Modifier.semantics { contentDescription = "Paired device" }
                        ) {
                            Text(
                                text = "Paired",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        if (isPaired) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChanged(it) }
            )
        }
    }
}

@Composable
fun EmptyDevicesList(isScanning: Boolean, message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_connect),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isScanning) "Scanning for devices..." else message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Make sure Bluetooth is enabled on nearby devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
fun sendFileOverBluetooth(
    context: Context,
    device: BluetoothDevice,
    fileUri: Uri,
    onResult: (Boolean) -> Unit
) {
    Thread {
        try {
            val socket = device.createRfcommSocketToServiceRecord(MY_UUID)

            Thread.sleep(1000)
            socket.connect()

            val inputStream = context.contentResolver.openInputStream(fileUri)
            val outputStream = DataOutputStream(socket.outputStream)

            val fileName = getFileName(context, fileUri) ?: "shared_file"
            val fileBytes = inputStream?.readBytes() ?: throw IOException("Unable to read file")

            // Send file name and size first
            outputStream.writeUTF(fileName)
            outputStream.writeInt(fileBytes.size)

            // Send file bytes
            outputStream.write(fileBytes)
            outputStream.flush()

            inputStream.close()
            outputStream.close()
            socket.close()

            // ✅ Run result on main thread
            Handler(Looper.getMainLooper()).post {
                onResult(true)
                Toast.makeText(context, "File sent to ${device.name ?: "device"}", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                onResult(false)
                Toast.makeText(context, "Error sending to ${device.name ?: "device"}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

@Composable
fun FileInfoCard(uri: Uri, context: Context) {
    val fileName = remember(uri) {
        getFileName(context, uri) ?: uri.lastPathSegment ?: "Unknown file"
    }

    val fileSize = remember(uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val size = input.available()
                when {
                    size < 1024 -> "$size B"
                    size < 1024 * 1024 -> "${size / 1024} KB"
                    else -> "${size / (1024 * 1024)} MB"
                }
            } ?: "Unknown size"
        } catch (e: Exception) {
            "Unknown size"
        }
    }

    val fileType = remember(uri) {
        context.contentResolver.getType(uri)?.split("/")?.lastOrNull()?.uppercase() ?: "FILE"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon/badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fileType.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return name
}