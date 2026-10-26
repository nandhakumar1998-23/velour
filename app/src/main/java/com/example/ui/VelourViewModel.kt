package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.BlockRecord
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.CoinPackageOption
import com.example.data.model.Conversation
import com.example.data.model.InsufficientCreditsException
import com.example.data.model.ModerationReport
import com.example.data.model.PricingConfig
import com.example.data.model.PublicProfile
import com.example.data.model.SwipeAction
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.repository.VelourRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoverFilter(
    val searchQuery: String = "",
    val minAge: Int = 18,
    val maxAge: Int = 60,
    val maxDistanceKm: Int = 200,
    val verifiedOnly: Boolean = false,
    val onlineOnly: Boolean = false
)

enum class PaymentOutcomeStatus {
    NONE,
    SUCCESS,
    FAILED
}

data class PaymentOutcome(
    val status: PaymentOutcomeStatus = PaymentOutcomeStatus.NONE,
    val title: String = "",
    val message: String = ""
)

class VelourViewModel(
    private val repository: VelourRepository,
    val currentUserId: String,
    val currentUserEmail: String
) : ViewModel() {

    val isAdminUser: Boolean = currentUserEmail.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _matchedCelebration = MutableStateFlow<Conversation?>(null)
    val matchedCelebration: StateFlow<Conversation?> = _matchedCelebration.asStateFlow()

    private val _discoverFilter = MutableStateFlow(DiscoverFilter())
    val discoverFilter: StateFlow<DiscoverFilter> = _discoverFilter.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _paymentOutcome = MutableStateFlow(PaymentOutcome())
    val paymentOutcome: StateFlow<PaymentOutcome> = _paymentOutcome.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var callTimerJob: Job? = null

    // --- Real-Time Two-Tier Error-Handled Flows ---

    val myProfileState: StateFlow<UiState<PublicProfile?>> = repository.observeMyProfile(currentUserId)
        .map<PublicProfile?, UiState<PublicProfile?>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing profile", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val privateInfoState: StateFlow<UiState<UserPrivateInfo?>> = repository.observeMyPrivateInfo(currentUserId)
        .map<UserPrivateInfo?, UiState<UserPrivateInfo?>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing private settings", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load account settings"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val pricingState: StateFlow<PricingConfig> = repository.observePricingConfig()
        .catch { err ->
            Log.w(TAG, "Using default pricing fallback", err)
            emit(PricingConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PricingConfig())

    val walletState: StateFlow<Wallet?> = repository.observeWallet(currentUserId)
        .catch { err ->
            Log.w(TAG, "Error observing wallet", err)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val transactionsState: StateFlow<UiState<List<WalletTransaction>>> = repository.observeWalletTransactions(currentUserId)
        .map<List<WalletTransaction>, UiState<List<WalletTransaction>>> { UiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing transactions", err)
            emit(UiState.Error(err.localizedMessage ?: "Failed to load transactions"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val blockedUsersState: StateFlow<List<BlockRecord>> = repository.observeBlockedUsers(currentUserId)
        .catch { err ->
            Log.w(TAG, "Error observing blocks", err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mySwipesFlow: StateFlow<List<SwipeAction>> = repository.observeMySwipes(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val incomingLikesState: StateFlow<List<SwipeAction>> = repository.observeIncomingLikes(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val discoverCandidatesState: StateFlow<UiState<List<PublicProfile>>> = combine(
        repository.observeDiscoverableProfiles(),
        mySwipesFlow,
        blockedUsersState,
        _discoverFilter
    ) { allProfiles, swipes, blocks, filter ->
        val swipedIds = swipes.map { it.toUserId }.toSet()
        val blockedIds = blocks.map { it.blockedId }.toSet()
        val filtered = allProfiles.filter { candidate ->
            candidate.userId != currentUserId &&
                candidate.userId !in swipedIds &&
                candidate.userId !in blockedIds &&
                candidate.age in filter.minAge..filter.maxAge &&
                candidate.distanceKm <= filter.maxDistanceKm &&
                (!filter.verifiedOnly || candidate.verified) &&
                (!filter.onlineOnly || candidate.isOnline) &&
                (filter.searchQuery.isBlank() ||
                    candidate.displayName.contains(filter.searchQuery, ignoreCase = true) ||
                    candidate.city.contains(filter.searchQuery, ignoreCase = true) ||
                    candidate.interests.any { it.contains(filter.searchQuery, ignoreCase = true) })
        }
        UiState.Success(filtered) as UiState<List<PublicProfile>>
    }
        .catch { err ->
            Log.w(TAG, "Error observing discover profiles", err)
            emit(UiState.Error(err.localizedMessage ?: "Unable to load discovery feed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val allDiscoverableProfilesForAdmin: StateFlow<List<PublicProfile>> = repository.observeDiscoverableProfiles()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val conversationsState: StateFlow<UiState<List<Conversation>>> = combine(
        repository.observeConversations(currentUserId),
        blockedUsersState
    ) { convs, blocks ->
        val blockedIds = blocks.map { it.blockedId }.toSet()
        UiState.Success(convs.filter { it.partnerId(currentUserId) !in blockedIds }) as UiState<List<Conversation>>
    }
        .catch { err ->
            Log.w(TAG, "Error observing conversations", err)
            emit(UiState.Error(err.localizedMessage ?: "Unable to load matches"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessagesState: StateFlow<List<ChatMessage>> = _activeConversation
        .flatMapLatest { conv ->
            if (conv == null) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(conv.id, currentUserId)
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val callHistoryState: StateFlow<List<CallSession>> = repository.observeCallHistory(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val incomingRingingCallState: StateFlow<CallSession?> = callHistoryState
        .map { calls ->
            calls.firstOrNull { it.calleeId == currentUserId && it.status == "ringing" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeCallSessionState: StateFlow<CallSession?> = combine(
        callHistoryState,
        _activeCallId
    ) { calls, callId ->
        if (callId == null) null else calls.firstOrNull { it.id == callId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val notificationsState: StateFlow<List<AppNotification>> = repository.observeNotifications(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val adminReportsState: StateFlow<List<ModerationReport>> =
        if (isAdminUser) {
            repository.observeOpenReportsForAdmin()
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    fun buildCoinPackages(pricing: PricingConfig): List<CoinPackageOption> = listOf(
        CoinPackageOption(
            id = "starter",
            title = "Starter Spark",
            subtitle = "Great for paid intros & voice calls",
            coins = pricing.starterPackCoins,
            priceUsd = pricing.starterPackPriceUsd,
            badge = null
        ),
        CoinPackageOption(
            id = "popular",
            title = "Velour Plus",
            subtitle = "Most popular for video & messaging",
            coins = pricing.popularPackCoins,
            priceUsd = pricing.popularPackPriceUsd,
            badge = "MOST POPULAR"
        ),
        CoinPackageOption(
            id = "vip",
            title = "Premiere Vault",
            subtitle = "Extended HD video & priority connection",
            coins = pricing.vipPackCoins,
            priceUsd = pricing.vipPackPriceUsd,
            badge = "BEST VALUE"
        )
    )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissMatchCelebration() {
        _matchedCelebration.value = null
    }

    fun dismissPaymentOutcome() {
        _paymentOutcome.value = PaymentOutcome()
    }

    fun updateFilter(updated: DiscoverFilter) {
        _discoverFilter.value = updated
    }

    fun openConversation(conversation: Conversation) {
        _activeConversation.value = conversation
    }

    fun closeConversation() {
        val current = _activeConversation.value
        if (current != null) {
            viewModelScope.launch { repository.setTypingIndicator(current.id, false) }
        }
        _activeConversation.value = null
    }

    fun completeSetup(
        displayName: String,
        birthYear: Int,
        gender: String,
        bio: String,
        city: String,
        interests: List<String>,
        photoKey: String,
        phone: String,
        callPrivacyConsent: Boolean,
        minPreferredAge: Int,
        maxPreferredAge: Int,
        maxDistanceKm: Int
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.completeProfileSetup(
                displayName = displayName,
                birthYear = birthYear,
                gender = gender,
                bio = bio,
                city = city,
                interests = interests,
                photoKey = photoKey,
                email = currentUserEmail,
                phone = phone,
                callPrivacyConsent = callPrivacyConsent,
                minPreferredAge = minPreferredAge,
                maxPreferredAge = maxPreferredAge,
                maxDistanceKm = maxDistanceKm
            )
            _isBusy.value = false
            result.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not save profile."
            }
        }
    }

    fun saveProfileUpdates(
        displayName: String,
        bio: String,
        city: String,
        interests: List<String>,
        photoKey: String,
        visibility: String,
        isOnline: Boolean,
        minPreferredAge: Int,
        maxPreferredAge: Int,
        maxDistanceKm: Int
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateMyProfile(
                displayName = displayName,
                bio = bio,
                city = city,
                interests = interests,
                photoKey = photoKey,
                visibility = visibility,
                isOnline = isOnline,
                minPreferredAge = minPreferredAge,
                maxPreferredAge = maxPreferredAge,
                maxDistanceKm = maxDistanceKm
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Profile updated."
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update profile."
            }
        }
    }

    fun savePrivatePreferences(phone: String, callConsent: Boolean, notificationsEnabled: Boolean) {
        viewModelScope.launch {
            repository.updatePrivateSettings(phone, callConsent, notificationsEnabled)
                .onSuccess { _statusMessage.value = "Privacy & notification preferences saved." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update settings." }
        }
    }

    fun swipeCandidate(target: PublicProfile, action: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.recordSwipeAndCheckMatch(me, target, action)
            res.onSuccess { matchedConv ->
                if (matchedConv != null) {
                    _matchedCelebration.value = matchedConv
                } else if (action == "super_like") {
                    _statusMessage.value = "Super-Like sent to ${target.displayName}!"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not record action."
            }
        }
    }

    fun sendConfirmedMessage(
        text: String,
        mediaUri: String = "",
        messageType: String = "text",
        onNeedWallet: () -> Unit = {}
    ) {
        val conv = _activeConversation.value ?: return
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val pricing = pricingState.value
        viewModelScope.launch {
            val result = repository.sendChatMessage(
                conversation = conv,
                senderProfile = me,
                pricing = pricing,
                text = text,
                mediaUri = mediaUri,
                messageType = messageType
            )
            result.onFailure { err ->
                if (err is InsufficientCreditsException) {
                    _statusMessage.value = err.message
                    onNeedWallet()
                } else {
                    _statusMessage.value = err.localizedMessage ?: "Message could not be sent."
                }
            }
        }
    }

    fun updateTyping(isTyping: Boolean) {
        val conv = _activeConversation.value ?: return
        viewModelScope.launch {
            repository.setTypingIndicator(conv.id, isTyping)
        }
    }

    fun markActiveChatMessagesRead(messages: List<ChatMessage>) {
        val conv = _activeConversation.value ?: return
        viewModelScope.launch {
            repository.markMessagesRead(conv.id, messages)
        }
    }

    fun unmatch(conversationId: String) {
        viewModelScope.launch {
            repository.unmatchConversation(conversationId)
                .onSuccess {
                    if (_activeConversation.value?.id == conversationId) {
                        _activeConversation.value = null
                    }
                    _statusMessage.value = "Unmatched conversation."
                }
        }
    }

    fun purchasePackage(pkg: CoinPackageOption, simulateFailure: Boolean = false) {
        if (simulateFailure) {
            _paymentOutcome.value = PaymentOutcome(
                status = PaymentOutcomeStatus.FAILED,
                title = "Payment Declined",
                message = "Your payment method was not charged. Please verify your billing method or try another package."
            )
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.purchaseCoinPackage(
                coins = pkg.coins,
                priceUsd = pkg.priceUsd,
                packageTitle = pkg.title
            )
            _isBusy.value = false
            result.onSuccess { newBalance ->
                _paymentOutcome.value = PaymentOutcome(
                    status = PaymentOutcomeStatus.SUCCESS,
                    title = "Payment Verified",
                    message = "${pkg.coins} coins added to your Velour Wallet. Available balance: $newBalance coins."
                )
            }.onFailure { err ->
                _paymentOutcome.value = PaymentOutcome(
                    status = PaymentOutcomeStatus.FAILED,
                    title = "Verification Failed",
                    message = err.localizedMessage ?: "Unable to verify payment order."
                )
            }
        }
    }

    fun initiateConfirmedCall(
        calleeId: String,
        calleeName: String,
        callType: String,
        onNeedWallet: () -> Unit = {}
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val pricing = pricingState.value
        viewModelScope.launch {
            val result = repository.startCallSession(
                callerProfile = me,
                calleeId = calleeId,
                calleeName = calleeName,
                callType = callType,
                pricing = pricing
            )
            result.onSuccess { session ->
                _activeCallId.value = session.id
                startCallMeteringLoop(session.id)
            }.onFailure { err ->
                if (err is InsufficientCreditsException) {
                    _statusMessage.value = err.message
                    onNeedWallet()
                } else {
                    _statusMessage.value = err.localizedMessage ?: "Unable to initiate call."
                }
            }
        }
    }

    fun acceptIncomingCall(session: CallSession) {
        viewModelScope.launch {
            repository.acceptIncomingCall(session.id).onSuccess {
                _activeCallId.value = session.id
                startCallMeteringLoop(session.id)
            }
        }
    }

    fun rejectIncomingCall(session: CallSession) {
        viewModelScope.launch {
            repository.endCallSession(
                callId = session.id,
                finalStatus = "rejected",
                durationSeconds = 0,
                totalCoinsCharged = 0
            )
        }
    }

    private fun startCallMeteringLoop(callId: String) {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            var localElapsedSec = 0
            var billedMinutes = 0
            while (true) {
                delay(1000L)
                val latest = callHistoryState.value.firstOrNull { it.id == callId } ?: break
                if (latest.status in listOf("ended", "rejected", "exhausted")) {
                    break
                }
                if (latest.status == "connected") {
                    localElapsedSec += 1
                    val targetMinute = (localElapsedSec / 60) + 1
                    if (targetMinute > billedMinutes) {
                        val meterRes = repository.meterCallMinuteAndUpdate(
                            callSession = latest,
                            newDurationSeconds = localElapsedSec,
                            minuteIndex = targetMinute
                        )
                        if (meterRes.isFailure) {
                            _statusMessage.value = "Call ended gracefully: coin balance exhausted."
                            break
                        }
                        billedMinutes = targetMinute
                    } else if (localElapsedSec % 5 == 0) {
                        repository.meterCallMinuteAndUpdate(
                            callSession = latest,
                            newDurationSeconds = localElapsedSec,
                            minuteIndex = 0
                        )
                    }
                }
            }
        }
    }

    fun hangUpActiveCall() {
        val current = activeCallSessionState.value
        callTimerJob?.cancel()
        _activeCallId.value = null
        if (current != null && current.status !in listOf("ended", "rejected", "exhausted")) {
            viewModelScope.launch {
                repository.endCallSession(
                    callId = current.id,
                    finalStatus = "ended",
                    durationSeconds = current.durationSeconds,
                    totalCoinsCharged = current.totalCoinsCharged
                )
            }
        }
    }

    fun blockMember(userId: String, userName: String, reason: String, conversationId: String? = null) {
        viewModelScope.launch {
            repository.blockUser(userId, userName, reason, conversationId)
                .onSuccess {
                    if (_activeConversation.value?.id == conversationId) {
                        _activeConversation.value = null
                    }
                    _statusMessage.value = "$userName has been blocked."
                }
        }
    }

    fun unblockMember(blockId: String) {
        viewModelScope.launch {
            repository.unblockUser(blockId)
                .onSuccess { _statusMessage.value = "Member unblocked." }
        }
    }

    fun reportMember(userId: String, userName: String, category: String, details: String) {
        viewModelScope.launch {
            repository.submitReport(userId, userName, category, details)
                .onSuccess { _statusMessage.value = "Safety report submitted for moderation review." }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun deleteAccountData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteMyAccountData()
            _isBusy.value = false
            res.onSuccess { onDeleted() }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not delete account data." }
        }
    }

    // --- Admin Actions ---

    fun adminSavePricing(config: PricingConfig) {
        viewModelScope.launch {
            repository.adminSavePricingConfig(config)
                .onSuccess { _statusMessage.value = "Platform pricing & free-access policy updated." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Admin update denied." }
        }
    }

    fun adminModerateProfile(targetUserId: String, verified: Boolean, visibility: String) {
        viewModelScope.launch {
            repository.adminVerifyOrModerateProfile(targetUserId, verified, visibility)
                .onSuccess { _statusMessage.value = "Member moderation status updated." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Moderation action denied." }
        }
    }

    fun adminUpdateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            repository.adminResolveReport(reportId, status)
                .onSuccess { _statusMessage.value = "Report marked as $status." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not update report." }
        }
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
    }

    companion object {
        private const val TAG = "VelourVM"
        private const val STOP_TIMEOUT_MS = 5000L
        const val BOOTSTRAPPED_ADMIN_EMAIL = "nandhakumarid023@gmail.com"
    }
}
