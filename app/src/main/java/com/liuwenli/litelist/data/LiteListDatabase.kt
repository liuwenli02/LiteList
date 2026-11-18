package com.liuwenli.litelist.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DatasetGeneration::class, DatasetPointer::class, TaskEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class LiteListDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dataset_pointer ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            }
        }
    }
}

