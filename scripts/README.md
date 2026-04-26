# scripts 目录说明

本文档说明 `scripts/` 下各打包脚本的作用、用法和可选参数。

## 推荐优先使用

### `build-desktop-macos.sh`

- 作用:
  - macOS 统一桌面打包脚本（Tauri 壳 + 内嵌 Spring Boot 后端）
  - 执行前端构建、后端打包、`jlink` 运行时裁剪、Tauri 打包（`.app/.dmg`）
- 用法:
  - `./scripts/build-desktop-macos.sh`
- 可选参数（环境变量）:
  - `TARGET_ARCH=arm64|x64|auto`（默认 `auto`）
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `BUILD_TARGET_DMG=true|false`（默认 `true`）
  - `TAURI_BUILD_CI=true|false`（默认 `true`）
  - `TAURI_UPDATER_PUBKEY=<pubkey>`（可选；用于 updater 验签）
  - `DESKTOP_VERSION=yyyy.M.d`（默认当天日期）
  - `DESKTOP_NAME_PREFIX=<name>`（默认 `NomoClaw`）
- 示例:
  - `TARGET_ARCH=arm64 DESKTOP_VERSION=2026.4.14 ./scripts/build-desktop-macos.sh`

常用平台命令:

```bash
# Apple Silicon (arm64)
TARGET_ARCH=arm64 ./scripts/build-desktop-macos.sh

# Intel (x64)
TARGET_ARCH=x64 ./scripts/build-desktop-macos.sh
```

### `build-desktop-windows-x64.sh`

- 作用:
  - Windows x64 / arm64 统一桌面打包脚本（Tauri 壳 + 内嵌 Spring Boot 后端）
  - 执行前端构建、后端打包、`jlink` 运行时裁剪、Tauri 打包（默认 MSI）
- 运行环境:
  - 需在 Windows（Git Bash / MSYS / Cygwin）执行
- 用法:
  - `./scripts/build-desktop-windows-x64.sh`
- 可选参数（环境变量）:
  - `TARGET_ARCH=x64|arm64`（默认 `x64`）
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `TAURI_BUILD_CI=true|false`（默认 `true`）
  - `TAURI_BUNDLES=msi|nsis|msi,nsis`（默认 `msi`）
  - `SKIP_RUST_CHECK=true|false`（默认 `false`）
  - `TAURI_UPDATER_PUBKEY=<pubkey>`（可选；用于 updater 验签）
  - `DESKTOP_VERSION=x.y.z`（默认 `1.MDD.MINUTES_OF_DAY`，推荐手动指定如 `1.0.0`）
  - `DESKTOP_NAME_PREFIX=<name>`（默认 `NomoClaw`）
  - `WINDOWS_ICON_FILE=<path to .ico>`（可选；未指定时会自动探测 `desktop/tauri/src-tauri/icons/icon.ico`、`build/windows/NomoClaw.ico`）
- 示例:
  - `TAURI_BUNDLES=msi,nsis DESKTOP_VERSION=1.0.0 ./scripts/build-desktop-windows-x64.sh`
  - `TARGET_ARCH=arm64 TAURI_BUNDLES=msi,nsis DESKTOP_VERSION=1.0.0 ./scripts/build-desktop-windows-x64.sh`

## 快速建议

- 新项目或日常构建:
  - macOS 用 `build-desktop-macos.sh`
  - Windows 用 `build-desktop-windows-x64.sh`

## 更新元数据校验

- 脚本：`./scripts/validate-updater-json.sh`
- 默认校验文件：`releases/latest/download/latest.json`
- 可选参数：
  - `--file <path>` 指定文件
  - `--check-urls` 额外校验平台下载地址可达性
  - `--require-signature` 启用签名必填校验（默认不强制）

示例：

```bash
./scripts/validate-updater-json.sh --check-urls
```

## 更新元数据写入工具

- 脚本：`./scripts/update-latest-json.sh`
- 作用：按参数写入 `releases/latest/download/latest.json`，并默认触发校验。
- 说明：三个 `--*-signature` 参数为可选；如果你启用签名链路，再填写对应签名。
- 典型用法：

```bash
./scripts/update-latest-json.sh \
  --version 1.2.3 \
  --darwin-arm64-url "https://example.com/NomoClaw-1.2.3-macos-arm64.app.tar.gz" \
  --darwin-arm64-signature "<sig-arm64>" \
  --darwin-x64-url "https://example.com/NomoClaw-1.2.3-macos-x64.app.tar.gz" \
  --darwin-x64-signature "<sig-x64>" \
  --windows-x64-url "https://example.com/NomoClaw-1.2.3-windows-x64.msi.zip" \
  --windows-x64-signature "<sig-win>" \
  --check-urls
```
