package com.localexpense.tracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.domain.Assistant
import com.localexpense.tracker.viewmodel.FinanceViewModel

/**
 * "اسأل عن مصروفاتك" (المرحلة 19).
 *
 * مساعد محلي: مفيش نموذج ذكاء اصطناعي ولا إنترنت ولا مفتاح API. الأسئلة
 * بتتطابق مع نوايا معروفة، والأرقام كلها محسوبة سلفًا في محرّك التحليلات
 * (راجع domain/Assistant.kt) — التطبيق مبيبعتش أي بيانات لأي مكان.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(finance: FinanceViewModel, onBack: () -> Unit) {
    val messages by finance.assistantMessages.collectAsStateWithLifecycle()
    val context by finance.financialContext.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسأل عن مصروفاتك") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        TextButton(onClick = { finance.clearAssistant() }) { Text("مسح") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "المساعد محلي بالكامل: بيقرا أرقام محسوبة على جهازك بس، ومش بيحسب فلوس بنفسه " +
                    "ومش بيبعت أي حاجة على الإنترنت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Assistant.sampleQuestions.forEach { sample ->
                    AssistChip(onClick = { finance.ask(sample) }, label = { Text(sample) })
                }
            }

            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                if (messages.isEmpty()) {
                    item {
                        EmptyState(
                            "اسأل أي حاجة عن مصروفاتك",
                            context?.let { "بيانات ${it.monthLabel} جاهزة." } ?: "جاري تحضير الأرقام..."
                        )
                    }
                }
                items(messages.size) { index ->
                    val message = messages[index]
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                            ) {
                                Text(
                                    message.question,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                            ) {
                                Text(
                                    message.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("اكتب سؤالك") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    finance.ask(question)
                    question = ""
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "إرسال")
                }
            }
        }
    }
}
