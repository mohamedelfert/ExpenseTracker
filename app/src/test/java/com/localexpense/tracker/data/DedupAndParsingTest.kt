package com.localexpense.tracker.data

import com.localexpense.tracker.parser.SmsParser
import com.localexpense.tracker.util.rawMessageHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تحليل الرسائل ومنع التكرار (المرحلة 17). الفحص الفعلي على قاعدة البيانات
 * محتاج جهاز، فاللي بيتختبر هنا هو المفاتيح اللي الفحص بيعتمد عليها:
 * نوع العملية، رقم المرجع، وبصمة النص.
 */
class DedupAndParsingTest {

    @Test
    fun `detects expense income and refund`() {
        assertEquals(TransactionType.EXPENSE, SmsParser.detectType("تم خصم مبلغ 100 EGP"))
        assertEquals(TransactionType.INCOME, SmsParser.detectType("تم إيداع راتب 5000 EGP"))
        assertEquals(TransactionType.REFUND, SmsParser.detectType("تم استرداد مبلغ 100 EGP"))
        // رسالة مش عملية مالية
        assertNull(SmsParser.detectType("كود التحقق 4321"))
    }

    @Test
    fun `refund wins over deposit wording`() {
        // رسالة الاسترداد غالبًا فيها كلمة إيداع كمان - لازم تتصنف استرداد
        assertEquals(
            TransactionType.REFUND,
            SmsParser.detectType("تم إيداع مبلغ 100 EGP كاسترداد عن عملية سابقة")
        )
    }

    @Test
    fun `extracts bank reference id when present`() {
        assertEquals("A1B2C3D4", SmsParser.extractReference("عملية ناجحة، رقم المرجع: A1B2C3D4"))
        assertEquals("998877", SmsParser.extractReference("Purchase approved. Ref no 998877"))
        assertEquals("", SmsParser.extractReference("تم خصم 50 جنيه"))
    }

    @Test
    fun `raw hash is stable across spacing and case but not across content`() {
        val a = rawMessageHash("CIB", "تم خصم مبلغ 125.50 EGP")
        val b = rawMessageHash("cib", "تم خصم   مبلغ 125.50 EGP ")
        val c = rawMessageHash("CIB", "تم خصم مبلغ 125.51 EGP")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `parsed sms carries dedup keys and source`() {
        val expense = SmsParser.parseSms(
            sender = "CIB",
            body = "تم خصم مبلغ 125.50 EGP لدى Talabat، رقم المرجع: XY99Z1",
            timestamp = 1_700_000_000_000,
            source = TransactionSource.NOTIFICATION
        )
        requireNotNull(expense)
        assertEquals(12_550, expense.amountMinor)
        assertEquals(TransactionType.EXPENSE, expense.type)
        assertEquals(TransactionSource.NOTIFICATION, expense.source)
        assertEquals("XY99Z1", expense.referenceId)
        assertTrue(expense.rawHash.isNotBlank())
    }

    @Test
    fun `isKnownSource recognizes registered banks regardless of message content`() {
        // CIB مسجّل في TransactionSources.ALL - لازم يترفض كـ "مصدر معروف"
        // حتى لو النص نفسه مش مطابق لأي كلمة دالة.
        assertTrue(SmsParser.isKnownSource("CIB", "أي نص عشوائي", emptyList()))
    }

    @Test
    fun `isKnownSource recognizes enabled custom rules by sender`() {
        val rule = SmsRule(
            bankName = "بنك تجريبي",
            senderPattern = "TESTBANK",
            debitKeywordPattern = "خصم",
            amountPattern = """([\d,]+(?:\.\d{1,2})?)""",
            merchantPattern = ""
        )
        assertTrue(SmsParser.isKnownSource("TESTBANK", "تم خصم 100 EGP", listOf(rule)))
        // نفس القاعدة بس متعطّلة - المفروض ترجع false
        assertEquals(
            false,
            SmsParser.isKnownSource("TESTBANK", "تم خصم 100 EGP", listOf(rule.copy(isEnabled = false)))
        )
    }

    @Test
    fun `isKnownSource returns false for a genuinely unrecognized sender`() {
        assertEquals(
            false,
            SmsParser.isKnownSource("55555", "تم خصم 100 جنيه من حسابك", emptyList())
        )
    }

    @Test
    fun `signed amount excludes transfers from any total`() {
        val base = Expense(
            amountMinor = 10_000, merchant = "x", bankName = "y",
            timestamp = 0, rawBody = ""
        )
        assertEquals(-10_000, base.signedMinor)
        assertEquals(10_000, base.copy(type = TransactionType.INCOME).signedMinor)
        assertEquals(10_000, base.copy(type = TransactionType.REFUND).signedMinor)
        assertEquals(0, base.copy(type = TransactionType.TRANSFER).signedMinor)
    }
}