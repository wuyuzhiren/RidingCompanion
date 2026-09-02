package com.riding.companion.data

import android.content.Context
import android.content.SharedPreferences

object AppConfig {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("riding_config", Context.MODE_PRIVATE)
    }

    var llmBaseUrl: String
        get() = sp.getString("llm_base_url", "") ?: ""
        set(v) { sp.edit().putString("llm_base_url", v).apply() }

    var llmApiKey: String
        get() = sp.getString("llm_api_key", "") ?: ""
        set(v) { sp.edit().putString("llm_api_key", v).apply() }

    var llmModel: String
        get() = sp.getString("llm_model", "") ?: ""
        set(v) { sp.edit().putString("llm_model", v).apply() }

    var systemPrompt: String
        get() = sp.getString("system_prompt", "") ?: ""
        set(v) { sp.edit().putString("system_prompt", v).apply() }

    var temperature: Float
        get() = sp.getFloat("temperature", 0.8f)
        set(v) { sp.edit().putFloat("temperature", v).apply() }

    var ttsRate: Float
        get() = sp.getFloat("tts_rate", 1.0f)
        set(v) { sp.edit().putFloat("tts_rate", v).apply() }

    var cyclingMode: Boolean
        get() = sp.getBoolean("cycling_mode", false)
        set(v) { sp.edit().putBoolean("cycling_mode", v).apply() }

    var cyclingAutoVolume: Int
        get() = sp.getInt("cycling_auto_volume", 0)
        set(v) { sp.edit().putInt("cycling_auto_volume", v).apply() }

    var localCommandMatching: Boolean
        get() = sp.getBoolean("local_cmd_matching", true)
        set(v) { sp.edit().putBoolean("local_cmd_matching", v).apply() }

    var cyclingBeep: Boolean
        get() = sp.getBoolean("cycling_beep", true)
        set(v) { sp.edit().putBoolean("cycling_beep", v).apply() }

    var duckLevel: Int
        get() = sp.getInt("duck_level", 10)
        set(v) { sp.edit().putInt("duck_level", v).apply() }

    var currentCharacter: Int
        get() = sp.getInt("current_character", 1)
        set(v) { sp.edit().putInt("current_character", v.coerceIn(1, 3)).apply() }
}