package com.riding.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容的大模型流式对话客户端。
 * 对接任意提供 /chat/completions SSE 流式接口的服务（DeepSeek、通义、GLM、Ollama 等）。
 */
object ChatEngine {

    data class Msg(val role: String, val content: String)

    suspend fun streamChat(messages: List<Msg>, onDelta: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val base = AppConfig.llmBaseUrl.trim().trimEnd('/')
            require(base.isNotEmpty()) { "未配置大模型接口地址" }
            val url = URL("$base/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "text/event-stream")
                val key = AppConfig.llmApiKey.trim()
                if (key.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $key")
                }

                val body = JSONObject()
                body.put("model", AppConfig.llmModel.trim().ifEmpty { "deepseek-chat" })
                body.put("stream", true)
                body.put("temperature", AppConfig.temperature.toDouble())
                val arr = JSONArray()
                messages.forEach { m -> arr.put(JSONObject().put("role", m.role).put("content", m.content)) }
                body.put("messages", arr)

                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    throw RuntimeException("接口返回 $code：${err.take(300)}")
                }

                val sb = StringBuilder()
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    if (l.startsWith("data:")) {
                        val data = l.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue
                        try {
                            val jo = JSONObject(data)
                            val choices = jo.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val obj = choices.getJSONObject(0).optJSONObject("delta")
                                val delta = obj?.optString("content", "") ?: ""
                                if (delta.isNotEmpty()) {
                                    sb.append(delta)
                                    onDelta(delta)
                                }
                            }
                        } catch (_: Exception) {
                            // 忽略无法解析的分片
                        }
                    }
                }
                sb.toString()
            } finally {
                conn.disconnect()
            }
        }

    /**
     * 自动识别服务商支持的模型列表。
     * 自动探测常见路径：{base}/models、{base}/v1/models（OpenAI 兼容）、
     * {base}/api/tags（Ollama 原生）等；兼容 data[].id 与 models[].name 两种返回格式。
     * 失败时返回已尝试过的地址明细，方便定位。
     */
    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val base = AppConfig.llmBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "未配置大模型接口地址" }
        // 归一化：去掉可能误填的 /chat/completions 尾巴
        var root = base
        if (root.endsWith("/v1/chat/completions")) root = root.removeSuffix("/v1/chat/completions")
        if (root.endsWith("/chat/completions")) root = root.removeSuffix("/chat/completions")

        val candidates = linkedSetOf<String>()
        if (root.endsWith("/v1")) {
            candidates.add("$root/models")
            candidates.add(root.removeSuffix("/v1") + "/models")
            candidates.add(root.removeSuffix("/v1") + "/v1/models")
            candidates.add(root.removeSuffix("/v1") + "/api/tags")
        } else if (root.endsWith("/api")) {
            candidates.add("$root/v1/models")
            candidates.add("$root/tags")
            candidates.add("$root/models")
        } else {
            candidates.add("$root/models")
            candidates.add("$root/v1/models")
            candidates.add("$root/api/tags")
        }

        val attempts = mutableListOf<String>()
        var lastErr: Exception? = null
        var fatal: RuntimeException? = null
        for (u in candidates) {
            if (attempts.contains(u)) continue
            attempts.add(u)
            val conn = try {
                (URL(u).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    val key = AppConfig.llmApiKey.trim()
                    if (key.isNotEmpty()) setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Accept", "application/json")
                }
            } catch (e: Exception) {
                lastErr = e
                continue
            }
            try {
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    // 路径正确但鉴权失败：Key 缺失/无效，立即提示，不再试其它路径
                    fatal = RuntimeException("接口地址正确，但 API Key 缺失或无效（HTTP $code）。请检查设置里的 Key 是否填写完整（通常以 sk- 开头）。")
                    break
                }
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    lastErr = RuntimeException("$u → $code：${err.take(120)}")
                    continue
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val jo = JSONObject(text)
                val ids = mutableListOf<String>()
                val data = jo.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val id = data.optJSONObject(i)?.optString("id")
                        if (!id.isNullOrBlank()) ids.add(id)
                    }
                }
                val models = jo.optJSONArray("models")
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val name = models.optJSONObject(i)?.optString("name")
                        if (!name.isNullOrBlank()) ids.add(name)
                    }
                }
                if (ids.isNotEmpty()) return@withContext ids
            } catch (e: Exception) {
                lastErr = e
            } finally {
                conn.disconnect()
            }
        }
        if (fatal != null) throw fatal
        val diag = attempts.joinToString("；")
        throw RuntimeException("已尝试 $diag 均失败（最后错误：${lastErr?.message ?: "未知"}）。若该服务不提供模型列表，请手动输入模型名。")
    }
}
