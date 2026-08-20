package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * قاعدة "الجهة دي فئتها كذا". بتتولد لما المستخدم يغيّر فئة حركة ويوافق على
 * تطبيقها على كل حركات نفس الجهة، أو بيضيفها بنفسه من شاشة القواعد.
 *
 * [pattern] بيتقارن بالاسم المُطبَّع للجهة (MerchantNormalizer): تطابق كامل
 * أو احتواء. [priority] الأعلى بيكسب، والترتيب ثابت (مفيش أي عشوائية) عشان
 * التصنيف يفضل قابل للتفسير: راجع domain/Categorizer.
 */
@Entity(
    tableName = "merchant_rules",
    indices = [Index("pattern")]
)
data class MerchantRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val categoryName: String,
    @ColumnInfo(defaultValue = "10")
    val priority: Int = 10,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0
)
