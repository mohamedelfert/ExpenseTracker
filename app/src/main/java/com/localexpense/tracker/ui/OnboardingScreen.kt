package com.localexpense.tracker.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localexpense.tracker.ui.theme.finance
import kotlinx.coroutines.launch

/**
 * أول شاشة يشوفها المستخدم قبل ما يدخل التطبيق فعليًا — مرة واحدة بس، وبعدين
 * بتتسجل في [com.localexpense.tracker.data.AppSettings.hasCompletedOnboarding]
 * فمش هتظهر تاني. الهدف إنها تشرح: التطبيق أوفلاين بالكامل، إزاي الاستيراد
 * التلقائي شغال، الحماية، وإيه اللي التطبيق بيقدمه — من غير ما تطلب أي إذن
 * فعليًا هنا (طلب الأذونات نفسه بيحصل بعدين من الشاشة الرئيسية زي ما هو).
 */
private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Savings,
        title = "أهلاً بيك في مصروفاتي",
        description = "تطبيق مصاريف عربي بالكامل بيشتغل من غير إنترنت خالص. بياناتك المالية متخزنة ومشفّرة على جهازك بس، ومفيش سيرفر ولا مشاركة بيانات مع حد."
    ),
    OnboardingPage(
        icon = Icons.Filled.AutoAwesome,
        title = "تسجيل تلقائي للمصاريف",
        description = "التطبيق يقدر يتعرف على رسائل وإشعارات البنوك ويسجل المصروف لوحده أول ما يوصل. الميزة دي اختيارية بالكامل وتقدر تتحكم فيها من الإعدادات في أي وقت."
    ),
    OnboardingPage(
        icon = Icons.Filled.Lock,
        title = "بياناتك محمية",
        description = "قاعدة البيانات مشفّرة بالكامل على جهازك. تقدر كمان تحط رقم سري أو تفعّل البصمة عشان محدش غيرك يفتح التطبيق."
    ),
    OnboardingPage(
        icon = Icons.Filled.Insights,
        title = "تابع مصاريفك بذكاء",
        description = "ميزانيات شهرية، أهداف ادخار، تحليلات ورسوم بيانية، وويدجت على الشاشة الرئيسية — كل حاجة تساعدك تتحكم في فلوسك."
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(MaterialTheme.finance.heroGradient)
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // تخطي — دايمًا ظاهر غير في آخر صفحة، بيوديك على طول لنهاية الرحلة.
        if (!isLastPage) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text("تخطي", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(6f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            PageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (isLastPage) "يلا نبدأ" else "التالي",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = tween(250),
                label = "dotWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.4f,
                animationSpec = tween(250),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha))
            )
        }
    }
}