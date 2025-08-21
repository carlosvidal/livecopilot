package com.livecopilot.data.local

import android.content.Context
import androidx.room.Room

object LocalDatabaseProvider {
    @Volatile
    private var db: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "livecopilot.db"
            )
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { db = it }
        }
    }
}
