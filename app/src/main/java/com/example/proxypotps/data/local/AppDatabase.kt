package com.example.proxypotps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NodeEntity::class, StressTestEntity::class, StressTestResultEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun stressTestDao(): StressTestDao
}
