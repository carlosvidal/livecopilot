package com.livecopilot.data.migration

import android.content.Context
import com.livecopilot.data.ImageManager
import com.livecopilot.data.local.entity.GalleryImageEntity
import com.livecopilot.data.repository.GalleryImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GalleryMigrations {
    fun migratePrefsToRoomIfPresent(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = ImageManager(context)
                val list = manager.getAllImages()
                if (list.isEmpty()) return@launch
                val repo = GalleryImageRepository(context)
                list.forEach { g ->
                    runCatching {
                        repo.upsert(
                            GalleryImageEntity(
                                id = g.id,
                                name = g.name,
                                imagePath = g.imagePath,
                                description = g.description,
                                dateAdded = g.dateAdded
                            )
                        )
                    }
                }
            } catch (_: Throwable) { }
        }
    }
}
