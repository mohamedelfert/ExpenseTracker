plugins {
    // اتبع نسخة AGP 8.6.1 عشان تدعم compileSdk/targetSdk 36 (Android 16) بشكل
    // رسمي - النسخة القديمة 8.5.2 اتعملت قبل صدور Android 16 وممكن تدي تحذير
    // أو تفشل مع compileSdk = 36.
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
