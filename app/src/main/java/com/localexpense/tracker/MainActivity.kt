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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.ui.AppNavHost
import com.localexpense.tracker.ui.theme.ExpenseTrackerTheme
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * الأذونات الحساسة المطلوبة:
 * - RECEIVE_SMS: التقاط الرسائل البنكية فور وصولها لتحديث البيانات حياً.
 * - READ_SMS: قراءة الرسائل القديمة من صندوق الوارد لاستيراد المصروفات.
 */
private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS
)

class MainActivity : ComponentActivity() {

    private var smsPermissionGranted = mutableStateOf(false)
    private var notificationAccessGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshPermissionState() }

    private fun hasSmsPermissions(): Boolean =
        SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasNotificationAccess(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun refreshPermissionState() {
        smsPermissionGranted.value = hasSmsPermissions()
        notificationAccessGranted.value = hasNotificationAccess()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshPermissionState()

        setContent {
            ExpenseTrackerTheme {
                val viewModel: MainViewModel = viewModel()
                val navController = rememberNavController()
                val smsGranted by smsPermissionGranted
                val notifGranted by notificationAccessGranted

                // إعادت فحص حالة الإذن عند العودة للتطبيق من إعدادات النظام
                LifecycleResumeEffect(Unit) {
                    refreshPermissionState()
                    onPauseOrDispose { }
                }

                AppNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    smsPermissionGranted = smsGranted,
                    notificationAccessGranted = notifGranted,
                    onRequestSmsPermission = {
                        permissionLauncher.launch(SMS_PERMISSIONS)
                    },
                    onRequestNotificationPermission = { openNotificationSettings() },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }
}