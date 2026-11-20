package com.example.execution

import com.example.tools.ToolCallRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentPhase(val label: String) {
    IDLE("Idle"),
    PLANNING("Planning"),
    RESEARCHING("Researching"),
    READING_FILES("Reading Files"),
    EXECUTING_TOOL("Executing Tools"),
    PROCESSING_RESULTS("Processing Results"),
    AWAITING_APPROVAL("Awaiting Approval"),
    AWAITING_WEB_BRIDGE("DeepSeek Web Bridge"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    ERROR("Error")
}

data class PendingApproval(
    val toolCall: ToolCallRequest,
    val reason: String
)

data class PendingWebBridge(
    val preparedPrompt: String,
    val explanation: String,
    val hasSuggestedLocalTools: Boolean
)

data class ExecutionUiState(
    val isRunning: Boolean = false,
    val phase: AgentPhase = AgentPhase.IDLE,
    val activeToolName: String? = null,
    val currentStep: Int = 0,
    val maxSteps: Int = 10,
    val statusDetail: String = "Ready",
    val pendingApproval: PendingApproval? = null,
    val pendingWebBridge: PendingWebBridge? = null,
    val lastArtifactPath: String? = null,
    val lastHtmlPreview: String? = null
)

class ExecutionMonitor {
    private val _state = MutableStateFlow(ExecutionUiState())
    val state: StateFlow<ExecutionUiState> = _state.asStateFlow()

    fun update(transform: (ExecutionUiState) -> ExecutionUiState) {
        _state.value = transform(_state.value)
    }

    fun resetToIdle() {
        _state.value = ExecutionUiState()
    }
}
