package com.neumusic.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * 搜索历史（SharedPreferences 持久化）。
 *
 * - 搜索成功点下搜索按钮时记录（[add]），最近在前、去重（重复词提前到最前）；
 * - 上限 [MAX] 条，超出淘汰最旧；
 * - 支持单条删除与全部清空；
 * - 空列表时界面**什么都不显示**（用户要求），所以历史非空才有 UI。
 */
object SearchHistoryStore {
    private const val FILE = "neumusic_search_history"
    private const val MAX = 20

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    @Volatile
    private var sp: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(sp?.getString("items", "[]") ?: "[]")
            _items.value = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
        }
    }

    /** 记录一次搜索。空词不记；已有则提前。 */
    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = ArrayList<String>()
        next.add(q)
        _items.value.forEach { if (it != q && next.size < MAX) next.add(it) }
        _items.value = next
        persist()
    }

    fun remove(query: String) {
        _items.value = _items.value - query
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach { arr.put(it) }
        sp?.edit()?.putString("items", arr.toString())?.apply()
    }
}
