package com.pixvault

import android.app.Application
import androidx.room.Room
import com.pixvault.data.db.AppDatabase
import com.pixvault.data.embedding.EmbeddingService
import com.pixvault.data.processor.ImageProcessor

class PixVaultApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "pixvault.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    val embeddingService: EmbeddingService by lazy { EmbeddingService(this) }

    val imageProcessor: ImageProcessor by lazy { ImageProcessor(this) }
}
