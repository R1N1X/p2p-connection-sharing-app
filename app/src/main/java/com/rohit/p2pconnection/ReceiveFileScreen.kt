package com.rohit.p2pconnection



import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import android.bluetooth.BluetoothAdapter
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.URLConnection
import java.util.*

@RequiresApi(Build.VERSION_CODES.Q)
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun ReceiveFileScreen() {
    var receivedMessage by remember { mutableStateOf("Waiting for file...") }

    var context = LocalContext.current

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothFileTransfer", uuid)
                val socket = serverSocket.accept()

                val inputStream = DataInputStream(socket.inputStream)

                // Receive file metadata
                val fileName = inputStream.readUTF()
                val fileSize = inputStream.readInt()

                // Read file data
                val fileBytes = ByteArray(fileSize)
                inputStream.readFully(fileBytes)

                // Save to Downloads using MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, URLConnection.guessContentTypeFromName(fileName))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = resolver.insert(collection, contentValues)

                fileUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(fileBytes)
                        outputStream.flush()
                    }

                    // Mark the file as not pending
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    withContext(Dispatchers.Main) {
                        receivedMessage = "Received and saved: $fileName"
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        receivedMessage = "Failed to save file"
                    }
                }

                inputStream.close()
                socket.close()
                serverSocket.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    receivedMessage = "Error: ${e.message}"
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Receive File Screen", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = receivedMessage)
    }
}
