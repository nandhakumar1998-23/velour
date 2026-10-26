package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AppNotification
import com.example.data.model.BlockRecord
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.InsufficientCreditsException
import com.example.data.model.ModerationReport
import com.example.data.model.PricingConfig
import com.example.data.model.PublicProfile
import com.example.data.model.SwipeAction
import com.example.data.model.UserPrivateInfo
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VelourRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun sanitizeId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifEmpty {
            UUID.randomUUID().toString()
        }
    }

    private fun sanitizeUidSegment(uid: String): String {
        return uid.replace("--", "_").replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(50)
    }

    fun buildConversationId(uid1: String, uid2: String): String {
        val sorted = listOf(sanitizeUidSegment(uid1), sanitizeUidSegment(uid2)).sorted()
        return "${sorted[0]}--${sorted[1]}"
    }

    // --- Profile & Account Setup ---

    fun observeMyProfile(userId: String): Flow<PublicProfile?> {
        val path = "public_profiles/$userId"
        return db.collection("public_profiles").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    fun observeMyPrivateInfo(userId: String): Flow<UserPrivateInfo?> {
        val path = "users_private/$userId"
        return db.collection("users_private").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    suspend fun getPublicProfile(userId: String): PublicProfile? {
        val path = "public_profiles/$userId"
        return try {
            val snap = db.collection("public_profiles").document(userId).get().await()
            if (snap.exists()) {
                snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun completeProfileSetup(
        displayName: String,
        birthYear: Int,
        gender: String,
        bio: String,
        city: String,
        interests: List<String>,
        photoKey: String,
        email: String,
        phone: String,
        callPrivacyConsent: Boolean,
        minPreferredAge: Int,
        maxPreferredAge: Int,
        maxDistanceKm: Int
    ): Result<Unit> {
        val uid = requireUserId()
        val computedAge = (2026 - birthYear).coerceIn(18, 100)
        require(birthYear <= 2008 && computedAge >= 18) {
            "Velour is strictly for adults aged 18 and above."
        }
        require(gender in listOf("female", "male")) {
            "Valid gender selection is required."
        }

        val profileRef = db.collection("public_profiles").document(uid)
        val privateRef = db.collection("users_private").document(uid)
        val walletRef = db.collection("wallets").document(uid)
        val welcomeTxId = "${sanitizeUidSegment(uid)}--welcome"
        val welcomeTxRef = db.collection("wallet_transactions").document(welcomeTxId)
        val welcomeNotifRef = db.collection("notifications").document(welcomeTxId)

        return try {
            val existingProfile = profileRef.get().await()
            if (!existingProfile.exists()) {
                val profile = PublicProfile(
                    userId = uid,
                    displayName = displayName.trim(),
                    birthYear = birthYear,
                    age = computedAge,
                    gender = gender,
                    bio = bio.trim(),
                    city = city.trim().ifBlank { "San Francisco" },
                    distanceKm = 5,
                    interests = interests,
                    photoKey = photoKey,
                    verified = false,
                    visibility = "discoverable",
                    isOnline = true,
                    minPreferredAge = minPreferredAge,
                    maxPreferredAge = maxPreferredAge,
                    maxDistanceKm = maxDistanceKm
                )
                val privateInfo = UserPrivateInfo(
                    userId = uid,
                    email = email,
                    phone = phone,
                    ageConfirmed18Plus = true,
                    callPrivacyConsent = callPrivacyConsent,
                    notificationsEnabled = true
                )
                val batch = db.batch()
                batch.set(profileRef, profile.toCreateMap())
                batch.set(privateRef, privateInfo.toCreateMap())
                batch.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "balanceCoins" to 25L,
                        "totalPurchasedCoins" to 0L,
                        "totalSpentCoins" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    welcomeTxRef,
                    mapOf(
                        "userId" to uid,
                        "idempotencyKey" to welcomeTxId,
                        "type" to "welcome_bonus",
                        "deltaCoins" to 25L,
                        "balanceAfter" to 25L,
                        "description" to "Welcome bonus credits upon 18+ profile verification",
                        "status" to "completed",
                        "amountUsd" to "$0.00",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    welcomeNotifRef,
                    mapOf(
                        "recipientId" to uid,
                        "actorId" to uid,
                        "type" to "safety_notice",
                        "title" to "Welcome to Velour",
                        "body" to if (gender == "female") {
                            "Eligible female profile active: core chat & calling are free under our fair-use policy."
                        } else {
                            "25 welcome coins added! Pricing is always shown transparently before paid chats or calls."
                        },
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, profileRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(
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
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("public_profiles").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().take(40),
                    "bio" to bio.trim().take(300),
                    "city" to city.trim().take(60).ifBlank { "San Francisco" },
                    "interests" to interests.take(8).map { it.take(30) },
                    "photoKey" to photoKey.take(120),
                    "visibility" to visibility,
                    "isOnline" to isOnline,
                    "minPreferredAge" to minPreferredAge.coerceIn(18, 99),
                    "maxPreferredAge" to maxPreferredAge.coerceIn(minPreferredAge.coerceIn(18, 99), 100),
                    "maxDistanceKm" to maxDistanceKm.coerceIn(5, 500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivateSettings(
        phone: String,
        callPrivacyConsent: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users_private").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "phone" to phone.trim().take(30),
                    "callPrivacyConsent" to callPrivacyConsent,
                    "notificationsEnabled" to notificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Discovery & Matching ---

    fun observeDiscoverableProfiles(): Flow<List<PublicProfile>> {
        val path = "public_profiles"
        return db.collection(path)
            .whereEqualTo("visibility", "discoverable")
            .snapshots()
            .map { snap ->
                snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observeMySwipes(userId: String): Flow<List<SwipeAction>> {
        val path = "swipes"
        return db.collection(path)
            .whereEqualTo("fromUserId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(SwipeAction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observeIncomingLikes(userId: String): Flow<List<SwipeAction>> {
        val path = "swipes"
        return db.collection(path)
            .whereEqualTo("toUserId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(SwipeAction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.action == "like" || it.action == "super_like" }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun recordSwipeAndCheckMatch(
        myProfile: PublicProfile,
        targetProfile: PublicProfile,
        action: String
    ): Result<Conversation?> {
        val uid = requireUserId()
        require(uid != targetProfile.userId) { "Cannot swipe on own profile" }
        val swipeId = "${sanitizeUidSegment(uid)}--${sanitizeUidSegment(targetProfile.userId)}"
        val swipeRef = db.collection("swipes").document(swipeId)

        return try {
            swipeRef.set(
                mapOf(
                    "fromUserId" to uid,
                    "toUserId" to targetProfile.userId,
                    "action" to action,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (action == "pass") {
                return Result.success(null)
            }

            // Check if target user already liked us
            val reciprocalId = "${sanitizeUidSegment(targetProfile.userId)}--${sanitizeUidSegment(uid)}"
            val reciprocalSnap = db.collection("swipes").document(reciprocalId).get().await()
            val reciprocalAction = reciprocalSnap.getString("action")
            val isMutual = reciprocalSnap.exists() && (reciprocalAction == "like" || reciprocalAction == "super_like")

            if (!isMutual) {
                return Result.success(null)
            }

            val convId = buildConversationId(uid, targetProfile.userId)
            val convRef = db.collection("conversations").document(convId)
            val existingConv = convRef.get().await()
            if (existingConv.exists()) {
                return Result.success(
                    existingConv.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                )
            }

            val sortedParticipants = listOf(uid, targetProfile.userId).sorted()
            val isUidFirst = sortedParticipants[0] == uid
            val convData = mapOf(
                "participantUids" to sortedParticipants,
                "userAId" to if (isUidFirst) uid else targetProfile.userId,
                "userBId" to if (isUidFirst) targetProfile.userId else uid,
                "userAName" to if (isUidFirst) myProfile.displayName else targetProfile.displayName,
                "userBName" to if (isUidFirst) targetProfile.displayName else myProfile.displayName,
                "userAPhotoKey" to if (isUidFirst) myProfile.photoKey else targetProfile.photoKey,
                "userBPhotoKey" to if (isUidFirst) targetProfile.photoKey else myProfile.photoKey,
                "lastMessageText" to "New mutual match! Say hello.",
                "lastSenderId" to uid,
                "status" to "matched",
                "typingUserId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(convData).await()

            // Notify matched partner
            val notifId = sanitizeId("match_${convId}_${System.currentTimeMillis()}")
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "recipientId" to targetProfile.userId,
                    "actorId" to uid,
                    "type" to "new_match",
                    "title" to "It's a Match!",
                    "body" to "You and ${myProfile.displayName} liked each other.",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val created = convRef.get().await()
                .toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, swipeRef.path)
            Result.failure(e)
        }
    }

    // --- Conversations & Real-Time Messaging ---

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        val path = "conversations"
        return db.collection(path)
            .whereArrayContains("participantUids", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "matched" }
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    fun observeMessages(conversationId: String, userId: String): Flow<List<ChatMessage>> {
        val path = "conversations/$conversationId/messages"
        return db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereArrayContains("participantUids", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun sendChatMessage(
        conversation: Conversation,
        senderProfile: PublicProfile,
        pricing: PricingConfig,
        text: String,
        mediaUri: String = "",
        messageType: String = "text",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanKey = sanitizeId(idempotencyKey)
        val requiredCoins = pricing.messageCostForGender(senderProfile.gender)
        val msgRef = db.collection("conversations").document(conversation.id)
            .collection("messages").document(cleanKey)
        val convRef = db.collection("conversations").document(conversation.id)

        return try {
            if (requiredCoins > 0) {
                spendCoinsInternal(
                    uid = uid,
                    costCoins = requiredCoins,
                    type = "paid_message",
                    description = "Paid chat message to ${conversation.partnerName(uid)}",
                    idempotencyKey = "msg_$cleanKey"
                )
            }

            val preview = when (messageType) {
                "image" -> "Shared a photo"
                else -> text.trim().take(120)
            }

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "conversationId" to conversation.id,
                    "participantUids" to conversation.participantUids,
                    "senderId" to uid,
                    "text" to text.trim().take(1000),
                    "mediaUri" to mediaUri.take(300),
                    "messageType" to messageType,
                    "coinsCharged" to requiredCoins,
                    "readByRecipient" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                convRef,
                mapOf(
                    "lastMessageText" to preview,
                    "lastSenderId" to uid,
                    "typingUserId" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val partnerId = conversation.partnerId(uid)
            val notifRef = db.collection("notifications").document(sanitizeId("notif_$cleanKey"))
            batch.set(
                notifRef,
                mapOf(
                    "recipientId" to partnerId,
                    "actorId" to uid,
                    "type" to "new_message",
                    "title" to "New message from ${senderProfile.displayName}",
                    "body" to preview.ifBlank { "Sent an attachment" },
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingIndicator(conversationId: String, isTyping: Boolean) {
        val uid = requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "typingUserId" to if (isTyping) uid else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
        }
    }

    suspend fun markMessagesRead(conversationId: String, unreadMessages: List<ChatMessage>) {
        val uid = requireUserId()
        val toMark = unreadMessages.filter { it.senderId != uid && !it.readByRecipient }
        if (toMark.isEmpty()) return
        try {
            val batch = db.batch()
            toMark.forEach { msg ->
                val ref = db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(
                    ref,
                    mapOf(
                        "readByRecipient" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages")
        }
    }

    suspend fun unmatchConversation(conversationId: String): Result<Unit> {
        requireUserId()
        val convRef = db.collection("conversations").document(conversationId)
        return try {
            convRef.update(
                mapOf(
                    "status" to "unmatched",
                    "typingUserId" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
            Result.failure(e)
        }
    }

    // --- Pricing, Wallet & Idempotent Payments ---

    fun observePricingConfig(): Flow<PricingConfig> {
        val path = "platform_config/pricing"
        return db.collection("platform_config").document("pricing")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(PricingConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PricingConfig()
                } else {
                    PricingConfig()
                }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    fun observeWallet(userId: String): Flow<Wallet?> {
        val path = "wallets/$userId"
        return db.collection("wallets").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(Wallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.GET, path)
                throw err
            }
    }

    fun observeWalletTransactions(userId: String): Flow<List<WalletTransaction>> {
        val path = "wallet_transactions"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun purchaseCoinPackage(
        coins: Int,
        priceUsd: String,
        packageTitle: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Long> {
        val uid = requireUserId()
        require(coins in listOf(100, 300, 800)) { "Invalid coin package amount." }
        val cleanKey = "${sanitizeUidSegment(uid)}--${sanitizeId("order_$idempotencyKey").take(60)}"
        val walletRef = db.collection("wallets").document(uid)
        val txRef = db.collection("wallet_transactions").document(cleanKey)
        val notifRef = db.collection("notifications").document(sanitizeId("pay_$cleanKey"))

        return try {
            val updatedBalance = db.runTransaction { transaction ->
                val existingTx = transaction.get(txRef)
                val walletSnap = transaction.get(walletRef)
                val currentBalance = walletSnap.getLong("balanceCoins") ?: 0L
                if (existingTx.exists()) {
                    return@runTransaction currentBalance
                }
                val currentPurchased = walletSnap.getLong("totalPurchasedCoins") ?: 0L
                val nextBalance = currentBalance + coins.toLong()
                val nextPurchased = currentPurchased + coins.toLong()

                transaction.update(
                    walletRef,
                    mapOf(
                        "balanceCoins" to nextBalance,
                        "totalPurchasedCoins" to nextPurchased,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    txRef,
                    mapOf(
                        "userId" to uid,
                        "idempotencyKey" to cleanKey,
                        "type" to "coin_purchase",
                        "deltaCoins" to coins.toLong(),
                        "balanceAfter" to nextBalance,
                        "description" to "$packageTitle ($coins coins)",
                        "status" to "completed",
                        "amountUsd" to priceUsd,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    notifRef,
                    mapOf(
                        "recipientId" to uid,
                        "actorId" to uid,
                        "type" to "payment_confirmed",
                        "title" to "Credits Added (+$coins Coins)",
                        "body" to "Verified checkout for $packageTitle ($priceUsd). New balance: $nextBalance coins.",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextBalance
            }.await()
            Result.success(updatedBalance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, walletRef.path)
            Result.failure(e)
        }
    }

    private suspend fun spendCoinsInternal(
        uid: String,
        costCoins: Int,
        type: String,
        description: String,
        idempotencyKey: String
    ): Long {
        if (costCoins <= 0) return 0L
        val cleanKey = "${sanitizeUidSegment(uid)}--${sanitizeId(idempotencyKey).take(60)}"
        val walletRef = db.collection("wallets").document(uid)
        val txRef = db.collection("wallet_transactions").document(cleanKey)

        return db.runTransaction { transaction ->
            val existingTx = transaction.get(txRef)
            val walletSnap = transaction.get(walletRef)
            val currentBalance = walletSnap.getLong("balanceCoins") ?: 0L
            if (existingTx.exists()) {
                return@runTransaction currentBalance
            }
            if (currentBalance < costCoins.toLong()) {
                throw InsufficientCreditsException(
                    requiredCoins = costCoins,
                    availableCoins = currentBalance.toInt()
                )
            }
            val currentSpent = walletSnap.getLong("totalSpentCoins") ?: 0L
            val nextBalance = currentBalance - costCoins.toLong()
            val nextSpent = currentSpent + costCoins.toLong()

            transaction.update(
                walletRef,
                mapOf(
                    "balanceCoins" to nextBalance,
                    "totalSpentCoins" to nextSpent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "idempotencyKey" to cleanKey,
                    "type" to type,
                    "deltaCoins" to -costCoins.toLong(),
                    "balanceAfter" to nextBalance,
                    "description" to description.take(160),
                    "status" to "completed",
                    "amountUsd" to "$0.00",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            nextBalance
        }.await()
    }

    // --- 1-on-1 Audio & Video Call Signaling & Per-Minute Metering ---

    fun observeCallHistory(userId: String): Flow<List<CallSession>> {
        val path = "calls"
        return db.collection(path)
            .whereArrayContains("participantUids", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun startCallSession(
        callerProfile: PublicProfile,
        calleeId: String,
        calleeName: String,
        callType: String,
        pricing: PricingConfig
    ): Result<CallSession> {
        val uid = requireUserId()
        val ratePerMin = pricing.callRatePerMinForGender(callerProfile.gender, callType)

        // Pre-verify at least 1 minute of credits if paid call
        if (ratePerMin > 0) {
            val walletSnap = db.collection("wallets").document(uid).get().await()
            val balance = (walletSnap.getLong("balanceCoins") ?: 0L).toInt()
            if (balance < ratePerMin) {
                return Result.failure(InsufficientCreditsException(ratePerMin, balance))
            }
        }

        val callId = sanitizeId("call_${UUID.randomUUID()}")
        val callRef = db.collection("calls").document(callId)
        val participants = listOf(uid, calleeId).sorted()

        return try {
            val payload = mapOf(
                "callerId" to uid,
                "callerName" to callerProfile.displayName,
                "calleeId" to calleeId,
                "calleeName" to calleeName,
                "participantUids" to participants,
                "callType" to callType,
                "status" to "ringing",
                "rateCoinsPerMin" to ratePerMin,
                "durationSeconds" to 0,
                "totalCoinsCharged" to 0,
                "signalingMode" to "webrtc_signaling_preview",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            callRef.set(payload).await()

            val notifId = sanitizeId("callnotif_$callId")
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "recipientId" to calleeId,
                    "actorId" to uid,
                    "type" to "incoming_call",
                    "title" to "Incoming ${callType.replaceFirstChar { it.uppercase() }} Call",
                    "body" to "${callerProfile.displayName} is calling you on Velour.",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val created = callRef.get().await()
                .toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: CallSession(id = callId, callerId = uid, calleeId = calleeId, callType = callType)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, callRef.path)
            Result.failure(e)
        }
    }

    suspend fun acceptIncomingCall(callId: String): Result<Unit> {
        requireUserId()
        val callRef = db.collection("calls").document(callId)
        return try {
            callRef.update(
                mapOf(
                    "status" to "connected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            Result.failure(e)
        }
    }

    suspend fun meterCallMinuteAndUpdate(
        callSession: CallSession,
        newDurationSeconds: Int,
        minuteIndex: Int
    ): Result<Long> {
        val uid = requireUserId()
        val callRef = db.collection("calls").document(callSession.id)
        return try {
            var remainingBalance = 0L
            var updatedCharged = callSession.totalCoinsCharged
            if (callSession.callerId == uid && callSession.rateCoinsPerMin > 0 && minuteIndex > 0) {
                remainingBalance = spendCoinsInternal(
                    uid = uid,
                    costCoins = callSession.rateCoinsPerMin,
                    type = if (callSession.callType == "video") "video_call" else "audio_call",
                    description = "${callSession.callType.replaceFirstChar { it.uppercase() }} call minute #$minuteIndex with ${callSession.calleeName}",
                    idempotencyKey = "call_${callSession.id}_min_$minuteIndex"
                )
                updatedCharged += callSession.rateCoinsPerMin
            }
            if (callSession.status == "connected") {
                callRef.update(
                    mapOf(
                        "durationSeconds" to newDurationSeconds,
                        "totalCoinsCharged" to updatedCharged,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(remainingBalance)
        } catch (e: InsufficientCreditsException) {
            // Gracefully terminate call when credits are exhausted
            endCallSession(
                callId = callSession.id,
                finalStatus = "exhausted",
                durationSeconds = newDurationSeconds,
                totalCoinsCharged = callSession.totalCoinsCharged
            )
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            Result.failure(e)
        }
    }

    suspend fun endCallSession(
        callId: String,
        finalStatus: String = "ended",
        durationSeconds: Int,
        totalCoinsCharged: Int
    ): Result<Unit> {
        requireUserId()
        val callRef = db.collection("calls").document(callId)
        return try {
            callRef.update(
                mapOf(
                    "status" to finalStatus,
                    "durationSeconds" to durationSeconds.coerceAtLeast(0),
                    "totalCoinsCharged" to totalCoinsCharged.coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            Result.failure(e)
        }
    }

    // --- Notifications, Safety (Block / Report), Account Deletion & Admin ---

    fun observeNotifications(userId: String): Flow<List<AppNotification>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("recipientId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun markNotificationRead(notificationId: String) {
        requireUserId()
        val ref = db.collection("notifications").document(notificationId)
        try {
            ref.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockRecord>> {
        val path = "blocks"
        return db.collection(path)
            .whereEqualTo("blockerId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun blockUser(
        blockedUserId: String,
        blockedUserName: String,
        reason: String,
        conversationId: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val blockId = "${sanitizeId(uid)}_${sanitizeId(blockedUserId)}"
        val blockRef = db.collection("blocks").document(blockId)
        return try {
            blockRef.set(
                mapOf(
                    "blockerId" to uid,
                    "blockedId" to blockedUserId,
                    "blockedName" to blockedUserName.take(60).ifBlank { "Member" },
                    "reason" to reason.take(160),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!conversationId.isNullOrBlank()) {
                val convRef = db.collection("conversations").document(conversationId)
                val convSnap = convRef.get().await()
                if (convSnap.exists() && convSnap.getString("status") != "blocked") {
                    convRef.update(
                        mapOf(
                            "status" to "blocked",
                            "typingUserId" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, blockRef.path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockId: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("blocks").document(blockId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportedUserId: String,
        reportedUserName: String,
        category: String,
        details: String
    ): Result<Unit> {
        val uid = requireUserId()
        val reportId = sanitizeId("rep_${UUID.randomUUID()}")
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "reportedUserId" to reportedUserId,
                    "reportedUserName" to reportedUserName.take(60).ifBlank { "Member" },
                    "category" to category.take(60),
                    "details" to details.take(500),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMyAccountData(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val batch = db.batch()
            batch.delete(db.collection("public_profiles").document(uid))
            batch.delete(db.collection("users_private").document(uid))
            batch.delete(db.collection("wallets").document(uid))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    // --- Admin Console Operations (Protected by isAdmin() in Firestore Rules) ---

    fun observeOpenReportsForAdmin(): Flow<List<ModerationReport>> {
        val path = "reports"
        return db.collection(path)
            .whereEqualTo("status", "open")
            .snapshots()
            .map { snap ->
                snap.toObjects(ModerationReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun adminSavePricingConfig(config: PricingConfig): Result<Unit> {
        requireUserId()
        val ref = db.collection("platform_config").document("pricing")
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                val updates = config.toWriteMap().toMutableMap().apply { remove("configId") }
                ref.update(updates).await()
            } else {
                ref.set(config.toWriteMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun adminVerifyOrModerateProfile(
        targetUserId: String,
        verified: Boolean,
        visibility: String
    ): Result<Unit> {
        requireUserId()
        val ref = db.collection("public_profiles").document(targetUserId)
        return try {
            ref.update(
                mapOf(
                    "verified" to verified,
                    "visibility" to visibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun adminResolveReport(reportId: String, newStatus: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }
}
