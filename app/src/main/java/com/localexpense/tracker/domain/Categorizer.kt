package com.localexpense.tracker.domain

import com.localexpense.tracker.data.MerchantRule

/** منين جِت الفئة — بيتعرض للمستخدم عشان التصنيف يبقى مفهوم مش سحر. */
enum class CategorySource { MERCHANT_MAPPING, MERCHANT_RULE, KEYWORD, DEFAULT }

data class CategoryDecision(
    val categoryName: String,
    val source: CategorySource
)

const val DEFAULT_CATEGORY = "عام"

/**
 * محرّك التصنيف (المرحلة 5). الترتيب ثابت ومحدّد سلفًا:
 *
 * 1. الربط الصريح للجهة (جدول merchants) — المستخدم قال "طلبات = مطاعم".
 * 2. قواعد الجهات (merchant_rules) بالأولوية الأعلى، وعند التساوي الأقدم.
 * 3. الكلمات المفتاحية من نص الرسالة (منطق SmsParser الحالي).
 * 4. الفئة الافتراضية.
 *
 * قواعد الـ SMS (جدول sms_rules) بتتطبق قبل كده أثناء تحليل الرسالة نفسها —
 * هي اللي بتحدد المبلغ والبنك والجهة — فنتيجتها بتوصل هنا كـ [keywordCategory].
 *
 * مفيش أي مسار هنا بيغيّر فئة اختارها المستخدم بإيده: التصنيف بيتنادى وقت
 * إنشاء الحركة بس، أو لما المستخدم يطلب صريح "طبّق على كل حركات الجهة دي".
 */
fun categorize(
    merchant: String,
    rules: List<MerchantRule>,
    explicitMerchantCategory: String? = null,
    keywordCategory: String? = null,
    default: String = DEFAULT_CATEGORY
): CategoryDecision {
    if (!explicitMerchantCategory.isNullOrBlank()) {
        return CategoryDecision(explicitMerchantCategory, CategorySource.MERCHANT_MAPPING)
    }

    val normalized = normalizeMerchant(merchant)
    if (normalized.isNotEmpty()) {
        val match = rules
            .filter { it.isEnabled && it.pattern.isNotBlank() }
            .sortedWith(compareByDescending<MerchantRule> { it.priority }.thenBy { it.id })
            .firstOrNull { rule ->
                val pattern = normalizeMerchant(rule.pattern)
                pattern.isNotEmpty() && (normalized == pattern || normalized.contains(pattern))
            }
        if (match != null) return CategoryDecision(match.categoryName, CategorySource.MERCHANT_RULE)
    }

    if (!keywordCategory.isNullOrBlank() && keywordCategory != default) {
        return CategoryDecision(keywordCategory, CategorySource.KEYWORD)
    }

    return CategoryDecision(default, CategorySource.DEFAULT)
}
