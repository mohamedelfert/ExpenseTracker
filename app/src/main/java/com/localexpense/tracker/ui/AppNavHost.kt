package com.localexpense.tracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localexpense.tracker.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val RULES = "rules"
    const val RULE_EDIT = "rule_edit/{ruleId}"
    const val ADD_EXPENSE = "add_expense"
    const val DASHBOARD = "dashboard"
    fun ruleEdit(id: Long) = "rule_edit/$id"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel,
    smsPermissionGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                smsPermissionGranted = smsPermissionGranted,
                onRequestSmsPermission = onRequestSmsPermission,
                onOpenAppSettings = onOpenAppSettings,
                onOpenRules = { navController.navigate(Routes.RULES) },
                onOpenAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RULES) {
            RulesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRule = { id -> navController.navigate(Routes.ruleEdit(id)) }
            )
        }

        composable(Routes.RULE_EDIT) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull() ?: -1L
            RuleEditScreen(
                viewModel = viewModel,
                ruleId = ruleId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_EXPENSE) {
            AddExpenseScreen(
                categories = viewModel.categories, // or state/list of categories
                onSaveExpense = { expense -> viewModel.saveExpense(expense) }
            )
        }
    }
}
