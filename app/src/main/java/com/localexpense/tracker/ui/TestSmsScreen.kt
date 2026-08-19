package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.viewmodel.MainViewModel
import com.localexpense.tracker.viewmodel.SmsTestResult

/**
 * The parser (SmsParser.kt) recognises common Egyptian bank / InstaPay wording out of
 * the box, with no per-bank configuration needed. Use this screen to paste a real message
 * you actually received and confirm the amount / merchant / bank / category come out right —
 * the text never leaves the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSmsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var sender by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SmsTestResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اختبار رسالة SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "التطبيق بيتعرف تلقائيًا على أشهر صيغ رسائل البنوك المصرية وInstaPay من غير ما تحتاج تظبط حاجة. " +
                    "الصفحة دي بس عشان تتأكد إن رسالة بنكك بتتقرا صح — النص بيفضل على موبايلك بس.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = sender, onValueChange = { sender = it },
                label = { Text("رقم/اسم المرسل (اختياري)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("نص الرسالة") }, modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            OutlinedButton(
                onClick = { result = viewModel.testSmsMessage(sender, body) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("اختبار") }

            result?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (r.matched) "تم التعرف على الرسالة ✓" else "لم يتم التعرف عليها كمصروف",
                            fontWeight = FontWeight.Medium,
                            color = if (r.matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (r.matched) {
                            Text("المبلغ: ${r.amount}")
                            Text("الجهة: ${r.merchant}")
                            Text("البنك: ${r.bankName}")
                            Text("الفئة المقترحة: ${r.categoryName}")
                        } else {
                            Text(
                                "لو الرسالة دي فعلًا عملية خصم ومش متعرف عليها، ابعتلي نص الرسالة (بأرقام وهمية) في المحادثة وأزوّد التعرف عليها.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
