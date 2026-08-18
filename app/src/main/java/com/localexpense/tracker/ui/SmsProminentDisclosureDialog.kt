package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SmsProminentDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE0E0E0),
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF80CBC4),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "إفصاح هامة حول الخصوصية وأذونات الرسائل",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "يحتاج التطبيق إلى إذن قراءة الرسائل النصية القصيرة (READ_SMS) لتقديم ميزته الأساسية وهي:",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• التعرف التلقائي على إشعارات المعاملات البنكية والمصروفات وتسجيلها داخل التطبيق.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF80CBC4)
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFA5D6A7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ضمان الأمان والخصوصية: يتم تحليل وقراءة المعاملات محلياً 100% على جهازك فقط. لا يتم رفع، مشاركة، أو نقل أي جزء من رسائلك إلى أي سيرفرات أو أطراف خارجية.",
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "قراءة سياسة الخصوصية الكاملة",
                        fontSize = 11.sp,
                        color = Color(0xFF4DB6AC)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("موافقة ومتابعة", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}