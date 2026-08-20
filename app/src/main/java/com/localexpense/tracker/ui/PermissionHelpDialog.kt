package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * شرح "الإعدادات المقيّدة" (Restricted settings) — أندرويد 13 وأحدث.
 *
 * لما التطبيق يتثبّت من ملف APK بره جوجل بلاي، النظام بيمنع منح إذن قراءة
 * الإشعارات ويقول "الإعداد ده غير متاح حاليًا"، وفي شاشة معلومات التطبيق
 * بيظهر تحذير أحمر "تم تقييد التطبيق". ده إجراء أمان من أندرويد نفسه —
 * **مفيش تطبيق يقدر يتخطاه من جواه**، لازم المستخدم يسمح بنفسه من قائمة
 * النقاط الثلاث في شاشة معلومات التطبيق.
 *
 * الشاشة دي بتشرح الخطوات وبتوديه للمكان الصح على طول.
 */
@Composable
fun RestrictedSettingsDialog(
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * لو ممرّرة، بيظهر زرار تالت "كمّل لإعدادات الإشعارات" — بنستخدمه لما
     * الشرح بيظهر **قبل** ما نودّي المستخدم للإعدادات (استباقيًا)، فيقدر يفك
     * التقييد الأول أو يكمّل على طول لو جهازه مش مقيّد أصلاً.
     */
    onContinue: (() -> Unit)? = null,
    proactive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (proactive) "قبل ما تفعّل الإذن" else "الإذن محجوب من النظام؟") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (proactive) {
                        "جهازك بأندرويد 13 أو أحدث والتطبيق متثبّت من ملف APK بره جوجل بلاي، " +
                            "فالنظام غالبًا هيحجب إذن قراءة الإشعارات ويقول \"الإعداد غير متاح " +
                            "حاليًا\". مفيش تطبيق يقدر يشيل التقييد ده من جواه (ده إجراء أمان " +
                            "في أندرويد نفسه) — بس دي الخطوات، وبعدها الإذن هيتفعّل عادي:"
                    } else {
                        "لو ظهرت لك رسالة زي \"الإعداد غير متاح حاليًا\" أو تحذير أحمر " +
                            "\"تم تقييد التطبيق\"، فده إجراء أمان في أندرويد 13 وأحدث لأي تطبيق " +
                            "متثبّت من ملف APK بره جوجل بلاي. مفيش تطبيق يقدر يشيل التقييد ده " +
                            "من جواه — لازم تسمح بيه بنفسك:"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Step(1, "اضغط \"فك التقييد الأول\" تحت — هتفتح شاشة معلومات التطبيق.")
                Step(2, "من فوق على اليمين، افتح قائمة النقاط الثلاث (⋮).")
                Step(3, "اختار \"السماح بالإعدادات المقيّدة\" (Allow restricted settings).")
                Step(4, "ارجع للتطبيق واضغط \"إذن الإشعارات\" تاني — هيشتغل عادي.")
                Spacer(Modifier.height(12.dp))
                Text(
                    "لو زرار القائمة مش ظاهر، جرّب تقفل شاشة معلومات التطبيق وتفتحها تاني " +
                        "بعد ما تحاول تفعّل الإذن من التطبيق مرة واحدة على الأقل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onOpenAppInfo()
                onDismiss()
            }) { Text("فك التقييد الأول") }
        },
        dismissButton = {
            if (onContinue != null) {
                TextButton(onClick = {
                    onContinue()
                    onDismiss()
                }) { Text("كمّل للإعدادات") }
            } else {
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
