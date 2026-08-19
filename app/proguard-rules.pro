# قواعد ProGuard/R8 الخاصة بتطبيق مصروفاتي.
# الهدف: تقليل حجم الـ APK/AAB وتشويش الكود من غير ما نكسر Room أو الـ
# Broadcast Receiver / NotificationListenerService (بيتم استدعاؤهم من النظام
# مباشرة عن طريق الاسم المُعلَن في AndroidManifest، فلازم نحافظ عليهم).

# --- Room ---
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- كيانات قاعدة البيانات (Entities) وكائنات النقل بين الطبقات ---
# محافظين على أسماء الحقول عشان الـ reflection/serialization الداخلي في Room
-keepclassmembers class com.localexpense.tracker.data.** {
    <fields>;
    <init>(...);
}

# --- المكوّنات اللي بيستدعيها نظام أندرويد بالاسم عن طريق الـ Manifest ---
-keep class com.localexpense.tracker.receiver.SmsReceiver { *; }
-keep class com.localexpense.tracker.receiver.ExpenseNotificationListener { *; }
-keep class com.localexpense.tracker.MainActivity { *; }

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- Kotlin metadata (يمنع تحذيرات غير ضرورية وقت البناء) ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlin.**

# --- منع حذف الـ Regex/parsing logic الخاص بتحليل رسائل البنوك عن طريق الخطأ ---
-keep class com.localexpense.tracker.parser.** { *; }
