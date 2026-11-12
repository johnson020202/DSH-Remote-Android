# 验证状态

分项记录，不把前一项的通过当成后一项的通过。验证使用隔离的 DSH `test` profile 和 Android 模拟器；日常 profile 的模型配置与会话数据没有被复制或修改。

## 已验证

### 1. 桥在隔离 profile 中可运行

- DSH 0.2.1-rc.3，`DSH_HOME` 指向临时测试目录，profile 为 `test`。
- `/ios/v1` 成功挂载。
- `pair.begin` → P-256 签名 → `pair.finish` 成功拿到 `deviceToken`。
- 重连后 `hello.auth == "paired"`。
- `session.list`、`workspace.list`、`presets.list`、`session.create`、`session.snapshot` 正常。
- `session.prompt` 后 mock 模型返回，`session.delta` 到达，最终快照包含助手文本。

### 2. 桌面应用接入桥（只走局域网）

- 应用版本 0.2.1-rc.3。
- `~/.dsh/profiles/desktop/cordis.patch.yml` 中明确配置：
  - `relay: false`
  - `lanBind: true`
- 启动日志出现：
  - `[remote-bridge] protocol v1 listening on /ios/v1`
  - `[remote-bridge] ingress gate listening on 127.0.0.1:<gate> -> 127.0.0.1:<web>`
  - `[remote-bridge] LAN edge listening on 192.168.1.7:<port> -> gate <gate>`
  - `[remote-bridge] push: off ...`
  - 没有 relay lease 或 `*.dsh-market.com` 连接。
- `GET http://127.0.0.1:<port>/remote-bridge/pair/token` 返回 200，含 43 字符 token、`origin` 与 `pairingUrl`，`originSource` 为 `lan`。
- 说明：临时 `test` profile 没有写 remote-bridge 配置，插件默认 `relay: true`，测试期间出现过 relay lease；这只是临时测试 profile，桌面交付 profile 已明确关闭 relay，测试 profile 已清理。

### 3. Android 工程

- `JAVA_HOME` 指向 JDK 21 时 `testDebugUnitTest` 与 `assembleDebug` 成功。
- APK 约 19 MB，包名 `com.dshremote.app.debug`。
- 10 个 JVM 单元测试通过：邀请码/URL 解析、快照消息与工具解析、运行态判断。
- `Pairing.normalizeWsUrl` 会丢弃一次性 token query，避免把 bootstrap token 存进设置。
- 注意：Gradle 8.13 不能用默认 JDK 26，构建必须用 JDK 21。

### 4. Android 模拟器端到端

环境：AVD `dsh-test`（Pixel 6，API 36 google_apis arm64-v8a），`emulator-5554`，1080x2400/420dpi；测试宿主 `127.0.0.1:3999`，mock 模型 `127.0.0.1:18080`。

- **配对**：配对页填 `10.0.2.2:3999` 和 43 字符邀请码，点“用邀请码连接”，显示“已连接电脑”。
- **会话同步**：会话列表按 `/tmp/dsh-remote-test-workspace` 分组，显示 2 条会话；打开会话后用户消息、助手消息与工具结果正确渲染。
- **手机操作落到电脑**：手机发送 `phone-e2e-ok-`，电脑侧 session journal 写入 user/message，mock 收到 `POST /v1/chat/completions` 并返回 200；手机端随后显示助手回复 `远程链路已连通：phone-e2e-ok-`。这不是只改手机本地 UI。
- **中断**：手机发送 `sleep-25`，处理中时点“中断”；手机端退出处理中且没有完整助手回复，电脑侧 journal 记录该轮 `reason={"kind":"aborted","reason":{"kind":"user"}}`。
- **新建会话**：会话列表点“新建会话”，填 `/tmp/dsh-remote-test-workspace`，点“创建”；手机自动打开“未命名会话”，电脑侧生成 `session-ec6686e3-b3af-47b7-9ec3-cc5714039621.json`。
- **截图**：[`docs/screenshots/emulator-e2e.png`](screenshots/emulator-e2e.png)。

排障记录：第一次手机发言时测试 mock 进程已被宿主回收，电脑侧 turn 记录为 `error` / `ECONNREFUSED 127.0.0.1:18080`，没有助手消息；重启 mock 后重发成功。这个失败不是客户端解析或协议问题。

## 待验证 / 未验证

- **真实手机**：`adb devices` 没有物理设备，只在模拟器上完成上述端到端；真机安装、相机和真实局域网未验证。
- **相机扫码**：模拟器相机没有有效取景画面，未完成真实二维码识别；只验证了邀请码配对。扫码后的链路可以参考邀请码结果，但不能算相机识别通过。
- **审批 / 提问 UI**：协议字段已按桥源码修正（审批决定 `allowed-once` / `rejected`，回答 `answers[{id,selected,custom?}]`），但还没有在真实 `approval.request` / `question.request` 事件上点过。
- **App 断网重连 UI**：协议层重连 `hello.auth == "paired"` 已验证，App 的“重新连接”按钮和网络切换场景未单独验证。
- **Release APK / 商店签名**：未构建 release，也未做商店签名；当前交付物是 debug APK，适合侧载。
- **公网 relay / 隧道**：未启用，不在验收范围；桌面 profile 明确 `relay: false`。
