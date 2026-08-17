package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenRule: (Long) -> Unit
) {
    val rules by viewModel.rules.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قواعد الرسائل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenRule(-1L) }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة قاعدة جديدة")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "كل رسالة SMS بتتفحص مقابل القواعد دي. لو رسالة بنكك أو انستا باي مش بتتسجل صح، افتح القاعدة واضغط \"اختبار على رسالة حقيقية\" لتظبط الـ Regex.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(rules) { rule ->
                    RuleRow(rule = rule, onClick = { onOpenRule(rule.id) }, onToggle = {
                        viewModel.saveRule(rule.copy(isEnabled = !rule.isEnabled))
                    })
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: SmsRule, onClick: () -> Unit, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(rule.bankName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (rule.isBuiltIn) "قاعدة افتراضية — راجعها قبل الاعتماد عليها" else "قاعدة مخصصة",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Switch(checked = rule.isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "تعديل")
            }
        }
    }
}
