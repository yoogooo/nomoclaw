# Codex MCP 接入

本文档说明如何把 OpenAI Codex 作为 MCP server 接入 `nomoclaw`。

## 前提

- 本机已安装 `codex` CLI，并且命令行可直接执行 `codex --help`
- 后端运行环境已经具备 Codex 认证

默认推荐直接复用你本机已经登录的 Codex 账号，不要求必须提供 `OPENAI_API_KEY`。

只要满足下面条件，`nomoclaw` 启动 `codex mcp-server` 时通常就能直接复用登录态：

- 你已经执行过 `codex login`
- `nomoclaw` 后端进程和你登录 Codex 时使用的是同一个系统用户
- 后端进程可以访问该用户下的 Codex 本地认证缓存

OpenAI 官方文档说明，Codex CLI 支持 `ChatGPT 登录` 和 `API key` 两种认证方式，并会缓存登录信息供后续运行复用。

如果你没有复用本机登录态，或者后端运行环境拿不到这份登录态，再考虑配置 `OPENAI_API_KEY` 作为兜底。

示例：

```bash
export OPENAI_API_KEY=your_api_key
```

如果不希望依赖后端进程环境，也可以在创建 MCP server 时通过 `env.OPENAI_API_KEY` 单独传入。

OpenAI 官方文档：

- [Codex MCP](https://developers.openai.com/codex/mcp)
- [Codex CLI Reference](https://developers.openai.com/codex/cli/reference)
- [Codex Authentication](https://developers.openai.com/codex/auth)

## 1. 创建 Codex MCP Server

新增了一个预设接口，默认创建如下配置：

- `transport=STDIO`
- `command=codex`
- `args=["mcp-server"]`
- `serverName=codex`
- `timeoutSeconds=3600`

请求示例：

```bash
curl -X POST http://127.0.0.1:8080/api/mcp/servers/presets/codex \
  -H 'Content-Type: application/json' \
  -d '{
    "serverName": "codex",
    "cwd": "/Users/sun/Develop/IdeaProjects/nomoclaw"
  }'
```

如果你已经在当前系统用户下执行过 `codex login`，通常上面的请求就够了，不需要额外传认证参数。

如果你希望在该 MCP server 进程中显式注入 API Key，再使用下面这种方式：

```bash
curl -X POST http://127.0.0.1:8080/api/mcp/servers/presets/codex \
  -H 'Content-Type: application/json' \
  -d '{
    "serverName": "codex",
    "cwd": "/Users/sun/Develop/IdeaProjects/nomoclaw",
    "env": {
      "OPENAI_API_KEY": "your_api_key"
    }
  }'
```

## 2. 测试连接

先拿到返回值中的 `serverUid`，再执行：

```bash
curl -X POST http://127.0.0.1:8080/api/mcp/servers/{serverUid}/test
```

如果这里失败，优先检查：

- `codex` 命令是否在后端进程 `PATH` 中
- `nomoclaw` 后端进程与 `codex login` 是否为同一个系统用户
- 当前用户下的 Codex 登录态是否仍然有效
- `cwd` 是否存在

如果你本机 `codex` 命令已经能正常使用，但 `nomoclaw` 内测试失败，通常是后端进程读取不到你的 Codex 登录缓存，而不是 MCP 逻辑本身有问题。

## 3. 刷新 Codex 工具快照

```bash
curl -X POST http://127.0.0.1:8080/api/mcp/servers/{serverUid}/refresh-tools
```

刷新完成后，`nomoclaw` 会把 Codex 暴露出的 MCP tools 保存为本地快照。

## 4. 绑定到某个 Agent

查看某个 agent 可配置的 MCP 工具：

```bash
curl http://127.0.0.1:8080/api/agents/{agentUid}/mcp-tools
```

启用其中某个工具：

```bash
curl -X PATCH http://127.0.0.1:8080/api/agents/{agentUid}/mcp-tools/{toolKey} \
  -H 'Content-Type: application/json' \
  -d '{
    "enabled": true
  }'
```

## 5. 推荐的使用方式

建议把 Codex 作为“复杂编码委托器”使用，而不是替代所有内置工具：

- 小范围读文件、查状态：优先内置工具
- 多文件改动、修复测试、重构：优先委托 Codex MCP 工具

这样更符合当前 `nomoclaw` 的 loop 结构，也更容易做权限和审批控制。
