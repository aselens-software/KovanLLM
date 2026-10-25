package com.aselens.kovanllm.data

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var tokensPerSec: Double? = null,
    var ttftMs: Long? = null,
    var totalTokens: Int? = null,
    var isStreaming: Boolean = false,
    var attachedMediaUri: String? = null,
    var attachedMediaName: String? = null,
    var attachedMediaType: String? = null // "image", "video", "file"
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
)

data class StationItem(
    val url: String,
    val name: String,
    val lastConnected: Long = System.currentTimeMillis(),
    var latencyMs: Long? = null
)

data class EngineConfig(
    var engineHost: String = "http://127.0.0.1",
    var enginePort: Int = 58080,
    var activeModel: String = "",
    var contextLength: Int = 4096,
    var customContextLength: Int? = null,
    var gpuOffload: String = "max",
    var gpuOffloadPercent: Int = 100, // 0 to 100% graduated slider
    var cpuThreads: Int = 8,
    var temperature: Float = 0.7f,
    var topP: Float = 0.95f,
    var topK: Int = 40,
    var frequencyPenalty: Float = 0.0f,
    var presencePenalty: Float = 0.0f,
    var maxTokens: Int = -1,
    var systemRoleEnabled: Boolean = false,
    var systemPrompt: String = "",
    var language: String = "en",
    var theme: String = "dark" // "dark" or "light"
)

data class ModelPreset(
    val nameEn: String,
    val nameTr: String,
    val icon: String,
    val descriptionEn: String,
    val descriptionTr: String,
    val systemPrompt: String
)

val GLOBAL_PRESETS = listOf(
    ModelPreset(
        nameEn = "General Assistant",
        nameTr = "Genel Asistan",
        icon = "⚡",
        descriptionEn = "Helpful, direct, and concise AI assistant",
        descriptionTr = "Faydalı, doğrudan ve net yapay zeka asistanı",
        systemPrompt = "You are a helpful, direct, and concise AI assistant."
    ),
    ModelPreset(
        nameEn = "Software Engineer",
        nameTr = "Yazılım Mühendisi",
        icon = "💻",
        descriptionEn = "Clean code, architecture patterns, and debugging",
        descriptionTr = "Temiz kod, mimari desenler ve hata ayıklama",
        systemPrompt = "You are an expert software engineer. Provide clean, maintainable, and well-documented code with practical explanations."
    ),
    ModelPreset(
        nameEn = "Language Specialist",
        nameTr = "Dil Uzmanı / Çevirmen",
        icon = "🌐",
        descriptionEn = "Nuanced linguistic translation and cultural fidelity",
        descriptionTr = "Akademik ve günlük doğal dil çevirisi",
        systemPrompt = "You are an expert linguist and translator. Translate accurately while preserving nuance, style, and tone."
    ),
    ModelPreset(
        nameEn = "Executive Summarizer",
        nameTr = "Özetleyici",
        icon = "📝",
        descriptionEn = "High-density bullet points and key takeaways",
        descriptionTr = "Uzun metinleri ana hatlarıyla maddelere dökme",
        systemPrompt = "You are an analytical assistant. Summarize text clearly with structured key takeaways, removing fluff."
    ),
    ModelPreset(
        nameEn = "Creative Writer",
        nameTr = "Yaratıcı Yazar",
        icon = "🎭",
        descriptionEn = "Engaging narratives, metaphors, and storytelling",
        descriptionTr = "Özgün anlatım, kurgu ve hikaye tasarımı",
        systemPrompt = "You are a creative and engaging writer. Craft rich, vivid, and thoughtful narratives with compelling prose."
    )
)
