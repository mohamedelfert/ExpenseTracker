package com.localexpense.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.ui.AppNavHost
import com.localexpense.tracker.ui.theme.ExpenseTrackerTheme
import com.localexpense.tracker.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var smsPermissionGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> smsPermissionGranted.value = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        smsPermissionGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            ExpenseTrackerTheme {
                val viewModel: MainViewModel = viewModel()
                val navController = rememberNavController()
                var granted by smsPermissionGranted

                AppNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    smsPermissionGranted = granted,
                    onRequestSmsPermission = {
                        permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                )
            }
        }
    }
}
