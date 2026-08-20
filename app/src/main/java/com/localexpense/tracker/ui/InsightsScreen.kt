package com.localexpense.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localexpense.tracker.domain.InsightLevel
import com.localexpense.tracker.viewmodel.FinanceViewModel

/**
 * كل الرؤى (المرحلة 11). كل رؤية قابلة للإخفاء، والإخفاء بيفضل محفوظ
 * (SharedPreferences) عشان مترجعش تظهر تاني بعد كل إعادة حساب.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(finance: FinanceViewModel, onBack: () -> Unit) {
    val insights by finance.insights.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رؤى مالية") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = { finance.restoreDismissedInsights() }) { Text("رجّع المخفي") }
                }
            )
        }
    ) { padding ->
        if (insights.isEmpty()) {
            EmptyState(
                title = "مفيش رؤى دلوقتي",
                hint = "الرؤى بتظهر لما يبقى فيه بيانات كفاية للمقارنة، أو لما تقرب من ميزانية.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "كل الأرقام في الرؤى دي محسوبة من حركاتك المسجّلة، وتقدر تتأكد منها من الداشبورد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(insights.size) { index ->
                val insight = insights[index]
                val accent = when (insight.level) {
                    InsightLevel.INFO -> MaterialTheme.colorScheme.primary
                    InsightLevel.WARNING -> MaterialTheme.finance.warning
                    InsightLevel.ALERT -> MaterialTheme.finance.expense
                }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = accent)
                        }
                        IconButton(onClick = { finance.dismissInsight(insight) }) {
                            Icon(Icons.Filled.Close, contentDescription = "إخفاء")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
