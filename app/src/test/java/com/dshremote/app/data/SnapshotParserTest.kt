package com.dshremote.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotParserTest {
    private fun snapshot(endTime: Long): JSONObject = JSONObject(
        """
        {
          "turns": [
            {
              "endTime": $endTime,
              "entries": [
                {
                  "seq": 1,
                  "type": "user/message",
                  "actor": {"kind": "human"},
                  "data": {"content": [{"type": "text", "text": "你好"}]}
                },
                {
                  "seq": 2,
                  "type": "assistant/message",
                  "data": {"message": {"content": [{"type": "text", "text": "回复"}]}}
                },
                {
                  "seq": 3,
                  "type": "tool/call",
                  "data": {"name": "shell", "input": {"cmd": "ls"}}
                },
                {
                  "seq": 4,
                  "type": "tool/result",
                  "data": {"content": [{"type": "text", "text": "ok"}]}
                }
              ]
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun parsesMessagesAndToolsInOrder() {
        val items = SnapshotParser.parse(snapshot(123))
        assertEquals(4, items.size)
        assertEquals(ChatRole.USER, items[0].role)
        assertEquals("你好", items[0].text)
        assertEquals(ChatRole.ASSISTANT, items[1].role)
        assertEquals("回复", items[1].text)
        assertEquals(ChatRole.TOOL, items[2].role)
        assertTrue(items[2].text.contains("shell"))
        assertEquals(ChatRole.TOOL, items[3].role)
        assertEquals("ok", items[3].text)
    }

    @Test
    fun runningTurnHasNoEndTime() {
        assertTrue(SnapshotParser.isRunning(snapshot(0)))
        assertFalse(SnapshotParser.isRunning(snapshot(123)))
    }

    @Test
    fun ignoresNonHumanUserMessages() {
        val value = JSONObject(
            """
            {
              "turns": [
                {
                  "endTime": 1,
                  "entries": [
                    {
                      "seq": 1,
                      "type": "user/message",
                      "actor": {"kind": "system"},
                      "data": {"content": [{"type": "text", "text": "注入"}]}
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(SnapshotParser.parse(value).isEmpty())
    }
}
