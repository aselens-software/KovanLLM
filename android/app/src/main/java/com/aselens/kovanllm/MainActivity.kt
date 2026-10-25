package com.aselens.kovanllm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.aselens.kovanllm.data.*
import com.aselens.kovanllm.ui.ChatScreen
import com.aselens.kovanllm.ui.ModelSettingsScreen
import com.aselens.kovanllm.ui.StationLauncherScreen
import com.aselens.kovanllm.ui.theme.KovanLLMTheme
import com.aselens.kovanllm.ads.AdMobInterstitialManager
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class AppScreen {
    LAUNCHER,
    SETTINGS,
    CHAT
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val repository = LLMRepository()
    private var activeCall: Call? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Google Mobile Ads with UMP Consent Management
        try {
            val consentManager = com.aselens.kovanllm.ads.ConsentManager(this)
            consentManager.requestConsent(this) {
                try {
                    MobileAds.initialize(this) {}
                    AdMobInterstitialManager.loadAd(this)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try {
                MobileAds.initialize(this) {}
                AdMobInterstitialManager.loadAd(this)
            } catch (_: Exception) {}
        }

        // Initialize Android TextToSpeech engine
        tts = TextToSpeech(this, this)

        val prefs = getSharedPreferences("kovan_prefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("station_auth_token", null)
        repository.stationAuthToken = savedToken

        val initialUrl = prefs.getString("last_station_url", "http://10.0.2.2:27615") ?: "http://10.0.2.2:27615"
        val savedStationsJson = prefs.getString("recent_stations", "[]") ?: "[]"
        val initialRecent = parseRecentStations(savedStationsJson)
        val savedLanguage = prefs.getString("app_language", "en") ?: "en"
        val initialDark = prefs.getBoolean("is_dark_theme", true)

        val savedSessionsJson = prefs.getString("saved_sessions", "[]") ?: "[]"
        val initialSessions = parseSavedSessions(savedSessionsJson).ifEmpty {
            mutableListOf(ChatSession(title = "Default Session"))
        }

        setContent {
            var isDark by remember { mutableStateOf(initialDark) }

            KovanLLMTheme(isDark = isDark) {
                var currentScreen by remember { mutableStateOf(AppScreen.LAUNCHER) }
                var stationUrl by remember { mutableStateOf(initialUrl) }
                var recentStations by remember { mutableStateOf(initialRecent) }
                var isConnecting by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var language by remember { mutableStateOf(savedLanguage) }

                var models by remember { mutableStateOf(listOf<String>()) }
                var engineConfig by remember { mutableStateOf(EngineConfig(language = language, theme = if (isDark) "dark" else "light")) }
                var probeResultText by remember { mutableStateOf<String?>(null) }

                val sessions = remember { mutableStateListOf<ChatSession>().apply { addAll(initialSessions) } }
                var currentSessionId by remember { mutableStateOf(sessions.first().id) }

                val currentSession = sessions.find { it.id == currentSessionId } ?: sessions.first()
                val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(currentSession.messages) } }

                var isStreaming by remember { mutableStateOf(false) }
                var currentTokensPerSec by remember { mutableStateOf<Double?>(null) }
                var currentTtftMs by remember { mutableStateOf<Long?>(null) }
                var isSpeaking by remember { mutableStateOf(false) }

                var isLiveVoiceOpen by remember { mutableStateOf(false) }
                var startChatStreamingRef by remember { mutableStateOf<(String) -> Unit>({}) }

                val liveVoiceManager = remember {
                    LiveVoiceManager(
                        context = this@MainActivity,
                        onSendPrompt = { promptText ->
                            runOnUiThread {
                                startChatStreamingRef(promptText)
                            }
                        },
                        onInterruptLLM = {
                            runOnUiThread {
                                activeCall?.cancel()
                                activeCall = null
                                isStreaming = false
                                currentTokensPerSec = null
                            }
                        }
                    )
                }

                val chatSpeechPlayer = remember {
                    NaturalSpeechPlayer(this@MainActivity) {
                        runOnUiThread { isSpeaking = false }
                    }
                }

                LaunchedEffect(stationUrl, language) {
                    liveVoiceManager.stationUrl = stationUrl
                    liveVoiceManager.language = language
                    chatSpeechPlayer.stationUrl = stationUrl
                    chatSpeechPlayer.language = language
                }

                DisposableEffect(Unit) {
                    onDispose {
                        liveVoiceManager.destroy()
                        chatSpeechPlayer.destroy()
                    }
                }

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        isLiveVoiceOpen = true
                        liveVoiceManager.language = language
                        liveVoiceManager.startLiveSession()
                    } else {
                        Toast.makeText(this@MainActivity, I18n.t("live_mic_permission_needed", language), Toast.LENGTH_SHORT).show()
                    }
                }

                val coroutineScope = rememberCoroutineScope()

                fun saveRecent(newList: List<StationItem>) {
                    recentStations = newList
                    val arr = JSONArray()
                    for (st in newList) {
                        arr.put(JSONObject().apply {
                            put("url", st.url)
                            put("name", st.name)
                            put("lastConnected", st.lastConnected)
                        })
                    }
                    prefs.edit().putString("recent_stations", arr.toString()).apply()
                }

                fun saveSessions() {
                    val arr = JSONArray()
                    for (sess in sessions) {
                        val sessObj = JSONObject().apply {
                            put("id", sess.id)
                            put("title", sess.title)
                            put("createdAt", sess.createdAt)
                            put("updatedAt", sess.updatedAt)
                            val msgArr = JSONArray()
                            for (m in sess.messages) {
                                msgArr.put(JSONObject().apply {
                                    put("id", m.id)
                                    put("role", m.role)
                                    put("content", m.content)
                                    put("timestamp", m.timestamp)
                                    if (m.tokensPerSec != null) put("tokensPerSec", m.tokensPerSec)
                                    if (m.ttftMs != null) put("ttftMs", m.ttftMs)
                                    if (m.totalTokens != null) put("totalTokens", m.totalTokens)
                                    if (m.attachedMediaUri != null) put("attachedMediaUri", m.attachedMediaUri)
                                    if (m.attachedMediaName != null) put("attachedMediaName", m.attachedMediaName)
                                })
                            }
                            put("messages", msgArr)
                        }
                        arr.put(sessObj)
                    }
                    prefs.edit().putString("saved_sessions", arr.toString()).apply()
                }

                fun toggleLanguage() {
                    val newLang = if (language == "en") "tr" else "en"
                    language = newLang
                    prefs.edit().putString("app_language", newLang).apply()
                    engineConfig = engineConfig.copy(language = newLang)
                }

                fun toggleTheme() {
                    val newDark = !isDark
                    isDark = newDark
                    prefs.edit().putBoolean("is_dark_theme", newDark).apply()
                    engineConfig = engineConfig.copy(theme = if (newDark) "dark" else "light")
                }

                fun connectToStation(urlToConnect: String) {
                    isConnecting = true
                    errorMessage = null
                    val normalized = repository.normalizeUrl(urlToConnect)

                    coroutineScope.launch {
                        val statusRes = repository.getStationStatus(normalized)
                        val statusJson = statusRes.getOrNull()
                        val pinRequired = statusJson?.optBoolean("pin_required", false) ?: false

                        if (pinRequired && repository.stationAuthToken.isNullOrBlank()) {
                            isConnecting = false
                            stationUrl = normalized
                            errorMessage = "401: PIN Required"
                            return@launch
                        }

                        val configRes = repository.getEngineConfig(normalized)
                        configRes.onSuccess { cfg ->
                            engineConfig = cfg
                        }

                        val result = repository.fetchModels(normalized)
                        isConnecting = false
                        result.onSuccess { modelList ->
                            models = modelList
                            if (engineConfig.activeModel.isBlank()) {
                                engineConfig = engineConfig.copy(activeModel = modelList.firstOrNull() ?: "")
                            }
                            stationUrl = normalized
                            prefs.edit().putString("last_station_url", normalized).apply()

                            val updated = recentStations.filter { it.url != normalized }.toMutableList()
                            val hostName = try {
                                val uri = java.net.URI(normalized)
                                if (uri.host.contains("trycloudflare")) "Cloudflare Tünel" else "Station (${uri.host})"
                            } catch (e: Exception) {
                                "KovanLLM Station"
                            }
                            updated.add(0, StationItem(normalized, hostName, System.currentTimeMillis()))
                            saveRecent(updated.take(5))

                            currentScreen = AppScreen.SETTINGS
                        }.onFailure { err ->
                            val msg = err.localizedMessage ?: ""
                            if (msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)) {
                                errorMessage = "401: PIN Required"
                            } else {
                                errorMessage = "Cannot connect: ${err.localizedMessage}\nEnsure KovanLLM Station is running on Port 27615."
                            }
                        }
                    }
                }

                // Handle incoming deep link (e.g. from QR scan)
                LaunchedEffect(intent?.data) {
                    intent?.data?.let { uri ->
                        if (uri.scheme == "kovanllm" && uri.host == "pair") {
                            val urlParam = uri.getQueryParameter("url")
                            val tokenParam = uri.getQueryParameter("token")
                            if (!tokenParam.isNullOrBlank()) {
                                repository.stationAuthToken = tokenParam
                                prefs.edit().putString("station_auth_token", tokenParam).apply()
                            }
                            if (!urlParam.isNullOrBlank()) {
                                stationUrl = urlParam
                                connectToStation(urlParam)
                            }
                        }
                    }
                }

                data class QueuedTurn(
                    val userText: String,
                    val mediaUri: String?,
                    val mediaName: String?
                )
                val messageQueue = remember { mutableStateListOf<QueuedTurn>() }

                fun generateSmartTitle(userText: String, mediaName: String?): String {
                    val clean = userText.trim().replace("\n", " ").replace(Regex("[#*`>_]"), "")
                    if (clean.isNotBlank()) {
                        val words = clean.split("\\s+".toRegex()).take(6)
                        val titleCandidate = words.joinToString(" ")
                        return titleCandidate.take(32).trim()
                    }
                    if (!mediaName.isNullOrBlank()) {
                        return mediaName.take(28)
                    }
                    return "Chat"
                }

                fun executeChatTurn(userText: String, mediaUri: String?, mediaName: String?, isUserAlreadyAdded: Boolean) {
                    if (!isUserAlreadyAdded) {
                        val userMsg = ChatMessage(
                            role = "user",
                            content = userText,
                            attachedMediaUri = mediaUri,
                            attachedMediaName = mediaName
                        )
                        messages.add(userMsg)
                        currentSession.messages.add(userMsg)
                    }

                    if (currentSession.title.startsWith("Chat ") || currentSession.title == "New Chat" || currentSession.title == "Default Session" || currentSession.title.isBlank()) {
                        currentSession.title = generateSmartTitle(userText, mediaName)
                    }

                    val assistantMsg = ChatMessage(role = "assistant", content = "", isStreaming = true)
                    messages.add(assistantMsg)
                    currentSession.messages.add(assistantMsg)
                    val assistantIndex = messages.size - 1

                    isStreaming = true
                    currentTokensPerSec = null
                    currentTtftMs = null

                    activeCall = repository.streamChat(
                        baseUrl = stationUrl,
                        model = engineConfig.activeModel.ifEmpty { models.firstOrNull() ?: "qwen2.5-3b-instruct-abliterated" },
                        messages = messages.filter { it.role != "system" },
                        config = engineConfig,
                        context = this@MainActivity,
                        onFirstToken = { ttft ->
                            runOnUiThread {
                                currentTtftMs = ttft
                                if (assistantIndex < messages.size) {
                                    messages[assistantIndex] = messages[assistantIndex].copy(ttftMs = ttft)
                                }
                            }
                        },
                        onToken = { delta, tps ->
                            runOnUiThread {
                                liveVoiceManager.onAssistantStreamingUpdate(delta)
                                if (assistantIndex < messages.size) {
                                    val current = messages[assistantIndex]
                                    messages[assistantIndex] = current.copy(
                                        content = current.content + delta,
                                        tokensPerSec = tps,
                                        isStreaming = true
                                    )
                                    currentTokensPerSec = tps
                                }
                            }
                        },
                        onComplete = { totalTokens, avgTps, ttft ->
                            runOnUiThread {
                                if (assistantIndex < messages.size) {
                                    val current = messages[assistantIndex]
                                    val finalMsg = current.copy(
                                        tokensPerSec = avgTps,
                                        totalTokens = totalTokens,
                                        ttftMs = ttft,
                                        isStreaming = false
                                    )
                                    messages[assistantIndex] = finalMsg
                                    currentSession.messages[assistantIndex] = finalMsg
                                    liveVoiceManager.onAssistantTurnComplete(finalMsg.content)
                                }
                                isStreaming = false
                                currentTokensPerSec = null
                                activeCall = null
                                saveSessions()

                                // If next queued message exists, automatically start generation!
                                if (messageQueue.isNotEmpty()) {
                                    val nextTurn = messageQueue.removeAt(0)
                                    executeChatTurn(nextTurn.userText, nextTurn.mediaUri, nextTurn.mediaName, isUserAlreadyAdded = true)
                                }
                            }
                        },
                        onError = { err ->
                            runOnUiThread {
                                liveVoiceManager.interrupt()
                                if (assistantIndex < messages.size) {
                                    val current = messages[assistantIndex]
                                    val errorMsg = current.copy(
                                        content = current.content + "\n\n⚠️ Error: $err",
                                        isStreaming = false
                                    )
                                    messages[assistantIndex] = errorMsg
                                    currentSession.messages[assistantIndex] = errorMsg
                                }
                                isStreaming = false
                                currentTokensPerSec = null
                                activeCall = null
                                saveSessions()

                                if (messageQueue.isNotEmpty()) {
                                    val nextTurn = messageQueue.removeAt(0)
                                    executeChatTurn(nextTurn.userText, nextTurn.mediaUri, nextTurn.mediaName, isUserAlreadyAdded = true)
                                }
                            }
                        }
                    )
                }

                fun startChatStreaming(userText: String, mediaUri: String? = null, mediaName: String? = null) {
                    if (isStreaming) {
                        // User typed and clicked Send while assistant is generating:
                        // Add to chat list immediately so user sees their prompt, and put into pending queue.
                        val queuedUserMsg = ChatMessage(
                            role = "user",
                            content = userText,
                            attachedMediaUri = mediaUri,
                            attachedMediaName = mediaName
                        )
                        messages.add(queuedUserMsg)
                        currentSession.messages.add(queuedUserMsg)
                        saveSessions()
                        messageQueue.add(QueuedTurn(userText, mediaUri, mediaName))
                        return
                    }

                    executeChatTurn(userText, mediaUri, mediaName, isUserAlreadyAdded = false)
                }

                startChatStreamingRef = { text -> startChatStreaming(text, null, null) }

                when (currentScreen) {
                    AppScreen.LAUNCHER -> {
                        StationLauncherScreen(
                            stationUrl = stationUrl,
                            onUrlChange = {
                                stationUrl = it
                                errorMessage = null
                            },
                            recentStations = recentStations,
                            isConnecting = isConnecting,
                            errorMessage = errorMessage,
                            language = language,
                            onLanguageToggle = { toggleLanguage() },
                            isDark = isDark,
                            onThemeToggle = { toggleTheme() },
                            onConnect = { url -> connectToStation(url) },
                            onDeleteRecent = { toDelete ->
                                val filtered = recentStations.filter { it.url != toDelete }
                                saveRecent(filtered)
                            },
                            onScanQr = {
                                com.aselens.kovanllm.qr.QrScannerHelper.startScan(
                                    context = this@MainActivity,
                                    onScanned = { result ->
                                        stationUrl = result.url
                                        if (!result.token.isNullOrBlank()) {
                                            repository.stationAuthToken = result.token
                                            prefs.edit().putString("station_auth_token", result.token).apply()
                                        }
                                        errorMessage = null
                                        Toast.makeText(this@MainActivity, "QR: ${result.url}", Toast.LENGTH_SHORT).show()
                                        connectToStation(result.url)
                                    },
                                    onError = { err ->
                                        errorMessage = err
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onVerifyPin = { pin ->
                                coroutineScope.launch {
                                    val result = repository.verifyPairPin(stationUrl, pin)
                                    result.onSuccess { token ->
                                        repository.stationAuthToken = token
                                        prefs.edit().putString("station_auth_token", token).apply()
                                        Toast.makeText(this@MainActivity, "PIN verified! Connecting...", Toast.LENGTH_SHORT).show()
                                        connectToStation(stationUrl)
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: I18n.t("invalid_pin", language)
                                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }

                    AppScreen.SETTINGS -> {
                        ModelSettingsScreen(
                            stationUrl = stationUrl,
                            models = models,
                            engineConfig = engineConfig,
                            onConfigChange = { newCfg ->
                                engineConfig = newCfg
                                coroutineScope.launch {
                                    repository.updateEngineConfig(stationUrl, newCfg)
                                }
                            },
                            language = language,
                            onLanguageToggle = { toggleLanguage() },
                            isDark = isDark,
                            onThemeToggle = { toggleTheme() },
                            onProbeEngine = { port ->
                                coroutineScope.launch {
                                    val probeRes = repository.probeEngine(stationUrl, port)
                                    probeRes.onSuccess { data ->
                                        val online = data.optBoolean("online", false)
                                        val latency = data.optInt("latency_ms", 0)
                                        val count = data.optInt("model_count", 0)
                                        probeResultText = if (online) {
                                            "🟢 Online (${latency}ms) • $count models"
                                        } else {
                                            "🔴 Offline on Port $port"
                                        }
                                        if (online) {
                                            val refreshedModels = repository.fetchModels(stationUrl)
                                            refreshedModels.onSuccess { models = it }
                                        }
                                    }.onFailure {
                                        probeResultText = "🔴 Probe failed: ${it.localizedMessage}"
                                    }
                                }
                            },
                            probeResultText = probeResultText,
                            onUnloadModel = {
                                coroutineScope.launch {
                                    val res = repository.unloadModel(stationUrl)
                                    res.onSuccess { msg ->
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        Toast.makeText(this@MainActivity, "Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onApplyAndReloadModel = {
                                coroutineScope.launch {
                                    Toast.makeText(this@MainActivity, I18n.t("applying_model", language), Toast.LENGTH_SHORT).show()
                                    val res = repository.applyAndReloadModel(
                                        baseUrl = stationUrl,
                                        model = engineConfig.activeModel,
                                        contextLength = engineConfig.contextLength,
                                        gpuOffload = engineConfig.gpuOffload,
                                        gpuOffloadPercent = engineConfig.gpuOffloadPercent
                                    )
                                    res.onSuccess { msg ->
                                        Toast.makeText(this@MainActivity, "🟢 $msg", Toast.LENGTH_LONG).show()
                                        val refreshed = repository.fetchModels(stationUrl)
                                        refreshed.onSuccess { models = it }
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, "🔴 Error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onStartChat = { currentScreen = AppScreen.CHAT },
                            onDisconnect = { currentScreen = AppScreen.LAUNCHER }
                        )
                    }

                    AppScreen.CHAT -> {
                        ChatScreen(
                            modelName = engineConfig.activeModel.ifEmpty { models.firstOrNull() ?: "Local LLM" },
                            sessions = sessions,
                            currentSessionId = currentSessionId,
                            messages = messages,
                            isStreaming = isStreaming,
                            currentTokensPerSec = currentTokensPerSec,
                            currentTtftMs = currentTtftMs,
                            language = language,
                            isDark = isDark,
                            onThemeToggle = { toggleTheme() },
                            onSendMessage = { text, mediaUri, mediaName ->
                                startChatStreaming(text, mediaUri, mediaName)
                            },
                            onRegenerate = {
                                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                if (lastUserMsg != null) {
                                    if (messages.isNotEmpty() && messages.last().role == "assistant") {
                                        messages.removeAt(messages.size - 1)
                                    }
                                    if (currentSession.messages.isNotEmpty() && currentSession.messages.last().role == "assistant") {
                                        currentSession.messages.removeAt(currentSession.messages.size - 1)
                                    }
                                    startChatStreaming(lastUserMsg.content, lastUserMsg.attachedMediaUri, lastUserMsg.attachedMediaName)
                                }
                            },
                            onStopStreaming = {
                                messageQueue.clear()
                                activeCall?.cancel()
                                activeCall = null
                                isStreaming = false
                                currentTokensPerSec = null
                            },
                            onSpeakText = { text ->
                                if (isSpeaking) {
                                    chatSpeechPlayer.stop()
                                    isSpeaking = false
                                    Toast.makeText(this@MainActivity, I18n.t("speech_stopped", language), Toast.LENGTH_SHORT).show()
                                } else {
                                    isSpeaking = true
                                    Toast.makeText(this@MainActivity, I18n.t("reading_aloud", language), Toast.LENGTH_SHORT).show()
                                    chatSpeechPlayer.speakFullText(text)
                                }
                            },
                            onStopSpeaking = {
                                chatSpeechPlayer.stop()
                                isSpeaking = false
                            },
                            isSpeaking = isSpeaking,
                            isLiveVoiceOpen = isLiveVoiceOpen,
                            liveVoiceState = liveVoiceManager.state.value,
                            liveAudioLevel = liveVoiceManager.audioLevel.value,
                            liveUserTranscript = liveVoiceManager.userTranscript.value,
                            liveAssistantTranscript = liveVoiceManager.assistantTranscript.value,
                            isLiveMuted = liveVoiceManager.isMuted.value,
                            onStartLiveVoice = {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    isLiveVoiceOpen = true
                                    liveVoiceManager.language = language
                                    liveVoiceManager.startLiveSession()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onLiveInterrupt = {
                                liveVoiceManager.interrupt()
                            },
                            onLiveToggleMute = {
                                liveVoiceManager.toggleMute()
                            },
                            onLiveEndCall = {
                                isLiveVoiceOpen = false
                                liveVoiceManager.endLiveSession()
                            },
                            onClearChat = {
                                messages.clear()
                                currentSession.messages.clear()
                                saveSessions()
                            },
                            onNewSession = {
                                messageQueue.clear()
                                AdMobInterstitialManager.showOnTransition(this@MainActivity)
                                val newSess = ChatSession(title = "Chat ${sessions.size + 1}")
                                sessions.add(0, newSess)
                                currentSessionId = newSess.id
                                messages.clear()
                                saveSessions()
                            },
                            onSelectSession = { sessId ->
                                if (sessId != currentSessionId) {
                                    messageQueue.clear()
                                    AdMobInterstitialManager.showOnTransition(this@MainActivity)
                                    val targetSess = sessions.find { it.id == sessId }
                                    if (targetSess != null) {
                                        currentSessionId = sessId
                                        messages.clear()
                                        messages.addAll(targetSess.messages)
                                    }
                                }
                            },
                            onDeleteSession = { sessId ->
                                if (sessions.size > 1) {
                                    sessions.removeAll { it.id == sessId }
                                    if (currentSessionId == sessId) {
                                        currentSessionId = sessions.first().id
                                        messages.clear()
                                        messages.addAll(sessions.first().messages)
                                    }
                                    saveSessions()
                                }
                            },
                            onBack = {
                                // Background streaming: DO NOT CANCEL call! It continues generating tokens in background.
                                currentScreen = AppScreen.SETTINGS
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun parseRecentStations(jsonStr: String): List<StationItem> {
        val list = mutableListOf<StationItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    StationItem(
                        url = obj.getString("url"),
                        name = obj.optString("name", "Station"),
                        lastConnected = obj.optLong("lastConnected", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun parseSavedSessions(jsonStr: String): MutableList<ChatSession> {
        val list = mutableListOf<ChatSession>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sess = ChatSession(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    title = obj.optString("title", "Saved Chat"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                val msgArr = obj.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until msgArr.length()) {
                    val mObj = msgArr.getJSONObject(j)
                    sess.messages.add(
                        ChatMessage(
                            id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                            role = mObj.optString("role", "user"),
                            content = mObj.optString("content", ""),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                            tokensPerSec = if (mObj.has("tokensPerSec")) mObj.getDouble("tokensPerSec") else null,
                            ttftMs = if (mObj.has("ttftMs")) mObj.getLong("ttftMs") else null,
                            totalTokens = if (mObj.has("totalTokens")) mObj.getInt("totalTokens") else null,
                            attachedMediaUri = if (mObj.has("attachedMediaUri")) mObj.getString("attachedMediaUri") else null,
                            attachedMediaName = if (mObj.has("attachedMediaName")) mObj.getString("attachedMediaName") else null
                        )
                    )
                }
                list.add(sess)
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }
}
