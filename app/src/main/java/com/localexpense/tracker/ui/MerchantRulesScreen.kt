package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.data.MerchantRule
import com.localexpense.tracker.domain.CategorySource
import com.localexpense.tracker.domain.categorize
import com.localexpense.tracker.viewmodel.MainViewModel

/**
 * قواعد التصنيف بالجهة (المرحلة 5، بند 18): إضافة/تعديل/تفعيل/أولوية/حذف،
 * وزر "اختبر" بيوريك الفئة اللي هتطلع لاسم جهة معيّن ومنين جِت — عشان
 * الترتيب يفضل مفهوم مش سحر.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantRulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val rules by viewModel.merchantRules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MerchantRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showTester by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قواعد الجهات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { showTester = true }) { Text("اختبر") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, contentDescription = "قاعدة جديدة")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "الترتيب: الربط الصريح للجهة، بعدين القواعد دي بالأولوية الأعلى، " +
                    "بعدين الكلمات المفتاحية، وأخيرًا الفئة الافتراضية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            if (rules.isEmpty()) {
                EmptyState(
                    "مفيش قواعد",
                    "أي قاعدة بتتولد لوحدها لما تغيّر فئة حركة وتطلب تطبيقها على كل حركات الجهة."
                )
            } else {
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        SoftCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${rule.pattern} → ${rule.categoryName}", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "أولوية ${rule.priority}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { viewModel.saveMerchantRule(rule.copy(isEnabled = it)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { editing = rule; showEditor = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteMerchantRule(rule) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RuleEditorDialog(
            rule = editing,
            onDismiss = { showEditor = false },
            onSave = {
                viewModel.saveMerchantRule(it)
                showEditor = false
            }
        )
    }

    if (showTester) {
        RuleTesterDialog(rules = rules, onDismiss = { showTester = false })
    }
}

@Composable
private fun RuleEditorDialog(
    rule: MerchantRule?,
    onDismiss: () -> Unit,
    onSave: (MerchantRule) -> Unit
) {
    var pattern by remember { mutableStateOf(rule?.pattern ?: "") }
    var category by remember { mutableStateOf(rule?.categoryName ?: "") }
    var priority by remember { mutableStateOf((rule?.priority ?: 10).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "قاعدة جديدة" else "تعديل القاعدة") },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("اسم الجهة (أو جزء منه)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("الفئة") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it },
                    label = { Text("الأولوية (الأعلى بيكسب)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pattern.isNotBlank() && category.isNotBlank()) {
                    onSave(
                        (rule ?: MerchantRule(pattern = pattern, categoryName = category)).copy(
                            pattern = pattern.trim(),
                            categoryName = category.trim(),
                            priority = priority.toIntOrNull() ?: 10
                        )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RuleTesterDialog(rules: List<MerchantRule>, onDismiss: () -> Unit) {
    var merchant by remember { mutableStateOf("") }
    val decision = remember(merchant, rules) {
        if (merchant.isBlank()) null else categorize(merchant = merchant, rules = rules)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختبار القواعد") },
        text = {
            Column {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("اكتب اسم جهة") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (decision != null) {
                    Text("الفئة: ${decision.categoryName}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "المصدر: " + when (decision.source) {
                            CategorySource.MERCHANT_MAPPING -> "ربط صريح للجهة"
                            CategorySource.MERCHANT_RULE -> "قاعدة جهة"
                            CategorySource.KEYWORD -> "كلمات مفتاحية"
                            CategorySource.DEFAULT -> "الفئة الافتراضية"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } }
    )
}
