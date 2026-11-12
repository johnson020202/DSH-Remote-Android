#!/usr/bin/env bash
set -euo pipefail

PROFILE="${1:-$HOME/.dsh/profiles/desktop}"
BRIDGE="@linxin666/dsh-remote-bridge"
VERSION="${BRIDGE_VERSION:-0.4.14}"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [[ ! -f "$PROFILE/package.json" ]]; then
  echo "找不到 $PROFILE/package.json" >&2
  echo "用法: $0 [desktop-profile-dir]" >&2
  exit 1
fi
if ! command -v pnpm >/dev/null 2>&1; then
  echo "需要 pnpm；请先安装 pnpm。" >&2
  exit 1
fi

cp -p "$PROFILE/package.json" "$PROFILE/package.json.bak-dsh-remote-$STAMP"
if [[ -f "$PROFILE/cordis.patch.yml" ]]; then
  cp -p "$PROFILE/cordis.patch.yml" "$PROFILE/cordis.patch.yml.bak-dsh-remote-$STAMP"
fi

python3 - "$PROFILE" "$BRIDGE" "$VERSION" <<'PY'
import json
import pathlib
import sys

profile = pathlib.Path(sys.argv[1])
bridge = sys.argv[2]
version = sys.argv[3]

package_path = profile / "package.json"
package = json.loads(package_path.read_text())
dependencies = package.setdefault("dependencies", {})
dependencies[bridge] = version
profile_meta = package.setdefault("dsh", {}).setdefault("profile", {})
bundles = profile_meta.setdefault("bundles", [])
if bridge not in bundles:
    bundles.append(bridge)
package_path.write_text(json.dumps(package, indent=2, ensure_ascii=False) + "\n")

patch_path = profile / "cordis.patch.yml"
text = patch_path.read_text() if patch_path.exists() else ""
if "id: remote-bridge" not in text:
    if text and not text.endswith("\n"):
        text += "\n"
    text += (
        "\n- id: remote-bridge\n"
        f"  name: '{bridge}'\n"
        "  config:\n"
        "    relay: false\n"
        "    lanBind: true\n"
    )
    patch_path.write_text(text)
print("profile updated:", package_path)
PY

cd "$PROFILE"
pnpm install

echo
echo "已接入 $BRIDGE@$VERSION"
echo "备份后缀: .bak-dsh-remote-$STAMP"
echo "现在退出并重新打开 DeepSeek Harness。"
echo "启动后可用下面的命令找配对码端口："
echo "  lsof -nP -iTCP -sTCP:LISTEN | grep -i DeepSeek"
echo "  curl http://127.0.0.1:<port>/remote-bridge/pair/token"
