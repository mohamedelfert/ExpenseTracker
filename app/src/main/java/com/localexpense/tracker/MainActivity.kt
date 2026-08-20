package com.localexpense.tracker

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.security.AppLock
import com.localexpense.tracker.util.CrashLog
import com.localexpense.tracker.receiver.ExpenseNotificationListener
import com.localexpense.tracker.ui.AppNavHost
import com.localexpense.tracker.ui.CrashReportScreen
import com.localexpense.tracker.ui.LockScreen
import com.localexpense.tracker.ui.RestrictedSettingsDialog
import com.localexpense.tracker.ui.theme.ExpenseTrackerTheme
import com.localexpense.tracker.viewmodel.FinanceViewModel
import com.localexpense.tracker.viewmodel.MainViewModel
import com.localexpense.tracker.viewmodel.PlansViewModel

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
private const val PREFS_UI = "ui_state"
private const val KEY_RESTRICTED_EXPLAINED = "restricted_settings_explained"

class MainActivity : FragmentActivity() {

    private var smsPermissionGranted = mutableStateOf(false)
    private var notificationAccessGranted = mutableStateOf(false)

    /** القفل (المرحلة 16): مقفول من البداية لو المستخدم مفعّل رقم سري. */
    private var locked = mutableStateOf(false)
    private var backgroundedAt = 0L

    /** بيتفتح لوحده لو رجع المستخدم من الإعدادات والإذن لسه محجوب. */
    private var showRestrictedHelp = mutableStateOf(false)

    /** نفس الشرح بس **قبل** ما نودّيه للإعدادات (لما التقييد متوقّع). */
    private var showRestrictedHelpBefore = mutableStateOf(false)
    private var expectNotificationRestriction = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissionState()
        // "متسألنيش تاني": مفيش نافذة هتظهر بعد كده، فالطريق الوحيد هو
        // شاشة معلومات التطبيق — بنقول كده للمستخدم بدل ما يفضل يضغط.
        val permanentlyDenied = results.any { (permission, granted) ->
            !granted && !shouldShowRequestPermissionRationale(permission)
        }
        if (permanentlyDenied) showRestrictedHelp.value = true
    }

    /**
     * طلب أذونات الرسائل. ملفوف في runCatching لأن بعض النسخ المعدّلة من
     * أندرويد بترمي استثناء لو الإذن مش معلن أو محجوب على مستوى النظام،
     * وده كان بيقفل التطبيق بدل ما يظهر رسالة.
     */
    private fun requestSmsPermissions() {
        if (!BuildConfig.ENABLE_SMS_IMPORT) return
        val requested = runCatching { permissionLauncher.launch(SMS_PERMISSIONS) }.isSuccess
        if (!requested) showRestrictedHelp.value = true
    }

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
        val notifGranted = hasNotificationAccess()
        notificationAccessGranted.value = notifGranted

        // على بعض الأجهزة الخدمة مبتربطش بعد منح الإذن غير بعد إعادة تشغيل
        // الجهاز. requestRebind بيحلها من غير ريستارت (بيتجاهل بأمان لو الخدمة
        // مربوطة أصلاً).
        if (notifGranted) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(this, ExpenseNotificationListener::class.java)
                )
            }
        }
    }

    /**
     * كل فتح لشاشة إعدادات نظام بيمر من هنا. بعض أجهزة الشاومي/سامسونج مفيهاش
     * الشاشة المطلوبة أصلاً، و startActivity ساعتها بيرمي ActivityNotFoundException
     * وده كان بيقفل التطبيق فجأة في وش المستخدم. دلوقتي كل محاولة ليها بديل،
     * وآخر بديل هو شاشة معلومات التطبيق، ولو حتى دي مش موجودة بيظهر Toast
     * بدل ما التطبيق يموت.
     */
    private fun startSettingsIntent(vararg candidates: Intent): Boolean {
        for (intent in candidates) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        Toast.makeText(this, "مش عارف أفتح شاشة الإعدادات على الجهاز ده", Toast.LENGTH_LONG).show()
        return false
    }

    private fun appInfoIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }

    private fun openAppSettings() {
        startSettingsIntent(appInfoIntent())
    }

    /**
     * فتح إعدادات إذن قراءة الإشعارات. الترتيب: الشاشة الخاصة بالتطبيق نفسه
     * (أندرويد 11+، بتوصّل المستخدم للمفتاح على طول)، بعدين القائمة العامة،
     * وأخيرًا معلومات التطبيق.
     */
    /**
     * نقطة الدخول من زرار "إذن الإشعارات".
     *
     * لو التقييد متوقّع (أندرويد 13+ وتثبيت من بره بلاي) بنشرح الخطوات
     * **الأول** ونوديه لشاشة معلومات التطبيق يفك التقييد، بدل ما يروح لشاشة
     * الإشعارات ويلاقي الإذن محجوب من غير أي تفسير. الشرح بيظهر مرة واحدة بس؛
     * بعد كده الزرار بيوديه على طول.
     */
    private fun onNotificationAccessRequested() {
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val alreadyExplained = prefs.getBoolean(KEY_RESTRICTED_EXPLAINED, false)

        if (!alreadyExplained && isRestrictedSettingsLikely()) {
            prefs.edit().putBoolean(KEY_RESTRICTED_EXPLAINED, true).apply()
            showRestrictedHelpBefore.value = true
            return
        }
        openNotificationSettings()
    }

    private fun openNotificationSettings() {
        val perAppIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, ExpenseNotificationListener::class.java).flattenToString()
            )
        } else {
            null
        }

        val launched = if (perAppIntent != null) {
            startSettingsIntent(
                perAppIntent,
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                appInfoIntent()
            )
        } else {
            startSettingsIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), appInfoIntent())
        }

        // على أندرويد 13+ والتطبيق متثبّت من APK: النظام بيحجب الإذن ده لحد ما
        // المستخدم يسمح بالإعدادات المقيّدة. بنجهّز الشرح مسبقًا عشان يظهر
        // فور ما يرجع من غير ما الإذن يتفعّل.
        if (launched && isRestrictedSettingsLikely()) {
            expectNotificationRestriction = true
        }
    }

    /**
     * هل الجهاز غالبًا هيحجب الإذن كـ "إعداد مقيّد"؟ الشرطين: أندرويد 13+،
     * والتطبيق مش متثبّت من جوجل بلاي.
     */
    private fun isRestrictedSettingsLikely(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        }.getOrNull()
        return installer != "com.android.vending"
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // لو التشغيلة اللي فاتت قفلت، نعرض السبب و**نوقف هنا**. أي كود تحت
        // (الأذونات، القفل، الـ ViewModels، قاعدة البيانات) هو نفسه احتمال
        // يكون سبب القفلة، فمينفعش يتنفّذ قبل ما المستخدم يشوف التقرير.
        // "متابعة" بتمسح التقرير وتعيد إنشاء الشاشة عشان يجرب فتح عادي.
        val pendingCrash = runCatching { CrashLog.readFatal(this) }.getOrNull()
        if (pendingCrash != null) {
            setContent {
                CrashReportScreen(
                    report = pendingCrash,
                    onContinue = {
                        CrashLog.clearFatal(this@MainActivity)
                        recreate()
                    }
                )
            }
            return
        }

        refreshPermissionState()
        locked.value = AppLock.isLockEnabled(this)

        setContent {
            val viewModelForTheme: MainViewModel = viewModel()
            val dynamicThemeEnabled = viewModelForTheme.useDynamicColor.collectAsState(initial = false).value
            ExpenseTrackerTheme(dynamicColor = dynamicThemeEnabled) {
                // كل الـ ViewModels على مستوى الـ Activity: كده محرّك
                // التحليلات نسخة واحدة لكل الشاشات (راجع التعليق في AppNavHost).
                val viewModel: MainViewModel = viewModel()
                val finance: FinanceViewModel = viewModel()
                val plans: PlansViewModel = viewModel()
                val navController = rememberNavController()
                val smsGranted by smsPermissionGranted
                val notifGranted by notificationAccessGranted
                val isLocked by locked
                val useDynamic by viewModel.useDynamicColor.collectAsState(initial = false)

                // إعادة فحص حالة الإذن، وفحص هل لازم نقفل تاني بعد الرجوع
                // من الخلفية حسب المهلة المحددة في الإعدادات.
                LifecycleResumeEffect(Unit) {
                    refreshPermissionState()
                    if (AppLock.shouldLock(this@MainActivity, backgroundedAt)) {
                        locked.value = true
                    }
                    // رجع من إعدادات الإشعارات والإذن لسه مش مفعّل على أندرويد
                    // 13+ = الإعدادات المقيّدة هي السبب الأرجح.
                    if (expectNotificationRestriction && !hasNotificationAccess()) {
                        expectNotificationRestriction = false
                        showRestrictedHelp.value = true
                    }
                    onPauseOrDispose { }
                }

                if (showRestrictedHelpBefore.value) {
                    RestrictedSettingsDialog(
                        onOpenAppInfo = { openAppSettings() },
                        onContinue = { openNotificationSettings() },
                        proactive = true,
                        onDismiss = { showRestrictedHelpBefore.value = false }
                    )
                }

                if (showRestrictedHelp.value) {
                    RestrictedSettingsDialog(
                        onOpenAppInfo = { openAppSettings() },
                        onDismiss = { showRestrictedHelp.value = false }
                    )
                }

                if (isLocked) {
                    LockScreen(onUnlocked = { locked.value = false })
                } else {
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        finance = finance,
                        plans = plans,
                        smsPermissionGranted = smsGranted,
                        notificationAccessGranted = notifGranted,
                        // في نسخة "play" الأذونات دي مش معلنة أصلاً في الـ Manifest،
                        // فالدالة بترجع من غير ما تعمل حاجة.
                        onRequestSmsPermission = { requestSmsPermissions() },
                        onRequestNotificationPermission = { onNotificationAccessRequested() },
                        onOpenAppSettings = { openAppSettings() }
                    )
                }
            }
        }
    }
}
