package com.localexpense.tracker.money

/**
 * تمثيل نقدي دقيق: المبلغ متخزّن كوحدات صغرى صحيحة (قروش) عشان نتجنب أخطاء
 * الأرقام العشرية (Double) في الحسابات المالية. 125.50 ج.م = 12550.
 *
 * العملة متخزّنة مع كل مبلغ عشان النموذج يفضل قابل لتعدد العملات لاحقًا،
 * لكن التطبيق حاليًا بيفترض عملة واحدة (EGP) ومفيش تحويل بين العملات.
 */
data class Money(
    val amountMinor: Long,
    val currency: String = DEFAULT_CURRENCY
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add different currencies: $currency vs ${other.currency}"
        }
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract different currencies: $currency vs ${other.currency}"
        }
        return Money(amountMinor - other.amountMinor, currency)
    }

    companion object {
        const val DEFAULT_CURRENCY = "EGP"
    }
}
