package com.example.proxypotps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NodeEntity::class, TaskEntity::class, SubTaskEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun taskDao(): TaskDao
}
