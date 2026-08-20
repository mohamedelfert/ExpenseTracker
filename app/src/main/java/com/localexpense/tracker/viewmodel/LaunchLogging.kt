package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.util.CrashLog
import kotlinx.coroutines.launch

/**
 * `launch` بيسجّل أي استثناء بدل ما يقفل التطبيق.
 *
 * `viewModelScope.launch` العادي مفيهوش أي معالجة أخطاء، فأي استثناء جوه
 * كوروتين بيتشغّل من `init` (تسجيل الدفعات المستحقة، بناء التحليلات، حركات
 * اليوم...) كان بيوصل للـ handler العام **قبل** ما الواجهة ترسم أي حاجة —
 * يعني التطبيق يقفل عند كل فتح من غير أي رسالة ومن غير طريقة يعرف بيها
 * المستخدم السبب.
 *
 * دلوقتي التطبيق بيفتح، والشاشة اللي فشلت بتفضل فاضية، والسبب الكامل بيتسجّل
 * ويبان في شاشة الإعدادات (زرار "نسخ") — راجع [CrashLog.recordNonFatal].
 *
 * مقصود إنها للمسارات اللي بتتنادى وقت الفتح بس. أي حاجة المستخدم بيضغط
 * عليها بنفسه ولازم تديه ردّ صريح عند الفشل، محلها مش هنا.
 */
fun AndroidViewModel.launchLogging(label: String, block: suspend () -> Unit) {
    viewModelScope.launch {
        runCatching { block() }.onFailure {
            CrashLog.recordNonFatal(getApplication<Application>(), label, it)
        }
    }
}
