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
}
