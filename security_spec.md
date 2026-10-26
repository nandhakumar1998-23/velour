# Velour Zero-Trust Firestore Security Specification

## 1. Data Invariants

1. **Authentication Prerequisite**: Zero unauthenticated reads or writes across all collections (`request.auth != null`).
2. **PII Split Isolation**: Private PII (`email`, `phone`, `ageConfirmed18Plus`, `callPrivacyConsent`) lives exclusively in `/users_private/{userId}` and is readable/writable strictly by `request.auth.uid == userId` (`allow list: if false`). Public discovery cards in `/public_profiles/{userId}` contain zero PII.
3. **18+ Adult Eligibility & Gender Anti-Abuse**: `PublicProfile` requires `age >= 18 && age <= 100` and `birthYear <= 2008`. Once created, `gender` (`'female'` or `'male'`) is strictly immutable by the user (`incoming().gender == existing().gender`), preventing gender-switching to abuse the Female Free-Access Policy.
4. **Privilege Escalation Prevention**: Users cannot set `verified == true` on `/public_profiles/{userId}` during creation or self-update, nor write to `/admins/{adminUid}` or `/platform_config/{configId}`. Admin checks require either an `/admins/{uid}` document or verified email (`request.auth.token.email == 'nandhakumarid023@gmail.com' && request.auth.token.email_verified == true`).
5. **Wallet Integrity & Append-Only Ledger**: Initial wallet balance must be `25` welcome coins (`totalPurchasedCoins == 0`, `totalSpentCoins == 0`). Top-ups only allow configured package increments (`100`, `300`, `800`) and deductions cannot drop `balanceCoins` below `0`. `/wallet_transactions/{txId}` is strictly append-only (`allow update, delete: if false`).
6. **Private Chat & Call Participant Isolation**: Conversations, chat messages, and call sessions require `request.auth.uid in participantUids` (`size() == 2`). Subcollection `/conversations/{conversationId}/messages/{messageId}` denormalizes `participantUids` for zero-latency flat authorization without `get()` overhead.
7. **Terminal State Locking**: Calls in `['ended', 'rejected', 'exhausted']` and conversations in `'blocked'` reject any subsequent user mutations.
8. **Query Enforcer Alignment**: Every `allow list` rule evaluates `resource.data` directly without `get()`/`exists()` calls, rejecting unfiltered collection scans.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Scrape**: `GET /public_profiles` with `auth = null` -> REJECTED.
2. **Cross-User PII Theft**: Alice (`alice_123`) executes `GET /users_private/bob_456` -> REJECTED.
3. **Underage Registration**: `CREATE /public_profiles/alice_123` with `{"age": 17, "birthYear": 2010}` -> REJECTED.
4. **Gender-Switch Free-Access Abuse**: Male user updates `/public_profiles/bob_456` changing `{"gender": "female"}` -> REJECTED.
5. **Shadow Verification Injection**: User updates `/public_profiles/alice_123` with `{"bio": "Hi", "verified": true}` or ghost key `{"isAdmin": true}` -> REJECTED by `affectedKeys().hasOnly(...)` and `incoming().verified == existing().verified`.
6. **Admin Email Spoofing**: Attacker with `{"email": "nandhakumarid023@gmail.com", "email_verified": false}` attempts `UPDATE /platform_config/pricing` -> REJECTED.
7. **Arbitrary Minting Attack**: User creates `/wallets/bob_456` with `{"balanceCoins": 999999}` -> REJECTED (`balanceCoins == 25` enforced on create).
8. **Negative Balance Spend**: User updates `/wallets/bob_456` setting `{"balanceCoins": -10}` -> REJECTED.
9. **Transaction Ledger Tampering**: User attempts `UPDATE` or `DELETE` on `/wallet_transactions/tx_1` -> REJECTED.
10. **Unauthorized Private Chat Snooping**: Mallory (`mallory_789`) attempts `LIST /conversations/alice_123_bob_456/messages` -> REJECTED.
11. **Terminal Call Revival**: Caller attempts `UPDATE /calls/call_1` from `status: 'ended'` back to `status: 'connected'` -> REJECTED.
12. **Unfiltered Query Scraping**: Authenticated user runs `db.collection("wallet_transactions").get()` without `.where("userId", "==", uid)` -> REJECTED.

## 3. Red Team Audit Summary

- **Shadow Update Test**: Passed across all mutable collections via `incoming().keys().hasOnly(...)` on create and `incoming().diff(existing()).affectedKeys().hasOnly(...)` on every update branch.
- **Email Spoofing Test**: Passed via explicit `request.auth.token.email_verified == true` check in `isBootstrappedAdmin()`.
- **PII Blanket Test**: Passed via Split Collection (`/users_private/{userId}` owner-only `get`, `list` disabled).
- **Query Trust Test**: Passed across all `allow list` rules (`resource.data.visibility == 'discoverable'`, `resource.data.userId == request.auth.uid`, `request.auth.uid in resource.data.participantUids`, etc.) with zero `get()`/`exists()` inside `list`.
- **Value Poisoning Test**: Passed because every `allow update` block wraps all branches in `isValid[Entity](incoming())` enforcing types, bounds, and `.size()` limits.
