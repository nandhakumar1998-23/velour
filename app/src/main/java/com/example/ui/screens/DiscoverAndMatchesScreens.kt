package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.PricingConfig
import com.example.data.model.PublicProfile
import com.example.data.model.SwipeAction
import com.example.data.model.UiState
import com.example.ui.DiscoverFilter
import com.example.ui.components.CoinBalancePill
import com.example.ui.components.PricingPolicyBanner
import com.example.ui.components.ProfilePhotoView
import com.example.ui.components.SafetyReportBlockDialog
import com.example.ui.components.SkeletonLoadingCard
import com.example.ui.components.VerifiedBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    myProfile: PublicProfile,
    candidatesState: UiState<List<PublicProfile>>,
    pricing: PricingConfig,
    balanceCoins: Long,
    unreadNotificationCount: Int,
    filter: DiscoverFilter,
    matchedCelebration: Conversation?,
    onUpdateFilter: (DiscoverFilter) -> Unit,
    onSwipe: (PublicProfile, String) -> Unit,
    onReportOrBlock: (PublicProfile, String?, String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMatchedChat: (Conversation) -> Unit,
    onDismissMatchCelebration: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var showFilterDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<PublicProfile?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Curated for ${myProfile.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoinBalancePill(balanceCoins = balanceCoins, onClick = onOpenWallet)
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("open_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge { Text("$unreadNotificationCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                    }
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("open_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Discovery filters"
                        )
                    }
                }
            }

            PricingPolicyBanner(gender = myProfile.gender, pricing = pricing)

            // Search bar
            OutlinedTextField(
                value = filter.searchQuery,
                onValueChange = { onUpdateFilter(filter.copy(searchQuery = it)) },
                placeholder = { Text("Search interests, city, or name...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_search_input")
            )

            // Candidate Deck
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (candidatesState) {
                    is UiState.Loading -> SkeletonLoadingCard()
                    is UiState.Error -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = candidatesState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val list = candidatesState.data
                        val topCandidate = list.firstOrNull()
                        if (topCandidate == null) {
                            EmptyDiscoverState(
                                hasActiveFilters = filter.verifiedOnly || filter.onlineOnly || filter.searchQuery.isNotBlank(),
                                onResetFilters = { onUpdateFilter(DiscoverFilter()) }
                            )
                        } else {
                            AnimatedContent(
                                targetState = topCandidate,
                                transitionSpec = {
                                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.94f)) togetherWith fadeOut()
                                },
                                label = "profile_card_deck"
                            ) { candidate ->
                                val sharedInterests = candidate.interests.intersect(myProfile.interests.toSet())
                                Card(
                                    shape = RoundedCornerShape(32.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("discover_profile_card")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        ProfilePhotoView(
                                            photoKey = candidate.photoKey,
                                            contentDescription = "${candidate.displayName}'s portrait",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        0.4f to Color.Transparent,
                                                        1.0f to Color(0xE6140710)
                                                    )
                                                )
                                        )

                                        // Top status chips
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (candidate.isOnline) {
                                                    Surface(
                                                        color = Color(0xCC1B5E20),
                                                        shape = CircleShape
                                                    ) {
                                                        Text(
                                                            text = "● Online",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                                if (candidate.verified) {
                                                    VerifiedBadge()
                                                }
                                            }
                                            IconButton(
                                                onClick = { reportTarget = candidate },
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                                    .testTag("report_candidate_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "Safety options",
                                                    tint = Color.White
                                                )
                                            }
                                        }

                                        // Bottom info & swipe buttons
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${candidate.displayName}, ${candidate.age}",
                                                style = MaterialTheme.typography.headlineLarge,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${candidate.city} • ${candidate.distanceKm} km away",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                            if (candidate.bio.isNotBlank()) {
                                                Text(
                                                    text = candidate.bio,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.92f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (candidate.interests.isNotEmpty()) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    candidate.interests.take(5).forEach { interest ->
                                                        val isShared = interest in sharedInterests
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = if (isShared) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                Color.White.copy(alpha = 0.2f)
                                                            }
                                                        ) {
                                                            Text(
                                                                text = if (isShared) "★ $interest" else interest,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Pass / Super-Like / Like Controls (all >= 56.dp touch targets)
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                FilledIconButton(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSwipe(candidate, "pass")
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = Color.White.copy(alpha = 0.22f),
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier
                                                        .size(58.dp)
                                                        .testTag("swipe_pass_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "Pass profile",
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }

                                                FilledIconButton(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onSwipe(candidate, "super_like")
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                                    ),
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .testTag("swipe_super_like_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Star,
                                                        contentDescription = "Super-like profile",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }

                                                FilledIconButton(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onSwipe(candidate, "like")
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .testTag("swipe_like_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Favorite,
                                                        contentDescription = "Like profile",
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mutual Match Celebration Dialog
        AnimatedVisibility(visible = matchedCelebration != null) {
            matchedCelebration?.let { conv ->
                AlertDialog(
                    onDismissRequest = onDismissMatchCelebration,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Mutual match",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "It's a Mutual Match!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Text(
                            text = "You and ${conv.partnerName(myProfile.userId)} both liked each other. Start a real-time conversation now!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDismissMatchCelebration()
                                onOpenMatchedChat(conv)
                            },
                            modifier = Modifier.testTag("match_say_hello_button")
                        ) {
                            Text("Message ${conv.partnerName(myProfile.userId)}")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissMatchCelebration) {
                            Text("Keep Discovering")
                        }
                    }
                )
            }
        }
    }

    if (showFilterDialog) {
        DiscoverFilterDialog(
            initial = filter,
            onDismiss = { showFilterDialog = false },
            onApply = {
                onUpdateFilter(it)
                showFilterDialog = false
            }
        )
    }

    reportTarget?.let { target ->
        SafetyReportBlockDialog(
            targetName = target.displayName,
            onDismiss = { reportTarget = null },
            onBlockOnly = { reason ->
                onReportOrBlock(target, null, reason)
            },
            onReportAndBlock = { category, details ->
                onReportOrBlock(target, category, details)
            }
        )
    }
}

@Composable
private fun EmptyDiscoverState(
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = "Discovery caught up",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_discover_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (hasActiveFilters) {
                    "No discoverable profiles match your current filter settings. Reset filters to widen your search."
                } else {
                    "Velour only shows real registered members (never fake bots). Invite another adult account on a second device or browser session to test live mutual matching, messaging, and calls!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasActiveFilters) {
                OutlinedButton(
                    onClick = onResetFilters,
                    modifier = Modifier.testTag("reset_discover_filters_button")
                ) {
                    Text("Reset Filters")
                }
            }
        }
    }
}

@Composable
private fun DiscoverFilterDialog(
    initial: DiscoverFilter,
    onDismiss: () -> Unit,
    onApply: (DiscoverFilter) -> Unit
) {
    var ageRange by remember { mutableStateOf(initial.minAge.toFloat()..initial.maxAge.toFloat()) }
    var distance by remember { mutableFloatStateOf(initial.maxDistanceKm.toFloat()) }
    var verifiedOnly by remember { mutableStateOf(initial.verifiedOnly) }
    var onlineOnly by remember { mutableStateOf(initial.onlineOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discovery Preferences") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Age Range: ${ageRange.start.toInt()} – ${ageRange.endInclusive.toInt()}")
                RangeSlider(
                    value = ageRange,
                    onValueChange = { ageRange = it },
                    valueRange = 18f..80f
                )
                Text("Maximum Distance: ${distance.toInt()} km")
                Slider(
                    value = distance,
                    onValueChange = { distance = it },
                    valueRange = 5f..500f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = verifiedOnly,
                        onClick = { verifiedOnly = !verifiedOnly },
                        label = { Text("Verified Only") }
                    )
                    FilterChip(
                        selected = onlineOnly,
                        onClick = { onlineOnly = !onlineOnly },
                        label = { Text("Online Now") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        initial.copy(
                            minAge = ageRange.start.toInt(),
                            maxAge = ageRange.endInclusive.toInt(),
                            maxDistanceKm = distance.toInt(),
                            verifiedOnly = verifiedOnly,
                            onlineOnly = onlineOnly
                        )
                    )
                },
                modifier = Modifier.testTag("apply_filters_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MatchesListScreen(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    incomingLikes: List<SwipeAction>,
    onOpenConversation: (Conversation) -> Unit,
    onUnmatch: (Conversation) -> Unit,
    onReportOrBlock: (partnerId: String, partnerName: String, convId: String, category: String?, details: String) -> Unit
) {
    var safetyTargetConv by remember { mutableStateOf<Conversation?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Matches & Chats", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${incomingLikes.size} admirer(s) liked your profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when (conversationsState) {
                is UiState.Loading -> SkeletonLoadingCard()
                is UiState.Error -> {
                    Text(
                        text = conversationsState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    val conversations = conversationsState.data
                    if (conversations.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "No matches yet",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(R.string.empty_matches_title),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = stringResource(R.string.empty_matches_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(conversations, key = { it.id }) { conv ->
                                var menuExpanded by remember { mutableStateOf(false) }
                                val partnerName = conv.partnerName(currentUserId)
                                val partnerPhoto = conv.partnerPhotoKey(currentUserId)

                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .clickable { onOpenConversation(conv) }
                                        .testTag("match_conversation_card_${conv.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        ProfilePhotoView(
                                            photoKey = partnerPhoto,
                                            contentDescription = "$partnerName avatar",
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = partnerName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (conv.typingUserId.isNotBlank() && conv.typingUserId != currentUserId) {
                                                    "Typing..."
                                                } else {
                                                    conv.lastMessageText.ifBlank { "Say hello!" }
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { menuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "Conversation actions"
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Unmatch") },
                                                    leadingIcon = {
                                                        Icon(Icons.Filled.HeartBroken, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onUnmatch(conv)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Block or Report") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        safetyTargetConv = conv
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
            }
        }
    }

    safetyTargetConv?.let { conv ->
        val partnerId = conv.partnerId(currentUserId)
        val partnerName = conv.partnerName(currentUserId)
        SafetyReportBlockDialog(
            targetName = partnerName,
            onDismiss = { safetyTargetConv = null },
            onBlockOnly = { reason ->
                onReportOrBlock(partnerId, partnerName, conv.id, null, reason)
            },
            onReportAndBlock = { category, details ->
                onReportOrBlock(partnerId, partnerName, conv.id, category, details)
            }
        )
    }
}
