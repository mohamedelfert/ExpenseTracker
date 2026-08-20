package com.localexpense.tracker.money

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MoneyTest {

    @Test
    fun adds_without_floating_point_error() {
        // 125.50 + 100.25 = 225.75  ->  12550 + 10025 = 22575
        val result = Money(12550) + Money(10025)
        assertEquals(22575L, result.amountMinor)
        assertEquals("EGP", result.currency)
    }

    @Test
    fun subtracts() {
        assertEquals(2525L, (Money(12550) - Money(10025)).amountMinor)
    }

    @Test
    fun adding_different_currencies_throws() {
        try {
            Money(100, "EGP") + Money(100, "USD")
            fail("expected IllegalArgumentException for mismatched currencies")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun formats_with_grouping_and_symbol() {
        assertEquals("1,250.50 ج.م", formatMinor(125050))
        assertEquals("0.05 ج.م", formatMinor(5))
        assertEquals("-125.50 ج.م", formatMinor(-12550))
        assertEquals("25,000 ج.م", formatMinor(2500000, withDecimals = false))
        assertEquals("100.00 USD", formatMinor(10000, currency = "USD"))
    }

    @Test
    fun plain_decimal_for_csv_has_no_grouping_or_symbol() {
        assertEquals("125.50", minorToPlainDecimal(12550))
        assertEquals("1250.75", minorToPlainDecimal(125075))
        assertEquals("-5.00", minorToPlainDecimal(-500))
    }
}
