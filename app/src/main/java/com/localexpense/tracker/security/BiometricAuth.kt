package com.localexpense.tracker.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

/**
 * مصادقة بالبصمة/الوجه عن طريق androidx.biometric — المكتبة الوحيدة اللي
 * اتضافت في المراحل دي، وسببها إن المصادقة الحيوية مينفعش تتكتب بالإيد
 * (لازم تمر على النظام).
 *
 * BIOMETRIC_WEAK + DEVICE_CREDENTIAL: لو الجهاز مفيهوش بصمة، المستخدم يفتح
 * برقم/نمط الجهاز — أحسن من إننا نقفل الشاشة في وشه.
 *
 * ملاحظتان اتعلمناهم بالطريقة الصعبة (minSdk = 26):
 * 1) دمج DEVICE_CREDENTIAL مع مصادقة حيوية مش مدعوم قبل أندرويد 11 (API 30) —
 *    على 8/9/10 لازم BIOMETRIC_WEAK لوحده، ولازم نص لزرار الإلغاء، وإلا
 *    canAuthenticate بيرجّع UNSUPPORTED و build()/authenticate() بيرموا.
 * 2) الـ Context في Compose ساعات بيكون ContextWrapper مش الـ Activity،
 *    فالـ cast المباشر بيرجّع null بصمت — [findActivity] بتفك الغلاف.
 */
object BiometricAuth {

    /** أقوى مجموعة مدعومة فعلاً على الإصدار الحالي. */
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK
        }

    private fun allowsDeviceCredential(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isAvailable(context: Context): Boolean = runCatching {
        BiometricManager.from(context).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** بيفك أي ContextWrapper للوصول للـ Activity الحقيقية. */
    fun findActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return null
    }

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (!isAvailable(activity)) {
            onFailure("المصادقة الحيوية غير متاحة على الجهاز")
            return
        }

        // أي استثناء من المكتبة هنا كان بيقفل التطبيق؛ بنلفّه ونرجّعه كرسالة.
        val error = runCatching {
            val prompt = BiometricPrompt(
                activity,
                androidx.core.content.ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onFailure(errString.toString())
                    }
                }
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح مصروفاتي")
                .setSubtitle(
                    if (allowsDeviceCredential()) "استخدم البصمة أو رقم الجهاز" else "استخدم البصمة"
                )
                .setAllowedAuthenticators(authenticators())
                .apply {
                    // مطلوب لما DEVICE_CREDENTIAL مش مسموح، وممنوع لما يكون مسموح.
                    if (!allowsDeviceCredential()) setNegativeButtonText("استخدم الرقم السري")
                }
                .build()

            prompt.authenticate(info)
        }.exceptionOrNull()

        if (error != null) {
            onFailure("تعذّر تشغيل المصادقة: ${error.message ?: "خطأ غير متوقع"}")
        }
    }
}
