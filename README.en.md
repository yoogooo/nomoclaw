# NomoClaw Bot

[![GitHub Repo](https://img.shields.io/badge/GitHub-Repo-181717?logo=github)](https://github.com/yoogooo/nomoclaw) [![License](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE) [![Last Commit](https://img.shields.io/github/last-commit/yoogooo/nomoclaw)](https://github.com/yoogooo/nomoclaw/commits) [![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)](https://openjdk.org/projects/jdk/21/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot) [![Rust](https://img.shields.io/badge/Rust-1.8x-000000?logo=rust)](https://www.rust-lang.org/) [![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)](https://www.typescriptlang.org/) [![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs)](https://vuejs.org/) [![Tauri](https://img.shields.io/badge/Tauri-2.x-24C8DB?logo=tauri)](https://tauri.app/)

[中文文档](README.md) | [English Docs](README.en.md)

A local-first multi-agent assistant platform that combines chat, tool execution, approvals, and scheduled jobs in one workflow.

## Desktop Install (Recommended)

- The installer and build commands below are for macOS only.
- Download the desktop installer (`.dmg`) for your platform and install it to `Applications`.
- One-click install and launch, with no extra environment setup required.
- To build installers locally:

```bash
# Apple Silicon
TARGET_ARCH=arm64 ./scripts/build-desktop-macos.sh

# Intel x64
TARGET_ARCH=x64 ./scripts/build-desktop-macos.sh
```

## Functional Capabilities

- Traceable execution loop: planning, step execution, approvals, and final summary with replayable run states instead of a black-box chat flow.
- Multi-agent operations workspace: manage roles, skills, tools, tips, and docs as long-lived execution assets.
- Risk-controlled actions: policy gates for command/file operations with human approval for high-risk steps.
- Automation that can be operated: built-in cron scheduling, execution reports, and channel delivery (Feishu / DingTalk).
- Decoupled model layer: unified multi-provider model management with local model discovery for cost/quality switching.

## Upcoming Feature Roadmap

- Knowledge base integration into the Agent assistant (moving from separate "tips/docs/skills" to a unified retrieval entry), improving response consistency and traceability.
- Workflow integration into the Agent assistant (evolving from chat execution to orchestrated flows), turning high-frequency tasks into reusable processes.
- Multi-Agent collaboration, supporting task decomposition, role-based execution, and coordinated delivery.
- Plugin capabilities, enabling on-demand integration with external systems and business-specific extensions.
- Control center (multi-Agent node status, output summaries, and token usage monitoring), enabling operational observability and cost management.

## Typical Use Cases

- Complex task execution: plan steps first, execute step-by-step, and require human approval for risky actions
- Knowledge reuse: save high-quality outputs as “tips” to speed up future tasks
- Routine automation: collect information on schedule, generate reports, and push notifications to channels

## Runtime & Dev Requirements

- Desktop app (recommended): macOS, install from `.dmg` and run directly (no manual JDK/Node/MySQL setup).
- Source deployment (backend): JDK 21+, MySQL 8+ (H2 file mode is available for dev/test).
- Source deployment (frontend): Node.js 20+, pnpm 10+.

## Quick Start

1. Configure environment variables

```bash
cp .env.example .env
cp web/.env.example web/.env
```

2. Initialize database: run `src/main/resources/db/schema-mysql.sql`

3. Start backend

```bash
./mvnw spring-boot:run
```

Notes:
- Local MySQL development uses Maven `prod-full` by default (activeByDefault), which includes `mysql-connector-j` and `flyway-mysql`.
- Desktop release scripts use `prod-lite` (H2) by default to keep the packaged jar smaller.

Default: `http://127.0.0.1:8080`

4. Start frontend

```bash
cd web
pnpm install
pnpm dev
```

Default: `http://127.0.0.1:5173`

## One-Command Docker Startup (Frontend + Backend)

For users who want a quick local run with minimal setup. This setup uses H2 file mode by default, so MySQL is not required.

1. (Optional) Create a `.env` file at repo root and set variables you need (for example `DASHSCOPE_API_KEY`, `LLM_MODEL_CONFIG_ENCRYPTION_KEY`).
2. Start both services:

```bash
docker compose up --build -d
```

3. Open:
- Frontend: `http://127.0.0.1:5173`
- Backend API: `http://127.0.0.1:8080`

4. Stop:

```bash
docker compose down
```

Notes:
- Backend data is persisted in Docker volume `nomoclaw_data`
- Frontend uses Nginx and proxies `/api` to the backend container
- To reset data: `docker compose down -v`

### Run with H2 (file mode, dev/test)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

- Default H2 file path: `${NOMOCLAW_ROOT_DIR}/data/nomoclaw`
- This mode is intended for development/testing compatibility, not as the recommended production primary database

## 1-Minute Demo

1. Open `http://127.0.0.1:5173`, go to `/`, and send a chat message.
2. Go to `/agents`, save one output as a tip, and preview it.
3. Go to `/cron`, create a scheduled job, and check the execution result panel.

## Useful Commands

Backend:

```bash
./mvnw -DskipTests compile
./mvnw test
```

Frontend:

```bash
cd web
pnpm dev
pnpm build
```

Desktop (macOS, Tauri):

```bash
./scripts/build-desktop-macos.sh
```

- Legacy scripts `scripts/build-dmg-apple-silicon.sh` and `scripts/build-dmg-macos-intel.sh` are kept temporarily as rollback paths.

## Documentation Map

For users:
- API docs (Chinese): [`docs/api.md`](./docs/api.md)
- API docs (English): [`docs/api.en.md`](./docs/api.en.md)

For developers:
- Architecture: [`docs/architecture/ARCHITECTURE.md`](./docs/architecture/ARCHITECTURE.md)
- Frontend architecture: [`docs/architecture/FRONTEND.md`](./docs/architecture/FRONTEND.md)
- Frontend API design: [`docs/architecture/FRONTEND-API.md`](./docs/architecture/FRONTEND-API.md)
- Desktop lifecycle (Tauri): [`docs/architecture/DESKTOP-TAURI-LIFECYCLE.md`](./docs/architecture/DESKTOP-TAURI-LIFECYCLE.md)

For operations and configuration:
- Core config: `src/main/resources/application.yml`
- Common env vars: `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `NOMOCLAW_ROOT_DIR`, `DASHSCOPE_API_KEY`, `LLM_MODEL_CONFIG_ENCRYPTION_KEY`, `FEISHU_APP_ID`, `FEISHU_APP_SECRET`, `DINGTALK_CLIENT_ID`, `DINGTALK_CLIENT_SECRET`, `DINGTALK_ROBOT_CODE`

## Security Notes

- `/api` is local-only by default (`agent.api.local-only-enabled: true`).
- In production, set `LLM_MODEL_CONFIG_ENCRYPTION_KEY` to avoid plaintext model API key storage.

## License

[MIT License](./LICENSE)

## Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) first.
