package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PricingConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VelourRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun completeProfileSetup_andIdempotentWalletPurchase_succeedsForOwner() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val uid = signInTestUser("julian_$uniqueSuffix@velour.test")
        val repo = VelourRepository(firestore)

        val setupResult = withTimeout(TIMEOUT_MS) {
            repo.completeProfileSetup(
                displayName = "Julian",
                birthYear = 1996,
                gender = "male",
                bio = "Coastal architect",
                city = "San Francisco",
                interests = listOf("Architecture", "Sailing"),
                photoKey = "img_profile_julian",
                email = "julian_$uniqueSuffix@velour.test",
                phone = "+15550101",
                callPrivacyConsent = true,
                minPreferredAge = 22,
                maxPreferredAge = 35,
                maxDistanceKm = 60
            )
        }
        assertTrue("Profile setup failed: ${setupResult.exceptionOrNull()}", setupResult.isSuccess)

        val profile = withTimeout(TIMEOUT_MS) { repo.getPublicProfile(uid) }
        assertNotNull(profile)
        assertEquals("male", profile?.gender)

        // Idempotent coin purchase: calling twice with same idempotencyKey must only credit once
        val orderKey = "idem_${UUID.randomUUID()}"
        val firstPurchase = withTimeout(TIMEOUT_MS) {
            repo.purchaseCoinPackage(
                coins = 100,
                priceUsd = "$4.99",
                packageTitle = "Starter Pack",
                idempotencyKey = orderKey
            )
        }
        assertTrue(firstPurchase.isSuccess)
        assertEquals(125L, firstPurchase.getOrThrow())

        val duplicatePurchase = withTimeout(TIMEOUT_MS) {
            repo.purchaseCoinPackage(
                coins = 100,
                priceUsd = "$4.99",
                packageTitle = "Starter Pack",
                idempotencyKey = orderKey
            )
        }
        assertTrue(duplicatePurchase.isSuccess)
        assertEquals(125L, duplicatePurchase.getOrThrow())
    }

    @Test
    fun mutualMatch_femaleFreeChat_andMalePaidChatDeduction_enforcesPolicy() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val repo = VelourRepository(firestore)

        // 1. Setup Aria (eligible female account - free messaging)
        val ariaUid = signInTestUser("aria_$suffix@velour.test")
        withTimeout(TIMEOUT_MS) {
            repo.completeProfileSetup(
                displayName = "Aria",
                birthYear = 2000,
                gender = "female",
                bio = "Botanical designer",
                city = "San Francisco",
                interests = listOf("Design", "Coffee"),
                photoKey = "img_profile_aria",
                email = "aria_$suffix@velour.test",
                phone = "+15550102",
                callPrivacyConsent = true,
                minPreferredAge = 24,
                maxPreferredAge = 36,
                maxDistanceKm = 50
            ).getOrThrow()
        }
        val ariaProfile = withTimeout(TIMEOUT_MS) { checkNotNull(repo.getPublicProfile(ariaUid)) }

        // 2. Setup Julian (male account - 25 welcome coins)
        val julianUid = signInTestUser("julian_match_$suffix@velour.test")
        withTimeout(TIMEOUT_MS) {
            repo.completeProfileSetup(
                displayName = "Julian",
                birthYear = 1997,
                gender = "male",
                bio = "Architect",
                city = "San Francisco",
                interests = listOf("Design", "Travel"),
                photoKey = "img_profile_julian",
                email = "julian_match_$suffix@velour.test",
                phone = "+15550103",
                callPrivacyConsent = true,
                minPreferredAge = 22,
                maxPreferredAge = 34,
                maxDistanceKm = 50
            ).getOrThrow()
        }
        val julianProfile = withTimeout(TIMEOUT_MS) { checkNotNull(repo.getPublicProfile(julianUid)) }

        // Julian likes Aria first (not mutual yet)
        val firstSwipe = withTimeout(TIMEOUT_MS) {
            repo.recordSwipeAndCheckMatch(julianProfile, ariaProfile, "like")
        }
        assertTrue(firstSwipe.isSuccess)
        assertEquals(null, firstSwipe.getOrThrow())

        // Aria likes Julian -> creates mutual match Conversation
        signInTestUser("aria_$suffix@velour.test")
        val mutualSwipe = withTimeout(TIMEOUT_MS) {
            repo.recordSwipeAndCheckMatch(ariaProfile, julianProfile, "like")
        }
        assertTrue("Mutual swipe failed: ${mutualSwipe.exceptionOrNull()}", mutualSwipe.isSuccess)
        val conversation = checkNotNull(mutualSwipe.getOrThrow())

        val pricing = PricingConfig()
        // Aria sends free message (0 coins deducted, balance remains 25)
        val ariaMsgResult = withTimeout(TIMEOUT_MS) {
            repo.sendChatMessage(
                conversation = conversation,
                senderProfile = ariaProfile,
                pricing = pricing,
                text = "Hi Julian! Lovely profile."
            )
        }
        assertTrue("Aria free message failed: ${ariaMsgResult.exceptionOrNull()}", ariaMsgResult.isSuccess)
        val ariaWallet = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeWallet(ariaUid).first { it != null }
        }
        assertEquals(25L, ariaWallet?.balanceCoins)

        // Julian sends paid message (5 coins deducted -> 20 coins remaining)
        signInTestUser("julian_match_$suffix@velour.test")
        val julianMsgResult = withTimeout(TIMEOUT_MS) {
            repo.sendChatMessage(
                conversation = conversation,
                senderProfile = julianProfile,
                pricing = pricing,
                text = "Thanks Aria! Would love to chat."
            )
        }
        assertTrue("Julian paid message failed: ${julianMsgResult.exceptionOrNull()}", julianMsgResult.isSuccess)
        val julianWallet = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeWallet(julianUid).first { it != null && it.balanceCoins == 20L }
        }
        assertEquals(20L, julianWallet?.balanceCoins)
    }

    @Test
    fun crossUserPrivatePiiAccess_failsWithPermissionDenied() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val repo = VelourRepository(firestore)

        val aliceUid = signInTestUser("alice_pii_$suffix@velour.test")
        withTimeout(TIMEOUT_MS) {
            repo.completeProfileSetup(
                displayName = "Alice",
                birthYear = 1999,
                gender = "female",
                bio = "Gallery curator",
                city = "New York",
                interests = listOf("Art"),
                photoKey = "img_profile_maya",
                email = "alice_pii_$suffix@velour.test",
                phone = "+15559999",
                callPrivacyConsent = true,
                minPreferredAge = 24,
                maxPreferredAge = 35,
                maxDistanceKm = 40
            ).getOrThrow()
        }

        // Bob signs in and attempts to read Alice's private PII document
        signInTestUser("bob_snooper_$suffix@velour.test")
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users_private").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's private PII")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun observeDiscoverableProfiles_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = VelourRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeDiscoverableProfiles().first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected nested FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
