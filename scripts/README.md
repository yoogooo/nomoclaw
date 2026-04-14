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
  - `TAURI_UPDATER_PUBKEY=<pubkey>`（可选）
  - `DESKTOP_VERSION=yyyy.M.d`（默认当天日期）
  - `DESKTOP_NAME_PREFIX=<name>`（默认 `NomoClaw`）
- 示例:
  - `TARGET_ARCH=arm64 DESKTOP_VERSION=2026.4.14 ./scripts/build-desktop-macos.sh`

### `build-desktop-windows-x64.sh`

- 作用:
  - Windows x64 统一桌面打包脚本（Tauri 壳 + 内嵌 Spring Boot 后端）
  - 执行前端构建、后端打包、`jlink` 运行时裁剪、Tauri 打包（默认 MSI）
- 运行环境:
  - 需在 Windows（Git Bash / MSYS / Cygwin）执行
- 用法:
  - `./scripts/build-desktop-windows-x64.sh`
- 可选参数（环境变量）:
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `TAURI_BUILD_CI=true|false`（默认 `true`）
  - `TAURI_BUNDLES=msi|nsis|msi,nsis`（默认 `msi`）
  - `SKIP_RUST_CHECK=true|false`（默认 `false`）
  - `TAURI_UPDATER_PUBKEY=<pubkey>`（可选）
  - `DESKTOP_VERSION=yyyy.M.d`（默认当天日期）
  - `DESKTOP_NAME_PREFIX=<name>`（默认 `NomoClaw`）
  - `WINDOWS_ICON_FILE=build/windows/NomoClaw.ico`（可选）
- 示例:
  - `TAURI_BUNDLES=msi,nsis DESKTOP_VERSION=2026.4.14 ./scripts/build-desktop-windows-x64.sh`

## 历史脚本（Legacy）

以下脚本仍可用，但建议优先迁移到上面的统一脚本。

### `build-dmg-apple-silicon.sh`

- 作用:
  - 仅用于 Apple Silicon（arm64）macOS 的 DMG 构建
- 用法:
  - `./scripts/build-dmg-apple-silicon.sh`
- 可选参数:
  - `APP_NAME=<name>`（默认 `NomoClaw`）
  - `APP_VERSION=<version>`（默认自动生成并归一化）
  - `ICON_FILE=<path>`（可选）
  - `JAVA_OPTIONS='<opts>'`（默认含 `h2` profile 与 `18080` 端口）
  - `LOG_FILE_PATH=<path>`（默认 `/tmp/NomoClaw.log`）
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `MAC_UI_ELEMENT=true|false`（默认 `false`）

### `build-dmg-macos-intel.sh`

- 作用:
  - macOS Intel（x64）DMG 构建
  - 支持 Apple Silicon 上通过 Rosetta + x64 JDK 21 构建
- 用法:
  - `./scripts/build-dmg-macos-intel.sh`
- 可选参数:
  - `APP_NAME=<name>`（默认 `NomoClaw`）
  - `APP_VERSION=<version>`（默认自动生成并归一化）
  - `ICON_FILE=<path>`（可选）
  - `JAVA_OPTIONS='<opts>'`（默认含 `h2` profile 与 `18080` 端口）
  - `LOG_FILE_PATH=<path>`（默认 `/tmp/NomoClaw.log`）
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `MAC_UI_ELEMENT=true|false`（默认 `false`）

### `build-msi-windows-x64.sh`

- 作用:
  - Windows x64 MSI 构建（非 Tauri 统一脚本，历史版本）
- 运行环境:
  - 需在 Windows（Git Bash / MSYS / Cygwin）执行
- 用法:
  - `./scripts/build-msi-windows-x64.sh`
- 可选参数:
  - `APP_NAME=<name>`（默认 `NomoClaw`）
  - `APP_VERSION=<version>`（默认自动生成并归一化）
  - `ICON_FILE=<path>`（可选）
  - `JAVA_OPTIONS='<opts>'`（默认含 `h2` profile 与 `18080` 端口）
  - `LOG_FILE_PATH=<path>`（默认 `NomoClaw.log`）
  - `SKIP_TESTS=true|false`（默认 `true`）
  - `MAVEN_PROFILE=prod-lite`（默认 `prod-lite`）
  - `SKIP_WEB_BUILD=true|false`（默认 `false`）
  - `WIN_MENU_GROUP=<name>`（默认 `NomoClaw`）
  - `WIN_PER_USER_INSTALL=true|false`（默认 `true`）

## 快速建议

- 新项目或日常构建:
  - macOS 用 `build-desktop-macos.sh`
  - Windows 用 `build-desktop-windows-x64.sh`
- Legacy 脚本适合兼容旧流程，不建议继续扩展新能力。
