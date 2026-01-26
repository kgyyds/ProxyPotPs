package com.example.proxypotps.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sub_tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class SubTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val subTaskId: String,
    val url: String,
    val method: String,
    val paramsJson: String,
    val status: String,
    val nodeName: String?,
    val startTime: Long?,
    val endTime: Long?,
    val durationMs: Long?,
    val retryCount: Int,
    val resultSizeBytes: Long,
    val httpCode: Int?,
    val errorMessage: String?,
    val responsePreview: String?
)
