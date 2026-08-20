package com.localexpense.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * جهة/تاجر (طلبات، كارفور، أوبر...). [normalizedName] هو المفتاح الحقيقي
 * للمطابقة (راجع domain/MerchantNormalizer) عشان "TALABAT" و "Talabat.com"
 * و "طلبات" ما يبقوش 3 جهات مختلفة.
 */
@Entity(
    tableName = "merchants",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class Merchant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    /** الفئة المرتبطة بالجهة دي (لو المستخدم حددها) - بتغذّي التصنيف التلقائي. */
    @ColumnInfo(defaultValue = "")
    val categoryName: String = "",
    @ColumnInfo(defaultValue = "")
    val icon: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0
)
