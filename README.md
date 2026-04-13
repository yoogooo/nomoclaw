# NomoClaw Bot

一个本地优先的多 Agent 助手平台：把对话、工具执行、审批和定时任务放进同一个工作流，让任务更稳定地落地。

## 桌面版安装（推荐）

- 以下安装包与打包命令仅适用于 macOS。
- 直接下载对应平台的桌面安装包（`.dmg`），双击安装到 `Applications`。
- 安装后可一键启动，无需手动配置额外环境。
- 如需自行打包：

```bash
# Apple Silicon
TARGET_ARCH=arm64 ./scripts/build-desktop-macos.sh

# Intel x64
TARGET_ARCH=x64 ./scripts/build-desktop-macos.sh
```

## 功能特性

- 可追踪的执行闭环：从规划、步骤执行、审批到总结，全程带事件流与状态回放，不是“只会聊天”的黑盒。
- 多 Agent 协作工作台：按角色管理 Agent 的技能、工具、锦囊和文档，支持持续沉淀可复用的执行能力。
- 高风险操作可控：命令与文件写入具备策略门禁，支持人工确认，降低误操作成本。
- 自动化任务可运营：内置 Cron 调度、执行报告与渠道通知（飞书/钉钉），适合长期例行任务。
- 模型层解耦：统一管理多 Provider 与模型，支持本地模型发现，便于按场景切换成本与效果。

## 典型使用场景

- 复杂任务拆解与执行：先规划步骤，再按步骤执行，必要时人工审批
- 经验沉淀与复用：将高质量回答保存为“锦囊”，用于后续任务提速
- 自动化例行工作：定时抓取信息、生成简报并推送到通知渠道

## 运行与开发环境

- 桌面版（推荐）：macOS，直接安装 `.dmg` 即可运行（不需要手动安装 JDK/Node/MySQL）。
- 源码部署（后端）：JDK 21+，MySQL 8+（开发/测试可使用 H2 file 模式）。
- 源码部署（前端）：Node.js 20+，pnpm 10+。

## 快速开始

1. 配置环境变量

```bash
cp .env.example .env
cp web/.env.example web/.env
```

2. 初始化数据库：执行 `src/main/resources/db/schema-mysql.sql`

3. 启动后端

```bash
./mvnw spring-boot:run
```

默认：`http://127.0.0.1:8080`

4. 启动前端

```bash
cd web
pnpm install
pnpm dev
```

默认：`http://127.0.0.1:5173`

### 使用 H2（file 模式，开发/测试）

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

- H2 数据文件默认位于：`${NOMOCLAW_ROOT_DIR}/data/nomoclaw`
- 本模式用于开发/测试兼容验证，不作为生产主库建议

## 1 分钟体验

1. 打开 `http://127.0.0.1:5173`，进入 `/` 发起一条聊天消息。
2. 进入 `/agents`，为当前 Agent 保存一条锦囊并查看内容。
3. 进入 `/cron`，创建一个定时任务并查看执行结果面板。

## 常用命令

后端：

```bash
./mvnw -DskipTests compile
./mvnw test
```

前端：

```bash
cd web
pnpm dev
pnpm build
```

桌面版（macOS, Tauri）：

```bash
./scripts/build-desktop-macos.sh
```

- 旧脚本 `scripts/build-dmg-apple-silicon.sh` / `scripts/build-dmg-macos-intel.sh` 进入 legacy 维护期，仅作为回滚路径

## 文档导航

使用者：
- 接口文档（中文）：[`docs/api.md`](./docs/api.md)
- API docs (English): [`docs/api.en.md`](./docs/api.en.md)

开发者：
- 架构文档：[`docs/architecture/ARCHITECTURE.md`](./docs/architecture/ARCHITECTURE.md)
- 前端架构：[`docs/architecture/FRONTEND.md`](./docs/architecture/FRONTEND.md)
- 前端 API 设计：[`docs/architecture/FRONTEND-API.md`](./docs/architecture/FRONTEND-API.md)
- 桌面端生命周期（Tauri）：[`docs/architecture/DESKTOP-TAURI-LIFECYCLE.md`](./docs/architecture/DESKTOP-TAURI-LIFECYCLE.md)

运维与配置：
- 核心配置：`src/main/resources/application.yml`
- 常用环境变量：`MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`、`NOMOCLAW_ROOT_DIR`、`DASHSCOPE_API_KEY`、`LLM_MODEL_CONFIG_ENCRYPTION_KEY`、`FEISHU_APP_ID`、`FEISHU_APP_SECRET`、`DINGTALK_CLIENT_ID`、`DINGTALK_CLIENT_SECRET`、`DINGTALK_ROBOT_CODE`

## 安全说明

- 默认仅允许本机访问 `/api`（`agent.api.local-only-enabled: true`）。
- 生产环境建议配置 `LLM_MODEL_CONFIG_ENCRYPTION_KEY`，避免模型 API Key 明文存储。

## 许可证

[MIT License](./LICENSE)

## 贡献

请先阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。
