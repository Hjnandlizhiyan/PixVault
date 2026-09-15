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
    version = 3,
    exportSchema = false
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
    }
}