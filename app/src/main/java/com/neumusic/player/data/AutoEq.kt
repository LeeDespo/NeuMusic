package com.neumusic.player.data

import android.content.Context
import org.json.JSONObject

/** Offline subset of official AutoEq derived ParametricEQ results; see assets/autoeq/README.md. */
object AutoEq {
    data class Entry(val name: String, val source: String, val text: String)

    @Volatile
    private var cached: List<Entry>? = null

    /** Call from Dispatchers.IO. Keeps the original APO text for the common preset importer. */
    fun load(context: Context): List<Entry> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: readCatalog(context.applicationContext).also { cached = it }
        }
    }

    private fun readCatalog(context: Context): List<Entry> {
        val root = context.assets.open("autoeq/catalog.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
        require(root.getInt("version") == 1) { "Unsupported AutoEq catalog version" }
        val entries = root.getJSONArray("entries")
        require(entries.length() == root.getInt("entryCount")) { "Incomplete AutoEq catalog" }
        return List(entries.length()) { index ->
            val item = entries.getJSONObject(index)
            Entry(item.getString("name"), item.getString("source"), item.getString("text"))
        }
    }
}
