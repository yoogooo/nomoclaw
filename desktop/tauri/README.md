# NomoClaw Desktop (Tauri)

## 运行模式

- 默认开发模式：`attach`
  - `pnpm dev` 连接已有后端（默认 `127.0.0.1:18080`）
- 可选开发模式：`spawn`
  - `NOMOCLAW_DEV_ATTACH=0 pnpm dev` 由桌面端启动后端

## 环境变量

- `NOMOCLAW_BACKEND_PORT`：后端端口，默认 `18080`
- `NOMOCLAW_DEV_ATTACH`：`1/true` 代表 attach（默认），`0/false` 代表 spawn
- `NOMOCLAW_BACKEND_START_TIMEOUT_SECONDS`：后端健康检查超时，默认 `30`
- `NOMOCLAW_BACKEND_HEALTH_INTERVAL_MS`：健康检查轮询间隔，默认 `500`
- `NOMOCLAW_JAVA_BIN`：可选，显式指定 Java 可执行文件路径
- `NOMOCLAW_DEV_JAR`：可选，spawn 开发模式显式指定 jar 路径

## 开发

```bash
cd desktop/tauri
pnpm install
pnpm dev
```

## 构建

统一使用根目录脚本：

```bash
./scripts/build-desktop-macos.sh
```
