package com.aselens.kovanllm.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

enum class LiveVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class LiveVoiceManager(
    private val context: Context,
    private val onSendPrompt: (promptText: String) -> Unit,
    private val onInterruptLLM: () -> Unit
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSessionActive = false

    private val naturalPlayer = NaturalSpeechPlayer(context) {
        // Called when all queued sentences have finished playing
        mainHandler.post {
            if (isSessionActive && _state.value != LiveVoiceState.IDLE) {
                if (!_isMuted.value) {
                    startListeningInternal()
                } else {
                    _state.value = LiveVoiceState.IDLE
                }
            }
        }
    }

    private val _state = mutableStateOf(LiveVoiceState.IDLE)
    val state: State<LiveVoiceState> = _state

    private val _audioLevel = mutableFloatStateOf(0f)
    val audioLevel: State<Float> = _audioLevel

    private val _userTranscript = mutableStateOf("")
    val userTranscript: State<String> = _userTranscript

    private val _assistantTranscript = mutableStateOf("")
    val assistantTranscript: State<String> = _assistantTranscript

    private val _isMuted = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted

    var language: String = "en"
        set(value) {
            field = value
            naturalPlayer.language = value
        }

    var stationUrl: String = ""
        set(value) {
            field = value
            naturalPlayer.stationUrl = value
        }

    // Token streaming sentence buffer
    private val streamSentenceBuffer = StringBuilder()

    fun startLiveSession() {
        isSessionActive = true
        _isMuted.value = false
        _userTranscript.value = ""
        _assistantTranscript.value = ""
        streamSentenceBuffer.clear()
        naturalPlayer.resetForNewTurn()
        startListeningInternal()
    }

    fun endLiveSession() {
        isSessionActive = false
        _state.value = LiveVoiceState.IDLE
        mainHandler.removeCallbacksAndMessages(null)
        _audioLevel.floatValue = 0f
        streamSentenceBuffer.clear()
        naturalPlayer.stop()
        stopAndDestroyRecognizer()
    }

    private fun stopAndDestroyRecognizer() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    fun toggleMute() {
        if (!isSessionActive) return
        val nextMuted = !_isMuted.value
        _isMuted.value = nextMuted
        if (nextMuted) {
            stopListeningInternal()
            naturalPlayer.stop()
            _audioLevel.floatValue = 0f
        } else {
            if (_state.value != LiveVoiceState.SPEAKING && _state.value != LiveVoiceState.THINKING) {
                startListeningInternal()
            }
        }
    }

    fun interrupt() {
        if (!isSessionActive) return
        naturalPlayer.stop()
        onInterruptLLM()
        streamSentenceBuffer.clear()
        _assistantTranscript.value = ""
        if (!_isMuted.value) {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!isSessionActive || _isMuted.value) return

        mainHandler.post {
            if (!isSessionActive || _isMuted.value) return@post
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _state.value = LiveVoiceState.IDLE
                    return@post
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@LiveVoiceManager)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language.lowercase().startsWith("tr")) "tr-TR" else "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                _state.value = LiveVoiceState.LISTENING
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = LiveVoiceState.IDLE
            }
        }
    }

    private fun stopListeningInternal() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Real-time Token Streaming: Speaks as tokens arrive!
     * Splits incoming stream into sentence chunks and queues speech immediately.
     */
    fun onAssistantStreamingUpdate(chunk: String) {
        if (!isSessionActive || _state.value == LiveVoiceState.IDLE) return

        _assistantTranscript.value += chunk
        streamSentenceBuffer.append(chunk)

        val currentText = streamSentenceBuffer.toString()
        val delimiterIndex = findSentenceBoundary(currentText)

        if (delimiterIndex != -1) {
            val sentence = currentText.substring(0, delimiterIndex + 1).trim()
            streamSentenceBuffer.delete(0, delimiterIndex + 1)

            if (sentence.isNotBlank()) {
                _state.value = LiveVoiceState.SPEAKING
                naturalPlayer.enqueueSentence(sentence)
            }
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        // Look for sentence terminators: . ! ? or newline
        for (i in 0 until text.length) {
            val ch = text[i]
            if (ch == '!' || ch == '?' || ch == '\n') {
                return i
            }
            if (ch == '.') {
                // Ignore decimals like 3.14 or abbreviations if next char is digit
                val next = if (i + 1 < text.length) text[i + 1] else ' '
                if (next.isWhitespace() || i == text.length - 1) {
                    return i
                }
            }
            // If clause is long (> 70 chars) and has comma or semicolon, split for natural pacing
            if (i > 70 && (ch == ',' || ch == ';' || ch == ':')) {
                return i
            }
        }
        return -1
    }

    fun onAssistantTurnComplete(fullResponseText: String) {
        if (!isSessionActive || _state.value == LiveVoiceState.IDLE) return

        _assistantTranscript.value = fullResponseText
        val remaining = streamSentenceBuffer.toString().trim()
        streamSentenceBuffer.clear()

        _state.value = LiveVoiceState.SPEAKING
        naturalPlayer.onStreamEnd(remaining)
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        if (!isSessionActive) return
        if (_state.value != LiveVoiceState.IDLE) {
            _state.value = LiveVoiceState.LISTENING
        }
    }

    override fun onBeginningOfSpeech() {
        if (!isSessionActive) return
        _audioLevel.floatValue = 0.5f
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (!isSessionActive) return
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _audioLevel.floatValue = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        if (!isSessionActive) return
        _audioLevel.floatValue = 0f
    }

    override fun onError(error: Int) {
        if (!isSessionActive || _state.value == LiveVoiceState.IDLE) return

        // If user stayed silent or no match, gracefully restart listening without ending call
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            mainHandler.postDelayed({
                if (isSessionActive && _state.value == LiveVoiceState.LISTENING && !_isMuted.value) {
                    startListeningInternal()
                }
            }, 300)
            return
        }

        // On client or busy errors, recreate recognizer
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
            stopAndDestroyRecognizer()
            mainHandler.postDelayed({
                if (isSessionActive && _state.value == LiveVoiceState.LISTENING && !_isMuted.value) {
                    startListeningInternal()
                }
            }, 400)
            return
        }

        if (isSessionActive) {
            _state.value = LiveVoiceState.IDLE
        }
    }

    override fun onResults(results: Bundle?) {
        if (!isSessionActive || _state.value == LiveVoiceState.IDLE) return

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""

        if (text.isNotBlank()) {
            _userTranscript.value = text
            _assistantTranscript.value = ""
            streamSentenceBuffer.clear()
            naturalPlayer.resetForNewTurn()
            _state.value = LiveVoiceState.THINKING
            onSendPrompt(text)
        } else {
            if (!_isMuted.value) {
                startListeningInternal()
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (!isSessionActive) return
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""
        if (text.isNotBlank()) {
            _userTranscript.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        endLiveSession()
        naturalPlayer.destroy()
    }
}
