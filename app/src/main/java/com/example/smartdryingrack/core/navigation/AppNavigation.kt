package com.example.smartdryingrack.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.example.smartdryingrack.feature.alerts.NotificationsViewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material.icons.outlined.*
import com.example.smartdryingrack.core.designsystem.LocalDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartdryingrack.feature.settings.SettingsViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartdryingrack.R
import com.example.smartdryingrack.core.designsystem.AppColors
import com.example.smartdryingrack.core.designsystem.SmartDryingRackTheme
import com.example.smartdryingrack.feature.alerts.AlertsRoute
import com.example.smartdryingrack.feature.alerts.NotificationsScreen
import com.example.smartdryingrack.feature.control.ControlRoute
import com.example.smartdryingrack.feature.device.DeviceRoute
import com.example.smartdryingrack.feature.history.HistoryRoute
import com.example.smartdryingrack.feature.home.HomeRoute
import com.example.smartdryingrack.feature.onboarding.DeviceSetupScreen
import com.example.smartdryingrack.feature.onboarding.GetStartedScreen
import com.example.smartdryingrack.feature.onboarding.OnboardingScreen
import com.example.smartdryingrack.feature.onboarding.WelcomeScreen
import com.example.smartdryingrack.feature.settings.AboutScreen
import com.example.smartdryingrack.feature.settings.AppearanceSettingsScreen
import com.example.smartdryingrack.feature.settings.AutomationSettingsScreen
import com.example.smartdryingrack.feature.settings.DeviceSettingsScreen
import com.example.smartdryingrack.feature.settings.NotificationSettingsScreen
import com.example.smartdryingrack.feature.settings.SettingsRoute

enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    CONTROL("control", R.string.nav_control, Icons.Outlined.Tune),
    ALERTS("alerts", R.string.nav_alerts, Icons.Outlined.WarningAmber),
    HISTORY("history", R.string.nav_history, Icons.Outlined.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDryingRackRoot(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications: NotificationsViewModel = hiltViewModel()
    val events by notifications.events.collectAsStateWithLifecycle()
    val unread = events.count { !it.read }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !settings.darkTheme
                isAppearanceLightNavigationBars = !settings.darkTheme
            }
        }
    }
    val initialRoute = remember { if (viewModel.address.value.isBlank()) "welcome" else "home" }
    SmartDryingRackTheme(darkTheme = settings.darkTheme) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route

        val showBottomBar = current in Destination.entries.map { it.route }
        val showTopBar = current in Destination.entries.map { it.route } || current == "settings"

        Scaffold(
            containerColor = AppColors.Background,
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = { Text(when (current) { "history" -> "History"; "settings" -> "Settings"; else -> "Smart Drying Rack" }, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                        actions = {
                            IconButton(onClick = { nav.navigate("notifications") { launchSingleTop = true } }) {
                                BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = AppColors.Primary)
                                }
                            }
                            IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = if (current == "settings") AppColors.Primary else AppColors.TextPrimary)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NeonNavigation(current) { destination ->
                        nav.navigate(destination.route) {
                            popUpTo(Destination.HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = initialRoute, modifier = Modifier.padding(padding),
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                composable("welcome") {
                    WelcomeScreen(
                        onGetStarted = { nav.navigate("get_started") },
                        onHaveDevice = { nav.navigate("device_setup") }
                    )
                }
                composable("get_started") {
                    GetStartedScreen(
                        onGetStarted = { nav.navigate("onboarding") },
                        onHaveDevice = { nav.navigate("device_setup") }
                    )
                }
                composable("onboarding") {
                    OnboardingScreen(
                        onFinish = {
                            nav.navigate(Destination.HOME.route) { popUpTo("onboarding") { inclusive = true } }
                        },
                        onConnect = {
                            nav.navigate("device_setup")
                        }
                    )
                }
                composable("device_setup") {
                    DeviceSetupScreen(
                        onFinishSetup = {
                            nav.navigate(Destination.HOME.route) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
                        }
                    )
                }
                composable(Destination.HOME.route) { HomeRoute(onConnect = { nav.navigate("device_settings") }, onControl = { nav.navigate(Destination.CONTROL.route) { launchSingleTop = true } }) }
                composable(Destination.CONTROL.route) { ControlRoute() }
                composable(Destination.ALERTS.route) { AlertsRoute() }
                composable("notifications") {
                    NotificationsScreen(onBack = {
                        nav.navigate(Destination.HOME.route) {
                            popUpTo(Destination.HOME.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    })
                }
                composable(Destination.HISTORY.route) { HistoryRoute() }
                composable("settings") {
                    SettingsRoute(
                        onNavigateDevice = { nav.navigate("device_settings") },
                        onNavigateAutomation = { nav.navigate("automation_settings") },
                        onNavigateNotifications = { nav.navigate("notification_settings") },
                        onNavigateAppearance = { nav.navigate("appearance_settings") },
                        onNavigateAbout = { nav.navigate("about") }
                    )
                }
                composable("device_settings") { DeviceSettingsScreen(onBack = { nav.popBackStack() }) }
                composable("automation_settings") { AutomationSettingsScreen(onBack = { nav.popBackStack() }) }
                composable("notification_settings") { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
                composable("appearance_settings") { AppearanceSettingsScreen(onBack = { nav.popBackStack() }) }
                composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun NeonNavigation(current: String?, onNavigate: (Destination) -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val cyan = AppColors.Primary
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AppColors.Card, AppColors.Background)))
        .border(1.dp, cyan.copy(alpha = .24f), shape).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Destination.entries.forEach { destination ->
            val selected = current == destination.route
            val tint = if (selected) cyan else AppColors.TextSecondary
            val destinationLabel = stringResource(destination.label)
            Column(Modifier.weight(1f).heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(selected, role = Role.Tab, onClick = { onNavigate(destination) })
                .semantics(mergeDescendants = true) {
                    contentDescription = destinationLabel
                }
                .padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp)
                    .shadow(if (selected && LocalDarkTheme.current) 6.dp else 0.dp, RoundedCornerShape(12.dp),
                        ambientColor = cyan.copy(alpha = .4f), spotColor = cyan.copy(alpha = .4f)))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(destination.label), style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
