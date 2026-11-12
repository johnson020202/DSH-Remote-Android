# Protocol v1 速查

权威实现：`@linxin666/dsh-remote-bridge` 的 `src/core/protocol.ts`、`src/core/connection.ts`、`src/core/pairing.ts`。这里只列 Android 客户端实际使用的子集。

## 传输

- WebSocket 文本帧，路径 `/ios/v1`。
- 单帧上限 10 MiB。
- 服务端每 20 秒发 `ping`；客户端必须在 10 秒内回 `pong`，否则服务端以 4000 关闭。
- 未配对连接只能调用 `hello`、`pair.begin`、`pair.finish`。

## 帧

```json
{"t":"req","id":1,"m":"session.list","p":{}}
{"t":"res","id":1,"ok":true,"r":{}}
{"t":"res","id":1,"ok":false,"e":{"code":"...","msg":"..."}}
{"t":"ev","seq":12,"m":"session.delta","p":{}}
{"t":"ping","ts":1794472824690}
{"t":"pong","ts":1794472824690}
```

## 配对

1. 本机回环端点 `GET /remote-bridge/pair/token` 签发一次性 bootstrap token（30 秒）。
2. `pair.begin {token}` → `{nonce}`。
3. 客户端生成 P-256 密钥对；对 nonce 做 `SHA256withECDSA`；签名转成 raw `r||s`（64 字节，ieee-p1363）后 base64url。
4. `pair.finish {devicePubKey, signature}` → `{deviceToken, deviceId}`。
5. 之后连接在 `?token=<deviceToken>` 上认证。

设备公钥是 P-256 SPKI DER 的 base64url。

## hello

```json
{
  "proto": 1,
  "minor": 21,
  "client": "dsh-remote-android/1.0",
  "cap": ["session.snapshot.trim/page/since.v1", "questions.v1", "approvals.v1", "delta.v1"]
}
```

响应：

```json
{"proto":1,"minor":21,"server":"...","capabilities":[],"auth":"paired","resume":{}}
```

`auth` 为 `"paired"` 或 `"unpaired"`。

## 方法

### session.list

```json
{"since":0,"cwd":null,"maxRows":200,"after":null}
```

返回 `{mode, token, sessions:[...]}`。session 字段：`id`、`title`、`updatedAt`、`agentRunning`、`cwd`、`parentSessionId`、`origin`、`blank`、`archived`。

### session.snapshot

```json
{"sessionId":"...","sinceSeq":0,"maxBytes":null}
```

返回 `{turns:[...], lastSeq, hasMore?}`。entry 常见形状：

```json
{"seq":3,"time":1794472824690,"type":"assistant/message","data":{"message":{"role":"assistant","content":[{"type":"text","text":"..."}]}}}
```

用户消息为 `user/message`，`data.content` 是 text 块数组；工具调用为 `tool/call`（`data.name` / `data.input`），结果为 `tool/result`。turn 的 `endTime > 0` 表示已结束。

### session.prompt

```json
{"sessionId":"...","text":"...","requestId":"..."}
```

返回 `{accepted:true}`。增量通过 `session.delta` 事件到达；客户端收到后防抖刷新 snapshot。

### session.interrupt

```json
{"sessionId":"..."}
```

返回 `{ok:true}`。

### session.create

转发宿主 `SessionCreateRequest`：

```json
{"workspaceId":null,"cwd":"/path","sessionId":null,"agentPreset":"standard"}
```

### approval.resolve

审批事件：

```json
{"sessionId":"...","id":"...","summary":"...","toolName":"..."}
```

决定：

```json
{"id":"...","decision":"allowed-once"}
{"id":"...","decision":"rejected"}
```

注意不是 `"allow"` / `"deny"`。

### question.answer

提问事件：

```json
{
  "sessionId":"...",
  "callId":"...",
  "state":"pending",
  "questions":[
    {
      "id":"q1",
      "question":"...",
      "header":"...",
      "detail":"...",
      "multiSelect":false,
      "options":[{"label":"A","description":"..."}]
    }
  ]
}
```

回答必须带 `sessionId`、`callId` 和 `answers` 数组；每个 answer 的 `id` 是问题 id，`selected` 是选项 label 数组，自由文本放 `custom`：

```json
{
  "sessionId":"...",
  "callId":"...",
  "answers":[{"id":"q1","selected":["A"]}]
}
```

自由文本：

```json
{"id":"q1","selected":[],"custom":"自己填的答案"}
```

`state == "settled"` 表示该 call 的提问已结束，客户端应移除对应卡片。

## 事件 topic

- `session.delta`
- `approval.request`
- `question.request`
- `ui.request`

客户端可以忽略不认识的事件；不要因为未知 topic 断开连接。
