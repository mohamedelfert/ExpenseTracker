package com.localexpense.tracker.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

/**
 * مصادقة بالبصمة/الوجه (المرحلة 16) عن طريق androidx.biometric.
 *
 * تفاصيل مهمة اتعلمناها بالطريقة الصعبة:
 *
 * 1. **دمج DEVICE_CREDENTIAL مع مصادقة حيوية مش مدعوم قبل أندرويد 11 (API 30).**
 *    لو طلبناه على 8/9/10، `canAuthenticate` بيرجع UNSUPPORTED و`build()` بيرمي
 *    استثناء — فالبصمة كانت "مش شغالة" من غير أي تفسير. عشان كده مجموعة
 *    الـ authenticators بتتحدد حسب إصدار النظام.
 * 2. **لو DEVICE_CREDENTIAL مش مسموح، لازم نص لزرار الإلغاء** وإلا `build()`
 *    بيرمي IllegalArgumentException.
 * 3. **الـ Context في Compose ممكن يكون ContextWrapper** مش الـ Activity، فالـ
 *    cast المباشر كان بيفشل بصمت. [findActivity] بتفك الغلاف.
 * 4. كل نداء على المكتبة ملفوف في runCatching وبيرجّع رسالة، لأن أي استثناء
 *    هنا كان بيقفل التطبيق أو يخلي الزرار ما يعملش حاجة.
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

    fun status(context: Context): Int = runCatching {
        BiometricManager.from(context).canAuthenticate(authenticators())
    }.getOrDefault(BiometricManager.BIOMETRIC_STATUS_UNKNOWN)

    fun isAvailable(context: Context): Boolean =
        status(context) == BiometricManager.BIOMETRIC_SUCCESS

    /** رسالة توضح سبب عدم التوفّر، أو null لو متاحة. بتظهر في الإعدادات. */
    fun unavailableReason(context: Context): String? = when (status(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "مفيش بصمة أو وجه مسجّل على الجهاز — سجّلها من إعدادات النظام الأول."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "الجهاز ده مفيهوش قارئ بصمة أو تعرّف وجه."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "قارئ البصمة مشغول أو مش متاح دلوقتي، جرّب تاني."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "النظام محتاج تحديث أمني قبل ما البصمة تشتغل."
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
            "إصدار الأندرويد ده مش بيدعم النوع المطلوب من المصادقة."
        else -> "المصادقة الحيوية غير متاحة على الجهاز ده."
    }

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
        unavailableReason(activity)?.let { reason ->
            onFailure(reason)
            return
        }

        val result = runCatching {
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

            BiometricPrompt(
                activity,
                androidx.core.content.ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // إلغاء المستخدم مش خطأ يستاهل رسالة حمراء.
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (!cancelled) onFailure(errString.toString())
                    }
                }
            ).authenticate(info)
        }

        result.exceptionOrNull()?.let { error ->
            onFailure("تعذّر تشغيل المصادقة: ${error.message ?: "خطأ غير متوقع"}")
        }
    }
}
