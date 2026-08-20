package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.security.AppLock
import com.localexpense.tracker.security.BiometricAuth

/**
 * شاشة القفل (المرحلة 16). لو البصمة مفعّلة بتفتح تلقائيًا أول ما الشاشة
 * تظهر، والرقم السري بيفضل بديل دايمًا.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometricEnabled = AppLock.isBiometricEnabled(context) && BiometricAuth.isAvailable(context)

    fun tryBiometric() {
        // الـ Context في Compose ممكن يكون ContextWrapper، فالـ cast المباشر
        // كان بيرجّع null وما يحصلش أي حاجة لما تضغط "استخدم البصمة".
        val activity = BiometricAuth.findActivity(context)
        if (activity == null) {
            error = "تعذّر فتح شاشة البصمة، استخدم الرقم السري"
            return
        }
        BiometricAuth.prompt(
            activity = activity,
            onSuccess = onUnlocked,
            onFailure = { message -> error = message }
        )
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) tryBiometric()
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("مصروفاتي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "التطبيق مقفول. ادخل الرقم السري للمتابعة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { c -> c.isDigit() }.take(8)
                error = null
            },
            label = { Text("الرقم السري") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (AppLock.verifyPin(context, pin)) onUnlocked() else error = "الرقم غلط"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("فتح") }

        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { tryBiometric() }, modifier = Modifier.fillMaxWidth()) {
                Text("استخدم البصمة")
            }
        }
    }
}
