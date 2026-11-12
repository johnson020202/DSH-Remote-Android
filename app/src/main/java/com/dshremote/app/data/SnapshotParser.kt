package com.dshremote.app.data

import org.json.JSONArray
import org.json.JSONObject

object SnapshotParser {
    fun parse(snapshot: JSONObject): List<ChatItem> {
        val result = mutableListOf<ChatItem>()
        val turns = snapshot.optJSONArray("turns") ?: return result
        var index = 0
        for (turnIndex in 0 until turns.length()) {
            val turn = turns.optJSONObject(turnIndex) ?: continue
            val entries = turn.optJSONArray("entries") ?: continue
            for (entryIndex in 0 until entries.length()) {
                val entry = entries.optJSONObject(entryIndex) ?: continue
                val item = entryToItem(entry, index) ?: continue
                result += item
                index++
            }
        }
        return result
    }

    fun isRunning(snapshot: JSONObject): Boolean {
        val turns = snapshot.optJSONArray("turns") ?: return false
        if (turns.length() == 0) return false
        val last = turns.optJSONObject(turns.length() - 1) ?: return false
        return last.optLong("endTime", 0L) <= 0L
    }

    private fun entryToItem(entry: JSONObject, index: Int): ChatItem? {
        val type = entry.optString("type")
        val data = entry.optJSONObject("data") ?: JSONObject()
        val id = "entry-${entry.optLong("seq", index.toLong())}-$index"
        return when (type) {
            "user/message" -> {
                val actor = entry.optJSONObject("actor")?.optString("kind")
                val source = data.optJSONObject("source")?.optString("kind")
                if (actor != "human" && source != "user") return null
                val text = contentText(data.opt("content"))
                if (text.isBlank()) null else ChatItem(id, ChatRole.USER, text)
            }

            "assistant/message" -> {
                val message = data.optJSONObject("message")
                val text = contentText(message?.opt("content"))
                if (text.isBlank()) null else ChatItem(id, ChatRole.ASSISTANT, text)
            }

            "tool/call" -> {
                val name = data.optString("name")
                    .ifBlank { data.optString("toolName") }
                    .ifBlank { data.optString("tool") }
                    .ifBlank { "工具调用" }
                val input = data.opt("input")
                    ?: data.opt("args")
                    ?: data.opt("arguments")
                val detail = input?.toString().orEmpty()
                ChatItem(
                    id = id,
                    role = ChatRole.TOOL,
                    text = if (detail.isBlank()) name else "$name\n$detail",
                    label = "工具调用",
                )
            }

            "tool/result" -> {
                val text = contentText(data.opt("content"))
                    .ifBlank { data.optString("result") }
                    .ifBlank { data.optString("text") }
                if (text.isBlank()) null else ChatItem(id, ChatRole.TOOL, text, label = "工具结果")
            }

            else -> null
        }
    }

    private fun contentText(value: Any?): String = when (value) {
        is JSONArray -> buildString {
            for (i in 0 until value.length()) {
                val block = value.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }

        is JSONObject -> value.optString("text")
            .ifBlank { value.optString("content") }

        is String -> value
        else -> ""
    }
}
