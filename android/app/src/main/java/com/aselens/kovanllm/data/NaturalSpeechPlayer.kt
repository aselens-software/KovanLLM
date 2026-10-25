package com.aselens.kovanllm.data

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Native Device TextToSpeech Player.
 * Uses the phone's native built-in TTS engine and default voice directly.
 * Zero network dependencies, zero latency, and seamless QUEUE_ADD sentence streaming.
 */
class NaturalSpeechPlayer(
    private val context: Context,
    private val onAllSpeechFinished: () -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isSpeakingActive = false

    var language: String = "en"
        set(value) {
            field = value
            updateTtsLanguage()
        }

    var stationUrl: String = "" // Kept for interface compatibility

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            updateTtsLanguage()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeakingActive = true
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (tts?.isSpeaking != true) {
                            isSpeakingActive = false
                            onAllSpeechFinished()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        isSpeakingActive = false
                        onAllSpeechFinished()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        isSpeakingActive = false
                        onAllSpeechFinished()
                    }
                }
            })
        }
    }

    private fun updateTtsLanguage() {
        if (!isTtsReady || tts == null) return
        try {
            val targetLocale = if (language.lowercase().startsWith("tr")) {
                Locale.forLanguageTag("tr-TR")
            } else {
                Locale.getDefault()
            }
            val res = tts?.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPlaying(): Boolean = isSpeakingActive || (tts?.isSpeaking == true)

    fun resetForNewTurn() {
        stop()
    }

    fun enqueueSentence(sentence: String) {
        if (!isTtsReady || tts == null) return
        val cleanText = cleanForSpeech(sentence)
        if (cleanText.isBlank()) return

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, params, utteranceId)
        isSpeakingActive = true
    }

    fun onStreamEnd(remainingText: String) {
        if (remainingText.isNotBlank()) {
            enqueueSentence(remainingText)
        }
        if (!isPlaying()) {
            onAllSpeechFinished()
        }
    }

    fun speakFullText(text: String) {
        stop()
        if (!isTtsReady || tts == null) return
        val cleanText = cleanForSpeech(text)
        if (cleanText.isBlank()) return

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        isSpeakingActive = true
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " ") // Remove code blocks
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("[#*_~]"), "")
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            .replace(Regex("https?://\\S+"), " ")
            .trim()
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isSpeakingActive = false
    }

    fun destroy() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tts = null
        isTtsReady = false
    }
}
