package com.localexpense.tracker.sources

import com.localexpense.tracker.data.TransactionSource
import com.localexpense.tracker.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سجل المصادر (المرحلة 17، بند 37): التعرّف على البنك من المرسل أو نص
 * الرسالة، وقائمة الباكيدجات المسموح قراءة إشعاراتها.
 */
class TransactionSourcesTest {

    @Test
    fun `resolves banks from sender or body`() {
        assertEquals("CIB", TransactionSources.bankNameFor("CIB", "تم خصم مبلغ 100"))
        assertEquals("Bank-AlAhly", TransactionSources.bankNameFor("NBE-Alerts", ""))
        assertEquals("Bank-AlAhly", TransactionSources.bankNameFor("", "رسالة من البنك الأهلي"))
        assertEquals("InstaPay", TransactionSources.bankNameFor("INSTAPAY", ""))
        assertEquals("فودافون كاش", TransactionSources.bankNameFor("VF-CASH", ""))
    }

    @Test
    fun `unknown sender falls back to the sender itself`() {
        assertEquals("SOME-BANK", TransactionSources.bankNameFor("SOME-BANK", "تم خصم 50"))
        assertEquals("بنك آخر", TransactionSources.bankNameFor("", ""))
    }

    @Test
    fun `notification package allow list is exact match only`() {
        assertTrue("com.cib.cibmobile" in TransactionSources.ALLOWED_PACKAGES)
        // تطبيق خبيث باسم قريب مينفعش يعدّي
        assertFalse("com.cib.cibmobile.fake" in TransactionSources.ALLOWED_PACKAGES)
        assertTrue("com.google.android.apps.messaging" in TransactionSources.ALLOWED_PACKAGES)
    }

    @Test
    fun `package maps back to its source spec`() {
        assertEquals("QNB", TransactionSources.forPackage("com.qnbalahli.mobile")?.bankName)
        assertNull(TransactionSources.forPackage("com.android.mms"))   // تطبيق رسائل، مش بنك
    }

    @Test
    fun `parser uses the registry for the bank name`() {
        val expense = SmsParser.parseSms(
            sender = "QNB",
            body = "تم خصم مبلغ 250.00 EGP لدى Carrefour",
            timestamp = 1_700_000_000_000,
            source = TransactionSource.SMS
        )
        requireNotNull(expense)
        assertEquals("QNB", expense.bankName)
        assertEquals(25_000, expense.amountMinor)
    }

    @Test
    fun `generic amount extraction handles both currency positions`() {
        assertEquals(12_550L, SmsParser.extractAmountMinor("تم خصم مبلغ 125.50 من حسابك"))
        assertEquals(12_550L, SmsParser.extractAmountMinor("Purchase 125.50 EGP approved"))
        assertEquals(125_075L, SmsParser.extractAmountMinor("خصم EGP 1,250.75"))
        assertNull(SmsParser.extractAmountMinor("رصيدك الحالي غير متاح"))
    }
}
