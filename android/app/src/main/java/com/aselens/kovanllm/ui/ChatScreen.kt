package com.aselens.kovanllm.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aselens.kovanllm.data.ChatMessage
import com.aselens.kovanllm.data.ChatSession
import com.aselens.kovanllm.data.I18n
import com.aselens.kovanllm.data.LiveVoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modelName: String,
    sessions: List<ChatSession>,
    currentSessionId: String,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    currentTokensPerSec: Double?,
    currentTtftMs: Long?,
    language: String,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onSendMessage: (text: String, mediaUri: String?, mediaName: String?) -> Unit,
    onRegenerate: () -> Unit,
    onStopStreaming: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    isSpeaking: Boolean,
    isLiveVoiceOpen: Boolean = false,
    liveVoiceState: LiveVoiceState = LiveVoiceState.IDLE,
    liveAudioLevel: Float = 0f,
    liveUserTranscript: String = "",
    liveAssistantTranscript: String = "",
    isLiveMuted: Boolean = false,
    onStartLiveVoice: () -> Unit = {},
    onLiveInterrupt: () -> Unit = {},
    onLiveToggleMute: () -> Unit = {},
    onLiveEndCall: () -> Unit = {},
    onClearChat: () -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var attachedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var attachedMediaName by remember { mutableStateOf<String?>(null) }
    var showSessionsModal by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    // Speech-to-Text Recognizer Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    // Media Picker Launcher (Images, Documents, Media)
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedMediaUri = uri
            attachedMediaName = queryFileName(context, uri) ?: "file"
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2
        }
    }

    var userScrolledUp by remember { mutableStateOf(false) }

    // If user manually scrolls and is not at bottom, allow them to freely read earlier messages
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolledUp = true
        } else if (isAtBottom && !listState.isScrollInProgress) {
            userScrolledUp = false
        }
    }

    // Scroll when user sends a new message or new turn begins
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            userScrolledUp = false
            listState.scrollToItem(messages.size - 1)
        }
    }

    // While streaming tokens: ONLY auto-scroll IF the user hasn't scrolled up!
    LaunchedEffect(messages.lastOrNull()?.content?.length) {
        if (isStreaming && !userScrolledUp && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    if (showSessionsModal) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsModal = false },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = I18n.t("sessions_title", language),
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Button(
                        onClick = {
                            onNewSession()
                            showSessionsModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(I18n.t("new_chat", language), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        val isSelected = session.id == currentSessionId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.background)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.primary else colors.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSelectSession(session.id)
                                    showSessionsModal = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        color = if (isSelected) colors.primary else colors.onBackground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${session.messages.size} ${I18n.t("tokens", language)}",
                                        color = colors.onBackground.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }

                                if (sessions.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteSession(session.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = colors.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = I18n.t("footer_aselens", language),
                    color = colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(modifier = Modifier.background(colors.surface)) {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.widthIn(max = 160.dp)) {
                            Text(
                                text = modelName,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.secondary)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Port 27615",
                                    color = colors.onSurface.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.onSurface
                            )
                        }
                    },
                    actions = {
                        // Theme Toggle
                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Theme",
                                tint = colors.onSurface
                            )
                        }

                        // Sessions Drawer Icon
                        IconButton(onClick = { showSessionsModal = true }) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = "Sessions",
                                tint = colors.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Share/Export Button
                        IconButton(onClick = {
                            val fullTranscript = messages.joinToString("\n\n") { msg ->
                                val prefix = if (msg.role == "user") "### User" else "### Assistant"
                                "$prefix\n${msg.content}"
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, fullTranscript)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export KovanLLM Chat"))
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Clear Chat
                        IconButton(onClick = onClearChat) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear",
                                tint = colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
                )

                // Sub-header Live Performance Strip (Separated from Title so it NEVER wraps title!)
                if ((currentTokensPerSec != null && currentTokensPerSec > 0.0) || isSpeaking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.primaryContainer)
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentTokensPerSec != null && currentTokensPerSec > 0.0) {
                            val ttftStr = if (currentTtftMs != null) "TTFT: ${currentTtftMs}ms • " else ""
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$ttftStr${String.format("%.1f", currentTokensPerSec)} ${I18n.t("tok_per_sec", language)}",
                                    color = colors.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (isSpeaking) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onStopSpeaking() }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = I18n.t("stop_audio", language),
                                    color = colors.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = colors.surface,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Attachment preview if a file or image is picked
                    if (attachedMediaUri != null) {
                        val previewExt = (attachedMediaName ?: "").substringAfterLast('.', "").lowercase()
                        val isPreviewImg = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").contains(previewExt)
                        val previewBmp = if (isPreviewImg) rememberImageBitmapFromUri(context, attachedMediaUri.toString()) else null

                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primaryContainer)
                                .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPreviewImg && previewBmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = previewBmp,
                                    contentDescription = "Thumbnail",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = previewExt.uppercase().take(4).ifBlank { "FILE" },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = attachedMediaName ?: "Media file",
                                    color = colors.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isPreviewImg) "Görsel eklendi" else "Dosya eklendi",
                                    color = colors.onPrimaryContainer.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    attachedMediaUri = null
                                    attachedMediaName = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media Attach Button ("📎")
                        IconButton(
                            onClick = { mediaLauncher.launch("*/*") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = I18n.t("attach_media", language),
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Live Voice Mode Button ("Live 🎙️")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable { onStartLiveVoice() }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = I18n.t("live_voice_mode", language),
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Live",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    I18n.t("input_placeholder", language),
                                    color = colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 12.sp
                                )
                            },
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.primaryContainer,
                                unfocusedContainerColor = colors.primaryContainer,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline,
                                focusedTextColor = colors.onPrimaryContainer,
                                unfocusedTextColor = colors.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Send or Stop Button
                        val canSend = inputText.isNotBlank() || attachedMediaUri != null
                        if (isStreaming && !canSend) {
                            IconButton(
                                onClick = onStopStreaming,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colors.error)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (canSend) {
                                        val text = inputText
                                        val uri = attachedMediaUri?.toString()
                                        val name = attachedMediaName
                                        inputText = ""
                                        attachedMediaUri = null
                                        attachedMediaName = null
                                        onSendMessage(text, uri, name)
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (canSend) colors.primary else colors.primaryContainer)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) Color.White else colors.onPrimaryContainer.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    // Embedded AdMob Banner
                    com.aselens.kovanllm.ads.AdMobBanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = I18n.t("chat_ready_title", language),
                        color = colors.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = I18n.t("chat_ready_desc", language),
                        color = colors.onBackground.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        val isLastAssistant = !isStreaming && msg.role == "assistant" && messages.lastOrNull()?.id == msg.id
                        ChatBubble(
                            message = msg,
                            isLastAssistant = isLastAssistant,
                            language = language,
                            onRegenerate = onRegenerate,
                            onSpeak = { onSpeakText(msg.content) },
                            onCopyText = { text, isCode ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("KovanLLM", text)
                                clipboard.setPrimaryClip(clip)
                                val toastMsg = if (isCode) I18n.t("code_copied", language) else I18n.t("copied", language)
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Show an inline ad card periodically (e.g. after every 4th message)
                        if (index > 0 && (index + 1) % com.aselens.kovanllm.ads.AdConfig.INLINE_AD_FREQUENCY == 0 && index < messages.size - 1) {
                            com.aselens.kovanllm.ads.AdMobInlineBox(
                                language = language,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Floating Scroll-to-Bottom indicator button when user scrolled up
                AnimatedVisibility(
                    visible = userScrolledUp,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            userScrolledUp = false
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        },
                        containerColor = colors.surface,
                        contentColor = colors.primary,
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, colors.outline, CircleShape)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(16.dp)
                            )
                            if (isStreaming) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isLiveVoiceOpen) {
        LiveVoiceOverlay(
            state = liveVoiceState,
            audioLevel = liveAudioLevel,
            userTranscript = liveUserTranscript,
            assistantTranscript = liveAssistantTranscript,
            isMuted = isLiveMuted,
            language = language,
            onInterrupt = onLiveInterrupt,
            onToggleMute = onLiveToggleMute,
            onEndCall = onLiveEndCall
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isLastAssistant: Boolean,
    language: String,
    onRegenerate: () -> Unit,
    onSpeak: () -> Unit,
    onCopyText: (String, Boolean) -> Unit
) {
    val isUser = message.role == "user"
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .border(1.dp, colors.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .background(if (isUser) colors.primary else colors.surface)
                .border(
                    width = 1.dp,
                    color = if (isUser) colors.primary else colors.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(11.dp)
        ) {
            Column {
                // If attached media exists: render WhatsApp-style rich preview
                if (!message.attachedMediaName.isNullOrBlank()) {
                    WhatsAppMediaAttachment(
                        mediaUri = message.attachedMediaUri,
                        mediaName = message.attachedMediaName!!,
                        isUser = isUser,
                        language = language
                    )
                }

                if (isUser) {
                    if (message.content.isNotBlank()) {
                        RichMarkdownContent(
                            content = message.content,
                            isUser = true,
                            language = language,
                            onCopyCode = { code -> onCopyText(code, true) }
                        )
                    }
                    if (message.content.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { onCopyText(message.content, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Check if model is loading weights or generating
                    if (message.isStreaming && message.content.isEmpty()) {
                        ModelLoadingIndicator(language)
                    } else {
                        RichMarkdownContent(
                            content = message.content,
                            isUser = false,
                            language = language,
                            onCopyCode = { code -> onCopyText(code, true) }
                        )
                    }

                    // Assistant Footer
                    if (message.content.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.tokensPerSec != null && message.tokensPerSec!! > 0.0) {
                                Text(
                                    text = String.format("%.1f %s", message.tokensPerSec, I18n.t("tok_per_sec", language)),
                                    color = colors.onSurface.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Speak Aloud Button
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { onSpeak() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read Aloud",
                                        tint = colors.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                if (isLastAssistant) {
                                    // Regenerate Button
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { onRegenerate() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Regenerate",
                                            tint = colors.onSurface.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Copy Message Button
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { onCopyText(message.content, false) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = colors.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(15.dp)
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

@Composable
fun ModelLoadingIndicator(language: String) {
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = colors.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = I18n.t("model_loading_hint", language),
            color = colors.onSurface.copy(alpha = alpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RichMarkdownContent(
    content: String,
    isUser: Boolean,
    language: String,
    onCopyCode: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val chunks = remember(content) { parseMarkdownCodeBlocks(content) }
    val baseTextColor = if (isUser) Color.White else colors.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEach { chunk ->
            if (chunk.isCode) {
                // Code Block Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isUser) Color.Black.copy(alpha = 0.35f) else colors.primaryContainer)
                        .border(1.dp, if (isUser) Color.White.copy(alpha = 0.25f) else colors.outline, RoundedCornerShape(8.dp))
                ) {
                    Column {
                        // Header with language tag and copy button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isUser) Color.Black.copy(alpha = 0.45f) else colors.surface)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chunk.languageTag.ifEmpty { "code" },
                                color = if (isUser) Color.White.copy(alpha = 0.9f) else colors.primary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { onCopyCode(chunk.text) },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isUser) Color.White.copy(alpha = 0.2f) else colors.primaryContainer),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isUser) Color.White else colors.onPrimaryContainer, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(I18n.t("copy_code", language), color = if (isUser) Color.White else colors.onPrimaryContainer, fontSize = 10.sp)
                            }
                        }

                        // Code content
                        Text(
                            text = chunk.text,
                            color = if (isUser) Color.White.copy(alpha = 0.95f) else colors.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            } else {
                if (chunk.text.isNotBlank()) {
                    val formatted = remember(chunk.text, isUser) {
                        parseDiscordMarkdown(chunk.text, isUser, colors.primary)
                    }
                    Text(
                        text = formatted,
                        color = baseTextColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

fun parseDiscordMarkdown(
    text: String,
    isUser: Boolean,
    primaryColor: Color
): AnnotatedString {
    val codeBg = if (isUser) Color.Black.copy(alpha = 0.30f) else Color(0x22888888)
    val codeColor = if (isUser) Color.White else primaryColor

    // Matches Discord markdown formatting tags:
    // ***bold italic***, **bold**, ~~strikethrough~~, __underline__, `inline code`, *italic*, _italic_
    val tokenRegex = Regex("""(\*\*\*[^\*\n]+?\*\*\*|\*\*[^\*\n]+?\*\*|~~[^~\n]+?~~|__[^_\n]+?__|`[^`\n]+?`|\*[^\*\n]+?\*|_[^_\n]+?_)""")

    val lines = text.split('\n')
    return buildAnnotatedString {
        lines.forEachIndexed { lineIdx, line ->
            if (lineIdx > 0) {
                append("\n")
            }

            var processedLine = line
            var headerStyle: SpanStyle? = null

            // Discord & markdown headers (# , ## , ### ), bullet points (- , * ), quotes (> )
            if (processedLine.startsWith("### ")) {
                headerStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                processedLine = processedLine.removePrefix("### ")
            } else if (processedLine.startsWith("## ")) {
                headerStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                processedLine = processedLine.removePrefix("## ")
            } else if (processedLine.startsWith("# ")) {
                headerStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                processedLine = processedLine.removePrefix("# ")
            } else if (processedLine.startsWith("- ") || processedLine.startsWith("* ")) {
                append("• ")
                processedLine = processedLine.substring(2)
            } else if (processedLine.startsWith("> ")) {
                append("▎ ")
                processedLine = processedLine.removePrefix("> ")
            }

            val lineStartIndex = length
            var cursor = 0
            val matches = tokenRegex.findAll(processedLine).toList()

            for (match in matches) {
                val range = match.range
                if (range.first > cursor) {
                    append(processedLine.substring(cursor, range.first))
                }
                val token = match.value
                when {
                    token.startsWith("***") && token.endsWith("***") && token.length >= 6 -> {
                        val inner = token.substring(3, token.length - 3)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(inner)
                        pop()
                    }
                    token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                        val inner = token.substring(2, token.length - 2)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(inner)
                        pop()
                    }
                    token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                        val inner = token.substring(2, token.length - 2)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(inner)
                        pop()
                    }
                    token.startsWith("__") && token.endsWith("__") && token.length >= 4 -> {
                        val inner = token.substring(2, token.length - 2)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(inner)
                        pop()
                    }
                    token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                        val inner = token.substring(1, token.length - 1)
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor))
                        append(" $inner ")
                        pop()
                    }
                    (token.startsWith("*") && token.endsWith("*") && token.length >= 2) ||
                    (token.startsWith("_") && token.endsWith("_") && token.length >= 2) -> {
                        val inner = token.substring(1, token.length - 1)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(inner)
                        pop()
                    }
                    else -> append(token)
                }
                cursor = range.last + 1
            }

            if (cursor < processedLine.length) {
                append(processedLine.substring(cursor))
            }

            if (headerStyle != null) {
                addStyle(headerStyle, lineStartIndex, length)
            }
        }
    }
}

data class MarkdownChunk(val text: String, val isCode: Boolean, val languageTag: String = "")

fun parseMarkdownCodeBlocks(content: String): List<MarkdownChunk> {
    val list = mutableListOf<MarkdownChunk>()
    val parts = content.split("```")

    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 == 0) {
            if (part.isNotEmpty()) {
                list.add(MarkdownChunk(text = part, isCode = false))
            }
        } else {
            val newlineIdx = part.indexOf('\n')
            if (newlineIdx != -1) {
                val lang = part.substring(0, newlineIdx).trim()
                val code = part.substring(newlineIdx + 1)
                list.add(MarkdownChunk(text = code, isCode = true, languageTag = lang))
            } else {
                list.add(MarkdownChunk(text = part, isCode = true, languageTag = ""))
            }
        }
    }
    return list
}

fun queryFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    result = cursor.getString(idx)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
