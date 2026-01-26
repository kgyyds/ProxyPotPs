package com.example.proxypotps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mainTaskId: String,
    val startTime: Long,
    val endTime: Long?,
    val totalDuration: Long?,
    val status: String,
    val successCount: Int,
    val failCount: Int,
    val timeoutCount: Int,
    val nodeUsedCount: Int,
    val totalCount: Int,
    val completedCount: Int
)
