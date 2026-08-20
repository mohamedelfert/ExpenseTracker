package com.localexpense.tracker.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

/**
 * مصادقة بالبصمة/الوجه عن طريق androidx.biometric — المكتبة الوحيدة اللي
 * اتضافت في المراحل دي، وسببها إن المصادقة الحيوية مينفعش تتكتب بالإيد
 * (لازم تمر على النظام).
 *
 * BIOMETRIC_WEAK + DEVICE_CREDENTIAL: لو الجهاز مفيهوش بصمة، المستخدم يفتح
 * برقم/نمط الجهاز — أحسن من إننا نقفل الشاشة في وشه.
 */
object BiometricAuth {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (!isAvailable(activity)) {
            onFailure("المصادقة الحيوية غير متاحة على الجهاز")
            return
        }

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

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح مصروفاتي")
                .setSubtitle("استخدم البصمة أو رقم الجهاز")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}
