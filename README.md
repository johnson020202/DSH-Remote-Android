# DSH Remote Android

用 Android 手机通过局域网连接电脑上的 **DeepSeek Harness（DSH）**：同步项目与会话、查看历史、远程发言、中断任务，并处理工具审批和提问。

电脑端不自己造协议，直接复用开源插件 [`@linxin666/dsh-remote-bridge`](https://www.npmjs.com/package/@linxin666/dsh-remote-bridge)（Apache-2.0）。手机端是按该插件 **protocol v1** 写的原生 Kotlin / Jetpack Compose 客户端。

## 特性

- 扫码或手动填邀请码配对，设备身份为 P-256 密钥对
- 会话列表按工作目录分组，显示运行中状态
- 打开会话同步历史消息、工具调用与工具结果
- 发送 prompt，实时刷新助手回复
- 中断正在运行的任务
- 工具审批：允许一次 / 拒绝
- 提问卡片：选项点选、多选提交、自定义文本回答
- 新建会话
- 默认只走局域网，不启用公共 relay

## 架构

```text
Android app (Kotlin/Compose)
        │  ws://<电脑局域网 IP>:<port>/ios/v1
        │  protocol v1: req / res / ev / ping / pong
        ▼
DSH remote bridge (开源插件，跑在 DSH host 进程里)
        │  DSH 自己的会话、模型、工具、审批与提问
        ▼
DeepSeek Harness profile
```

配对流程：

1. 电脑端桥在本机回环地址上签发一次性 bootstrap token（30 秒有效）。
2. 手机用 token 调 `pair.begin`，拿到 nonce。
3. 手机生成 P-256 密钥对，对 nonce 做 `SHA256withECDSA` 签名（raw `r||s`，base64url）。
4. 手机调 `pair.finish`，拿到 `deviceToken` / `deviceId`；之后连接用 `deviceToken` 认证。

## 快速开始

### 1. 电脑端接入桥

要求：DSH **>= 0.2.1-rc.3**，Node.js >= 22.19。

如果使用 CLI profile（非 `desktop`）：

```bash
dsh plugin --profile <profile> add @linxin666/dsh-remote-bridge@0.4.14
```

然后在 profile 的 `cordis.patch.yml` 里加：

```yaml
- id: remote-bridge
  name: '@linxin666/dsh-remote-bridge'
  config:
    relay: false
    lanBind: true
```

如果使用 **DeepSeek Harness 桌面应用**，它的 `desktop` profile 由 Electron 独占管理，`dsh plugin --profile desktop` 会拒绝执行。请按 [`docs/DESKTOP.md`](docs/DESKTOP.md) 手动接入，或运行：

```bash
./scripts/install-desktop-bridge.sh
```

重启 DSH 后，host 日志应出现：

```text
[remote-bridge] protocol v1 listening on /ios/v1
[remote-bridge] LAN edge listening on 192.168.x.x:<port> -> gate <port>
```

### 2. 构建手机端

需要 JDK 21（Gradle 8.13 不支持 Java 26）和 Android SDK 36：

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

安装到手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 配对

- **扫码**：桌面侧栏底部「配对 iPhone」会显示 30 秒一次性二维码；手机配对页点「扫码」。
- **邀请码**：在电脑上取配对 token：

```bash
curl http://127.0.0.1:<port>/remote-bridge/pair/token
```

响应里的 `pairingUrl` 是完整 `ws://…/ios/v1?token=…` 地址；也可以只把 `token` 和电脑局域网地址填进手机配对页。

手机和电脑必须在同一局域网，且电脑防火墙允许该端口。

## 安全边界

- 默认 `relay: false`，不连接 `*.dsh-market.com` 公共中继，也不开公网隧道。
- 桥的配对 token 端点只接受本机回环请求；手机拿到的是 30 秒一次性 token。
- 设备私钥只存在手机 App 内，`deviceToken` 存在 App 私有 SharedPreferences。
- App 允许明文 `ws://` 仅用于局域网；**不要把 DSH 暴露到公网**。
- 电脑端会话与工具权限仍然由 DSH 自己控制；手机只是已配对设备。

## 协议

手机端使用 protocol v1 的子集：`hello`、`pair.begin`、`pair.finish`、`session.list`、`session.snapshot`、`session.prompt`、`session.interrupt`、`session.create`、`approval.resolve`、`question.answer`。字段和事件形状见 [`docs/PROTOCOL.md`](docs/PROTOCOL.md)，权威实现以桥的源码为准。

## 目录

```text
app/                 Android 客户端
  src/main/java/com/dshremote/app/
    data/            协议、配对、快照解析、设备存储
    ui/              Compose 界面与主题
  src/test/          JVM 单元测试
docs/                桌面接入与协议说明
scripts/             桌面 profile 接入脚本
```

## 构建与测试

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew testDebugUnitTest :app:assembleDebug
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:assembleRelease
```

Release 构建目前用 debug 签名，适合侧载；上架应用商店前请换成自己的签名。

## 验证状态

见 [`docs/VERIFICATION.md`](docs/VERIFICATION.md)。那里分别列出「协议链路」「桌面桥」「Android 构建」「手机端端到端」的已验证与未验证项，不把单元测试或配对码生成当成端到端通过。

## 许可

本项目以 MIT 发布。电脑端桥插件 `@linxin666/dsh-remote-bridge` 是 Apache-2.0，版权归其作者；本项目只复用其协议与插件，不复制其实现。
