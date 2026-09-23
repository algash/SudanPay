package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BiometricSecurityDialog
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OfflineModeScreen
import com.example.ui.screens.SmsParserScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SudanEmerald
import com.example.ui.viewmodel.SudanPayViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: SudanPayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Ensure RTL Arabic Orientation for authentic Sudanese financial UX
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SudanPayApp(
                        viewModel = viewModel,
                        activity = this@MainActivity
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }
}

sealed class Screen(val route: String, val titleAr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "الرئيسية", Icons.Default.AccountBalanceWallet)
    object Transfer : Screen("transfer", "التحويل", Icons.Default.SwapHoriz)
    object Offline : Screen("offline", "أوفلاين", Icons.Default.CloudOff)
    object SmsParser : Screen("sms_parser", "فاحص SMS", Icons.Default.Sms)
}

@Composable
fun SudanPayApp(
    viewModel: SudanPayViewModel,
    activity: FragmentActivity
) {
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val showSecurityDialog by viewModel.showSecurityDialog.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isAppLocked,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "app_lock_transition"
    ) { locked ->
        if (locked) {
            BiometricLockScreen(
                viewModel = viewModel,
                activity = activity
            )
        } else {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val navItems = listOf(
                Screen.Dashboard,
                Screen.Transfer,
                Screen.Offline,
                Screen.SmsParser
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        navItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.titleAr,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = screen.titleAr,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SudanEmerald,
                                    selectedTextColor = SudanEmerald,
                                    indicatorColor = SudanEmerald.copy(alpha = 0.15f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            activity = activity,
                            onNavigateToTransfer = { navController.navigate(Screen.Transfer.route) },
                            onNavigateToOffline = { navController.navigate(Screen.Offline.route) },
                            onNavigateToSmsParser = { navController.navigate(Screen.SmsParser.route) }
                        )
                    }
                    composable(Screen.Transfer.route) {
                        TransferScreen(
                            viewModel = viewModel,
                            activity = activity,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Offline.route) {
                        OfflineModeScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.SmsParser.route) {
                        SmsParserScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Security dialog when toggled by user
            if (showSecurityDialog) {
                BiometricSecurityDialog(
                    viewModel = viewModel,
                    activity = activity,
                    onDismiss = { viewModel.setShowSecurityDialog(false) }
                )
            }
        }
    }
}
