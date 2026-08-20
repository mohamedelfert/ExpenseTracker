plugins {
    // AGP 8.13.0: أول نسخة معلنة رسميًا بدعم compileSdk/targetSdk 36
    // (Android 16) لحد API 36.1. النسخة القديمة 8.6.1 اتعملت قبل صدور
    // Android 16 استقرار، فمكنش عندها دعم رسمي - ده كان ممكن يدي تحذير
    // أو يفشل حسب صرامة الإعدادات.
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
