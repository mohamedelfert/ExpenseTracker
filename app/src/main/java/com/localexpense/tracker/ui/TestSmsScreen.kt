package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Rule
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
 * التطبيق بيتعرف تلقائيًا على أشهر صيغ رسائل البنوك المصرية وInstaPay، وأي
 * قاعدة مخصصة (من شاشة "قواعد الرسائل") بتتفحص الأول وبتاخد الأولوية.
 * الصفحة دي عشان تتأكد إن رسالة بنكك بتتقرا صح — النص بيفضل على موبايلك بس.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSmsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenRules: () -> Unit
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
                },
                actions = {
                    IconButton(onClick = onOpenRules) {
                        Icon(Icons.Filled.Rule, contentDescription = "قواعد مخصصة")
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
                "التطبيق بيتعرف تلقائيًا على أشهر صيغ رسائل البنوك المصرية وInstaPay. " +
                    "لو عندك بنك أو صيغة رسالة مش متعرف عليها صح، تقدر تضيف قاعدة مخصصة بنفسك " +
                    "من الأيقونة اللي فوق. الصفحة دي عشان تتأكد إن الرسالة بتتقرا صح — النص بيفضل على موبايلك بس.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = onOpenRules,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Rule, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("إدارة القواعد المخصصة")
            }

            OutlinedTextField(
                value = sender, onValueChange = { sender = it },
                label = { Text("رقم/اسم المرسل (اختياري)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("نص الرسالة") }, modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Button(
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
                                "لو الرسالة دي فعلًا عملية خصم، دوس \"إدارة القواعد المخصصة\" فوق وضيف قاعدة جديدة لها.",
                                style = MaterialTheme.typography.labelSmall
                            )
                            OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) {
                                Text("إضافة قاعدة لهذه الرسالة")
                            }
                        }
                    }
                }
            }
        }
    }
}
