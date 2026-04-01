# Contributing

感谢你愿意参与贡献。

## 开发前准备

1. Fork 本仓库并创建分支（建议：`feat/*`、`fix/*`）。
2. 安装环境：JDK 21+、Node.js 20+、pnpm 10+、MySQL 8+。
3. 复制环境变量示例：

```bash
cp .env.example .env
cp web/.env.example web/.env
```

4. 初始化数据库：执行 `src/main/resources/db/schema.sql`。

## 本地开发

### 后端

```bash
./mvnw spring-boot:run
```

### 前端

```bash
cd web
pnpm install
pnpm dev
```

## 提交前检查

### 后端

```bash
./mvnw -DskipTests compile
```

### 前端

```bash
cd web
pnpm build
```

## Pull Request 规范

- PR 标题清晰说明变更目标。
- 描述中包含：变更内容、影响范围、验证方式。
- 若涉及 UI，请附截图或录屏。
- 避免在同一个 PR 混入无关重构。

## Commit 建议

推荐使用 Conventional Commits：

- `feat:` 新功能
- `fix:` 问题修复
- `refactor:` 重构
- `docs:` 文档
- `chore:` 工程改动

## 问题反馈

提交 Issue 时请尽量包含：

- 复现步骤
- 期望结果与实际结果
- 日志/报错信息
- 运行环境（OS、JDK、Node、pnpm）
