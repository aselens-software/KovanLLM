package com.aselens.kovanllm.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LLMRepository {

    var stationAuthToken: String? = null

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val orig = chain.request()
            val builder = orig.newBuilder()
            stationAuthToken?.takeIf { it.isNotBlank() }?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = if (trimmed.contains(".trycloudflare.com") || trimmed.contains("https")) {
                "https://$trimmed"
            } else {
                "http://$trimmed"
            }
        }
        return trimmed
    }

    suspend fun pingStation(baseUrl: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/status"
            val start = System.currentTimeMillis()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val latency = System.currentTimeMillis() - start
                    Result.success(latency)
                } else {
                    // Fallback to /v1/models
                    val v1Url = "${normalizeUrl(baseUrl)}/v1/models"
                    val v1Request = Request.Builder().url(v1Url).get().build()
                    client.newCall(v1Request).execute().use { v1Resp ->
                        if (v1Resp.isSuccessful) {
                            Result.success(System.currentTimeMillis() - start)
                        } else {
                            Result.failure(Exception("HTTP ${response.code}"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStationStatus(baseUrl: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/status"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Result.success(json)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyPairPin(baseUrl: String, pin: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/pair/verify"
            val jsonBody = JSONObject().put("pin", pin.trim()).toString()
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val token = json.optString("auth_token", "")
                    if (token.isNotBlank()) {
                        stationAuthToken = token
                        Result.success(token)
                    } else {
                        Result.failure(Exception("Missing auth_token in response"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: Invalid PIN"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchModels(baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/v1/models"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<String>()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val id = item.optString("id")
                    if (id.isNotBlank()) {
                        models.add(id)
                    }
                }
                if (models.isEmpty()) {
                    models.add("qwen2.5-3b-instruct-abliterated")
                }
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEngineConfig(baseUrl: String): Result<EngineConfig> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/engine/config"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val rawOffload = json.optString("gpu_offload", "max")
                    val rawPct = json.optInt("gpu_offload_percent", -1)
                    val calculatedPct = if (rawPct in 0..100) {
                        rawPct
                    } else {
                        when (rawOffload.lowercase().trim()) {
                            "off", "0", "0%" -> 0
                            "max", "1", "100%", "full" -> 100
                            else -> try {
                                val s = rawOffload.replace(",", ".").replace("%", "").trim()
                                if ("/" in s) {
                                    val parts = s.split("/")
                                    ((parts[0].toFloat() / parts[1].toFloat()) * 100f).toInt()
                                } else {
                                    val f = s.toFloat()
                                    if (f <= 1.0f) (f * 100f).toInt() else f.toInt()
                                }
                            } catch (e: Exception) { 100 }
                        }
                    }

                    val cfg = EngineConfig(
                        engineHost = json.optString("engine_host", "http://127.0.0.1"),
                        enginePort = json.optInt("engine_port", 58080),
                        activeModel = json.optString("active_model", ""),
                        contextLength = json.optInt("context_length", 4096),
                        gpuOffload = rawOffload,
                        gpuOffloadPercent = calculatedPct,
                        cpuThreads = json.optInt("cpu_threads", 8),
                        temperature = json.optDouble("temperature", 0.7).toFloat(),
                        topP = json.optDouble("top_p", 0.95).toFloat(),
                        topK = json.optInt("top_k", 40),
                        frequencyPenalty = json.optDouble("frequency_penalty", 0.0).toFloat(),
                        presencePenalty = json.optDouble("presence_penalty", 0.0).toFloat(),
                        maxTokens = json.optInt("max_tokens", -1),
                        systemRoleEnabled = json.optBoolean("system_role_enabled", false),
                        systemPrompt = json.optString("system_prompt", ""),
                        language = json.optString("language", "en")
                    )
                    Result.success(cfg)
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEngineConfig(baseUrl: String, config: EngineConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/engine/config"
            val json = JSONObject().apply {
                put("engine_host", config.engineHost)
                put("engine_port", config.enginePort)
                put("active_model", config.activeModel)
                put("context_length", config.contextLength)
                put("gpu_offload", config.gpuOffload)
                put("gpu_offload_percent", config.gpuOffloadPercent)
                put("cpu_threads", config.cpuThreads)
                put("temperature", config.temperature)
                put("top_p", config.topP)
                put("top_k", config.topK)
                put("frequency_penalty", config.frequencyPenalty)
                put("presence_penalty", config.presencePenalty)
                put("max_tokens", config.maxTokens)
                put("system_role_enabled", config.systemRoleEnabled)
                put("system_prompt", config.systemPrompt)
                put("language", config.language)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { resp ->
                Result.success(resp.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun probeEngine(baseUrl: String, port: Int): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/engine/probe"
            val json = JSONObject().apply {
                put("host", "127.0.0.1")
                put("port", port)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(JSONObject(resp.body?.string() ?: "{}"))
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unloadModel(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/engine/unload"
            val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                val msg = json.optString("message", "Model unloaded successfully.")
                Result.success(msg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyAndReloadModel(
        baseUrl: String,
        model: String,
        contextLength: Int,
        gpuOffload: String,
        gpuOffloadPercent: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${normalizeUrl(baseUrl)}/api/engine/apply-and-reload"
            val json = JSONObject().apply {
                put("model", model)
                put("context_length", contextLength)
                put("gpu_offload", gpuOffload)
                if (gpuOffloadPercent != null) {
                    put("gpu_offload_percent", gpuOffloadPercent)
                }
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { resp ->
                val jsonRes = JSONObject(resp.body?.string() ?: "{}")
                val msg = jsonRes.optString("message", if (resp.isSuccessful) "Model reloaded successfully." else "Failed to reload model.")
                if (resp.isSuccessful && jsonRes.optBoolean("success", true)) {
                    Result.success(msg)
                } else {
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        baseUrl: String,
        model: String,
        messages: List<ChatMessage>,
        config: EngineConfig,
        context: Context? = null,
        onFirstToken: (ttftMs: Long) -> Unit,
        onToken: (delta: String, tokensPerSec: Double) -> Unit,
        onComplete: (totalTokens: Int, avgTokensPerSec: Double, ttftMs: Long) -> Unit,
        onError: (error: String) -> Unit
    ): Call {
        val url = "${normalizeUrl(baseUrl)}/v1/chat/completions"

        val jsonMessages = JSONArray()
        // Only inject system prompt if explicitly enabled and non-empty
        if (config.systemRoleEnabled && config.systemPrompt.trim().isNotEmpty()) {
            jsonMessages.put(JSONObject().apply {
                put("role", "system")
                put("content", config.systemPrompt.trim())
            })
        }

        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg.role)

            if (msg.role == "user" && context != null && !msg.attachedMediaUri.isNullOrBlank()) {
                val prepared = try {
                    FileHelper.processUri(context, Uri.parse(msg.attachedMediaUri), msg.attachedMediaName)
                } catch (e: Exception) {
                    null
                }

                if (prepared != null) {
                    if (prepared.isImage && prepared.base64DataUri != null) {
                        // OpenAI Vision format
                        val arr = JSONArray()
                        val textPrompt = if (msg.content.isNotBlank()) msg.content else "Please analyze this image."
                        arr.put(JSONObject().apply {
                            put("type", "text")
                            put("text", textPrompt)
                        })
                        arr.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", prepared.base64DataUri)
                            })
                        })
                        obj.put("content", arr)
                    } else if (!prepared.isImage && prepared.textContent != null) {
                        // Inject text/code into prompt
                        val textPrompt = if (msg.content.isNotBlank()) msg.content else "Please analyze this attached file."
                        val ext = prepared.fileName.substringAfterLast('.', "txt")
                        val injected = "[Attached File: ${prepared.fileName}]\n```$ext\n${prepared.textContent}\n```\n\n$textPrompt"
                        obj.put("content", injected)
                    } else {
                        obj.put("content", msg.content.ifBlank { "[Attached File: ${prepared.fileName}]" })
                    }
                } else {
                    obj.put("content", msg.content.ifBlank { "[Attachment: ${msg.attachedMediaName ?: "file"}]" })
                }
                jsonMessages.put(obj)
            } else if (msg.content.isNotBlank()) {
                obj.put("content", msg.content)
                jsonMessages.put(obj)
            }
        }

        val requestPayload = JSONObject().apply {
            put("model", model)
            put("messages", jsonMessages)
            put("temperature", config.temperature)
            put("top_p", config.topP)
            if (config.frequencyPenalty != 0.0f) put("frequency_penalty", config.frequencyPenalty)
            if (config.presencePenalty != 0.0f) put("presence_penalty", config.presencePenalty)
            if (config.maxTokens > 0) put("max_tokens", config.maxTokens)
            put("stream", true)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestPayload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "text/event-stream")
            .build()

        val call = client.newCall(request)

        Thread {
            var tokenCount = 0
            val startTime = System.currentTimeMillis()
            var ttft: Long = 0
            var firstTokenReported = false

            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "HTTP Error ${response.code}"
                    onError("Server error (${response.code}): $errBody")
                    return@Thread
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    onError("Empty stream received")
                    return@Thread
                }

                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

                    if (currentLine.startsWith("data:")) {
                        val dataContent = currentLine.substring(5).trim()
                        if (dataContent == "[DONE]") {
                            break
                        }

                        try {
                            val chunkJson = JSONObject(dataContent)
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val delta = firstChoice.optJSONObject("delta")
                                val text = delta?.optString("content") ?: ""
                                if (text.isNotEmpty()) {
                                    tokenCount++
                                    if (!firstTokenReported) {
                                        ttft = System.currentTimeMillis() - startTime
                                        firstTokenReported = true
                                        onFirstToken(ttft)
                                    }
                                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                                    val tps = if (elapsedSec > 0.05) tokenCount / elapsedSec else 0.0
                                    onToken(text, tps)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore malformed chunks
                        }
                    }
                }

                val totalElapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                val finalTps = if (totalElapsedSec > 0.1) tokenCount / totalElapsedSec else 0.0
                onComplete(tokenCount, finalTps, ttft)

            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    onError(e.localizedMessage ?: "Connection error occurred")
                }
            }
        }.start()

        return call
    }
}
