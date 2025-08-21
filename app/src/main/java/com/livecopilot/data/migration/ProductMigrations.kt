package com.livecopilot.data.migration

import android.content.Context
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.ProductEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ProductMigrations {
    /**
     * Migrates products from SharedPreferences (ProductManager) into Room if Room is empty.
     * Non-blocking: runs on Dispatchers.IO.
     */
    fun migratePrefsToRoomIfNeeded(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = LocalDatabaseProvider.getDatabase(context)
                val dao = db.productDao()
                val current = dao.observeAll() // Flow, but we just need a snapshot; fallback to count query
                // Quick check by counting via a query
                val count = db.openHelper.readableDatabase
                    .query("SELECT COUNT(*) FROM products")
                    .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                if (count > 0) return@launch

                val pm = ProductManager(context)
                val prefsProducts = pm.getAllProducts()
                if (prefsProducts.isEmpty()) return@launch

                val entities = prefsProducts.map { it.toEntity() }
                dao.upsertAll(entities) // suspend
            } catch (_: Throwable) {
                // swallow to avoid impacting app startup
            }
        }
    }
}

private fun Product.toEntity(): ProductEntity = ProductEntity(
    id = this.id.ifBlank { java.util.UUID.randomUUID().toString() },
    name = this.name,
    description = this.description.ifBlank { null },
    priceCents = (this.price * 100).toInt(),
    currency = "USD",
    isAvailable = true,
    updatedAt = System.currentTimeMillis()
)
