# NomoClaw Bot

一个基于 Spring Boot + Vue 3 的多 Agent 对话与调度系统，包含：
- Chat 对话（SSE 事件流）
- Agent 管理（基础信息、技能、工具、锦囊、配置文件）
- Cron 调度与执行报告
- Channel 子系统（飞书/钉钉入站与通知路由）

## 技术栈

- Backend: Java 21, Spring Boot 4, MyBatis-Plus, MySQL, Quartz
- Frontend: Vue 3, Vite, TypeScript, Pinia, Naive UI

## 目录结构

- `src/`: 后端代码
- `web/`: 前端代码
- `src/main/resources/db/schema.sql`: 初始化表结构与示例数据

## 环境要求

- JDK 21+
- Node.js 20+
- pnpm 10+
- MySQL 8+

## 快速开始

### 1) 配置环境变量

复制示例文件并按需修改：

```bash
cp .env.example .env
cp web/.env.example web/.env
```

### 2) 初始化数据库

执行：

- `src/main/resources/db/schema.sql`

### 3) 启动后端

```bash
./mvnw spring-boot:run
```

默认监听：`http://127.0.0.1:8080`

### 4) 启动前端

```bash
cd web
pnpm install
pnpm dev
```

默认访问：`http://127.0.0.1:5173`

## 常用命令

### 后端

```bash
./mvnw -DskipTests compile
./mvnw test
```

### IM 长连接依赖

无公网 IP 场景下，飞书与钉钉入站通过 Java SDK 长连接接入（飞书 WS + 钉钉 Stream）。
后端依赖已内置在 Maven，无需 Python/Node sidecar。

### 前端

```bash
cd web
pnpm build
```

## CI

仓库内置 GitHub Actions（`.github/workflows/ci.yml`），默认执行：
- 后端编译校验（Java 21）
- 前端安装与构建（pnpm）

## 配置说明

核心配置在 `src/main/resources/application.yml`，可通过环境变量覆盖，例如：

- `MYSQL_URL`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `DASHSCOPE_API_KEY`
- `NOMOCLAW_ROOT_DIR`
- `LLM_MODEL_CONFIG_ENCRYPTION_KEY` (optional)
- `FEISHU_APP_ID`
- `FEISHU_APP_SECRET`
- `DINGTALK_CLIENT_ID`
- `DINGTALK_CLIENT_SECRET`

### 模型 API Key 存储规则

- 配置了 `LLM_MODEL_CONFIG_ENCRYPTION_KEY`：模型配置里的 `apiKey` 会以 AES-GCM 加密后保存（`enc:` 前缀）。
- 未配置 `LLM_MODEL_CONFIG_ENCRYPTION_KEY`：系统仍可启动与保存模型配置，`apiKey` 会以明文封装格式保存（`plain:` 前缀）。
- 为避免接口因历史密文不可解密导致失败，读取模型配置时会做降级处理：单个 provider 解密失败仅返回空 `apiKey`，不会影响 `/api/system/models` 整体返回。
- 安全建议：生产环境务必配置 `LLM_MODEL_CONFIG_ENCRYPTION_KEY`。

## 开源协议

本项目使用 [MIT License](./LICENSE)。

## 贡献

请先阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。
