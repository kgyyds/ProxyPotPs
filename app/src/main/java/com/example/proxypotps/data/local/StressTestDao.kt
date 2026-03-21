package com.example.proxypotps.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StressTestDao {
    @Query("SELECT * FROM stress_tests ORDER BY created_at DESC")
    fun getAllStressTests(): Flow<List<StressTestEntity>>
    
    @Query("SELECT * FROM stress_tests WHERE test_id = :testId")
    fun getStressTest(testId: String): Flow<StressTestEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStressTest(test: StressTestEntity): String
    
    @Update
    suspend fun updateStressTest(test: StressTestEntity)
    
    @Query("DELETE FROM stress_tests WHERE test_id = :testId")
    suspend fun deleteStressTest(testId: String)
    
    @Query("SELECT * FROM stress_test_results WHERE test_id = :testId ORDER BY round, node_name")
    fun getStressTestResults(testId: String): Flow<List<StressTestResultEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStressTestResult(result: StressTestResultEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStressTestResults(results: List<StressTestResultEntity>): List<Long>
    
    @Query("DELETE FROM stress_test_results WHERE test_id = :testId")
    suspend fun deleteStressTestResults(testId: String)
    
    @Query("DELETE FROM stress_test_results")
    suspend fun clearAllResults()
}