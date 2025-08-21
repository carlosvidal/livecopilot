package com.livecopilot.data.migration

import android.content.Context
import com.livecopilot.data.FavoritesManager
import com.livecopilot.data.local.entity.FavoriteGenericEntity
import com.livecopilot.data.repository.FavoriteGenericRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FavoriteMigrations {
    fun migratePrefsToRoomIfPresent(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = FavoritesManager(context)
                val list = manager.getAll()
                if (list.isEmpty()) return@launch
                val repo = FavoriteGenericRepository(context)
                list.forEach { f ->
                    runCatching {
                        repo.upsert(
                            FavoriteGenericEntity(
                                id = f.id,
                                type = f.type.name,
                                name = f.name,
                                content = f.content,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (_: Throwable) { }
        }
    }
}
