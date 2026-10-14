package com.janreins.piso

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janreins.piso.ui.accounts.AccountsScreen
import com.janreins.piso.ui.accounts.AccountsViewModel
import com.janreins.piso.ui.activity.ActivityScreen
import com.janreins.piso.ui.activity.ActivityViewModel
import com.janreins.piso.ui.activity.TransactionDialog
import com.janreins.piso.ui.app.AppViewModel
import com.janreins.piso.ui.auth.LockScreen
import com.janreins.piso.ui.auth.WelcomeScreen
import com.janreins.piso.ui.budgets.BudgetsScreen
import com.janreins.piso.ui.budgets.BudgetsViewModel
import com.janreins.piso.ui.debts.DebtsScreen
import com.janreins.piso.ui.debts.DebtsViewModel
import com.janreins.piso.ui.goals.GoalsScreen
import com.janreins.piso.ui.goals.GoalsViewModel
import com.janreins.piso.ui.home.HomeScreen
import com.janreins.piso.ui.home.HomeViewModel
import com.janreins.piso.ui.invest.InvestScreen
import com.janreins.piso.ui.invest.InvestViewModel
import com.janreins.piso.ui.more.MoreScreen
import com.janreins.piso.ui.more.MoreViewModel
import com.janreins.piso.ui.settings.CategoriesScreen
import com.janreins.piso.ui.settings.SettingsScreen
import com.janreins.piso.ui.settings.SettingsViewModel
import com.janreins.piso.ui.state.MainTab
import com.janreins.piso.ui.state.MoreSubScreen
import com.janreins.piso.ui.theme.PisoTheme
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        setContent {
            val userProfile by appViewModel.userProfile.collectAsStateWithLifecycle()
            val isAppLocked by appViewModel.isAppLocked.collectAsStateWithLifecycle()

            // Hoisted above the screen switch so app-level messages sent while the Welcome or
            // Lock screen is showing (e.g. "Welcome to Piso", reset confirmation) aren't dropped.
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                appViewModel.messageEvent.collectLatest { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }
            val showMainApp = userProfile.displayName.isNotBlank() && !(userProfile.hasPin && isAppLocked)

            PisoTheme(themeMode = userProfile.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        // 1. First Launch Welcome (No profile created yet)
                        userProfile.displayName.isBlank() -> {
                            WelcomeScreen(
                                onStartUsingPiso = { name, pin ->
                                    appViewModel.createProfile(name, pin)
                                }
                            )
                        }

                        // 2. Lock Screen (Profile exists, PIN enabled, app is locked)
                        userProfile.hasPin && isAppLocked -> {
                            LockScreen(
                                displayName = userProfile.displayName,
                                onUnlock = { pin ->
                                    appViewModel.unlockApp(pin)
                                },
                                onResetAllData = {
                                    appViewModel.clearAllDataAndReset()
                                },
                                getLockoutRemainingSeconds = {
                                    appViewModel.getLockoutRemainingSeconds()
                                }
                            )
                        }

                        // 3. Main Piso Application (Unlocked)
                        else -> {
                            PisoApp(appViewModel = appViewModel, snackbarHostState = snackbarHostState)
                        }
                    }
                    if (!showMainApp) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        // Observe the request used by ActivityResultRegistry without changing picker callbacks.
        appViewModel.pickerLockExemption.onRequestLaunched(requestCode)
        super.startActivityForResult(intent, requestCode, options)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        appViewModel.pickerLockExemption.onResultReturned(requestCode)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        // A pending picker return gets until onResume to deliver its matching result.
        appViewModel.onAppStarted()
        super.onStart()
    }

    override fun onResume() {
        // Resolve the exemption before super can notify resume callbacks/lifecycle observers.
        appViewModel.onAppResumed()
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        // Rotation / theme changes also stop the activity; don't force a PIN re-entry for those.
        if (!isChangingConfigurations) {
            appViewModel.onAppBackgrounded()
        }
    }
}

data class NavigationTabItem(
    val tab: MainTab,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun PisoApp(
    appViewModel: AppViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    homeViewModel: HomeViewModel = viewModel(),
    activityViewModel: ActivityViewModel = viewModel(),
    accountsViewModel: AccountsViewModel = viewModel(),
    goalsViewModel: GoalsViewModel = viewModel(),
    budgetsViewModel: BudgetsViewModel = viewModel(),
    debtsViewModel: DebtsViewModel = viewModel(),
    investViewModel: InvestViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    moreViewModel: MoreViewModel = viewModel()
) {
    val currentTab by appViewModel.currentTab.collectAsStateWithLifecycle()
    val moreSubScreen by appViewModel.moreSubScreen.collectAsStateWithLifecycle()

    val activityState by activityViewModel.uiState.collectAsStateWithLifecycle()
    val accounts = activityState.accounts
    val categories = activityState.categories
    val subcategories = activityState.subcategories

    // Quick Add Transaction Modal from Home
    var quickAddType by remember { mutableStateOf<String?>(null) }

    // Handle back button press
    BackHandler(enabled = moreSubScreen != null || currentTab != MainTab.HOME) {
        if (moreSubScreen != null) {
            appViewModel.closeMoreSubScreen()
        } else if (currentTab != MainTab.HOME) {
            appViewModel.selectTab(MainTab.HOME)
        }
    }

    // Collect Snackbar notifications from the screen ViewModels (AppViewModel's are collected in MainActivity)
    LaunchedEffect(Unit) {
        merge(
            homeViewModel.messageEvent,
            activityViewModel.messageEvent,
            accountsViewModel.messageEvent,
            goalsViewModel.messageEvent,
            budgetsViewModel.messageEvent,
            debtsViewModel.messageEvent,
            investViewModel.messageEvent,
            settingsViewModel.messageEvent,
            moreViewModel.messageEvent
        ).collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val tabs = remember {
        listOf(
            NavigationTabItem(MainTab.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            NavigationTabItem(MainTab.ACTIVITY, "Activity", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_activity"),
            NavigationTabItem(MainTab.ACCOUNTS, "Accounts", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance, "nav_accounts"),
            NavigationTabItem(MainTab.GOALS, "Goals", Icons.Filled.Flag, Icons.Outlined.Flag, "nav_goals"),
            NavigationTabItem(MainTab.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_more")
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            // Show bottom navigation on main tabs
            if (moreSubScreen == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    tabs.forEach { tabItem ->
                        val selected = currentTab == tabItem.tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { appViewModel.selectTab(tabItem.tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tabItem.selectedIcon else tabItem.unselectedIcon,
                                    contentDescription = tabItem.title
                                )
                            },
                            label = { Text(text = tabItem.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                indicatorColor = TealContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tabItem.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Check if user is inside a More sub-screen
            if (moreSubScreen != null) {
                when (moreSubScreen) {
                    MoreSubScreen.BUDGETS -> BudgetsScreen(
                        viewModel = budgetsViewModel,
                        onBack = { appViewModel.closeMoreSubScreen() }
                    )
                    MoreSubScreen.DEBTS -> DebtsScreen(
                        viewModel = debtsViewModel,
                        onBack = { appViewModel.closeMoreSubScreen() }
                    )
                    MoreSubScreen.INVEST -> InvestScreen(
                        viewModel = investViewModel,
                        onBack = { appViewModel.closeMoreSubScreen() }
                    )
                    MoreSubScreen.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { appViewModel.closeMoreSubScreen() },
                        onOpenCategories = { appViewModel.openMoreSubScreen(MoreSubScreen.CATEGORIES) }
                    )
                    MoreSubScreen.CATEGORIES -> CategoriesScreen(
                        viewModel = settingsViewModel,
                        onBack = { appViewModel.openMoreSubScreen(MoreSubScreen.SETTINGS) }
                    )
                    null -> {}
                }
            } else {
                // Main 5 Tabs
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        MainTab.HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            onOpenAddTransaction = { preselectedType ->
                                quickAddType = preselectedType
                            },
                            onNavigateToTab = { targetTab ->
                                appViewModel.selectTab(targetTab)
                            },
                            onNavigateToSubScreen = { subScreen ->
                                appViewModel.openMoreSubScreen(subScreen)
                            },
                            onNavigateToActivityFilter = { monthKey, filter ->
                                activityViewModel.setSelectedMonthKey(monthKey)
                                activityViewModel.setActivityFilter(filter)
                            }
                        )
                        MainTab.ACTIVITY -> ActivityScreen(viewModel = activityViewModel)
                        MainTab.ACCOUNTS -> AccountsScreen(viewModel = accountsViewModel)
                        MainTab.GOALS -> GoalsScreen(viewModel = goalsViewModel)
                        MainTab.MORE -> MoreScreen(
                            viewModel = moreViewModel,
                            onOpenSubScreen = { subScreen ->
                                appViewModel.openMoreSubScreen(subScreen)
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Quick Add Transaction Dialog from Home Screen ---
    quickAddType?.let { type ->
        TransactionDialog(
            preselectedType = type,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            onAddCategory = { name, kind, onComplete ->
                activityViewModel.addCategory(name, kind) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(activityViewModel::showMessage)
                }
            },
            onAddSubcategory = { parent, kind, name, onComplete ->
                activityViewModel.addSubcategory(parent, kind, name) { success, error ->
                    if (success) onComplete(name.trim()) else error?.let(activityViewModel::showMessage)
                }
            },
            onDismiss = { quickAddType = null },
            onSave = { newTx ->
                activityViewModel.addTransaction(newTx)
                quickAddType = null
            }
        )
    }
}
