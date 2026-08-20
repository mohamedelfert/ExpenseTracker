package com.localexpense.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.security.AppLock
import com.localexpense.tracker.ui.AppNavHost
import com.localexpense.tracker.ui.LockScreen
import com.localexpense.tracker.ui.theme.ExpenseTrackerTheme
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * الأذونات الحساسة المطلوبة (نسخة "direct" فقط):
 * - RECEIVE_SMS: التقاط الرسائل البنكية فور وصولها لتحديث البيانات حياً.
 * - READ_SMS: قراءة الرسائل القديمة من صندوق الوارد لاستيراد المصروفات.
 *
 * في نسخة "play" الفلاج BuildConfig.ENABLE_SMS_IMPORT بيبقى false والأذونات
 * دي مش حتى معلنة في الـ Manifest (اتشالت في app/src/play/AndroidManifest.xml)،
 * فمفيش داعي نطلبها أو نحاول نتحقق منها هنا - الاعتماد بيبقى بالكامل على
 * NotificationListenerService (قراءة إشعارات تطبيقات البنوك) والإدخال اليدوي.
 */
private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS
)

/**
 * FragmentActivity (مش ComponentActivity زي الأول) لأن BiometricPrompt بتطلب
 * FragmentActivity — راجع security/BiometricAuth.kt. FragmentActivity وارثة من
 * ComponentActivity فكل حاجة في Compose بتفضل شغالة زي ما هي.
 */
class MainActivity : FragmentActivity() {

    private var smsPermissionGranted = mutableStateOf(false)
    private var notificationAccessGranted = mutableStateOf(false)

    /** القفل (المرحلة 16): مقفول من البداية لو المستخدم مفعّل رقم سري. */
    private var locked = mutableStateOf(false)
    private var backgroundedAt = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshPermissionState() }

    private fun hasSmsPermissions(): Boolean {
        if (!BuildConfig.ENABLE_SMS_IMPORT) return false
        return SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshPermissionState()
        locked.value = AppLock.isLockEnabled(this)

        setContent {
            ExpenseTrackerTheme {
                val viewModel: MainViewModel = viewModel()
                val navController = rememberNavController()
                val smsGranted by smsPermissionGranted
                val notifGranted by notificationAccessGranted
                val isLocked by locked

                // إعادة فحص حالة الإذن، وفحص هل لازم نقفل تاني بعد الرجوع
                // من الخلفية حسب المهلة المحددة في الإعدادات.
                LifecycleResumeEffect(Unit) {
                    refreshPermissionState()
                    if (AppLock.shouldLock(this@MainActivity, backgroundedAt)) {
                        locked.value = true
                    }
                    onPauseOrDispose { }
                }

                if (isLocked) {
                    LockScreen(onUnlocked = { locked.value = false })
                } else {
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        smsPermissionGranted = smsGranted,
                        notificationAccessGranted = notifGranted,
                        onRequestSmsPermission = {
                            // في نسخة "play" الأذونات دي مش معلنة أصلاً في الـ Manifest،
                            // فمحاولة طلبها هتترفض من النظام تلقائيًا - نتجاهلها بأمان.
                            if (BuildConfig.ENABLE_SMS_IMPORT) {
                                permissionLauncher.launch(SMS_PERMISSIONS)
                            }
                        },
                        onRequestNotificationPermission = { openNotificationSettings() },
                        onOpenAppSettings = { openAppSettings() }
                    )
                }
            }
        }
    }
}
