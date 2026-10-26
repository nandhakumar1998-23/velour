const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const MALLORY_UID = "mallory_789";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "nandhakumarid023@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function serverTs(db) {
  return db.app.firebase_.firestore.FieldValue.serverTimestamp
    ? db.app.firebase_.firestore.FieldValue.serverTimestamp()
    : new Date();
}

test("1. Unauthenticated user cannot read public profiles or wallets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("public_profiles").where("visibility", "==", "discoverable").get());
  await assertFails(unauthDb.collection("wallets").doc(ALICE_UID).get());
});

test("2. PII Isolation: Authenticated user cannot read another user's private info", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users_private").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      phone: "+15550199",
      ageConfirmed18Plus: true,
      callPrivacyConsent: true,
      notificationsEnabled: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users_private").doc(BOB_UID).get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("users_private").doc(BOB_UID).get());
});

test("3. 18+ Enforcement & Anti-Abuse: Underage creation and gender-switching update both fail", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  // Underage (age 17, birthYear 2010) must fail
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Young Bob",
      birthYear: 2010,
      age: 17,
      gender: "male",
      bio: "Hello",
      city: "Austin",
      distanceKm: 10,
      interests: ["Music"],
      photoKey: "img_profile_julian",
      verified: false,
      visibility: "discoverable",
      isOnline: true,
      minPreferredAge: 18,
      maxPreferredAge: 30,
      maxDistanceKm: 50,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid adult male profile succeeds
  await assertSucceeds(
    bobDb.collection("public_profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Julian",
      birthYear: 1997,
      age: 29,
      gender: "male",
      bio: "Architect and coastal runner",
      city: "San Francisco",
      distanceKm: 8,
      interests: ["Architecture", "Running"],
      photoKey: "img_profile_julian",
      verified: false,
      visibility: "discoverable",
      isOnline: true,
      minPreferredAge: 22,
      maxPreferredAge: 34,
      maxDistanceKm: 50,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Attempting to switch gender to 'female' to abuse free-access policy must fail
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).update({
      gender: "female",
      updatedAt: new Date(),
    })
  );

  // Shadow update injecting verified: true must fail
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).update({
      bio: "Updated bio",
      verified: true,
      updatedAt: new Date(),
    })
  );
});

test("4. Email Spoofing Test: Unverified admin email cannot modify pricing config", async () => {
  const spoofAdminDb = testEnv
    .authenticatedContext(MALLORY_UID, {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  const pricingPayload = {
    configId: "pricing",
    femaleFreeChatEnabled: true,
    femaleFreeCallsEnabled: true,
    femaleDailyFreeMessagesLimit: 250,
    maleMessageCostCoins: 5,
    maleAudioCallCoinsPerMin: 15,
    maleVideoCallCoinsPerMin: 30,
    lowBalanceWarningThresholdCoins: 20,
    starterPackCoins: 100,
    starterPackPriceUsd: "$4.99",
    popularPackCoins: 300,
    popularPackPriceUsd: "$12.99",
    vipPackCoins: 800,
    vipPackPriceUsd: "$29.99",
    updatedAt: new Date(),
  };

  await assertFails(spoofAdminDb.collection("platform_config").doc("pricing").set(pricingPayload));

  // Real verified admin succeeds
  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(realAdminDb.collection("platform_config").doc("pricing").set(pricingPayload));
});

test("5. Wallet Integrity & Append-Only Ledger: Rejects arbitrary initial balance and negative spend", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date();

  // Arbitrary initial balance fails
  await assertFails(
    bobDb.collection("wallets").doc(BOB_UID).set({
      userId: BOB_UID,
      balanceCoins: 5000,
      totalPurchasedCoins: 0,
      totalSpentCoins: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Exact welcome bonus (25 coins) succeeds
  await assertSucceeds(
    bobDb.collection("wallets").doc(BOB_UID).set({
      userId: BOB_UID,
      balanceCoins: 25,
      totalPurchasedCoins: 0,
      totalSpentCoins: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Spend 5 coins succeeds
  await assertSucceeds(
    bobDb.collection("wallets").doc(BOB_UID).update({
      balanceCoins: 20,
      totalSpentCoins: 5,
      updatedAt: new Date(),
    })
  );

  // Spend below 0 fails
  await assertFails(
    bobDb.collection("wallets").doc(BOB_UID).update({
      balanceCoins: -5,
      totalSpentCoins: 30,
      updatedAt: new Date(),
    })
  );

  // Append-only ledger: create succeeds, update/delete fails
  const bobTxId = `${BOB_UID}--welcome`;
  await assertSucceeds(
    bobDb.collection("wallet_transactions").doc(bobTxId).set({
      userId: BOB_UID,
      idempotencyKey: bobTxId,
      type: "welcome_bonus",
      deltaCoins: 25,
      balanceAfter: 25,
      description: "Welcome bonus credits",
      status: "completed",
      amountUsd: "$0.00",
      createdAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("wallet_transactions").doc(bobTxId).delete());
});

test("6. Private Chat & Terminal Call Locking: Blocks third-party snooping and ended call revival", async () => {
  const convId = `${ALICE_UID}--${BOB_UID}`;
  const ts = new Date();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantUids: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userBId: BOB_UID,
      userAName: "Aria",
      userBName: "Julian",
      userAPhotoKey: "img_profile_aria",
      userBPhotoKey: "img_profile_julian",
      lastMessageText: "Matched!",
      lastSenderId: ALICE_UID,
      status: "matched",
      typingUserId: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      text: "Hey Julian!",
      mediaUri: "",
      messageType: "text",
      coinsCharged: 0,
      readByRecipient: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Mallory cannot list or get private messages
  await assertFails(
    malloryDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", MALLORY_UID)
      .get()
  );
  await assertFails(
    malloryDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );

  // Alice CAN list messages with aligned participantUids filter
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  // Call terminal state locking
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Aria",
      calleeId: BOB_UID,
      calleeName: "Julian",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      rateCoinsPerMin: 0,
      durationSeconds: 0,
      totalCoinsCharged: 0,
      signalingMode: "webrtc_signaling_preview",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 0,
      totalCoinsCharged: 0,
      updatedAt: new Date(),
    })
  );

  // Reviving an ended call must fail
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "connected",
      updatedAt: new Date(),
    })
  );
});

test("7. Query Trust Test: Unfiltered list queries fail while aligned filtered queries succeed", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aria",
      birthYear: 2000,
      age: 26,
      gender: "female",
      bio: "Botanical enthusiast",
      city: "Seattle",
      distanceKm: 5,
      interests: ["Art"],
      photoKey: "img_profile_aria",
      verified: true,
      visibility: "discoverable",
      isOnline: true,
      minPreferredAge: 24,
      maxPreferredAge: 35,
      maxDistanceKm: 50,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await db.collection("public_profiles").doc(MALLORY_UID).set({
      userId: MALLORY_UID,
      displayName: "Mallory",
      birthYear: 1998,
      age: 28,
      gender: "female",
      bio: "Paused account",
      city: "Seattle",
      distanceKm: 5,
      interests: ["Art"],
      photoKey: "img_profile_maya",
      verified: false,
      visibility: "paused",
      isOnline: false,
      minPreferredAge: 24,
      maxPreferredAge: 35,
      maxDistanceKm: 50,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Unfiltered scan fails because Mallory's profile has visibility == 'paused'
  await assertFails(bobDb.collection("public_profiles").get());
  // Aligned Discover query succeeds
  await assertSucceeds(bobDb.collection("public_profiles").where("visibility", "==", "discoverable").get());
});
