package com.rohit.p2pconnection



import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.bluetooth.BluetoothAdapter
import androidx.annotation.RequiresPermission

@RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun NavigationScreen(bluetoothAdapter: BluetoothAdapter) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "device_list") {
        composable("device_list") {
            DeviceListScreen(navController, bluetoothAdapter)
        }
        composable("receive") {
            ReceiveFileScreen()
        }
    }
}