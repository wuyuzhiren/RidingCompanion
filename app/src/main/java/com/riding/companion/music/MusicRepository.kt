package com.riding.companion.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MusicRepository {

    private const val PREFS = "music_lib"
    private const val KEY = "songs"

    fun load(ctx: Context): MutableList<Song> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val list = mutableListOf<Song>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Song(o.optString("title"), o.optString("url")))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun save(ctx: Context, songs: List<Song>) {
        val arr = JSONArray()
        songs.forEach { s -> arr.put(JSONObject().put("title", s.title).put("url", s.url)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
