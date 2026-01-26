package com.example.proxypotps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY name")
    fun observeNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes")
    suspend fun getNodes(): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Query("DELETE FROM nodes")
    suspend fun clearAll()

    @Query("UPDATE nodes SET status = :status, latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, latencyMs: Long?)
}
