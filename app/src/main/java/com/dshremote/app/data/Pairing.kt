package com.dshremote.app.data

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class PairingInvite(
    val token: String,
    val wsUrl: String?,
    val label: String?,
)

object Pairing {
    private val rawToken = Regex("^[A-Za-z0-9_-]{20,}$")

    /**
     * Accepts the three shapes the desktop can show: a bare bootstrap token,
     * a `{token,url}` document, or a ws/http URL carrying `?token=`.
     */
    fun parse(raw: String, fallbackHost: String = ""): PairingInvite? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val token = json.optString("token")
                .ifBlank { json.optString("code") }
                .ifBlank { json.optString("invite") }
            if (token.isBlank()) return null
            val url = json.optString("url")
                .ifBlank { json.optString("ws") }
                .ifBlank { json.optString("pairingUrl") }
                .ifBlank { json.optString("server") }
            return PairingInvite(
                token = token,
                wsUrl = if (url.isBlank()) fallbackUrl(fallbackHost) else normalizeWsUrl(url),
                label = json.optString("name").ifBlank { null },
            )
        }

        if (text.startsWith("ws://") || text.startsWith("wss://") ||
            text.startsWith("http://") || text.startsWith("https://") ||
            text.startsWith("dshremote://") || text.startsWith("dsh://")
        ) {
            return fromUrl(text)
        }

        if (rawToken.matches(text)) {
            return PairingInvite(text, fallbackUrl(fallbackHost), null)
        }
        return null
    }

    fun wsUrlForHost(host: String): String = normalizeWsUrl(host)

    private fun fromUrl(text: String): PairingInvite? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val query = parseQuery(uri.rawQuery)
        val token = query["token"] ?: query["code"] ?: query["invite"] ?: return null
        if (uri.scheme == "dshremote" || uri.scheme == "dsh") {
            val target = query["url"] ?: query["ws"] ?: query["server"]
            return PairingInvite(token, target?.let(::normalizeWsUrl), uri.host)
        }
        val ws = when (uri.scheme) {
            "http" -> text.replaceFirst("http://", "ws://")
            "https" -> text.replaceFirst("https://", "wss://")
            else -> text
        }
        return PairingInvite(token, normalizeWsUrl(ws), uri.host)
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val cut = part.indexOf('=')
            if (cut <= 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, cut), "UTF-8")
            val value = URLDecoder.decode(part.substring(cut + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun fallbackUrl(host: String): String? =
        if (host.isBlank()) null else normalizeWsUrl(host)

    private fun normalizeWsUrl(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return value
        if (!value.contains("://")) {
            value = if (value.contains(':')) "ws://$value" else "ws://$value:3080"
        }
        value = when {
            value.startsWith("http://") -> value.replaceFirst("http://", "ws://")
            value.startsWith("https://") -> value.replaceFirst("https://", "wss://")
            else -> value
        }
        val schemeEnd = value.indexOf("://") + 3
        val authorityEnd = listOf(
            value.indexOf('/', schemeEnd),
            value.indexOf('?', schemeEnd),
            value.indexOf('#', schemeEnd),
        ).filter { it >= 0 }.minOrNull() ?: value.length
        val pathEnd = listOf(
            value.indexOf('?', authorityEnd),
            value.indexOf('#', authorityEnd),
        ).filter { it >= 0 }.minOrNull() ?: value.length
        val base = value.substring(0, authorityEnd)
        val path = value.substring(authorityEnd, pathEnd)
        val cleanPath = if (path.isBlank() || path == "/" || !path.startsWith("/ios/v1")) {
            "/ios/v1"
        } else {
            path
        }
        return base + cleanPath
    }
}
