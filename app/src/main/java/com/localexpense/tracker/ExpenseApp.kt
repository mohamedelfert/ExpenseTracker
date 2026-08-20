package com.localexpense.tracker

import android.app.Application
import com.localexpense.tracker.notification.NotificationHelper
import com.localexpense.tracker.util.CrashLog

/**
 * نقطة تهيئة واحدة للتطبيق:
 * 1. سجل الانهيار المحلي (CrashLog) — عشان لو التطبيق قفل فجأة يبان السبب في
 *    شاشة الإعدادات بدل ما يفضل مجهول.
 * 2. قنوات الإشعارات — بتتعمل مرة واحدة بدري بدل ما كل إشعار يتحقق منها.
 */
class ExpenseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        runCatching { NotificationHelper.ensureChannels(this) }
    }
}
