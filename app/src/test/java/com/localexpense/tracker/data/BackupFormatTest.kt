package com.localexpense.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار صيغة النسخة الاحتياطية (المطلوب في الـ spec: export / import / ملف
 * غير صالح / ملف تالف / نسخة غير مدعومة). الكتابة والقراءة الفعلية للملف
 * محتاجة جهاز، لكن التحويل من وإلى JSON منطق خالص وبيتختبر هنا.
 */
class BackupFormatTest {

    private fun sampleSnapshot() = BackupSnapshot(
        expenses = listOf(
            Expense(
                id = 1,
                amountMinor = 12_550,
                type = TransactionType.EXPENSE,
                merchant = "طلبات",
                bankName = "CIB",
                timestamp = 1_700_000_000_000,
                rawBody = "خصم مبلغ 125.50 EGP",
                categoryName = "مطاعم",
                source = TransactionSource.SMS,
                referenceId = "REF12345",
                note = "عشاء",
                isVerified = true,
                rawHash = "abc123"
            ),
            Expense(
                id = 2,
                amountMinor = 2_500_000,
                type = TransactionType.INCOME,
                merchant = "راتب",
                bankName = "دخل",
                timestamp = 1_700_100_000_000,
                rawBody = "",
                categoryName = "عام"
            )
        ),
        categories = listOf(Category(1, "مطاعم", true)),
        budgets = listOf(Budget("مطاعم", 500_000), Budget(Budget.OVERALL_KEY, 2_000_000)),
        recurring = listOf(
            RecurringExpense(
                id = 1, amountMinor = 35_000, merchant = "نتفليكس", bankName = "CIB",
                categoryName = "ترفيه", dayOfMonth = 25, lastAddedMonth = "2026-08",
                frequency = Frequency.MONTHLY, isSubscription = true
            )
        ),
        accounts = listOf(Account(1, "CIB", AccountType.BANK)),
        merchants = listOf(Merchant(1, "طلبات", "طلبات", "مطاعم")),
        merchantRules = listOf(MerchantRule(1, "طلبات", "مطاعم", 100)),
        installments = listOf(
            Installment(
                id = 1, title = "لابتوب", totalMinor = 2_400_000, installmentMinor = 200_000,
                count = 12, paidCount = 4, startDate = 1_700_000_000_000, nextDueDate = 1_702_000_000_000
            )
        )
    )

    @Test
    fun `round trip preserves every table and every money value`() {
        val original = sampleSnapshot()
        val restored = BackupManager.decode(BackupManager.encode(original).toString())

        assertEquals(original.totalRows, restored.totalRows)
        assertEquals(12_550, restored.expenses[0].amountMinor)
        assertEquals(TransactionType.INCOME, restored.expenses[1].type)
        assertEquals("REF12345", restored.expenses[0].referenceId)
        assertTrue(restored.expenses[0].isVerified)
        assertEquals(TransactionSource.SMS, restored.expenses[0].source)
        assertEquals(2_000_000, restored.budgets.first { it.categoryName == Budget.OVERALL_KEY }.limitMinor)
        assertTrue(restored.recurring[0].isSubscription)
        assertEquals(4, restored.installments[0].paidCount)
        assertEquals(1_600_000, restored.installments[0].remainingMinor)
    }

    @Test
    fun `rejects a file that is not a backup`() {
        val error = assertThrows(BackupManager.BackupFormatException::class.java) {
            BackupManager.decode("""{"hello":"world"}""")
        }
        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `rejects a newer format version`() {
        assertThrows(BackupManager.BackupFormatException::class.java) {
            BackupManager.decode("""{"formatVersion":99,"expenses":[]}""")
        }
    }

    @Test
    fun `rejects a backup missing the transactions table`() {
        assertThrows(BackupManager.BackupFormatException::class.java) {
            BackupManager.decode("""{"formatVersion":1,"categories":[]}""")
        }
    }

    @Test
    fun `rejects corrupt json`() {
        // مش BackupFormatException - JSONException، واللي بينادي بيترجمها لرسالة
        // "الملف تالف" (راجع BackupManager.restore).
        assertThrows(Exception::class.java) { BackupManager.decode("{not json at all") }
    }
}
