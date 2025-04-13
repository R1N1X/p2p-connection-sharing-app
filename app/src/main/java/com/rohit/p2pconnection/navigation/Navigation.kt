package com.rohit.p2pconnection.navigation


import android.Manifest
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.bluetooth.BluetoothAdapter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.rohit.p2pconnection.screens.DeviceListScreen
import com.rohit.p2pconnection.screens.ReceiveFileScreen

@RequiresApi(Build.VERSION_CODES.Q)
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun NavigationScreen(bluetoothAdapter: BluetoothAdapter) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "device_list") {
        composable("device_list") {
            DeviceListScreen(navController, bluetoothAdapter)
        }
        composable("receive") {
            ReceiveFileScreen(navController)
        }
    }
}