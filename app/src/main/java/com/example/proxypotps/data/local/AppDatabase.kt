package com.example.proxypotps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NodeEntity::class, TaskEntity::class, SubTaskEntity::class, StressTestEntity::class, StressTestResultEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun taskDao(): TaskDao
    abstract fun stressTestDao(): StressTestDao
}
