package com.example.proxypotps.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.domain.model.*
import com.example.proxypotps.scheduler.StressTestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SlowLorisTestViewModel @Inject constructor(
    private val stressTestManager: StressTestManager
) : ViewModel() {
    
    private val _testState = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _testState.asStateFlow()
    
    private var currentTestConfig: StressTestConfig? = null
    private var currentTestJob: Job? = null
    private var currentTestId: String? = null
    
    init {
        // Collect test results from StressTestManager
        viewModelScope.launch {
            stressTestManager.testResults.collect { summary ->
                if (currentTestId == summary.testId) {
                    _testState.value = _testState.value.copy(
                        isRunning = summary.status == StressTestStatus.RUNNING,
                        currentRound = summary.currentRound,
                        totalActiveConnections = summary.totalActiveConnections,
                        totalSuccessfulConnections = summary.totalSuccessfulConnections,
                        totalFailedConnections = summary.totalFailedConnections,
                        overallSuccessRate = summary.overallSuccessRate,
                        status = summary.status
                    )
                }
            }
        }
    }
    
    fun configureTest(
        targetUrl: String,
        concurrentConnections: Int,
        connectionTimeoutSeconds: Int,
        requestIntervalSeconds: Double,
        maxRounds: Int
    ) {
        currentTestConfig = StressTestConfig(
            testId = "slow_loris_${System.currentTimeMillis()}",
            testName = "HTTP/HTTPS慢连接压测",
            targetType = StressTestType.SLOW_LORIS,
            targetUrl = targetUrl,
            concurrentConnections = concurrentConnections,
            connectionTimeoutSeconds = connectionTimeoutSeconds,
            requestIntervalSeconds = requestIntervalSeconds,
            maxRounds = maxRounds
        )
    }
    
    fun startStressTest() {
        val config = currentTestConfig ?: return
        
        currentTestId = config.testId
        
        currentTestJob = viewModelScope.launch {
            try {
                stressTestManager.startStressTest(config)
            } catch (e: Exception) {
                // Handle error
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    status = StressTestStatus.FAILED
                )
            }
        }
    }
    
    fun stopStressTest() {
        currentTestId?.let { testId ->
            viewModelScope.launch {
                stressTestManager.stopStressTest(testId)
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    status = StressTestStatus.CANCELLED
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        currentTestJob?.cancel()
        currentTestId?.let { stressTestManager.stopStressTest(it) }
    }
    
    data class TestState(
        val isRunning: Boolean = false,
        val currentRound: Int = 0,
        val totalActiveConnections: Int = 0,
        val totalSuccessfulConnections: Int = 0,
        val totalFailedConnections: Int = 0,
        val overallSuccessRate: Double = 0.0,
        val status: StressTestStatus = StressTestStatus.RUNNING
    )
}