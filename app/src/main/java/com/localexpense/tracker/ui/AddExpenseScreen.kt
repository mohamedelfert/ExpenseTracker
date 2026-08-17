package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة مصروف يدوي") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = merchant, onValueChange = { merchant = it },
                label = { Text("الوصف / الجهة") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("الفئة (اختياري)") }, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val value = amount.toDoubleOrNull()
                    if (value != null && merchant.isNotBlank()) {
                        viewModel.addManualExpense(value, merchant, category.ifBlank { "غير مصنف" })
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ") }
        }
    }
}
