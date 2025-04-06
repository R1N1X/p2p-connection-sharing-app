package com.rohit.p2pconnection



import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import java.io.DataOutputStream

import java.util.*


val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun sendFileOverBluetooth(
    context: Context,
    device: BluetoothDevice,
    fileUri: Uri,
    onResult: (Boolean) -> Unit
) {
    Thread {
        try {
            val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
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

            onResult(true)
        } catch (e: Exception) {
            Log.e("BluetoothSend", "Failed to send file: ${e.message}", e)
            onResult(false)
        }
    }.start()
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


@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun DeviceListScreen(navController: NavHostController, bluetoothAdapter: BluetoothAdapter) {
    val context = LocalContext.current
    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pickedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pickedFileUri = uri
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text("Select a device to send file", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(pairedDevices.toList()) { device ->
                Text(
                    text = device.name ?: device.address,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDevice = device }
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
            Text("Pick a file to send")
        }

        pickedFileUri?.let { uri ->
            Spacer(modifier = Modifier.height(8.dp))
            Text("Selected: ${uri.lastPathSegment}")

            Button(
                onClick = {
                    selectedDevice?.let { device ->
                        isSending = true
                        sendFileOverBluetooth(context, device, uri) { success ->
                            isSending = false
                            sendResult = if (success) "File sent successfully" else "Failed to send file"
                        }
                    }
                },
                enabled = selectedDevice != null && !isSending
            ) {
                Text("Send File")
            }

            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            sendResult?.let {
                Text(it, modifier = Modifier.padding(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { navController.navigate("receive") }) {
            Text("Go to Receive Screen")
        }
    }
}

