package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GeneratingTokens
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PricingConfig
import com.example.data.model.PublicProfile
import com.example.ui.components.CoinBalancePill
import com.example.ui.components.PricingPolicyBanner
import com.example.ui.components.ProfilePhotoView
import com.example.ui.components.SafetyReportBlockDialog
import java.text.SimpleDateFormat
import java.util.Locale

private data class PendingPaidMessage(
    val text: String,
    val mediaUri: String,
    val messageType: String,
    val costCoins: Int
)

private data class PendingCallRequest(
    val calleeId: String,
    val calleeName: String,
    val callType: String,
    val rateCoinsPerMin: Int
)

@Composable
fun ChatScreen(
    currentUserId: String,
    myProfile: PublicProfile,
    conversation: Conversation,
    messages: List<ChatMessage>,
    pricing: PricingConfig,
    balanceCoins: Long,
    onBack: () -> Unit,
    onSendMessage: (text: String, mediaUri: String, messageType: String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onMarkMessagesRead: (List<ChatMessage>) -> Unit,
    onStartCall: (calleeId: String, calleeName: String, callType: String) -> Unit,
    onUnmatch: (String) -> Unit,
    onReportOrBlock: (partnerId: String, partnerName: String, convId: String, category: String?, details: String) -> Unit,
    onOpenWallet: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val partnerId = conversation.partnerId(currentUserId)
    val partnerName = conversation.partnerName(currentUserId)
    val partnerPhoto = conversation.partnerPhotoKey(currentUserId)
    val msgCost = pricing.messageCostForGender(myProfile.gender)

    var draftText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var pendingPaidMsg by remember { mutableStateOf<PendingPaidMessage?>(null) }
    var pendingCall by remember { mutableStateOf<PendingCallRequest?>(null) }
    var permissionDeniedNote by remember { mutableStateOf<String?>(null) }
    var callAwaitingPerms by remember { mutableStateOf<PendingCallRequest?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        onMarkMessagesRead(messages)
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val uriStr = uri.toString()
            if (msgCost > 0) {
                pendingPaidMsg = PendingPaidMessage(
                    text = draftText.ifBlank { "Shared a photo" },
                    mediaUri = uriStr,
                    messageType = "image",
                    costCoins = msgCost
                )
            } else {
                onSendMessage(draftText.ifBlank { "Shared a photo" }, uriStr, "image")
                draftText = ""
                onTypingChanged(false)
            }
        }
    }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = callAwaitingPerms
        callAwaitingPerms = null
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = req?.callType != "video" ||
            grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (req != null && micGranted && camGranted) {
            permissionDeniedNote = null
            onStartCall(req.calleeId, req.calleeName, req.callType)
        } else {
            permissionDeniedNote = "Microphone (and Camera for video) permission is required to place calls."
        }
    }

    fun triggerCallWithPermissions(req: PendingCallRequest) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (req.callType == "video" &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isEmpty()) {
            onStartCall(req.calleeId, req.calleeName, req.callType)
        } else {
            callAwaitingPerms = req
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .imePadding()
        ) {
            // Top Header
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to matches"
                        )
                    }
                    ProfilePhotoView(
                        photoKey = partnerPhoto,
                        contentDescription = "$partnerName avatar",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = partnerName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val isPartnerTyping = conversation.typingUserId == partnerId
                        Text(
                            text = if (isPartnerTyping) "Typing…" else "Mutual Match • Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPartnerTyping) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            val rate = pricing.callRatePerMinForGender(myProfile.gender, "audio")
                            val req = PendingCallRequest(partnerId, partnerName, "audio", rate)
                            if (rate > 0) pendingCall = req else triggerCallWithPermissions(req)
                        },
                        modifier = Modifier.testTag("start_audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Start audio call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            val rate = pricing.callRatePerMinForGender(myProfile.gender, "video")
                            val req = PendingCallRequest(partnerId, partnerName, "video", rate)
                            if (rate > 0) pendingCall = req else triggerCallWithPermissions(req)
                        },
                        modifier = Modifier.testTag("start_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Start video call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("chat_safety_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Conversation safety options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unmatch $partnerName") },
                                onClick = {
                                    menuExpanded = false
                                    onUnmatch(conversation.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block or Safety Report") },
                                onClick = {
                                    menuExpanded = false
                                    showSafetyDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Transparent Rate Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (msgCost == 0) {
                            "Free-Access Active: $0 messages & calls"
                        } else {
                            "Msg: $msgCost coins • Audio: ${pricing.maleAudioCallCoinsPerMin}/min • Video: ${pricing.maleVideoCallCoinsPerMin}/min"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$balanceCoins Coins",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenWallet)
                    )
                }
            }

            if (permissionDeniedNote != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = permissionDeniedNote ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "You matched with $partnerName! Break the ice with a thoughtful note or emoji.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                items(messages, key = { it.id.ifEmpty { "${it.senderId}_${it.text}" } }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    ChatBubbleItem(message = msg, isMine = isMine)
                }
            }

            // Quick Emoji Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                listOf("👋", "✨", "❤️", "😊", "🌹", "🥂").forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { draftText += emoji }
                    ) {
                        Text(
                            text = emoji,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Composer
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("chat_attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Share photo from gallery",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { next ->
                            draftText = next
                            onTypingChanged(next.isNotBlank())
                        },
                        placeholder = {
                            Text(if (msgCost > 0) "Write message ($msgCost coins)…" else "Write a free message…")
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val trimmed = draftText.trim()
                            if (trimmed.isEmpty()) return@FilledIconButton
                            if (msgCost > 0) {
                                pendingPaidMsg = PendingPaidMessage(
                                    text = trimmed,
                                    mediaUri = "",
                                    messageType = "text",
                                    costCoins = msgCost
                                )
                            } else {
                                onSendMessage(trimmed, "", "text")
                                draftText = ""
                                onTypingChanged(false)
                            }
                        },
                        enabled = draftText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message"
                        )
                    }
                }
            }
        }
    }

    // Explicit Paid Message Confirmation Dialog (Zero Silent Charges)
    pendingPaidMsg?.let { pending ->
        val hasEnough = balanceCoins >= pending.costCoins
        AlertDialog(
            onDismissRequest = { pendingPaidMsg = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.GeneratingTokens,
                    contentDescription = "Paid message confirmation"
                )
            },
            title = { Text("Confirm Paid Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sending this message to $partnerName deducts ${pending.costCoins} coins.")
                    Text(
                        text = "Available Balance: $balanceCoins coins",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (hasEnough) {
                        Text("Balance After Send: ${balanceCoins - pending.costCoins} coins")
                    } else {
                        Text(
                            text = "Insufficient coins. Top up your wallet to send this message.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (hasEnough) {
                    Button(
                        onClick = {
                            onSendMessage(pending.text, pending.mediaUri, pending.messageType)
                            draftText = ""
                            onTypingChanged(false)
                            pendingPaidMsg = null
                        },
                        modifier = Modifier.testTag("confirm_paid_message_button")
                    ) {
                        Text("Spend ${pending.costCoins} Coins & Send")
                    }
                } else {
                    Button(
                        onClick = {
                            pendingPaidMsg = null
                            onOpenWallet()
                        },
                        modifier = Modifier.testTag("topup_from_chat_button")
                    ) {
                        Text("Open Wallet")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaidMsg = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Explicit Paid Call Confirmation Dialog
    pendingCall?.let { callReq ->
        val hasEnough = balanceCoins >= callReq.rateCoinsPerMin
        val estMins = if (callReq.rateCoinsPerMin > 0) balanceCoins / callReq.rateCoinsPerMin else 99L
        AlertDialog(
            onDismissRequest = { pendingCall = null },
            icon = {
                Icon(
                    imageVector = if (callReq.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = "Paid call confirmation"
                )
            },
            title = { Text("Start Paid ${callReq.callType.replaceFirstChar { it.uppercase() }} Call?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rate: ${callReq.rateCoinsPerMin} coins / minute (billed per connected minute).")
                    Text("Available Balance: $balanceCoins coins (~$estMins min max).")
                    Text(
                        text = stringResource(R.string.call_signaling_disclosure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasEnough) {
                        Text(
                            text = "At least 1 minute (${callReq.rateCoinsPerMin} coins) is required to start.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (hasEnough) {
                    Button(
                        onClick = {
                            pendingCall = null
                            triggerCallWithPermissions(callReq)
                        },
                        modifier = Modifier.testTag("confirm_paid_call_button")
                    ) {
                        Text("Confirm (${callReq.rateCoinsPerMin} coins/min)")
                    }
                } else {
                    Button(
                        onClick = {
                            pendingCall = null
                            onOpenWallet()
                        }
                    ) {
                        Text("Top Up Coins")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCall = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSafetyDialog) {
        SafetyReportBlockDialog(
            targetName = partnerName,
            onDismiss = { showSafetyDialog = false },
            onBlockOnly = { reason ->
                onReportOrBlock(partnerId, partnerName, conversation.id, null, reason)
            },
            onReportAndBlock = { category, details ->
                onReportOrBlock(partnerId, partnerName, conversation.id, category, details)
            }
        )
    }
}

@Composable
private fun ChatBubbleItem(message: ChatMessage, isMine: Boolean) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeLabel = message.createdAt?.toDate()?.let { timeFmt.format(it) } ?: "Now"

    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            color = if (isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.mediaUri.isNotBlank()) {
                    AsyncImage(
                        model = message.mediaUri,
                        contentDescription = "Shared photo attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val receiptNote = if (message.coinsCharged > 0 && isMine) {
                        "$timeLabel • ${message.coinsCharged}c"
                    } else {
                        timeLabel
                    }
                    Text(
                        text = receiptNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        }
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.readByRecipient) Icons.Filled.DoneAll else Icons.Filled.Check,
                            contentDescription = if (message.readByRecipient) "Read" else "Delivered",
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallsHistoryScreen(
    currentUserId: String,
    myProfile: PublicProfile,
    pricing: PricingConfig,
    balanceCoins: Long,
    callHistory: List<CallSession>,
    onOpenWallet: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                    Text("Calls & Receipts", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "1-on-1 Audio & Video session history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CoinBalancePill(balanceCoins = balanceCoins, onClick = onOpenWallet)
            }

            PricingPolicyBanner(gender = myProfile.gender, pricing = pricing)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.call_signaling_disclosure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (callHistory.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneCallback,
                            contentDescription = "No calls yet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Call Sessions Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Open any mutual match conversation to place a 1-on-1 audio or video call with upfront rate confirmation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(callHistory, key = { it.id }) { session ->
                        val peer = session.peerName(currentUserId)
                        val mins = session.durationSeconds / 60
                        val secs = session.durationSeconds % 60
                        val durStr = String.format(Locale.US, "%02d:%02d", mins, secs)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (session.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "${session.callType} call",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${session.callType.uppercase()} • Status: ${session.status} • Duration: $durStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (session.totalCoinsCharged > 0) {
                                        "-${session.totalCoinsCharged} coins"
                                    } else {
                                        "$0 Free"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    currentUserId: String,
    session: CallSession,
    balanceCoins: Long,
    lowBalanceThreshold: Int,
    onAccept: (CallSession) -> Unit,
    onReject: (CallSession) -> Unit,
    onHangUp: () -> Unit,
    onOpenWallet: () -> Unit
) {
    val context = LocalContext.current
    val isIncomingRinging = session.calleeId == currentUserId && session.status == "ringing"
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(true) }

    BackHandler {
        if (isIncomingRinging) onReject(session) else onHangUp()
    }

    val acceptPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onAccept(session)
    }

    val mins = session.durationSeconds / 60
    val secs = session.durationSeconds % 60
    val formattedTimer = String.format(Locale.US, "%02d:%02d", mins, secs)
    val isCallerPaying = session.callerId == currentUserId && session.rateCoinsPerMin > 0
    val isLowBalance = isCallerPaying && balanceCoins <= lowBalanceThreshold

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0914), Color(0xFF2D0A1F), Color(0xFF12070E))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(vertical = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.call_signaling_disclosure),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = session.peerName(currentUserId),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isIncomingRinging -> "Incoming ${session.callType.uppercase()} Call…"
                        session.status == "ringing" -> "Ringing peer via Firestore signaling…"
                        else -> "${session.callType.uppercase()} Connected • $formattedTimer"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFFD8EC)
                )
                if (session.callType == "video" && session.status == "connected") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (useFrontCamera) "Local Camera: Front Lens" else "Local Camera: Rear Lens",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            // Live Metering & Balance Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Call Rate", color = Color.White.copy(alpha = 0.8f))
                        Text(
                            text = if (isCallerPaying) "${session.rateCoinsPerMin} coins / min" else "$0 Free-Access",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Session Billed", color = Color.White.copy(alpha = 0.8f))
                        Text("${session.totalCoinsCharged} coins", color = Color.White)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remaining Balance", color = Color.White.copy(alpha = 0.8f))
                        Text("$balanceCoins coins", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                    }
                    if (isLowBalance) {
                        Surface(
                            color = Color(0xFFB71C1C).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = "Low balance warning",
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Low balance ($balanceCoins coins). Call ends automatically when coins reach 0.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onOpenWallet) {
                                    Text("Top Up", color = Color(0xFFFFD54F))
                                }
                            }
                        }
                    }
                }
            }

            // Call Action Buttons
            if (isIncomingRinging) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onReject(session) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("decline_call_button")
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Decline call")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decline")
                    }
                    Button(
                        onClick = {
                            val perms = buildList {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    add(Manifest.permission.RECORD_AUDIO)
                                }
                                if (session.callType == "video" &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    add(Manifest.permission.CAMERA)
                                }
                            }
                            if (perms.isEmpty()) onAccept(session) else acceptPermLauncher.launch(perms.toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("accept_call_button")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Accept call")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accept")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { isMuted = !isMuted },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isMuted) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("toggle_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone"
                        )
                    }
                    FilledIconButton(
                        onClick = { isSpeakerOn = !isSpeakerOn },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isSpeakerOn) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("toggle_speaker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Toggle speakerphone"
                        )
                    }
                    if (session.callType == "video") {
                        FilledIconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("switch_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera"
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = onHangUp,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "End call"
                        )
                    }
                }
            }
        }
    }
}
