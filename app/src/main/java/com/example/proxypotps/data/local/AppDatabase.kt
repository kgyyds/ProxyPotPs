package com.example.proxypotps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NodeEntity::class, TaskEntity::class, SubTaskEntity::class, StressTestEntity::class, StressTestResultEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun taskDao(): TaskDao
    abstract fun stressTestDao(): StressTestDao
}
