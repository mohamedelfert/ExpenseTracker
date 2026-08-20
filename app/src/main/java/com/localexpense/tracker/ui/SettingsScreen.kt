package com.localexpense.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.BackupManager
import com.localexpense.tracker.security.AppLock
import com.localexpense.tracker.security.BiometricAuth
import com.localexpense.tracker.viewmodel.BackupState
import com.localexpense.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الإعدادات: النسخ الاحتياطي والاسترجاع (المرحلة 3)، قفل التطبيق (المرحلة 16)،
 * ومركز الخصوصية (بند 36) — والنصوص فيه بتوصف اللي التطبيق بيعمله فعلاً، مش
 * وعود عامة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenMerchantRules: () -> Unit,
    onOpenSmsRules: () -> Unit
) {
    val context = LocalContext.current
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    var pinSet by remember { mutableStateOf(AppLock.isPinSet(context)) }
    var biometric by remember { mutableStateOf(AppLock.isBiometricEnabled(context)) }
    var timeout by remember { mutableStateOf(AppLock.timeout(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) showRestoreConfirm = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {

            if (backupState !is BackupState.Idle) {
                item {
                    val text = when (val state = backupState) {
                        is BackupState.Running -> "جاري التنفيذ..."
                        is BackupState.Done -> state.message
                        is BackupState.Error -> state.message
                        else -> ""
                    }
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { viewModel.resetBackupState() }) { Text("تمام") }
                        }
                    }
                }
            }

            // ===== النسخ الاحتياطي =====
            item { SectionHeader("النسخ الاحتياطي") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "النسخة بتتحفظ في ملف JSON إنت بتختار مكانه. قاعدة البيانات نفسها مشفّرة، " +
                            "لكن ملف النسخة نص عادي عشان يبقى قابل للاسترجاع — احفظه في مكان آمن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val last = viewModel.settings.lastBackupAt
                    if (last > 0) {
                        Text(
                            "آخر نسخة: ${SimpleDateFormat("dd MMM yyyy - hh:mm a", Locale("ar")).format(Date(last))}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) }) {
                            Text("حفظ نسخة")
                        }
                        TextButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) {
                            Text("استرجاع نسخة")
                        }
                    }
                }
            }

            // ===== قفل التطبيق =====
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("قفل التطبيق") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("رقم سري (PIN)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (pinSet) "مفعّل — الرقم متخزّن كبصمة PBKDF2، مش كنص"
                            else "غير مفعّل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showPinDialog = true }) { Text(if (pinSet) "تغيير" else "تفعيل") }
                    if (pinSet) {
                        TextButton(onClick = {
                            AppLock.clearPin(context)
                            pinSet = false
                            biometric = false
                        }) { Text("إلغاء") }
                    }
                }
            }
            if (pinSet) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("البصمة / الوجه", style = MaterialTheme.typography.bodyMedium)
                            if (!BiometricAuth.isAvailable(context)) {
                                Text(
                                    "غير متاحة على الجهاز ده",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = biometric,
                            enabled = BiometricAuth.isAvailable(context),
                            onCheckedChange = {
                                biometric = it
                                AppLock.setBiometricEnabled(context, it)
                            }
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("يقفل", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppLock.Timeout.entries.forEach { option ->
                                FilterChip(
                                    selected = option == timeout,
                                    onClick = {
                                        timeout = option
                                        AppLock.setTimeout(context, option)
                                    },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                }
            }

            // ===== الإدارة =====
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("إدارة") }
            item { TextButton(onClick = onOpenAccounts, modifier = Modifier.padding(horizontal = 16.dp)) { Text("الحسابات") } }
            item { TextButton(onClick = onOpenMerchants, modifier = Modifier.padding(horizontal = 16.dp)) { Text("الجهات") } }
            item { TextButton(onClick = onOpenMerchantRules, modifier = Modifier.padding(horizontal = 16.dp)) { Text("قواعد الجهات") } }
            item { TextButton(onClick = onOpenSmsRules, modifier = Modifier.padding(horizontal = 16.dp)) { Text("قواعد رسائل البنوك") } }

            // ===== مركز الخصوصية =====
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("الخصوصية") }
            item {
                Column(Modifier.padding(16.dp)) {
                    PrivacyLine("بياناتك المالية كلها متخزّنة على جهازك، مفيش أي حساب ولا تسجيل دخول.")
                    PrivacyLine("قاعدة البيانات مشفّرة بـ SQLCipher ومفتاحها محمي بـ Android Keystore.")
                    PrivacyLine("مفيش أي كود في التطبيق بيتصل بالإنترنت — ولا التحليلات ولا المساعد.")
                    PrivacyLine("المساعد \"اسأل عن مصروفاتك\" بيشتغل محليًا على أرقام محسوبة على الجهاز.")
                    PrivacyLine("الملفات اللي بتتصدّر (CSV / PDF / نسخة احتياطية) بتروح للملف اللي إنت بتختاره بس.")
                    PrivacyLine("نص رسائل البنوك مش بيتصدّر في CSV إلا لو فعّلت الخيار بنفسك.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "الأذونات المستخدمة: قراءة الإشعارات (لالتقاط عمليات البنوك)، " +
                            "والرسائل في نسخة \"direct\" بس، والإشعارات لتنبيهات الميزانية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("تحديد رقم سري") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("الرقم (4 أرقام على الأقل)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("تأكيد الرقم") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pin.length < 4 -> error = "لازم 4 أرقام على الأقل"
                        pin != confirm -> error = "الرقمين مش متطابقين"
                        else -> {
                            AppLock.setPin(context, pin)
                            pinSet = true
                            showPinDialog = false
                        }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("إلغاء") } }
        )
    }

    showRestoreConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("استرجاع نسخة") },
            text = {
                Text(
                    "الاسترجاع بيمسح كل البيانات الحالية ويحل مكانها محتوى الملف. " +
                        "العملية كلها في transaction واحدة، فلو الملف تالف بياناتك الحالية بتفضل زي ما هي."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreBackup(uri)
                    showRestoreConfirm = null
                }) { Text("استرجاع") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("• ", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
