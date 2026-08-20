package com.localexpense.tracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberUtilsTest {

    @Test fun two_decimals() = assertEquals(12550L, parseAmountMinor("125.50"))

    @Test fun one_decimal_is_padded() = assertEquals(12550L, parseAmountMinor("125.5"))

    @Test fun no_decimals() = assertEquals(12500L, parseAmountMinor("125"))

    @Test fun thousands_separator_is_dropped() = assertEquals(125075L, parseAmountMinor("1,250.75"))

    @Test fun extra_decimals_are_truncated_not_rounded() = assertEquals(199L, parseAmountMinor("1.999"))

    @Test fun arabic_indic_digits_and_separator() = assertEquals(12550L, parseAmountMinor("١٢٥٫٥٠"))

    @Test fun garbage_returns_null() = assertNull(parseAmountMinor("abc"))

    @Test fun empty_returns_null() = assertNull(parseAmountMinor(""))

    @Test fun negative() = assertEquals(-12550L, parseAmountMinor("-125.50"))
}
