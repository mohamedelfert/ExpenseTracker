package com.localexpense.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.ui.AppNavHost
import com.localexpense.tracker.ui.theme.ExpenseTrackerTheme
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * Both permissions are needed:
 *  - RECEIVE_SMS: catch new messages as they arrive (live tracking)
 *  - READ_SMS: let the user import older messages already sitting in the inbox
 */
private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS
)

class MainActivity : ComponentActivity() {

    private var smsPermissionGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshPermissionState() }

    private fun hasSmsPermissions(): Boolean =
        SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun refreshPermissionState() {
        smsPermissionGranted.value = hasSmsPermissions()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshPermissionState()

        setContent {
            ExpenseTrackerTheme {
                val viewModel: MainViewModel = viewModel()
                val navController = rememberNavController()
                val granted by smsPermissionGranted

                // Re-check every time the user comes back to the app — covers the case
                // where they granted the permission from the system Settings screen.
                LifecycleResumeEffect(Unit) {
                    refreshPermissionState()
                    onPauseOrDispose { }
                }

                AppNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    smsPermissionGranted = granted,
                    onRequestSmsPermission = {
                        permissionLauncher.launch(SMS_PERMISSIONS)
                    },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }
}
