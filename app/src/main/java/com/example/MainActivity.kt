package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.VelourAuthManager
import com.example.auth.authStateFlow
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.data.repository.VelourRepository
import com.example.ui.VelourViewModel
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.AuthOnboardingScreen
import com.example.ui.screens.CallsHistoryScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.MatchesListScreen
import com.example.ui.screens.NotificationsDialog
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SettingsAndProfileScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.VelourTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.velourDataStore by preferencesDataStore(name = "velour_ui_prefs")
private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")

enum class PrimaryDestination(
    val routeId: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    DISCOVER("discover", R.string.nav_discover, Icons.Filled.Explore, "nav_discover_tab"),
    MATCHES("matches", R.string.nav_matches, Icons.Filled.Favorite, "nav_matches_tab"),
    CALLS("calls", R.string.nav_calls, Icons.Filled.Call, "nav_calls_tab"),
    WALLET("wallet", R.string.nav_wallet, Icons.Filled.AccountBalanceWallet, "nav_wallet_tab"),
    PROFILE("profile", R.string.nav_settings, Icons.Filled.Person, "nav_profile_tab")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            val scope = rememberCoroutineScope()
            val darkPrefFlow = remember(context) {
                context.velourDataStore.data.map { prefs -> prefs[DARK_THEME_KEY] }
            }
            val storedDark by darkPrefFlow.collectAsStateWithLifecycle(initialValue = null)
            val useDarkTheme = storedDark ?: systemDark

            VelourTheme(darkTheme = useDarkTheme) {
                VelourRootAuthGate(
                    isDarkTheme = useDarkTheme,
                    onToggleDarkTheme = { enabled ->
                        scope.launch {
                            context.velourDataStore.edit { prefs ->
                                prefs[DARK_THEME_KEY] = enabled
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VelourRootAuthGate(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthOnboardingScreen(onAuthSuccess = {})
    } else {
        AuthenticatedVelourShell(
            user = user,
            isDarkTheme = isDarkTheme,
            onToggleDarkTheme = onToggleDarkTheme
        )
    }
}

@Composable
private fun AuthenticatedVelourShell(
    user: FirebaseUser,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val vm: VelourViewModel = viewModel(
        key = user.uid,
        factory = remember(user.uid) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VelourViewModel(
                        repository = VelourRepository(context.applicationContext),
                        currentUserId = user.uid,
                        currentUserEmail = user.email.orEmpty()
                    ) as T
                }
            }
        }
    )

    val myProfileState by vm.myProfileState.collectAsStateWithLifecycle()
    val privateInfoState by vm.privateInfoState.collectAsStateWithLifecycle()
    val pricing by vm.pricingState.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val transactionsState by vm.transactionsState.collectAsStateWithLifecycle()
    val candidatesState by vm.discoverCandidatesState.collectAsStateWithLifecycle()
    val discoverFilter by vm.discoverFilter.collectAsStateWithLifecycle()
    val matchedCelebration by vm.matchedCelebration.collectAsStateWithLifecycle()
    val conversationsState by vm.conversationsState.collectAsStateWithLifecycle()
    val incomingLikes by vm.incomingLikesState.collectAsStateWithLifecycle()
    val activeConversation by vm.activeConversation.collectAsStateWithLifecycle()
    val activeMessages by vm.activeMessagesState.collectAsStateWithLifecycle()
    val callHistory by vm.callHistoryState.collectAsStateWithLifecycle()
    val incomingCall by vm.incomingRingingCallState.collectAsStateWithLifecycle()
    val activeCall by vm.activeCallSessionState.collectAsStateWithLifecycle()
    val notifications by vm.notificationsState.collectAsStateWithLifecycle()
    val blockedUsers by vm.blockedUsersState.collectAsStateWithLifecycle()
    val paymentOutcome by vm.paymentOutcome.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val adminReports by vm.adminReportsState.collectAsStateWithLifecycle()
    val adminAllProfiles by vm.allDiscoverableProfilesForAdmin.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearStatusMessage()
        }
    }

    val performFullSignOut = {
        VelourAuthManager.signOut(
            credentialManager = credentialManager,
            onSignOutComplete = {},
            scope = scope
        )
    }

    when (val profileRes = myProfileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(profileRes.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = performFullSignOut) {
                        Text(stringResource(R.string.btn_sign_out))
                    }
                }
            }
        }
        is UiState.Success -> {
            val myProfile = profileRes.data
            if (myProfile == null) {
                ProfileSetupScreen(
                    defaultEmail = user.email.orEmpty(),
                    defaultName = user.displayName.orEmpty(),
                    isBusy = isBusy,
                    errorMessage = statusMessage,
                    onCompleteSetup = vm::completeSetup,
                    onSignOut = performFullSignOut
                )
            } else {
                VelourMainNavigationContent(
                    vm = vm,
                    myProfile = myProfile,
                    privateInfoState = privateInfoState,
                    pricing = pricing,
                    wallet = wallet,
                    transactionsState = transactionsState,
                    candidatesState = candidatesState,
                    discoverFilter = discoverFilter,
                    matchedCelebration = matchedCelebration,
                    conversationsState = conversationsState,
                    incomingLikes = incomingLikes,
                    activeConversation = activeConversation,
                    activeMessages = activeMessages,
                    callHistory = callHistory,
                    incomingCall = incomingCall,
                    activeCall = activeCall,
                    notifications = notifications,
                    blockedUsers = blockedUsers,
                    paymentOutcome = paymentOutcome,
                    isBusy = isBusy,
                    adminReports = adminReports,
                    adminAllProfiles = adminAllProfiles,
                    snackbarHostState = snackbarHostState,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onSignOut = performFullSignOut
                )
            }
        }
    }
}

@Composable
private fun VelourMainNavigationContent(
    vm: VelourViewModel,
    myProfile: PublicProfile,
    privateInfoState: UiState<com.example.data.model.UserPrivateInfo?>,
    pricing: com.example.data.model.PricingConfig,
    wallet: com.example.data.model.Wallet?,
    transactionsState: UiState<List<com.example.data.model.WalletTransaction>>,
    candidatesState: UiState<List<PublicProfile>>,
    discoverFilter: com.example.ui.DiscoverFilter,
    matchedCelebration: com.example.data.model.Conversation?,
    conversationsState: UiState<List<com.example.data.model.Conversation>>,
    incomingLikes: List<com.example.data.model.SwipeAction>,
    activeConversation: com.example.data.model.Conversation?,
    activeMessages: List<com.example.data.model.ChatMessage>,
    callHistory: List<com.example.data.model.CallSession>,
    incomingCall: com.example.data.model.CallSession?,
    activeCall: com.example.data.model.CallSession?,
    notifications: List<com.example.data.model.AppNotification>,
    blockedUsers: List<com.example.data.model.BlockRecord>,
    paymentOutcome: com.example.ui.PaymentOutcome,
    isBusy: Boolean,
    adminReports: List<com.example.data.model.ModerationReport>,
    adminAllProfiles: List<PublicProfile>,
    snackbarHostState: SnackbarHostState,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(PrimaryDestination.DISCOVER) }
    var showNotificationsDialog by rememberSaveable { mutableStateOf(false) }
    var showAdminScreen by rememberSaveable { mutableStateOf(false) }

    val balanceCoins = wallet?.balanceCoins ?: 25L
    val unreadNotifs = notifications.count { !it.isRead }
    val liveCallOverlay = activeCall?.takeIf { it.status in listOf("ringing", "connected") } ?: incomingCall

    BackHandler(enabled = selectedTab != PrimaryDestination.DISCOVER && activeConversation == null && !showAdminScreen && liveCallOverlay == null) {
        selectedTab = PrimaryDestination.DISCOVER
    }

    if (liveCallOverlay != null) {
        ActiveCallOverlay(
            currentUserId = vm.currentUserId,
            session = liveCallOverlay,
            balanceCoins = balanceCoins,
            lowBalanceThreshold = pricing.lowBalanceWarningThresholdCoins,
            onAccept = vm::acceptIncomingCall,
            onReject = vm::rejectIncomingCall,
            onHangUp = vm::hangUpActiveCall,
            onOpenWallet = {
                vm.hangUpActiveCall()
                selectedTab = PrimaryDestination.WALLET
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideScreen && activeConversation == null && !showAdminScreen) {
                    NavigationBar {
                        PrimaryDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = {
                                    if (dest == PrimaryDestination.MATCHES && incomingLikes.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text("${incomingLikes.size}") } }) {
                                            Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                        }
                                    } else {
                                        Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen && activeConversation == null && !showAdminScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        PrimaryDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        showAdminScreen -> {
                            AdminConsoleScreen(
                                pricing = pricing,
                                openReports = adminReports,
                                allProfiles = adminAllProfiles,
                                onSavePricing = vm::adminSavePricing,
                                onResolveReport = vm::adminUpdateReportStatus,
                                onModerateProfile = vm::adminModerateProfile,
                                onBack = { showAdminScreen = false }
                            )
                        }
                        activeConversation != null -> {
                            ChatScreen(
                                currentUserId = vm.currentUserId,
                                myProfile = myProfile,
                                conversation = activeConversation,
                                messages = activeMessages,
                                pricing = pricing,
                                balanceCoins = balanceCoins,
                                onBack = vm::closeConversation,
                                onSendMessage = { txt, uri, type ->
                                    vm.sendConfirmedMessage(
                                        text = txt,
                                        mediaUri = uri,
                                        messageType = type,
                                        onNeedWallet = {
                                            vm.closeConversation()
                                            selectedTab = PrimaryDestination.WALLET
                                        }
                                    )
                                },
                                onTypingChanged = vm::updateTyping,
                                onMarkMessagesRead = vm::markActiveChatMessagesRead,
                                onStartCall = { calleeId, calleeName, callType ->
                                    vm.initiateConfirmedCall(
                                        calleeId = calleeId,
                                        calleeName = calleeName,
                                        callType = callType,
                                        onNeedWallet = {
                                            vm.closeConversation()
                                            selectedTab = PrimaryDestination.WALLET
                                        }
                                    )
                                },
                                onUnmatch = vm::unmatch,
                                onReportOrBlock = { partnerId, partnerName, convId, cat, details ->
                                    if (cat != null) vm.reportMember(partnerId, partnerName, cat, details)
                                    vm.blockMember(partnerId, partnerName, details, convId)
                                },
                                onOpenWallet = {
                                    vm.closeConversation()
                                    selectedTab = PrimaryDestination.WALLET
                                }
                            )
                        }
                        selectedTab == PrimaryDestination.DISCOVER -> {
                            DiscoverScreen(
                                myProfile = myProfile,
                                candidatesState = candidatesState,
                                pricing = pricing,
                                balanceCoins = balanceCoins,
                                unreadNotificationCount = unreadNotifs,
                                filter = discoverFilter,
                                matchedCelebration = matchedCelebration,
                                onUpdateFilter = vm::updateFilter,
                                onSwipe = vm::swipeCandidate,
                                onReportOrBlock = { target, cat, details ->
                                    if (cat != null) vm.reportMember(target.userId, target.displayName, cat, details)
                                    vm.blockMember(target.userId, target.displayName, details)
                                },
                                onOpenWallet = { selectedTab = PrimaryDestination.WALLET },
                                onOpenNotifications = { showNotificationsDialog = true },
                                onOpenMatchedChat = { conv ->
                                    vm.dismissMatchCelebration()
                                    vm.openConversation(conv)
                                },
                                onDismissMatchCelebration = vm::dismissMatchCelebration
                            )
                        }
                        selectedTab == PrimaryDestination.MATCHES -> {
                            MatchesListScreen(
                                currentUserId = vm.currentUserId,
                                conversationsState = conversationsState,
                                incomingLikes = incomingLikes,
                                onOpenConversation = vm::openConversation,
                                onUnmatch = { conv -> vm.unmatch(conv.id) },
                                onReportOrBlock = { partnerId, partnerName, convId, cat, details ->
                                    if (cat != null) vm.reportMember(partnerId, partnerName, cat, details)
                                    vm.blockMember(partnerId, partnerName, details, convId)
                                }
                            )
                        }
                        selectedTab == PrimaryDestination.CALLS -> {
                            CallsHistoryScreen(
                                currentUserId = vm.currentUserId,
                                myProfile = myProfile,
                                pricing = pricing,
                                balanceCoins = balanceCoins,
                                callHistory = callHistory,
                                onOpenWallet = { selectedTab = PrimaryDestination.WALLET }
                            )
                        }
                        selectedTab == PrimaryDestination.WALLET -> {
                            WalletScreen(
                                myProfile = myProfile,
                                wallet = wallet,
                                pricing = pricing,
                                packages = vm.buildCoinPackages(pricing),
                                transactionsState = transactionsState,
                                paymentOutcome = paymentOutcome,
                                isBusy = isBusy,
                                onPurchasePackage = vm::purchasePackage,
                                onDismissPaymentOutcome = vm::dismissPaymentOutcome
                            )
                        }
                        selectedTab == PrimaryDestination.PROFILE -> {
                            val priv = (privateInfoState as? UiState.Success)?.data
                            SettingsAndProfileScreen(
                                myProfile = myProfile,
                                privateInfo = priv,
                                blockedUsers = blockedUsers,
                                isDarkTheme = isDarkTheme,
                                isAdminUser = vm.isAdminUser,
                                onToggleDarkTheme = onToggleDarkTheme,
                                onSaveProfile = vm::saveProfileUpdates,
                                onSavePrivateSettings = vm::savePrivatePreferences,
                                onUnblockUser = vm::unblockMember,
                                onOpenAdminConsole = { showAdminScreen = true },
                                onSignOut = onSignOut,
                                onDeleteAccount = { vm.deleteAccountData(onDeleted = onSignOut) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsDialog) {
        NotificationsDialog(
            notifications = notifications,
            onMarkRead = vm::markNotificationAsRead,
            onDismiss = { showNotificationsDialog = false }
        )
    }
}
