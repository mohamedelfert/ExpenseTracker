package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.viewmodel.MainViewModel
import com.localexpense.tracker.viewmodel.RuleTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    viewModel: MainViewModel,
    ruleId: Long,
    onBack: () -> Unit
) {
    val rules by viewModel.rules.collectAsState()
    val existing = rules.firstOrNull { it.id == ruleId }

    var bankName by remember { mutableStateOf(existing?.bankName ?: "") }
    var senderPattern by remember { mutableStateOf(existing?.senderPattern ?: "") }
    var keywordPattern by remember { mutableStateOf(existing?.debitKeywordPattern ?: "") }
    var amountPattern by remember { mutableStateOf(existing?.amountPattern ?: """(?:مبلغ|EGP|LE)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""") }
    var merchantPattern by remember { mutableStateOf(existing?.merchantPattern ?: """(?:في|لدى|at)\s+([A-Za-z0-9\u0600-\u06FF ._-]{3,40})""") }

    var testSender by remember { mutableStateOf("") }
    var testBody by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<RuleTestResult?>(null) }

    LaunchedEffect(existing) {
        if (existing != null) {
            bankName = existing.bankName
            senderPattern = existing.senderPattern
            keywordPattern = existing.debitKeywordPattern
            amountPattern = existing.amountPattern
            merchantPattern = existing.merchantPattern
        }
    }

    fun buildRule() = SmsRule(
        id = existing?.id ?: 0,
        bankName = bankName,
        senderPattern = senderPattern,
        debitKeywordPattern = keywordPattern,
        amountPattern = amountPattern,
        merchantPattern = merchantPattern,
        isEnabled = existing?.isEnabled ?: true,
        isBuiltIn = existing?.isBuiltIn ?: false
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "قاعدة جديدة" else "تعديل القاعدة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { viewModel.deleteRule(existing); onBack() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = bankName, onValueChange = { bankName = it },
                label = { Text("اسم البنك / الجهة") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = senderPattern, onValueChange = { senderPattern = it },
                label = { Text("نمط رقم/اسم المرسل (Regex)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = keywordPattern, onValueChange = { keywordPattern = it },
                label = { Text("كلمة تدل على خصم (Regex)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountPattern, onValueChange = { amountPattern = it },
                label = { Text("نمط استخراج المبلغ (Regex بمجموعة واحدة)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = merchantPattern, onValueChange = { merchantPattern = it },
                label = { Text("نمط استخراج الجهة/المكان (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = { viewModel.saveRule(buildRule()); onBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("حفظ القاعدة")
            }

            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختبار على رسالة حقيقية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "النص ده بيفضل جوه موبايلك بس، مبيتبعتش لحد. الصقه من أي رسالة وصلتك فعلًا وشوف هيتقرا صح ولا لأ.",
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedTextField(
                        value = testSender, onValueChange = { testSender = it },
                        label = { Text("رقم/اسم المرسل") }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = testBody, onValueChange = { testBody = it },
                        label = { Text("نص الرسالة") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedButton(
                        onClick = { testResult = viewModel.testRule(testSender, testBody, buildRule()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("اختبار") }

                    testResult?.let { result ->
                        Row {
                            Text(
                                if (result.matched) "تم التعرف على الرسالة ✓" else "لم يتم التعرف على الرسالة",
                                fontWeight = FontWeight.Medium,
                                color = if (result.matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (result.matched) {
                            Text("المبلغ: ${result.amount}")
                            Text("الجهة: ${result.merchant ?: "لم يتم استخراجها"}")
                        }
                    }
                }
            }
        }
    }
}