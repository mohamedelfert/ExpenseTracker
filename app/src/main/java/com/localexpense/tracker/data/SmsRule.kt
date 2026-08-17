package com.localexpense.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-editable (or built-in) rule describing how to recognise and parse
 * expense SMS messages from one sender (a bank or InstaPay).
 *
 * All patterns are plain Kotlin/Java regex. Test them from the in-app
 * "Test rule" screen against a real message before saving, since every
 * bank's exact wording differs and the built-in seed rules are best-effort
 * approximations.
 */
@Entity(tableName = "sms_rules")
data class SmsRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val senderPattern: String,      // regex matched against the SMS sender id/number
    val debitKeywordPattern: String, // must match somewhere in the body for this to count as an expense
    val amountPattern: String,       // must contain exactly one capture group -> the amount
    val merchantPattern: String,     // optional, one capture group -> merchant/place. empty = not used
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)
