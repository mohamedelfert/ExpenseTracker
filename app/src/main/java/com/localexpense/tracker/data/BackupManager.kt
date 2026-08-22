package com.localexpense.tracker.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسخ احتياطي واسترجاع (المرحلة 3 من خطة الترحيل).
 *
 * الشكل: ملف JSON واحد بكل الجداول + رقم نسخة. الكتابة والقراءة بتحصل على
 * ملف المستخدم بيختاره من منتقي ملفات النظام (SAF) — فمفيش أي رفع لأي سيرفر،
 * والمستخدم هو اللي بيقرر الملف يروح فين.
 *
 * التشفير اختياري بكلمة سر يحددها المستخدم وقت التصدير (AES-256-GCM عبر
 * [BackupCrypto]، مستقل تمامًا عن مفتاح تشفير قاعدة البيانات نفسها). لو
 * المستخدم سابها فاضية، الملف بيتحفظ نص عادي زي الأول — قابل للقراءة المباشرة
 * لكن لازم يتحفظ في مكان آمن.
 *
 * org.json جزء من أندرويد نفسه — مفيش أي مكتبة اتضافت للـ (de)serialization.
 */
object BackupManager {

    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/json"

    /** نتيجة الاسترجاع — رسالة واضحة للمستخدم في كل حالة فشل. */
    sealed class RestoreResult {
        data class Success(val rows: Int) : RestoreResult()
        data class Failure(val message: String) : RestoreResult()
        /** الملف مشفّر بكلمة سر ولسه محتاجين ندخلها (أو اللي اتدخلت غلط). */
        data class PasswordRequired(val wasWrong: Boolean) : RestoreResult()
    }

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "expense-tracker-backup-${com.localexpense.tracker.util.dayKey(now)}.json"

    // ===== تصدير =====

    suspend fun export(context: Context, uri: Uri, password: String? = null): Int =
        withContext(Dispatchers.IO) {
            val repository = ExpenseRepository(context)
            val snapshot = repository.snapshot()
            val text = encode(snapshot).toString()
            val output = if (password.isNullOrBlank()) text else encryptEnvelope(text, password)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(output.toByteArray(Charsets.UTF_8))
            } ?: error("تعذّر فتح الملف للكتابة")
            snapshot.totalRows
        }

    /** نص النسخة الاحتياطية كامل - بيستخدمه المسار الاحتياطي للحفظ. */
    suspend fun encodeToText(context: Context, password: String? = null): String =
        withContext(Dispatchers.IO) {
            val text = encode(ExpenseRepository(context).snapshot()).toString()
            if (password.isNullOrBlank()) text else encryptEnvelope(text, password)
        }

    // ===== غلاف التشفير =====
    // ملف مشفّر هو JSON تاني برّاني بيلف نص النسخة الأصلي المشفّر جواه —
    // مفرّق عن ملف النسخة العادي بحقل "encrypted": true، فبنعرف نطلب كلمة
    // السر من المستخدم قبل ما نحاول نفك أي حاجة.

    private fun encryptEnvelope(plainText: String, password: String): String {
        val enc = BackupCrypto.encrypt(plainText, password)
        return JSONObject().apply {
            put("encrypted", true)
            put("kdfIterations", enc.iterations)
            put("salt", with(BackupCrypto) { enc.salt.toBase64() })
            put("iv", with(BackupCrypto) { enc.iv.toBase64() })
            put("ciphertext", with(BackupCrypto) { enc.ciphertext.toBase64() })
        }.toString()
    }

    /** بيرجع true لو الملف اللي المستخدم اختاره محمي بكلمة سر. */
    suspend fun isEncrypted(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return@withContext false
        runCatching { JSONObject(text).optBoolean("encrypted", false) }.getOrDefault(false)
    }

    fun encode(snapshot: BackupSnapshot): JSONObject = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("dbVersion", 7)
        put("exportedAt", System.currentTimeMillis())
        put("expenses", JSONArray().apply { snapshot.expenses.forEach { put(it.toJson()) } })
        put("categories", JSONArray().apply { snapshot.categories.forEach { put(it.toJson()) } })
        put("smsRules", JSONArray().apply { snapshot.smsRules.forEach { put(it.toJson()) } })
        put("budgets", JSONArray().apply { snapshot.budgets.forEach { put(it.toJson()) } })
        put("recurring", JSONArray().apply { snapshot.recurring.forEach { put(it.toJson()) } })
        put("accounts", JSONArray().apply { snapshot.accounts.forEach { put(it.toJson()) } })
        put("merchants", JSONArray().apply { snapshot.merchants.forEach { put(it.toJson()) } })
        put("merchantRules", JSONArray().apply { snapshot.merchantRules.forEach { put(it.toJson()) } })
        put("installments", JSONArray().apply { snapshot.installments.forEach { put(it.toJson()) } })
    }

    // ===== استيراد =====

    suspend fun restore(context: Context, uri: Uri, password: String? = null): RestoreResult =
        withContext(Dispatchers.IO) {
        val rawText = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            return@withContext RestoreResult.Failure("تعذّر قراءة الملف: ${e.message}")
        } ?: return@withContext RestoreResult.Failure("الملف فاضي أو مش موجود")

        val isEncryptedFile = runCatching { JSONObject(rawText).optBoolean("encrypted", false) }
            .getOrDefault(false)

        val text = if (isEncryptedFile) {
            if (password.isNullOrBlank()) return@withContext RestoreResult.PasswordRequired(wasWrong = false)
            try {
                decryptEnvelope(rawText, password)
            } catch (e: BackupCrypto.WrongPasswordException) {
                return@withContext RestoreResult.PasswordRequired(wasWrong = true)
            } catch (e: Exception) {
                return@withContext RestoreResult.Failure("ملف النسخة المشفّر تالف")
            }
        } else rawText

        val snapshot = try {
            decode(text)
        } catch (e: BackupFormatException) {
            return@withContext RestoreResult.Failure(e.message ?: "ملف النسخة غير صالح")
        } catch (e: Exception) {
            return@withContext RestoreResult.Failure("ملف النسخة تالف أو مش JSON صالح")
        }

        // كل الاستبدال في transaction واحدة: لو أي حاجة فشلت، البيانات القديمة
        // بترجع زي ما هي بدل ما نسيب القاعدة نصف فاضية. withTransaction (من
        // room-ktx) هي النسخة الـ suspend - runInTransaction العادية مع
        // runBlocking بتعمل deadlock مع DAOs من نوع suspend.
        val db = AppDatabase.getDatabase(context)
        val repository = ExpenseRepository(context)
        return@withContext try {
            db.withTransaction {
                repository.replaceAll(snapshot)
            }
            RestoreResult.Success(snapshot.totalRows)
        } catch (e: Exception) {
            RestoreResult.Failure("فشل الاسترجاع: ${e.message}")
        }
    }

    private fun decryptEnvelope(envelopeText: String, password: String): String {
        val root = JSONObject(envelopeText)
        val encrypted = BackupCrypto.Encrypted(
            salt = with(BackupCrypto) { root.getString("salt").fromBase64() },
            iv = with(BackupCrypto) { root.getString("iv").fromBase64() },
            ciphertext = with(BackupCrypto) { root.getString("ciphertext").fromBase64() },
            iterations = root.optInt("kdfIterations", BackupCrypto.ITERATIONS)
        )
        return BackupCrypto.decrypt(encrypted, password)
    }

    class BackupFormatException(message: String) : Exception(message)

    fun decode(text: String): BackupSnapshot {
        val root = JSONObject(text)   // نص مش JSON بيرمي JSONException
        val version = root.optInt("formatVersion", -1)
        if (version == -1) throw BackupFormatException("الملف ده مش نسخة احتياطية من التطبيق")
        if (version > FORMAT_VERSION) {
            throw BackupFormatException("النسخة الاحتياطية دي من إصدار أحدث من التطبيق (v$version)")
        }
        if (!root.has("expenses")) throw BackupFormatException("ملف النسخة ناقص - مفيش جدول الحركات")

        return BackupSnapshot(
            expenses = root.array("expenses").map { it.toExpense() },
            categories = root.array("categories").map { it.toCategory() },
            smsRules = root.array("smsRules").map { it.toSmsRule() },
            budgets = root.array("budgets").map { it.toBudget() },
            recurring = root.array("recurring").map { it.toRecurring() },
            accounts = root.array("accounts").map { it.toAccount() },
            merchants = root.array("merchants").map { it.toMerchant() },
            merchantRules = root.array("merchantRules").map { it.toMerchantRule() },
            installments = root.array("installments").map { it.toInstallment() }
        )
    }
}

// ===== تحويلات JSON =====
// مكتوبة بالإيد على org.json (جزء من أندرويد) بدل إضافة مكتبة serialization
// لتسع كلاسات بيانات.

private fun JSONObject.array(name: String): List<JSONObject> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { array.getJSONObject(it) }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (isNull(name)) null else optLong(name)

private fun Expense.toJson() = JSONObject().apply {
    put("id", id)
    put("amountMinor", amountMinor)
    put("currency", currency)
    put("type", type.name)
    put("merchant", merchant)
    put("bankName", bankName)
    put("timestamp", timestamp)
    put("rawBody", rawBody)
    put("categoryName", categoryName)
    put("merchantId", merchantId ?: JSONObject.NULL)
    put("accountId", accountId ?: JSONObject.NULL)
    put("toAccountId", toAccountId ?: JSONObject.NULL)
    put("source", source.name)
    put("note", note)
    put("referenceId", referenceId)
    put("isVerified", isVerified)
    put("rawHash", rawHash)
    put("installmentId", installmentId ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toExpense() = Expense(
    id = optLong("id"),
    amountMinor = getLong("amountMinor"),
    currency = optString("currency", "EGP"),
    type = runCatching { TransactionType.valueOf(optString("type", "EXPENSE")) }
        .getOrDefault(TransactionType.EXPENSE),
    merchant = optString("merchant", ""),
    bankName = optString("bankName", ""),
    timestamp = getLong("timestamp"),
    rawBody = optString("rawBody", ""),
    categoryName = optString("categoryName", "عام"),
    merchantId = optNullableLong("merchantId"),
    accountId = optNullableLong("accountId"),
    toAccountId = optNullableLong("toAccountId"),
    source = runCatching { TransactionSource.valueOf(optString("source", "MANUAL")) }
        .getOrDefault(TransactionSource.MANUAL),
    note = optString("note", ""),
    referenceId = optString("referenceId", ""),
    isVerified = optBoolean("isVerified", false),
    rawHash = optString("rawHash", ""),
    installmentId = optNullableLong("installmentId"),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt")
)

private fun Category.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("isBuiltIn", isBuiltIn)
}

private fun JSONObject.toCategory() = Category(
    id = optLong("id"),
    name = getString("name"),
    isBuiltIn = optBoolean("isBuiltIn", false)
)

private fun SmsRule.toJson() = JSONObject().apply {
    put("id", id)
    put("bankName", bankName)
    put("senderPattern", senderPattern)
    put("debitKeywordPattern", debitKeywordPattern)
    put("amountPattern", amountPattern)
    put("merchantPattern", merchantPattern)
    put("isEnabled", isEnabled)
    put("isBuiltIn", isBuiltIn)
}

private fun JSONObject.toSmsRule() = SmsRule(
    id = optLong("id"),
    bankName = optString("bankName", ""),
    senderPattern = optString("senderPattern", ""),
    debitKeywordPattern = optString("debitKeywordPattern", ""),
    amountPattern = optString("amountPattern", ""),
    merchantPattern = optString("merchantPattern", ""),
    isEnabled = optBoolean("isEnabled", true),
    isBuiltIn = optBoolean("isBuiltIn", false)
)

private fun Budget.toJson() = JSONObject().apply {
    put("categoryName", categoryName); put("limitMinor", limitMinor)
}

private fun JSONObject.toBudget() = Budget(
    categoryName = getString("categoryName"),
    limitMinor = getLong("limitMinor")
)

private fun RecurringExpense.toJson() = JSONObject().apply {
    put("id", id)
    put("amountMinor", amountMinor)
    put("merchant", merchant)
    put("bankName", bankName)
    put("categoryName", categoryName)
    put("dayOfMonth", dayOfMonth)
    put("lastAddedMonth", lastAddedMonth)
    put("name", name)
    put("frequency", frequency.name)
    put("intervalDays", intervalDays)
    put("nextDueDate", nextDueDate)
    put("isActive", isActive)
    put("accountId", accountId ?: JSONObject.NULL)
    put("isSubscription", isSubscription)
    put("reminderDaysBefore", reminderDaysBefore)
}

private fun JSONObject.toRecurring() = RecurringExpense(
    id = optLong("id"),
    amountMinor = getLong("amountMinor"),
    merchant = optString("merchant", ""),
    bankName = optString("bankName", ""),
    categoryName = optString("categoryName", "عام"),
    dayOfMonth = optInt("dayOfMonth", 1),
    lastAddedMonth = optString("lastAddedMonth", ""),
    name = optString("name", ""),
    frequency = runCatching { Frequency.valueOf(optString("frequency", "MONTHLY")) }
        .getOrDefault(Frequency.MONTHLY),
    intervalDays = optInt("intervalDays", 30),
    nextDueDate = optLong("nextDueDate"),
    isActive = optBoolean("isActive", true),
    accountId = optNullableLong("accountId"),
    isSubscription = optBoolean("isSubscription", false),
    reminderDaysBefore = optInt("reminderDaysBefore", 1)
)

private fun Account.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("type", type.name)
    put("currency", currency)
    put("isActive", isActive)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toAccount() = Account(
    id = optLong("id"),
    name = getString("name"),
    type = runCatching { AccountType.valueOf(optString("type", "OTHER")) }.getOrDefault(AccountType.OTHER),
    currency = optString("currency", "EGP"),
    isActive = optBoolean("isActive", true),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt")
)

private fun Merchant.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("normalizedName", normalizedName)
    put("categoryName", categoryName)
    put("icon", icon)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toMerchant() = Merchant(
    id = optLong("id"),
    name = getString("name"),
    normalizedName = getString("normalizedName"),
    categoryName = optString("categoryName", ""),
    icon = optString("icon", ""),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt")
)

private fun MerchantRule.toJson() = JSONObject().apply {
    put("id", id)
    put("pattern", pattern)
    put("categoryName", categoryName)
    put("priority", priority)
    put("isEnabled", isEnabled)
    put("createdAt", createdAt)
}

private fun JSONObject.toMerchantRule() = MerchantRule(
    id = optLong("id"),
    pattern = getString("pattern"),
    categoryName = getString("categoryName"),
    priority = optInt("priority", 10),
    isEnabled = optBoolean("isEnabled", true),
    createdAt = optLong("createdAt")
)

private fun Installment.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("totalMinor", totalMinor)
    put("installmentMinor", installmentMinor)
    put("count", count)
    put("paidCount", paidCount)
    put("startDate", startDate)
    put("nextDueDate", nextDueDate)
    put("merchant", merchant)
    put("categoryName", categoryName)
    put("accountId", accountId ?: JSONObject.NULL)
    put("isActive", isActive)
    put("createdAt", createdAt)
}

private fun JSONObject.toInstallment() = Installment(
    id = optLong("id"),
    title = getString("title"),
    totalMinor = getLong("totalMinor"),
    installmentMinor = getLong("installmentMinor"),
    count = optInt("count", 1),
    paidCount = optInt("paidCount", 0),
    startDate = optLong("startDate"),
    nextDueDate = optLong("nextDueDate"),
    merchant = optString("merchant", ""),
    categoryName = optString("categoryName", "عام"),
    accountId = optNullableLong("accountId"),
    isActive = optBoolean("isActive", true),
    createdAt = optLong("createdAt")
)