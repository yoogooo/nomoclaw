# Tauri Desktop Lifecycle (macOS)

本文档描述 `nomoclaw` 桌面版在 macOS 下的当前生命周期设计：打包布局、启动流程、进程托管、退出流程与验收标准。

## 1. Design Goals

- 桌面壳由 Tauri 承担，业务后端由 Spring Boot 承担。
- 发布包内置 `nomoclaw.jar + runtime`，终端用户机器无需预装 Java（构建机仍需要 JDK）。
- 后端进程必须受托管，应用退出后不保留后台 Java 进程。
- 主窗口支持“红叉隐藏、Dock 重新唤起”。

## 2. Bundle Layout

入口脚本：`scripts/build-desktop-macos.sh`

构建流程：
- Build web and sync to `src/main/resources/static/nomoclaw`
- Build Spring Boot fat jar
- Build jlink runtime
- Copy runtime assets into `desktop/tauri/src-tauri/resources/backend`
- Build desktop bundle via `tauri build`

安装后关键路径：
- `Contents/Resources/resources/backend/nomoclaw.jar`
- `Contents/Resources/resources/backend/runtime/bin/java`

## 3. Runtime Model

实现文件：`desktop/tauri/src-tauri/src/main.rs`

### 3.1 Single Instance

- 锁文件：`~/Library/Application Support/ai.nomoclaw.desktop/app.lock`
- 启动时尝试创建锁；存在且 PID 存活则拒绝第二实例。
- 退出时释放锁文件。

### 3.2 Backend Process Ownership

- 后端由主进程启动并托管。
- 启动参数固定包含：
  - `-Dspring.profiles.active=h2`
  - `-Dnomoclaw.desktop.open-browser-on-startup=false`
  - `-Dserver.shutdown=immediate`
  - `-Dspring.lifecycle.timeout-per-shutdown-phase=2s`
- 端口策略：默认 `18080`，冲突时自动选择空闲端口。
- 健康检查：轮询 `GET /api/system/config`，成功后切换到业务页面。

### 3.3 Process Group Tracking (macOS/Unix)

- 后端以独立 process group 启动。
- 持久化文件：`~/Library/Application Support/ai.nomoclaw.desktop/backend.pgid`
- 启动前清理陈旧 PGID 并移除记录文件。

## 4. Window & UX Lifecycle

### 4.1 Startup UX

- App 启动后主窗口立即显示（加载页面先可见）。
- 后端 ready 后窗口跳转到实际 URL：`http://127.0.0.1:{port}/nomoclaw/#/`。

### 4.2 Close/Show Behavior

- 红叉关闭：拦截为 `hide`，不退出应用。
- Dock 点击应用图标：触发 reopen，恢复主窗口。
- 托盘菜单：
  - 显示主窗口
  - 重启后端
  - 退出

## 5. Shutdown Sequence

退出入口：
- 托盘“退出”
- 系统退出事件 `RunEvent::ExitRequested`

统一顺序：
1. 拦截退出事件。
2. 停止后端子进程（优雅停止 + 强制回收）。
3. 终止 process group 并等待退出确认（带超时）。
4. 清理 PGID 与实例锁。
5. 主进程 `exit(0)`。

## 6. Acceptance Criteria

### 6.1 Startup

- 应用启动后主窗口可立即看到加载态。
- 后端就绪后自动进入业务页面。

### 6.2 Reopen

- 点击红叉后应用不退出。
- 再次点击 Dock 图标可恢复主窗口。

### 6.3 Shutdown

执行退出后检查：

```bash
ps -Ao pid,ppid,command | rg 'NomoClaw.app|nomoclaw.jar'
```

验收标准：不应存在 `nomoclaw.jar` 进程。
