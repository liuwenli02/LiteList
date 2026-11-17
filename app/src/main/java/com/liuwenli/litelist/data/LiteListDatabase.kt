package com.liuwenli.litelist.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DatasetGeneration::class, DatasetPointer::class, TaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LiteListDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
