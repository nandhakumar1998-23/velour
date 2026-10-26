package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GeneratingTokens
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.BlockRecord
import com.example.data.model.CoinPackageOption
import com.example.data.model.ModerationReport
import com.example.data.model.PricingConfig
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.ui.PaymentOutcome
import com.example.ui.PaymentOutcomeStatus
import com.example.ui.components.PricingPolicyBanner
import com.example.ui.components.ProfilePhotoView
import com.example.ui.components.VerifiedBadge

@Composable
fun WalletScreen(
    myProfile: PublicProfile,
    wallet: Wallet?,
    pricing: PricingConfig,
    packages: List<CoinPackageOption>,
    transactionsState: UiState<List<WalletTransaction>>,
    paymentOutcome: PaymentOutcome,
    isBusy: Boolean,
    onPurchasePackage: (CoinPackageOption, Boolean) -> Unit,
    onDismissPaymentOutcome: () -> Unit
) {
    var selectedPkgForCheckout by remember { mutableStateOf<CoinPackageOption?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            item {
                Text("Velour Wallet", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Server-verified digital credits • Zero card details stored on device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.GeneratingTokens,
                                contentDescription = "Wallet balance",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Available Balance",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "${wallet?.balanceCoins ?: 25L} Coins",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Purchased: ${wallet?.totalPurchasedCoins ?: 0L} • Spent: ${wallet?.totalSpentCoins ?: 0L}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                PricingPolicyBanner(gender = myProfile.gender, pricing = pricing)
            }

            item {
                Text("Select a Credit Package", style = MaterialTheme.typography.titleLarge)
            }

            items(packages, key = { it.id }) { pkg ->
                Card(
                    border = if (pkg.badge != null) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (pkg.badge != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = pkg.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text("${pkg.title} • ${pkg.coins} Coins", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = pkg.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { selectedPkgForCheckout = pkg },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("buy_package_${pkg.id}_button")
                        ) {
                            Text(pkg.priceUsd)
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SupportAgent,
                            contentDescription = "Billing & refund policy",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google Play Digital Billing Compliant: No raw card data is collected or stored. Unused package refund inquiries can be submitted within 48 hours via Billing Support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = "Ledger")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transaction History & Receipts", style = MaterialTheme.typography.titleLarge)
                }
            }

            when (transactionsState) {
                is UiState.Loading -> item {
                    Text("Loading verified ledger…", style = MaterialTheme.typography.bodySmall)
                }
                is UiState.Error -> item {
                    Text(transactionsState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    if (transactionsState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No transactions recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(transactionsState.data, key = { it.id }) { tx ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = "Balance after: ${tx.balanceAfter} coins • ${tx.amountUsd}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val prefix = if (tx.deltaCoins >= 0) "+" else ""
                                    Text(
                                        text = "$prefix${tx.deltaCoins}c",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (tx.deltaCoins >= 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Explicit Checkout Confirmation Modal
    selectedPkgForCheckout?.let { pkg ->
        AlertDialog(
            onDismissRequest = { selectedPkgForCheckout = null },
            title = { Text("Confirm Checkout • ${pkg.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Package: ${pkg.coins} Digital Coins")
                    Text("Total Price: ${pkg.priceUsd} (Idempotent server-verified ledger credit)")
                    Text(
                        text = "Payment cards are never stored in Velour. Confirm below to verify and credit your wallet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pkg
                        selectedPkgForCheckout = null
                        onPurchasePackage(target, false)
                    },
                    modifier = Modifier.testTag("confirm_wallet_checkout_button")
                ) {
                    Text("Pay ${pkg.priceUsd} & Credit ${pkg.coins}c")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { selectedPkgForCheckout = null }) {
                        Text("Cancel")
                    }
                    OutlinedButton(
                        onClick = {
                            val target = pkg
                            selectedPkgForCheckout = null
                            onPurchasePackage(target, true)
                        }
                    ) {
                        Text("Test Declined")
                    }
                }
            }
        )
    }

    if (paymentOutcome.status != PaymentOutcomeStatus.NONE) {
        val isOk = paymentOutcome.status == PaymentOutcomeStatus.SUCCESS
        AlertDialog(
            onDismissRequest = onDismissPaymentOutcome,
            icon = {
                Icon(
                    imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = paymentOutcome.title,
                    tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(paymentOutcome.title) },
            text = { Text(paymentOutcome.message) },
            confirmButton = {
                Button(onClick = onDismissPaymentOutcome) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun NotificationsDialog(
    notifications: List<AppNotification>,
    onMarkRead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Notifications") },
        title = { Text("Activity & Safety Alerts") },
        text = {
            if (notifications.isEmpty()) {
                Text("You're all caught up! New matches, calls, and receipts appear here.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isRead) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkRead(item.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(item.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SettingsAndProfileScreen(
    myProfile: PublicProfile,
    privateInfo: UserPrivateInfo?,
    blockedUsers: List<BlockRecord>,
    isDarkTheme: Boolean,
    isAdminUser: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSaveProfile: (
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
    ) -> Unit,
    onSavePrivateSettings: (phone: String, callConsent: Boolean, notificationsEnabled: Boolean) -> Unit,
    onUnblockUser: (String) -> Unit,
    onOpenAdminConsole: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var displayName by remember(myProfile.displayName) { mutableStateOf(myProfile.displayName) }
    var bio by remember(myProfile.bio) { mutableStateOf(myProfile.bio) }
    var city by remember(myProfile.city) { mutableStateOf(myProfile.city) }
    var photoKey by remember(myProfile.photoKey) { mutableStateOf(myProfile.photoKey) }
    var isDiscoverable by remember(myProfile.visibility) { mutableStateOf(myProfile.visibility == "discoverable") }
    var isOnline by remember(myProfile.isOnline) { mutableStateOf(myProfile.isOnline) }
    var ageRange by remember(myProfile.minPreferredAge, myProfile.maxPreferredAge) {
        mutableStateOf(myProfile.minPreferredAge.toFloat()..myProfile.maxPreferredAge.toFloat())
    }
    var maxDist by remember(myProfile.maxDistanceKm) { mutableFloatStateOf(myProfile.maxDistanceKm.toFloat()) }

    var phone by remember(privateInfo?.phone) { mutableStateOf(privateInfo?.phone ?: "") }
    var callConsent by remember(privateInfo?.callPrivacyConsent) { mutableStateOf(privateInfo?.callPrivacyConsent ?: true) }
    var notifEnabled by remember(privateInfo?.notificationsEnabled) { mutableStateOf(privateInfo?.notificationsEnabled ?: true) }

    var activeLegalDoc by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoKey = uri.toString()
    }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifEnabled = granted
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
                    if (isAdminUser) {
                        OutlinedButton(
                            onClick = onOpenAdminConsole,
                            modifier = Modifier.testTag("open_admin_console_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin console")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Admin")
                        }
                    }
                }
            }

            // Profile Header Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ProfilePhotoView(
                            photoKey = photoKey,
                            contentDescription = "Your profile photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${myProfile.displayName}, ${myProfile.age}", style = MaterialTheme.typography.titleLarge)
                                if (myProfile.verified) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    VerifiedBadge()
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = "Immutable gender", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified Account Gender: ${myProfile.gender.uppercase()} (Locked for anti-abuse)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = "Change photo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Photo")
                            }
                        }
                    }
                }
            }

            // Edit Profile Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Edit Public Profile & Discovery", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City / Region") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("Bio") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show me in Discover")
                            Switch(checked = isDiscoverable, onCheckedChange = { isDiscoverable = it })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Online Status")
                            Switch(checked = isOnline, onCheckedChange = { isOnline = it })
                        }
                        Text("Preferred Age: ${ageRange.start.toInt()} – ${ageRange.endInclusive.toInt()}")
                        RangeSlider(value = ageRange, onValueChange = { ageRange = it }, valueRange = 18f..80f)
                        Text("Max Distance: ${maxDist.toInt()} km")
                        Slider(value = maxDist, onValueChange = { maxDist = it }, valueRange = 5f..500f)

                        Button(
                            onClick = {
                                onSaveProfile(
                                    displayName,
                                    bio,
                                    city,
                                    myProfile.interests,
                                    photoKey,
                                    if (isDiscoverable) "discoverable" else "paused",
                                    isOnline,
                                    ageRange.start.toInt(),
                                    ageRange.endInclusive.toInt(),
                                    maxDist.toInt()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_profile_settings_button")
                        ) {
                            Text("Save Profile Changes")
                        }
                    }
                }
            }

            // Appearance, Privacy & Notifications
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Theme, Privacy & Notifications", style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Luxury Dark Theme")
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = onToggleDarkTheme,
                                modifier = Modifier.testTag("dark_theme_switch")
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Push Notifications")
                            Switch(
                                checked = notifEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notifEnabled = checked
                                    }
                                }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1-on-1 Audio/Video Call Consent")
                            Switch(checked = callConsent, onCheckedChange = { callConsent = it })
                        }
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Private Recovery Phone (Never shown publicly)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { onSavePrivateSettings(phone, callConsent, notifEnabled) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Privacy & Alert Preferences")
                        }
                    }
                }
            }

            // Blocked Users
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Blocked Members (${blockedUsers.size})", style = MaterialTheme.typography.titleMedium)
                        if (blockedUsers.isEmpty()) {
                            Text(
                                text = "You haven't blocked anyone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            blockedUsers.forEach { block ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(block.blockedName, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { onUnblockUser(block.id) }) {
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legal & Account Actions
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Legal, Safety & Account", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                activeLegalDoc = "Terms of Service (18+)" to
                                    "Velour is strictly for consenting adults aged 18 and older. Eligible female accounts receive free core chat and calling subject to fair-use anti-spam limits. Male accounts view exact coin costs before any paid message or call. Harassment, impersonation, or solicitation results in immediate suspension."
                            }
                        ) { Text("Terms of Service") }
                        TextButton(
                            onClick = {
                                activeLegalDoc = "Privacy & Call Safety Policy" to
                                    "Private contact details (email, recovery phone) are stored in an isolated owner-only Firestore collection (/users_private) and never exposed in discovery. Audio and video calls are never recorded by Velour and require mutual consent."
                            }
                        ) { Text("Privacy Policy") }
                        TextButton(
                            onClick = {
                                activeLegalDoc = "Help, Billing & Safety Support" to
                                    "Need assistance with a coin package receipt or safety report? Contact support@velour.social with your transaction description. All reports are reviewed by verified moderators."
                            }
                        ) { Text("Help & Support") }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_account_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Delete account")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Account & Data")
                        }
                    }
                }
            }
        }
    }

    activeLegalDoc?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { activeLegalDoc = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(onClick = { activeLegalDoc = null }) { Text("Close") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Permanently Delete Account?") },
            text = {
                Text("This permanently deletes your public profile, private PII record, and wallet data from Velour and signs you out.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminConsoleScreen(
    pricing: PricingConfig,
    openReports: List<ModerationReport>,
    allProfiles: List<PublicProfile>,
    onSavePricing: (PricingConfig) -> Unit,
    onResolveReport: (String, String) -> Unit,
    onModerateProfile: (userId: String, verified: Boolean, visibility: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var femaleFreeChat by remember(pricing) { mutableStateOf(pricing.femaleFreeChatEnabled) }
    var femaleFreeCalls by remember(pricing) { mutableStateOf(pricing.femaleFreeCallsEnabled) }
    var msgCostText by remember(pricing) { mutableStateOf(pricing.maleMessageCostCoins.toString()) }
    var audioRateText by remember(pricing) { mutableStateOf(pricing.maleAudioCallCoinsPerMin.toString()) }
    var videoRateText by remember(pricing) { mutableStateOf(pricing.maleVideoCallCoinsPerMin.toString()) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit admin console")
                    }
                    Text("Admin Policy & Moderation", style = MaterialTheme.typography.headlineSmall)
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Configurable Pricing & Female Free-Access Policy", style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Female Free Core Chat")
                            Switch(checked = femaleFreeChat, onCheckedChange = { femaleFreeChat = it })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Female Free Audio/Video Calls")
                            Switch(checked = femaleFreeCalls, onCheckedChange = { femaleFreeCalls = it })
                        }
                        OutlinedTextField(
                            value = msgCostText,
                            onValueChange = { msgCostText = it },
                            label = { Text("Male Message Cost (Coins)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = audioRateText,
                            onValueChange = { audioRateText = it },
                            label = { Text("Male Audio Call Rate (Coins / min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = videoRateText,
                            onValueChange = { videoRateText = it },
                            label = { Text("Male Video Call Rate (Coins / min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onSavePricing(
                                    pricing.copy(
                                        femaleFreeChatEnabled = femaleFreeChat,
                                        femaleFreeCallsEnabled = femaleFreeCalls,
                                        maleMessageCostCoins = (msgCostText.toIntOrNull() ?: 5).coerceIn(0, 500),
                                        maleAudioCallCoinsPerMin = (audioRateText.toIntOrNull() ?: 15).coerceIn(0, 500),
                                        maleVideoCallCoinsPerMin = (videoRateText.toIntOrNull() ?: 30).coerceIn(0, 1000)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_save_pricing_button")
                        ) {
                            Text("Publish Live Pricing Policy")
                        }
                    }
                }
            }

            item {
                Text("Open Safety Reports (${openReports.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(openReports, key = { it.id }) { rep ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("${rep.reportedUserName} • ${rep.category}", fontWeight = FontWeight.Bold)
                        Text(rep.details, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onResolveReport(rep.id, "resolved") }) { Text("Resolve") }
                            TextButton(onClick = { onResolveReport(rep.id, "dismissed") }) { Text("Dismiss") }
                        }
                    }
                }
            }

            item {
                Text("Member Verification (${allProfiles.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(allProfiles, key = { it.userId }) { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${member.displayName} (${member.gender})", fontWeight = FontWeight.Medium)
                            Text("Verified: ${member.verified} • ${member.visibility}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                onModerateProfile(member.userId, !member.verified, member.visibility)
                            }
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = "Toggle verification", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (member.verified) "Unverify" else "Verify 18+")
                        }
                    }
                }
            }
        }
    }
}
