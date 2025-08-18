package com.livecopilot.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FavoritesManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
    }

    fun getAll(): List<Favorite> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Favorite>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Favorite(
                    id = o.getString("id"),
                    type = FavoriteType.valueOf(o.getString("type")),
                    name = o.getString("name"),
                    content = o.getString("content")
                )
            )
        }
        return list
    }

    fun add(type: FavoriteType, name: String, content: String): Favorite {
        val favorite = Favorite(UUID.randomUUID().toString(), type, name, content)
        val list = getAll().toMutableList()
        list.add(0, favorite)
        save(list)
        return favorite
    }

    fun update(updated: Favorite) {
        val list = getAll().map { if (it.id == updated.id) updated else it }
        save(list)
    }

    fun delete(id: String) {
        val list = getAll().filterNot { it.id == id }
        save(list)
    }

    private fun save(list: List<Favorite>) {
        val arr = JSONArray()
        list.forEach { f ->
            val o = JSONObject()
            o.put("id", f.id)
            o.put("type", f.type.name)
            o.put("name", f.name)
            o.put("content", f.content)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "favorites_list"
    }
}
