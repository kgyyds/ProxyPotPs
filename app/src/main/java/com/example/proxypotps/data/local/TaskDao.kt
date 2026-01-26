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

    @Query("UPDATE tasks SET completedCount = :completedCount, finishedAt = :finishedAt WHERE id = :taskId")
    suspend fun updateTaskProgress(taskId: Long, completedCount: Int, finishedAt: Long?)

    @Query("UPDATE sub_tasks SET status = :status, nodeName = :nodeName, durationMs = :durationMs, httpCode = :httpCode, responseData = :responseData WHERE id = :subTaskRowId")
    suspend fun updateSubTaskResult(
        subTaskRowId: Long,
        status: String,
        nodeName: String?,
        durationMs: Long?,
        httpCode: Int?,
        responseData: String?
    )

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY startedAt DESC")
    fun observeTasksWithSubTasks(): Flow<List<TaskWithSubTasks>>
}
