package com.example.proxypotps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mainTaskId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val totalCount: Int,
    val completedCount: Int
)
