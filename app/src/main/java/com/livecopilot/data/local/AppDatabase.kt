package com.livecopilot.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livecopilot.data.local.dao.FavoriteDao
import com.livecopilot.data.local.dao.NoteDao
import com.livecopilot.data.local.dao.PreferenceDao
import com.livecopilot.data.local.dao.ProductDao
import com.livecopilot.data.local.dao.ProductImageDao
import com.livecopilot.data.local.entity.FavoriteEntity
import com.livecopilot.data.local.entity.NoteEntity
import com.livecopilot.data.local.entity.PreferenceEntity
import com.livecopilot.data.local.entity.ProductEntity
import com.livecopilot.data.local.entity.ProductImageEntity

@Database(
    entities = [
        NoteEntity::class,
        ProductEntity::class,
        ProductImageEntity::class,
        FavoriteEntity::class,
        PreferenceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun productDao(): ProductDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT,
                        priceCents INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        isAvailable INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_images (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        position INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_images_productId ON product_images(productId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorites (
                        productId TEXT NOT NULL PRIMARY KEY,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS preferences (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
