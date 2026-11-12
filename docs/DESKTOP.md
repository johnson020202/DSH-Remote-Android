# 桌面端接入

电脑端复用开源插件 [`@linxin666/dsh-remote-bridge`](https://www.npmjs.com/package/@linxin666/dsh-remote-bridge)，不要自己实现一套协议。插件要求 DSH **>= 0.2.1-rc.3**。

## 方式 A：CLI profile

```bash
dsh plugin --profile <profile> add @linxin666/dsh-remote-bridge@0.4.14
```

在 `~/.dsh/profiles/<profile>/cordis.patch.yml` 末尾加入：

```yaml
- id: remote-bridge
  name: '@linxin666/dsh-remote-bridge'
  config:
    relay: false
    lanBind: true
```

重启该 profile 的 DSH。host 日志应出现：

```text
[remote-bridge] protocol v1 listening on /ios/v1
[remote-bridge] ingress gate listening on 127.0.0.1:<gate> -> 127.0.0.1:<web> (only /ios/v1)
[remote-bridge] LAN edge listening on 192.168.x.x:<port> -> gate <gate>
```

## 方式 B：DeepSeek Harness 桌面应用

桌面应用的 `desktop` profile 由 Electron 独占管理，直接运行：

```bash
dsh plugin --profile desktop add @linxin666/dsh-remote-bridge
```

会被拒绝：

```text
error: profile "desktop" is managed exclusively by the Electron application
```

这是应用的保护机制，不是插件本身的问题。可以运行仓库里的脚本：

```bash
./scripts/install-desktop-bridge.sh
```

脚本会：

1. 备份 `~/.dsh/profiles/desktop/package.json` 和 `cordis.patch.yml`；
2. 把桥加入 dependencies 和 `dsh.profile.bundles`；
3. 在 `cordis.patch.yml` 加入 `relay: false`、`lanBind: true` 的覆盖项；
4. 在 profile 目录执行 `pnpm install`。

然后退出并重新打开 DeepSeek Harness。启动后桥会随 host 进程加载。

## 取配对码

桌面侧栏底部应出现「配对 iPhone」入口，点开显示 30 秒一次性二维码。二维码内容是裸 43 字符 token，或 `{token,url}` JSON。

如果侧栏入口不可见，直接从本机回环地址取：

```bash
curl http://127.0.0.1:<port>/remote-bridge/pair/token
```

返回示例：

```json
{
  "token": "<43 字符一次性 token>",
  "expiresAt": 1794472824690,
  "url": "ws://127.0.0.1:<port>/ios/v1?token=...",
  "origin": "http://192.168.1.7:<port>",
  "originSource": "lan",
  "pairingUrl": "ws://192.168.1.7:<port>/ios/v1?token=..."
}
```

`pairingUrl` 已经包含手机应使用的局域网地址；也可以把 `token` 和 `origin` 里的地址分别填进手机配对页。

## 找端口

桌面应用启动后：

```bash
lsof -nP -iTCP -sTCP:LISTEN | grep -i 'DeepSeek'
```

会看到两个端口：

- web/配对码端口：`127.0.0.1:<port>`，局域网边缘也监听在 `<局域网 IP>:<port>`；
- ingress gate：`127.0.0.1:<gate>`，只接收 `/ios/v1` 转发。

## 排错

**应用启动失败，日志里是 `fetch failed`**

先确认没有把桥配置写坏。恢复备份后重启：

```bash
cd ~/.dsh/profiles/desktop
cp package.json.bak-dsh-remote-<时间戳> package.json
cp cordis.patch.yml.bak-dsh-remote-<时间戳> cordis.patch.yml
```

再确认：

- DSH 版本 >= 0.2.1-rc.3；
- `pnpm install` 成功；
- `package.json` 的 `dsh.profile.bundles` 含 `@linxin666/dsh-remote-bridge`；
- `cordis.patch.yml` 的 `id: remote-bridge` 只出现一次。

**手机连不上**

- 手机和电脑在同一 Wi-Fi；
- 电脑防火墙允许该端口；
- 手机填的是电脑局域网 IP，不是 `127.0.0.1`；
- 配对 token 已过期（30 秒），重新取；
- 不要同时开 VPN/代理把局域网流量劫走。

**不要开公网**

保持：

```yaml
config:
  relay: false
  lanBind: true
```

`relay: true` 会把桥接到 `*.dsh-market.com` 公共中继；本项目默认不这样做。
