package com.dshremote.app.data

data class SessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val agentRunning: Boolean,
    val cwd: String?,
    val parentSessionId: String?,
    val blank: Boolean,
    val archived: Boolean,
)

enum class ChatRole { USER, ASSISTANT, TOOL, SYSTEM }

data class ChatItem(
    val id: String,
    val role: ChatRole,
    val text: String,
    val label: String? = null,
    val streaming: Boolean = false,
)

data class ApprovalCard(
    val id: String,
    val title: String,
    val detail: String,
)

data class QuestionOption(
    val label: String,
    val description: String? = null,
)

data class QuestionCard(
    val id: String,
    val sessionId: String,
    val callId: String,
    val questionId: String,
    val title: String,
    val detail: String,
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
)

data class PairResult(
    val wsUrl: String,
    val deviceToken: String,
    val deviceId: String,
)
