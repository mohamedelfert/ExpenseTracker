package com.localexpense.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localexpense.tracker.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val TEST_SMS = "test_sms"
    const val ADD_EXPENSE = "add_expense"
    const val DASHBOARD = "dashboard"
    const val RECURRING = "recurring"
    const val RULES = "rules"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTION_DETAIL = "transaction"
    const val BUDGETS = "budgets"
    const val COMPARE = "compare"
    const val INSIGHTS = "insights"
    const val ACCOUNTS = "accounts"
    const val MERCHANTS = "merchants"
    const val MERCHANT_DETAIL = "merchant"
    const val MERCHANT_RULES = "merchant_rules"
    const val INSTALLMENTS = "installments"
    const val CALENDAR = "calendar"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val ASSISTANT = "assistant"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel,
    smsPermissionGranted: Boolean,
    notificationAccessGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                smsPermissionGranted = smsPermissionGranted,
                notificationAccessGranted = notificationAccessGranted,
                onRequestSmsPermission = onRequestSmsPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenAppSettings = onOpenAppSettings,
                onOpenTestSms = { navController.navigate(Routes.TEST_SMS) },
                onOpenAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenInstallments = { navController.navigate(Routes.INSTALLMENTS) },
                onOpenTransaction = { id -> navController.navigate("${Routes.TRANSACTION_DETAIL}/$id") }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                onOpenCompare = { navController.navigate(Routes.COMPARE) },
                onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                onOpenTransaction = { id -> navController.navigate("${Routes.TRANSACTION_DETAIL}/$id") }
            )
        }

        composable(Routes.TRANSACTIONS) {
            TransactionsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate("${Routes.TRANSACTION_DETAIL}/$id") }
            )
        }

        composable(
            route = "${Routes.TRANSACTION_DETAIL}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            TransactionDetailScreen(
                viewModel = viewModel,
                transactionId = entry.arguments?.getLong("id") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BUDGETS) {
            BudgetsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.COMPARE) {
            CompareMonthsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.INSIGHTS) {
            InsightsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.MERCHANTS) {
            MerchantsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMerchant = { name -> navController.navigate("${Routes.MERCHANT_DETAIL}/$name") }
            )
        }

        composable(
            route = "${Routes.MERCHANT_DETAIL}/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            MerchantDetailScreen(
                viewModel = viewModel,
                merchantName = entry.arguments?.getString("name") ?: "",
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate("${Routes.TRANSACTION_DETAIL}/$id") }
            )
        }

        composable(Routes.MERCHANT_RULES) {
            MerchantRulesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.RECURRING) {
            PlansScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.INSTALLMENTS) {
            InstallmentsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate("${Routes.TRANSACTION_DETAIL}/$id") }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.ASSISTANT) {
            AssistantScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                onOpenMerchants = { navController.navigate(Routes.MERCHANTS) },
                onOpenMerchantRules = { navController.navigate(Routes.MERCHANT_RULES) },
                onOpenSmsRules = { navController.navigate(Routes.RULES) }
            )
        }

        composable(Routes.TEST_SMS) {
            TestSmsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRules = { navController.navigate(Routes.RULES) }
            )
        }

        composable(Routes.RULES) {
            RulesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRule = { ruleId -> navController.navigate("${Routes.RULES}/$ruleId") }
            )
        }

        composable(
            route = "${Routes.RULES}/{ruleId}",
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getLong("ruleId") ?: -1L
            RuleEditScreen(
                viewModel = viewModel,
                ruleId = ruleId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_EXPENSE) {
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val accounts by viewModel.accounts.collectAsStateWithLifecycle()

            AddExpenseScreen(
                categories = categories,
                accounts = accounts,
                onSaveTransaction = { amount, merchant, categoryName, type, accountId, note ->
                    viewModel.addTransaction(
                        amountMinor = amount,
                        merchant = merchant,
                        category = categoryName,
                        type = type,
                        accountId = accountId,
                        note = note
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
