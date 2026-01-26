package com.example.proxypotps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(tasks: List<SubTaskEntity>): List<Long>

    @Query(
        "UPDATE tasks SET completedCount = :completedCount, endTime = :endTime, totalDuration = :totalDuration, " +
            "status = :status, successCount = :successCount, failCount = :failCount, timeoutCount = :timeoutCount, " +
            "nodeUsedCount = :nodeUsedCount WHERE id = :taskId"
    )
    suspend fun updateTaskMetrics(
        taskId: Long,
        completedCount: Int,
        endTime: Long?,
        totalDuration: Long?,
        status: String,
        successCount: Int,
        failCount: Int,
        timeoutCount: Int,
        nodeUsedCount: Int
    )

    @Query(
        "UPDATE sub_tasks SET status = :status, nodeName = :nodeName, startTime = :startTime, endTime = :endTime, " +
            "durationMs = :durationMs, retryCount = :retryCount, resultSizeBytes = :resultSizeBytes, " +
            "httpCode = :httpCode, errorMessage = :errorMessage, responsePreview = :responsePreview WHERE id = :subTaskRowId"
    )
    suspend fun updateSubTaskResult(
        subTaskRowId: Long,
        status: String,
        nodeName: String?,
        startTime: Long?,
        endTime: Long?,
        durationMs: Long?,
        retryCount: Int,
        resultSizeBytes: Long,
        httpCode: Int?,
        errorMessage: String?,
        responsePreview: String?
    )

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY startTime DESC")
    fun observeTasksWithSubTasks(): Flow<List<TaskWithSubTasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    fun observeTaskWithSubTasks(taskId: Long): Flow<TaskWithSubTasks?>
}
