package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * شاشة "التطبيق قفل آخر مرة".
 *
 * التطبيق بيتوزّع كـ APK بره المتجر، فلما بيقفل عند الفتح المستخدم مبيشوفش أي
 * سبب — ولا بيقدر يوصل لشاشة الإعدادات اللي بتعرض تقرير الانهيار، لأنها جوه
 * التطبيق اللي مش بيفتح من الأساس. الشاشة دي بتتعرض **قبل** أي حاجة تانية في
 * [com.localexpense.tracker.MainActivity]: مفيش ViewModel، مفيش قاعدة بيانات،
 * ومفيش ثيم مخصص — عشان تفضل قادرة تظهر حتى لو كل ده هو اللي بيقع.
 *
 * مقصود إنها تستخدم MaterialTheme الافتراضي مش ExpenseTrackerTheme: أي حاجة
 * إضافية هنا معناها احتمال إن شاشة الخطأ نفسها تفشل.
 */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    MaterialTheme {
        Surface {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("التطبيق قفل آخر مرة", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ده تفصيل الخطأ اللي حصل. اضغط \"نسخ\" وابعته، وبعد كده " +
                        "\"متابعة\" عشان تجرب تفتح التطبيق تاني.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(report, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("نسخ") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onContinue) { Text("متابعة") }
                }
            }
        }
    }
}
