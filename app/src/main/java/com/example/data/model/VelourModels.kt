package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class InsufficientCreditsException(
    val requiredCoins: Int,
    val availableCoins: Int
) : IllegalStateException("Requires $requiredCoins coins, but your balance is $availableCoins coins.")

@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val birthYear: Int = 1998,
    val age: Int = 28,
    val gender: String = "female",
    val bio: String = "",
    val city: String = "San Francisco",
    val distanceKm: Int = 8,
    val interests: List<String> = emptyList(),
    val photoKey: String = "img_profile_aria",
    val verified: Boolean = false,
    val visibility: String = "discoverable",
    val isOnline: Boolean = true,
    val minPreferredAge: Int = 21,
    val maxPreferredAge: Int = 40,
    val maxDistanceKm: Int = 80,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(40),
        "birthYear" to birthYear,
        "age" to age,
        "gender" to gender,
        "bio" to bio.take(300),
        "city" to city.take(60).ifBlank { "Metro Area" },
        "distanceKm" to distanceKm.coerceIn(1, 500),
        "interests" to interests.take(8).map { it.take(30) },
        "photoKey" to photoKey.take(120).ifBlank { "img_profile_aria" },
        "verified" to false,
        "visibility" to visibility,
        "isOnline" to isOnline,
        "minPreferredAge" to minPreferredAge.coerceIn(18, 99),
        "maxPreferredAge" to maxPreferredAge.coerceIn(minPreferredAge.coerceIn(18, 99), 100),
        "maxDistanceKm" to maxDistanceKm.coerceIn(5, 500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phone: String = "",
    val ageConfirmed18Plus: Boolean = true,
    val callPrivacyConsent: Boolean = true,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email.take(120),
        "phone" to phone.take(30),
        "ageConfirmed18Plus" to true,
        "callPrivacyConsent" to callPrivacyConsent,
        "notificationsEnabled" to notificationsEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class PricingConfig(
    @DocumentId val id: String = "pricing",
    val configId: String = "pricing",
    val femaleFreeChatEnabled: Boolean = true,
    val femaleFreeCallsEnabled: Boolean = true,
    val femaleDailyFreeMessagesLimit: Int = 250,
    val maleMessageCostCoins: Int = 5,
    val maleAudioCallCoinsPerMin: Int = 15,
    val maleVideoCallCoinsPerMin: Int = 30,
    val lowBalanceWarningThresholdCoins: Int = 20,
    val starterPackCoins: Int = 100,
    val starterPackPriceUsd: String = "$4.99",
    val popularPackCoins: Int = 300,
    val popularPackPriceUsd: String = "$12.99",
    val vipPackCoins: Int = 800,
    val vipPackPriceUsd: String = "$29.99",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun messageCostForGender(gender: String): Int {
        return if (gender == "female" && femaleFreeChatEnabled) 0 else maleMessageCostCoins
    }

    fun callRatePerMinForGender(gender: String, callType: String): Int {
        if (gender == "female" && femaleFreeCallsEnabled) return 0
        return if (callType == "video") maleVideoCallCoinsPerMin else maleAudioCallCoinsPerMin
    }

    fun toWriteMap(): Map<String, Any> = mapOf(
        "configId" to "pricing",
        "femaleFreeChatEnabled" to femaleFreeChatEnabled,
        "femaleFreeCallsEnabled" to femaleFreeCallsEnabled,
        "femaleDailyFreeMessagesLimit" to femaleDailyFreeMessagesLimit,
        "maleMessageCostCoins" to maleMessageCostCoins,
        "maleAudioCallCoinsPerMin" to maleAudioCallCoinsPerMin,
        "maleVideoCallCoinsPerMin" to maleVideoCallCoinsPerMin,
        "lowBalanceWarningThresholdCoins" to lowBalanceWarningThresholdCoins,
        "starterPackCoins" to starterPackCoins,
        "starterPackPriceUsd" to starterPackPriceUsd,
        "popularPackCoins" to popularPackCoins,
        "popularPackPriceUsd" to popularPackPriceUsd,
        "vipPackCoins" to vipPackCoins,
        "vipPackPriceUsd" to vipPackPriceUsd,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class CoinPackageOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val coins: Int,
    val priceUsd: String,
    val badge: String? = null
)

@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val balanceCoins: Long = 25L,
    val totalPurchasedCoins: Long = 0L,
    val totalSpentCoins: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val idempotencyKey: String = "",
    val type: String = "welcome_bonus",
    val deltaCoins: Long = 0L,
    val balanceAfter: Long = 0L,
    val description: String = "",
    val status: String = "completed",
    val amountUsd: String = "$0.00",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SwipeAction(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val action: String = "like",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val userAId: String = "",
    val userBId: String = "",
    val userAName: String = "",
    val userBName: String = "",
    val userAPhotoKey: String = "img_profile_aria",
    val userBPhotoKey: String = "img_profile_julian",
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    val status: String = "matched",
    val typingUserId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun partnerId(currentUserId: String): String =
        if (userAId == currentUserId) userBId else userAId

    fun partnerName(currentUserId: String): String =
        if (userAId == currentUserId) userBName else userAName

    fun partnerPhotoKey(currentUserId: String): String =
        if (userAId == currentUserId) userBPhotoKey else userAPhotoKey
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderId: String = "",
    val text: String = "",
    val mediaUri: String = "",
    val messageType: String = "text",
    val coinsCharged: Int = 0,
    val readByRecipient: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val rateCoinsPerMin: Int = 0,
    val durationSeconds: Int = 0,
    val totalCoinsCharged: Int = 0,
    val signalingMode: String = "webrtc_signaling_preview",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun peerName(currentUserId: String): String =
        if (callerId == currentUserId) calleeName else callerName
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val type: String = "safety_notice",
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    val blockedName: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val category: String = "",
    val details: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
