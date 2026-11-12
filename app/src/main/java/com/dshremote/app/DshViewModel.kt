package com.dshremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dshremote.app.data.ApprovalCard
import com.dshremote.app.data.ChatItem
import com.dshremote.app.data.ChatRole
import com.dshremote.app.data.DeviceStore
import com.dshremote.app.data.DshClient
import com.dshremote.app.data.Pairing
import com.dshremote.app.data.QuestionCard
import com.dshremote.app.data.QuestionOption
import com.dshremote.app.data.SessionSummary
import com.dshremote.app.data.SnapshotParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Screen { PAIR, SESSIONS, CHAT, SETTINGS }

data class UiState(
    val screen: Screen = Screen.PAIR,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val current: SessionSummary? = null,
    val items: List<ChatItem> = emptyList(),
    val approvals: List<ApprovalCard> = emptyList(),
    val questions: List<QuestionCard> = emptyList(),
    val agentRunning: Boolean = false,
    val deviceId: String? = null,
    val wsUrl: String? = null,
    val lastSync: Long = 0L,
)

class DshViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DeviceStore(application)
    private val client = DshClient()
    private val _ui = MutableStateFlow(
        UiState(deviceId = store.deviceId, wsUrl = store.wsUrl),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var currentSessionId: String? = null
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshing = false

    init {
        observeEvents()
        if (store.isPaired) connectSaved()
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun showPair() {
        _ui.update { it.copy(screen = Screen.PAIR, error = null) }
    }

    fun showSettings() {
        _ui.update { it.copy(screen = Screen.SETTINGS) }
    }

    fun backToSessions() {
        stopPolling()
        currentSessionId = null
        _ui.update { it.copy(screen = Screen.SESSIONS, current = null, items = emptyList(), approvals = emptyList(), questions = emptyList()) }
        loadSessions()
    }

    fun reconnect() {
        connectSaved()
    }

    fun connectSaved() {
        val url = store.wsUrl ?: return
        val token = store.deviceToken ?: return
        viewModelScope.launch {
            _ui.update { it.copy(connecting = true, error = null) }
            try {
                val hello = client.connect(url, token)
                val paired = hello.optString("auth") == "paired"
                _ui.update {
                    it.copy(
                        connecting = false,
                        connected = paired,
                        screen = if (paired) Screen.SESSIONS else Screen.PAIR,
                        error = if (paired) null else "设备令牌已失效，请重新配对",
                    )
                }
                if (paired) loadSessions()
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        error = error.message ?: "连接电脑失败",
                    )
                }
            }
        }
    }

    fun pair(host: String, token: String, raw: String) {
        viewModelScope.launch {
            _ui.update { it.copy(connecting = true, error = null) }
            try {
                val invite = if (raw.isNotBlank()) {
                    Pairing.parse(raw, host) ?: Pairing.parse(token, host)
                } else {
                    Pairing.parse(token, host)
                } ?: throw IllegalArgumentException("请扫描配对二维码，或填写邀请码")

                val wsUrl = invite.wsUrl ?: Pairing.wsUrlForHost(host)
                if (wsUrl.isBlank()) throw IllegalArgumentException("请填写电脑的局域网地址")
                if (wsUrl.contains("127.0.0.1") || wsUrl.contains("localhost")) {
                    throw IllegalArgumentException("127.0.0.1 只对电脑本机有效，请填电脑的局域网 IP")
                }
                val result = client.pair(wsUrl, invite.token)
                store.save(result)
                _ui.update { it.copy(deviceId = result.deviceId, wsUrl = result.wsUrl) }

                val hello = client.connect(result.wsUrl, result.deviceToken)
                val paired = hello.optString("auth") == "paired"
                _ui.update {
                    it.copy(
                        connecting = false,
                        connected = paired,
                        screen = if (paired) Screen.SESSIONS else Screen.PAIR,
                        error = if (paired) null else "设备令牌未被宿主接受",
                    )
                }
                if (paired) loadSessions()
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        error = error.message ?: "配对失败",
                    )
                }
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            try {
                val response = client.request("session.list", JSONObject())
                val array = response.optJSONArray("sessions") ?: JSONArray()
                val sessions = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            SessionSummary(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                updatedAt = item.optLong("updatedAt"),
                                agentRunning = item.optBoolean("agentRunning"),
                                cwd = item.optString("cwd").ifBlank { null },
                                parentSessionId = item.optString("parentSessionId").ifBlank { null },
                                blank = item.optBoolean("blank"),
                                archived = item.optBoolean("archived"),
                            ),
                        )
                    }
                }.sortedByDescending { it.updatedAt }
                _ui.update { it.copy(sessions = sessions, connected = true, error = null) }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "读取项目失败") }
            }
        }
    }

    fun openSession(session: SessionSummary) {
        currentSessionId = session.id
        _ui.update {
            it.copy(
                screen = Screen.CHAT,
                current = session,
                items = emptyList(),
                approvals = emptyList(),
                questions = emptyList(),
                agentRunning = session.agentRunning,
                error = null,
            )
        }
        viewModelScope.launch {
            refreshSnapshotNow()
            if (_ui.value.agentRunning) startPolling()
        }
    }

    fun sendPrompt(text: String) {
        val sessionId = currentSessionId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                client.request(
                    "session.prompt",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("text", text)
                        .put("requestId", UUID.randomUUID().toString()),
                )
                _ui.update {
                    it.copy(
                        agentRunning = true,
                        items = it.items + ChatItem(
                            id = "local-${UUID.randomUUID()}",
                            role = ChatRole.USER,
                            text = text,
                        ),
                    )
                }
                startPolling()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "发送失败") }
            }
        }
    }

    fun interrupt() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            try {
                client.request("session.interrupt", JSONObject().put("sessionId", sessionId))
                _ui.update { it.copy(agentRunning = false) }
                refreshSnapshotNow()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "中断失败") }
            }
        }
    }

    fun resolveApproval(id: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                client.request(
                    "approval.resolve",
                    JSONObject()
                        .put("id", id)
                        .put("decision", if (allow) "allowed-once" else "rejected"),
                )
                _ui.update { it.copy(approvals = it.approvals.filterNot { card -> card.id == id }) }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "审批失败") }
            }
        }
    }

    fun answerQuestion(card: QuestionCard, selected: List<String>, custom: String?) {
        viewModelScope.launch {
            try {
                val answer = JSONObject()
                    .put("id", card.questionId)
                    .put("selected", org.json.JSONArray(selected))
                if (!custom.isNullOrBlank()) answer.put("custom", custom)
                client.request(
                    "question.answer",
                    JSONObject()
                        .put("sessionId", card.sessionId)
                        .put("callId", card.callId)
                        .put("answers", org.json.JSONArray().put(answer)),
                )
                _ui.update { state ->
                    state.copy(questions = state.questions.filterNot { it.id == card.id })
                }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "回答失败") }
            }
        }
    }

    fun createSession(cwd: String) {
        if (cwd.isBlank()) return
        viewModelScope.launch {
            try {
                val response = client.request(
                    "session.create",
                    JSONObject().put("cwd", cwd.trim()).put("agentPreset", "standard"),
                )
                val id = response.optString("sessionId")
                loadSessions()
                delay(400)
                _ui.value.sessions.firstOrNull { it.id == id }?.let(::openSession)
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "新建会话失败") }
            }
        }
    }

    fun unpair() {
        store.clear()
        client.close()
        stopPolling()
        currentSessionId = null
        _ui.value = UiState(screen = Screen.PAIR)
    }

    private fun observeEvents() {
        viewModelScope.launch {
            client.events.collect { event ->
                when (event.topic) {
                    "session.delta" -> {
                        if (event.payload.optString("sessionId") == currentSessionId) {
                            val kind = event.payload.optString("kind")
                            if (kind.contains("turn") || kind.contains("tool") ||
                                kind.contains("assistant") || kind.contains("user-message") ||
                                kind.contains("status")
                            ) {
                                scheduleRefresh()
                            }
                        }
                    }

                    "approval.request" -> {
                        val payload = event.payload
                        val id = payload.optString("id")
                        if (id.isNotBlank()) {
                            val tool = payload.optString("toolName")
                            val summary = payload.optString("summary")
                                .ifBlank { tool }
                                .ifBlank { "工具需要授权" }
                            val detail = listOf(summary, tool)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString("\n")
                                .ifBlank { payload.toString(2) }
                            _ui.update { state ->
                                if (state.approvals.any { it.id == id }) state
                                else state.copy(approvals = state.approvals + ApprovalCard(id, summary, detail))
                            }
                        }
                    }

                    "question.request" -> {
                        val payload = event.payload
                        val sessionId = payload.optString("sessionId")
                        val callId = payload.optString("callId")
                        if (sessionId.isBlank() || callId.isBlank()) return@collect
                        val state = payload.optString("state")
                        if (state == "settled") {
                            _ui.update { current ->
                                current.copy(
                                    questions = current.questions.filterNot {
                                        it.sessionId == sessionId && it.callId == callId
                                    },
                                )
                            }
                            return@collect
                        }
                        val questions = payload.optJSONArray("questions") ?: return@collect
                        val cards = buildList {
                            for (i in 0 until questions.length()) {
                                val question = questions.optJSONObject(i) ?: continue
                                val questionId = question.optString("id").ifBlank { "q$i" }
                                val text = question.optString("question")
                                    .ifBlank { question.optString("header") }
                                    .ifBlank { "电脑需要你回答" }
                                val header = question.optString("header")
                                val detail = listOf(
                                    if (header.isNotBlank()) text else "",
                                    question.optString("detail"),
                                ).filter { it.isNotBlank() }.joinToString("\n")
                                val optionArray = question.optJSONArray("options")
                                val options = buildList {
                                    if (optionArray != null) {
                                        for (j in 0 until optionArray.length()) {
                                            val option = optionArray.optJSONObject(j) ?: continue
                                            val label = option.optString("label")
                                            if (label.isNotBlank()) {
                                                add(
                                                    QuestionOption(
                                                        label = label,
                                                        description = option.optString("description")
                                                            .ifBlank { null },
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                add(
                                    QuestionCard(
                                        id = "$sessionId/$callId/$questionId",
                                        sessionId = sessionId,
                                        callId = callId,
                                        questionId = questionId,
                                        title = if (header.isNotBlank()) header else text,
                                        detail = detail.ifBlank { text },
                                        options = options,
                                        multiSelect = question.optBoolean("multiSelect", false),
                                    ),
                                )
                            }
                        }
                        _ui.update { current ->
                            current.copy(
                                questions = current.questions.filterNot {
                                    it.sessionId == sessionId && it.callId == callId
                                } + cards,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(600)
            refreshSnapshotNow()
        }
    }

    private suspend fun refreshSnapshotNow(): Boolean {
        val sessionId = currentSessionId ?: return false
        if (refreshing) return _ui.value.agentRunning
        refreshing = true
        return try {
            val snapshot = client.request("session.snapshot", JSONObject().put("sessionId", sessionId))
            val items = SnapshotParser.parse(snapshot)
            val running = SnapshotParser.isRunning(snapshot)
            _ui.update {
                it.copy(
                    items = items,
                    agentRunning = running,
                    lastSync = System.currentTimeMillis(),
                )
            }
            running
        } catch (error: Exception) {
            _ui.update { it.copy(error = error.message ?: "同步会话失败") }
            false
        } finally {
            refreshing = false
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(1200)
                if (!refreshSnapshotNow()) break
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
