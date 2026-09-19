package com.pixvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pixvault.data.db.dao.ImageDao
import com.pixvault.data.db.dao.ImageTagDao
import com.pixvault.data.db.dao.TagDao
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.db.entity.ImageTagEntity
import com.pixvault.data.db.entity.TagEntity

@Database(
    entities = [ImageEntity::class, TagEntity::class, ImageTagEntity::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    abstract fun tagDao(): TagDao
    abstract fun imageTagDao(): ImageTagDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE images ADD COLUMN deletedTime INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE images ADD COLUMN contentHash TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_images_contentHash " +
                        "ON images(contentHash)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE images ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE images ADD COLUMN dateTaken INTEGER")
                db.execSQL("ALTER TABLE images ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE images ADD COLUMN longitude REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE images ADD COLUMN metadataIndexed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
