# 验证状态

分项记录，不把前一项的通过当成后一项的通过。

## 已验证

### 1. 桥在隔离 profile 中可运行

- DSH 0.2.1-rc.3，隔离 `DSH_HOME=/tmp/dsh-remote-test-home`
- `/ios/v1` 成功挂载
- `pair.begin` → P-256 签名 → `pair.finish` 成功拿到 `deviceToken`
- 重连后 `hello.auth == "paired"`
- `session.list`、`workspace.list`、`presets.list`、`session.create`、`session.snapshot` 正常
- `session.prompt` 后 mock 模型返回，`session.delta` 到达，最终快照含助手文本

### 2. 桌面应用接入桥

- 应用版本 0.2.1-rc.3
- `desktop` profile 手动加入桥后，host 日志出现：
  - `[remote-bridge] protocol v1 listening on /ios/v1`
  - `[remote-bridge] ingress gate listening on 127.0.0.1:<gate> -> 127.0.0.1:<web>`
  - `[remote-bridge] LAN edge listening on 192.168.1.7:<port> -> gate <gate>`
- `GET http://127.0.0.1:<port>/remote-bridge/pair/token` 返回 200，含 43 字符 token、`origin` 与 `pairingUrl`，`originSource` 为 `lan`

### 3. Android 工程

- `assembleDebug` 成功，产物约 19 MB
- JVM 单元测试通过：邀请码/URL 解析、快照消息与工具解析、运行态判断
- `Pairing.normalizeWsUrl` 会丢弃一次性 token query，避免把 bootstrap token 存进设置

## 待验证 / 未验证

- **Android 客户端端到端**：待模拟器/真机完成「配对 → 会话列表 → 打开会话 → 发言 → 看到回复 → 中断」。
- **真实手机**：当前 `adb devices` 无设备，未在真实手机上安装和操作。
- **相机扫码**：未验证。模拟器相机没有有效取景画面时，即使走通扫码后的链路，也不能算相机识别通过。
- **审批 / 提问 UI**：协议字段已按桥源码修正，但还没有在真实事件上点过。
- **公网 relay / 隧道**：未启用，也不在本项目验收范围。
