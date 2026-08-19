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
                onOpenRecurring = { navController.navigate(Routes.RECURRING) }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TEST_SMS) {
            TestSmsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECURRING) {
            RecurringExpensesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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

            AddExpenseScreen(
                categories = categories,
                onSaveExpense = { amount, merchant, categoryName ->
                    viewModel.addManualExpense(amount, merchant, categoryName)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
