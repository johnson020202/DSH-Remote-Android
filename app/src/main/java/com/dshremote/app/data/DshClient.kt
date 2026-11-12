package com.dshremote.app.data

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed class ConnState {
    data object Disconnected : ConnState()
    data object Connecting : ConnState()
    data class Connected(val paired: Boolean) : ConnState()
    data class Failed(val message: String) : ConnState()
}

data class DshEvent(
    val topic: String,
    val payload: JSONObject,
    val seq: Long,
)

class DshException(val code: String, message: String) : Exception("$code: $message")

class DshClient {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val nextId = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DshEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<DshEvent> = _events.asSharedFlow()

    suspend fun connect(wsUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        close()
        val url = buildUrl(wsUrl, token)
        val opened = CompletableDeferred<Unit>()
        _state.value = ConnState.Connecting
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                failPending(t)
                _state.value = ConnState.Failed(t.message ?: "连接失败")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failPending(IllegalStateException("连接已关闭"))
                _state.value = ConnState.Disconnected
            }
        })
        socket = ws
        try {
            withTimeout(15_000) { opened.await() }
        } catch (error: Exception) {
            ws.cancel()
            socket = null
            _state.value = ConnState.Failed(error.message ?: "连接超时")
            throw error
        }

        val hello = request("hello", helloParams())
        _state.value = ConnState.Connected(hello.optString("auth") == "paired")
        hello
    }

    suspend fun pair(wsUrl: String, bootstrapToken: String): PairResult = withContext(Dispatchers.IO) {
        connect(wsUrl, bootstrapToken)
        val begin = request("pair.begin", JSONObject().put("token", bootstrapToken))
        val nonce = begin.optString("nonce")
        if (nonce.isBlank()) throw DshException("pair-nonce", "宿主没有返回配对挑战")

        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public.encoded
        val nonceBytes = Base64.decode(nonce, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(nonceBytes)
        val rawSignature = derToRaw(signer.sign())

        val finish = request(
            "pair.finish",
            JSONObject()
                .put("devicePubKey", base64Url(publicKey))
                .put("signature", base64Url(rawSignature)),
        )
        val deviceToken = finish.optString("deviceToken")
        if (deviceToken.isBlank()) throw DshException("pair-failed", "宿主没有签发设备令牌")
        val result = PairResult(wsUrl, deviceToken, finish.optString("deviceId"))
        close()
        result
    }

    suspend fun request(method: String, params: JSONObject = JSONObject()): JSONObject {
        val ws = socket ?: throw IllegalStateException("尚未连接")
        val id = nextId.incrementAndGet()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val frame = JSONObject().put("t", "req").put("id", id).put("m", method).put("p", params)
        if (!ws.send(frame.toString())) {
            pending.remove(id)
            throw IllegalStateException("发送失败")
        }
        return withTimeout(30_000) { deferred.await() }
    }

    fun close() {
        socket?.close(1000, "client closed")
        socket = null
        failPending(IllegalStateException("连接已关闭"))
        _state.value = ConnState.Disconnected
    }

    private fun handleFrame(text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (frame.optString("t")) {
            "res" -> {
                val id = frame.optLong("id")
                val deferred = pending.remove(id) ?: return
                if (frame.optBoolean("ok")) {
                    deferred.complete(frame.optJSONObject("r") ?: JSONObject())
                } else {
                    val error = frame.optJSONObject("e")
                    deferred.completeExceptionally(
                        DshException(
                            error?.optString("code").orEmpty().ifBlank { "request-failed" },
                            error?.optString("msg").orEmpty().ifBlank { "请求失败" },
                        ),
                    )
                }
            }

            "ev" -> {
                _events.tryEmit(
                    DshEvent(
                        topic = frame.optString("m"),
                        payload = frame.optJSONObject("p") ?: JSONObject(),
                        seq = frame.optLong("seq"),
                    ),
                )
            }

            "ping" -> {
                socket?.send(JSONObject().put("t", "pong").put("ts", frame.optLong("ts")).toString())
            }
        }
    }

    private fun failPending(error: Throwable) {
        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { it.completeExceptionally(error) }
    }

    private fun buildUrl(wsUrl: String, token: String): String {
        val httpUrl = when {
            wsUrl.startsWith("wss://") -> "https://" + wsUrl.removePrefix("wss://")
            wsUrl.startsWith("ws://") -> "http://" + wsUrl.removePrefix("ws://")
            else -> wsUrl
        }
        val parsed = httpUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("连接地址格式不正确")
        val withToken = parsed.newBuilder()
            .removeAllQueryParameters("token")
            .addQueryParameter("token", token)
            .build()
        return withToken.toString()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
    }

    private fun helloParams(): JSONObject = JSONObject()
        .put("proto", 1)
        .put("minor", 21)
        .put("client", "dsh-remote-android/1.0")
        .put(
            "cap",
            JSONArray(
                listOf(
                    "session.snapshot.trim.v1",
                    "session.snapshot.page.v1",
                    "session.snapshot.since.v1",
                    "questions.v1",
                    "questions.list.v1",
                    "approvals.v1",
                    "agents.v1",
                    "workspace.v1",
                    "session.admin.v1",
                    "commands.v1",
                    "delta.v1",
                    "session.list.omit-archived.v1",
                    "usage.v1",
                ),
            ),
        )

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Android's ECDSA provider returns DER; the bridge expects raw r||s. */
    private fun derToRaw(der: ByteArray): ByteArray {
        var offset = 0
        require(der[offset++] == 0x30.toByte()) { "bad DER sequence" }
        var length = der[offset++].toInt() and 0xFF
        if (length and 0x80 != 0) {
            val bytes = length and 0x7F
            length = 0
            repeat(bytes) { length = (length shl 8) or (der[offset++].toInt() and 0xFF) }
        }
        require(der[offset++] == 0x02.toByte()) { "bad DER integer" }
        val rLength = der[offset++].toInt() and 0xFF
        val r = der.copyOfRange(offset, offset + rLength)
        offset += rLength
        require(der[offset++] == 0x02.toByte()) { "bad DER integer" }
        val sLength = der[offset++].toInt() and 0xFF
        val s = der.copyOfRange(offset, offset + sLength)

        fun trim(value: ByteArray): ByteArray {
            var start = 0
            while (start < value.size - 1 && value[start] == 0.toByte()) start++
            return value.copyOfRange(start, value.size)
        }

        val rTrimmed = trim(r)
        val sTrimmed = trim(s)
        require(rTrimmed.size <= 32 && sTrimmed.size <= 32) { "ECDSA component too long" }
        val out = ByteArray(64)
        System.arraycopy(rTrimmed, 0, out, 32 - rTrimmed.size, rTrimmed.size)
        System.arraycopy(sTrimmed, 0, out, 64 - sTrimmed.size, sTrimmed.size)
        return out
    }
}
