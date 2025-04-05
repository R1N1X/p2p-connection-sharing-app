package com.rohit.p2pconnection


import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.collections.isNullOrEmpty


class MainActivity : ComponentActivity() {

    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoverDuration = 300

    // Register Activity Results at the activity level
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedFileUri = uri
    }

    fun keepBluetoothAlive() {
        btAdapter?.takeIf { !it.isEnabled }?.apply {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            enable() // Forces Bluetooth to stay on
        }
    }

    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == discoverDuration) {
            selectedFileUri?.let {
                shareFileToMultipleDevices(it)
            }
        }
    }

    private var selectedFileUri by mutableStateOf<Uri?>(null)
    private var selectedFileType by mutableStateOf<String?>(null)

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }

        keepBluetoothAlive()

        setContent {
            BluetoothFileTransferScreen()
        }
    }

    /* UI START */
    @Composable
    fun BluetoothFileTransferScreen() {
        var isFileSelected by remember { mutableStateOf(false) }
        var showShimmer by remember { mutableStateOf(false) }

        LaunchedEffect(isFileSelected) {
            if (isFileSelected) {
                showShimmer = true
                delay(500) // Fake shimmer effect duration
                showShimmer = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(500))
        ) {
            HeroAnimation(selectedFileUri, showShimmer)

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(12.dp)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize()
                ) {
                    Text(
                        text = selectedFileUri?.lastPathSegment ?: "No file selected",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    selectedFileType?.let {
                        Text(
                            text = "File type: $it",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FileTypeSelector()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = selectedFileUri != null,
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(animationSpec = tween(600)),
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
            ) {
                Button(
                    onClick = { sendViaBluetooth() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    enabled = selectedFileUri != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Send via Bluetooth", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    fun FileTypeSelector() {
        var expandedMenu by remember { mutableStateOf(false) }

        Column {
            Button(
                onClick = { expandedMenu = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
            ) {
                Text("Select File Type", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                listOf(
                    Pair("Images", arrayOf("image/*")),
                    Pair("Videos", arrayOf("video/*")),
                    Pair("Audio", arrayOf("audio/*")),
                    Pair("Files", arrayOf("*/*")),
                    Pair("CSV Files", arrayOf("text/csv", "application/csv"))
                ).forEach { (label, mimeTypes) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expandedMenu = false
                            selectedFileType = label
                            pickFileLauncher.launch(mimeTypes)
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun HeroAnimation(fileUri: Uri?, showShimmer: Boolean) {
        AnimatedVisibility(
            visible = fileUri != null,
            enter = fadeIn(animationSpec = tween(500)) + expandVertically(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .clickable { /* Navigate to full-screen preview */ },
                contentAlignment = Alignment.Center
            ) {
                if (showShimmer) {
                    ShimmerEffect()
                } else {
                    if (fileUri != null) {
                        // Check file type and display appropriate preview
                        when (selectedFileType) {
                            "Images" -> {
                                AsyncImage(
                                    model = fileUri,
                                    contentDescription = "Selected Image",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                            "Videos" -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Video File",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.DarkGray
                                    )
                                    Text(
                                        text = "Video File Selected",
                                        fontSize = 16.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            "Audio" -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Audio File",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.DarkGray
                                    )
                                    Text(
                                        text = "Audio File Selected",
                                        fontSize = 16.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            "CSV Files" -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "CSV File",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.DarkGray
                                    )
                                    Text(
                                        text = "CSV File Selected",
                                        fontSize = 16.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            "Files" -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "CSV File",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.DarkGray
                                    )
                                    Text(
                                        text = "CSV File Selected",
                                        fontSize = 16.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "File",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.DarkGray
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No File Selected",
                            fontSize = 18.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ShimmerEffect() {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.LightGray.copy(alpha = 0.3f))
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
    /* UI END */

    /// Logic
    private fun sendViaBluetooth() {
        if (selectedFileUri == null) return
        if (btAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkBluetoothPermission()
        } else {
            enableBluetooth()
        }
    }

    private fun checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    101
                )
            } else {
                enableBluetooth()
            }
        } else {
            enableBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth()
            } else {
                Toast.makeText(this, "Permission denied for Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableBluetooth() {
        val discoveryIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverDuration)
        }
        bluetoothLauncher.launch(discoveryIntent)
    }

    // Determine the MIME type based on the URI
    private fun getMimeTypeFromUri(uri: Uri): String {
        val contentResolver = applicationContext.contentResolver
        return contentResolver.getType(uri) ?: when (selectedFileType) {
            "Images" -> "image/*"
            "Videos" -> "video/*"
            "Audio" -> "audio/*"
            "CSV Files" -> "text/csv"
            "Files" -> "*/*"
            else -> "*/*"
        }
    }

    // Multi
    private fun shareFileToMultipleDevices(fileUri: Uri) {
        val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        if (btAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (!btAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        // Get list of paired devices
        val pairedDevices: Set<BluetoothDevice>? = btAdapter.bondedDevices

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show()
            return
        }

        // Show a dialog for user to select multiple devices
        val deviceNames = pairedDevices.map {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            it.name
        }.toTypedArray()

        val selectedDevices = mutableListOf<BluetoothDevice>()

        AlertDialog.Builder(this)
            .setTitle("Select Bluetooth Devices")
            .setMultiChoiceItems(deviceNames, null) { _, which, isChecked ->
                if (isChecked) {
                    selectedDevices.add(pairedDevices.elementAt(which))
                } else {
                    selectedDevices.remove(pairedDevices.elementAt(which))
                }
            }
            .setPositiveButton("Send") { _, _ ->
                // Send file to selected devices
                selectedDevices.forEach { device ->
                    sendFileToDevice(fileUri, device)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFileToDevice(fileUri: Uri, device: BluetoothDevice) {
        val mimeType = getMimeTypeFromUri(fileUri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TEXT, "Sharing file via Bluetooth")
        }

        val pm: PackageManager = packageManager
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        if (apps.isNotEmpty()) {
            val btApp = apps.firstOrNull { it.activityInfo.packageName.contains("bluetooth", ignoreCase = true) }

            if (btApp != null) {
                intent.setClassName(btApp.activityInfo.packageName, btApp.activityInfo.name)
                startActivity(intent)
            } else {
                Toast.makeText(this, "No Bluetooth app found", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No apps available for sharing", Toast.LENGTH_SHORT).show()
        }
    }
}
