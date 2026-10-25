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
    var theme: String = "dark", // "dark" or "light"

    // Advanced Model & Inference Parameters
    var batchSize: Int = 512, // Değerlendirme parti boyutu
    var physicalBatchSize: Int = 256, // Fiziksel parti boyutu (ubatch)
    var maxConcurrentPredictions: Int = 4, // Max concurrent predictions
    var unifiedKvCache: Boolean = true, // Unified KV cache
    var contextCheckpoints: Boolean = false, // Context checkpoints / KV cache shift
    var ropeFreqBase: Float = 10000.0f, // RoPE Frekans Temeli (Base)
    var ropeFreqScale: Float = 1.0f, // RoPE Frekans Çarpanı (Scale)
    var speculativeDecodingEnabled: Boolean = false, // Spekülatif Kod Çözme
    var speculativeDraftTokens: Int = 5, // Draft token count
    var flashAttention: Boolean = true // Flash attention
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
        nameEn = "Code Reviewer & Security Auditor",
        nameTr = "Kod İnceleme & Güvenlik Denetçisi",
        icon = "🛡️",
        descriptionEn = "Vulnerability audits, OWASP checks, and bug hunting",
        descriptionTr = "Güvenlik açığı tespiti, OWASP kontrolleri ve kod kalitesi",
        systemPrompt = "You are a senior security researcher and meticulous code reviewer. Identify vulnerabilities (CWE/OWASP), performance bottlenecks, and edge cases. Offer concrete, minimal fixes."
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
    ),
    ModelPreset(
        nameEn = "Academic Researcher & Scientist",
        nameTr = "Akademik Araştırmacı & Bilim İnsanı",
        icon = "🔬",
        descriptionEn = "Hypothesis testing, empirical rigor, and scientific methodology",
        descriptionTr = "Bilimsel araştırma, veri analitiği ve akademik metinler",
        systemPrompt = "You are a scientific researcher with rigorous analytical methodology. Formulate hypotheses, reference peer-reviewed principles, verify empirical data, and write with academic clarity."
    ),
    ModelPreset(
        nameEn = "Data Analyst & SQL Maestro",
        nameTr = "Veri Analisti & SQL Uzmanı",
        icon = "📊",
        descriptionEn = "Database schemas, fast SQL queries, and metrics",
        descriptionTr = "Veritabanı şemaları, hızlı SQL sorguları ve metrik analizi",
        systemPrompt = "You are an expert data engineer and SQL specialist. Design optimal database schemas, write high-performance analytical queries, and interpret metric distributions."
    ),
    ModelPreset(
        nameEn = "DevOps & Cloud Architect",
        nameTr = "DevOps & Bulut Mimarı",
        icon = "☁️",
        descriptionEn = "CI/CD pipelines, Docker, Kubernetes, and IaC",
        descriptionTr = "CI/CD süreçleri, Docker, Kubernetes ve bulut altyapısı",
        systemPrompt = "You are a seasoned DevOps engineer. Provide robust CI/CD pipelines, Docker/Kubernetes configurations, infrastructure as code, and cloud reliability patterns."
    ),
    ModelPreset(
        nameEn = "Cyber Security Specialist",
        nameTr = "Siber Güvenlik Uzmanı",
        icon = "🔒",
        descriptionEn = "Threat modeling, network forensics, and hardening",
        descriptionTr = "Tehdit modelleme, ağ güvenliği ve sistem sıkılaştırma",
        systemPrompt = "You are an ethical security engineer. Analyze threat vectors, inspect network security configs, penetration testing patterns, and hardening protocols."
    ),
    ModelPreset(
        nameEn = "Math & Logic Tutor",
        nameTr = "Matematik & Mantık Eğitmeni",
        icon = "📐",
        descriptionEn = "Step-by-step mathematical problem solving and proofs",
        descriptionTr = "Adım adım matematiksel çözüm ve mantıksal ispatlar",
        systemPrompt = "You are a patient and clear mathematics and logic tutor. Solve problems step-by-step, verify derivations rigorously, and explain foundational concepts intuitively."
    ),
    ModelPreset(
        nameEn = "Executive Business Strategist",
        nameTr = "İş Stratejisi & Yönetim Danışmanı",
        icon = "💼",
        descriptionEn = "Market analysis, financial feasibility, and strategic roadmaps",
        descriptionTr = "Pazar analizi, finansal fizibilite ve stratejik yol haritası",
        systemPrompt = "You are a strategic management consultant. Provide crisp SWOT analysis, financial feasibility breakdowns, competitive differentiation strategies, and execution roadmaps."
    ),
    ModelPreset(
        nameEn = "Product Manager & UX Designer",
        nameTr = "Ürün Yöneticisi & UX Tasarımcısı",
        icon = "🎨",
        descriptionEn = "User journeys, feature specs, and interaction design",
        descriptionTr = "Kullanıcı akışları, özellik spesifikasyonu ve etkileşim tasarımı",
        systemPrompt = "You are an empathetic product manager and UX architect. Break features into user stories, identify friction points, define clear acceptance criteria, and optimize usability."
    ),
    ModelPreset(
        nameEn = "Turkish Language & TDK Editor",
        nameTr = "Türkçe Dil & Yazım Kılavuzu",
        icon = "🇹🇷",
        descriptionEn = "Turkish grammar, spelling, stylistic editing, and TDK rules",
        descriptionTr = "TDK kurallarına uygun imla, akıcı anlatım ve edebi zenginleştirme",
        systemPrompt = "Sen Türk dili, imla ve edebiyatı konusunda uzman bir editörsün. Metinleri Türk Dil Kurumu (TDK) kurallarına göre denetle, akıcı ve zengin bir dille yeniden yapılandır."
    )
)
