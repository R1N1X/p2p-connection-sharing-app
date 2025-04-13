package com.rohit.p2pconnection.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rohit.p2pconnection.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.URLConnection
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.Q)
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun ReceiveFileScreen(
    navController: NavHostController
) {
    var transferState by remember { mutableStateOf(TransferState.WAITING) }
    var receivedMessage by remember { mutableStateOf("Waiting for file...") }
    var transferProgress by remember { mutableStateOf(0f) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var bytesReceived by remember { mutableStateOf(0L) }
    var transferJob by remember { mutableStateOf<Job?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var savedFileUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // Function to start the Bluetooth receiver
    fun startReceiving() {
        transferState = TransferState.WAITING
        receivedMessage = "Waiting for file..."
        transferProgress = 0f
        fileName = ""
        fileSize = 0
        bytesReceived = 0
        showDetails = false

        transferJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                transferState = TransferState.CONNECTING
                withContext(Dispatchers.Main) {
                    receivedMessage = "Setting up Bluetooth connection..."
                }

                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothFileTransfer", uuid)

                withContext(Dispatchers.Main) {
                    receivedMessage = "Waiting for connection..."
                }

                val socket = serverSocket.accept()

                withContext(Dispatchers.Main) {
                    transferState = TransferState.RECEIVING
                    receivedMessage = "Connection established, receiving data..."
                    showDetails = true
                }

                val inputStream = DataInputStream(socket.inputStream)

                // Receive file metadata
                fileName = inputStream.readUTF()
                fileSize = inputStream.readInt().toLong()

                withContext(Dispatchers.Main) {
                    receivedMessage = "Receiving: $fileName"
                }

                // Create buffer for data chunks
                val buffer = ByteArray(8192)
                val fileBytes = ByteArrayOutputStream()
                var bytesRead = 0
                bytesReceived = 0

                // Read data in chunks and update progress
                try {
                    while (bytesReceived < fileSize && inputStream.read(buffer).also { bytesRead = it } > 0) {
                        fileBytes.write(buffer, 0, bytesRead)
                        bytesReceived += bytesRead

                        // Update progress on the main thread
                        withContext(Dispatchers.Main) {
                            transferProgress = bytesReceived.toFloat() / fileSize
                            receivedMessage = "Receiving: $fileName"
                        }

                        // Check if job was cancelled
                        if (!isActive) {
                            throw CancellationException("Transfer cancelled")
                        }
                    }
                } catch (e: IOException) {
                    // Only throw if we haven't received the complete file yet
                    if (bytesReceived < fileSize) {
                        throw IOException("Connection lost during transfer: ${e.message}")
                    }
                }

                // Save file to Downloads using MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, URLConnection.guessContentTypeFromName(fileName))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                    put(MediaStore.Downloads.SIZE, fileBytes.size())
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val fileUri = resolver.insert(collection, contentValues)

                fileUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        fileBytes.writeTo(outputStream)
                        outputStream.flush()
                    }

                    // Mark the file as not pending
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    withContext(Dispatchers.Main) {
                        transferState = TransferState.COMPLETED
                        receivedMessage = "File received successfully!"
                        transferProgress = 1f
                        savedFileUri = uri
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        transferState = TransferState.ERROR
                        receivedMessage = "Failed to save file"
                    }
                }

                fileBytes.close()
                inputStream.close()
                socket.close()
                serverSocket.close()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    transferState = if (e is CancellationException) TransferState.CANCELLED else TransferState.ERROR
                    receivedMessage = when {
                        e is CancellationException -> "Transfer cancelled"
                        e is IOException -> "Connection lost: ${e.message}"
                        else -> "Error: ${e.message}"
                    }
                }
            }
        }
    }

    // Start the receiver when the composable is first launched
    LaunchedEffect(Unit) {
        startReceiving()
    }

    // Handler for cancel button
    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        transferState = TransferState.CANCELLED
        receivedMessage = "Transfer cancelled"
    }

    // Handler for retry button
    fun retryTransfer() {
        transferJob?.cancel()
        startReceiving()
    }

    // Main UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive File") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    StatusBadge(transferState)
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animation area
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (transferState) {
                        TransferState.WAITING, TransferState.CONNECTING -> {
                            WaitingAnimation()
                        }
                        TransferState.RECEIVING -> {
                            ReceivingAnimation(transferProgress)
                        }
                        TransferState.COMPLETED -> {
                            CompletedAnimation()
                        }
                        TransferState.ERROR, TransferState.CANCELLED -> {
                            ErrorAnimation()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File details card
                if (transferState == TransferState.RECEIVING || transferState == TransferState.COMPLETED) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (fileName.isNotEmpty()) {
                                Text(
                                    text = "Filename",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (transferState == TransferState.RECEIVING) {
                                LinearProgressIndicator(
                                    progress = { transferProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeCap = StrokeCap.Round
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatFileSize(bytesReceived),
                                        style = MaterialTheme.typography.labelMedium
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${(transferProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = formatFileSize(fileSize),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            } else if (transferState == TransferState.COMPLETED) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Size: ${formatFileSize(fileSize)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Saved to Downloads",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Status message
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = when (transferState) {
                            TransferState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                            TransferState.ERROR, TransferState.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (transferState) {
                                TransferState.WAITING, TransferState.CONNECTING -> Icons.Default.Info
                                TransferState.RECEIVING -> ImageVector.vectorResource(R.drawable.ic_cloud_upload)
                                TransferState.COMPLETED -> Icons.Default.CheckCircle
                                TransferState.ERROR, TransferState.CANCELLED -> Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = when (transferState) {
                                TransferState.COMPLETED -> MaterialTheme.colorScheme.primary
                                TransferState.ERROR, TransferState.CANCELLED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = receivedMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (transferState) {
                                TransferState.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
                                TransferState.ERROR, TransferState.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action buttons
                when (transferState) {
                    TransferState.WAITING, TransferState.CONNECTING, TransferState.RECEIVING -> {
                        FilledTonalButton(
                            onClick = { cancelTransfer() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Receiving")
                        }
                    }
                    TransferState.COMPLETED -> {
                        Button(
                            onClick = {
                                savedFileUri?.let { uri ->
                                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, context.contentResolver.getType(uri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(openIntent)
                                    } catch (e: ActivityNotFoundException) {
                                        // No app to handle this file type
                                        Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_LONG).show()
                                    }
                                } ?: run {
                                    Toast.makeText(context, "File not available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(ImageVector.vectorResource(R.drawable.ic_file_open), contentDescription = "Open File")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open File")
                        }
                    }
                    TransferState.ERROR, TransferState.CANCELLED -> {
                        Button(
                            onClick = { retryTransfer() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Try Again")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary action
                if (transferState == TransferState.COMPLETED) {
                    OutlinedButton(
                        onClick = { retryTransfer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_sensors), contentDescription = "Receive Another")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Receive Another File")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(state: TransferState) {
    val statusText = when (state) {
        TransferState.WAITING -> "Waiting"
        TransferState.CONNECTING -> "Connecting"
        TransferState.RECEIVING -> "Receiving"
        TransferState.COMPLETED -> "Completed"
        TransferState.ERROR -> "Error"
        TransferState.CANCELLED -> "Cancelled"
    }

    val statusColor = when (state) {
        TransferState.WAITING, TransferState.CONNECTING -> MaterialTheme.colorScheme.tertiary
        TransferState.RECEIVING -> MaterialTheme.colorScheme.primaryContainer
        TransferState.COMPLETED -> MaterialTheme.colorScheme.primary
        TransferState.ERROR, TransferState.CANCELLED -> MaterialTheme.colorScheme.error
    }

    Surface(
        color = statusColor.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == TransferState.RECEIVING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun WaitingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-anim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pulsing circle animation
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer pulse circles
            Box(
                modifier = Modifier
                    .size(200.dp * pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) { }

            Box(
                modifier = Modifier
                    .size(160.dp * pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { }

            // Bluetooth icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_sensors),
                    contentDescription = "Waiting",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "Waiting for connection...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun ReceivingAnimation(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "data-transfer")
    val dataFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "data-flow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .padding(16.dp)
    ) {
        // Inner circle
        Surface(
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Progress text
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Animated data flow dots
        Canvas(modifier = Modifier.size(180.dp)) {
            val pathRadius = size.width / 2 * 0.8f
            val center = Offset(size.width / 2, size.height / 2)
            val dotSize = size.width * 0.02f

            for (i in 0 until 12) {
                val animatedPosition = (dataFlow + i / 12f) % 1f
                val angle = animatedPosition * 2 * Math.PI
                val x = center.x + (pathRadius * cos(angle)).toFloat()
                val y = center.y + (pathRadius * sin(angle)).toFloat()

                drawCircle(
                    color = Color.Blue,
                    radius = dotSize,
                    center = Offset(x, y),
                    alpha = if ((animatedPosition * 12).toInt() % 3 == 0) 0.9f else 0.3f
                )
            }
        }
    }
}

@Composable
fun CompletedAnimation() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Success circle
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Outer circle
            Canvas(
                modifier = Modifier.size(200.dp)
            ) {
                drawCircle(
                    color = Color.Blue,
                    style = Stroke(width = 4.dp.toPx()),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "File Received Successfully!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorAnimation() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Error circle
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_error),
                        contentDescription = "Error",
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Outer circle
            Canvas(
                modifier = Modifier.size(200.dp)
            ) {
                drawCircle(
                    color = Color.Red,
                    style = Stroke(width = 4.dp.toPx()),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Transfer Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

// Helper types and functions
enum class TransferState {
    WAITING,      // Initial state, waiting for connection
    CONNECTING,   // Establishing connection
    RECEIVING,    // Actively receiving file data
    COMPLETED,    // Transfer successfully completed
    ERROR,        // Error occurred
    CANCELLED     // User cancelled the transfer
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${(size / 1024f).toInt()} KB"
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024f * 1024f))
        else -> String.format("%.2f GB", size / (1024f * 1024f * 1024f))
    }
}