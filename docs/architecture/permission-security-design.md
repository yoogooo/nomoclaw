# NomoClaw 权限安全机制设计文档

## 1. 设计目标

1. 统一工具调用权限判定流程，输出可解释的 `ALLOW / ASK / DENY` 决策。
2. 在单用户、多 Agent 场景下支持差异化权限策略。
3. 提供不可配置绕过的硬安全边界（hard guard）。
4. 降低重复审批，提升规则复用率。

## 2. 核心原则

1. 安全优先：hard guard 先于所有可配置规则执行。
2. 行为优先：全局按 `deny > ask > allow` 判定。
3. 来源次序：同一行为下按 `session > command > agentSettings > userSettings`。
4. 默认谨慎：未命中规则时，除只读基线工具外默认 `ASK`。
5. 可解释：每次决策返回 `reasonCode / matchedSource / matchedRuleId / hardGuardHit`。

## 3. 权限来源与作用

1. `session`
- 会话内临时规则。
- 不落盘，会话结束后失效。
- 用于“本次对话中持续生效”的短期授权或拒绝。

2. `command`
- 当前请求上下文动态生成的即时规则。
- 不落盘。
- 用于表达“本次命令语义下应强制 ask/deny”的即时约束。

3. `agentSettings`
- Agent 级持久化规则。
- 用于同一用户下不同 Agent 的差异化策略。
- 默认推荐的“记住”作用域。

4. `userSettings`
- 用户全局持久化规则。
- 作为跨 Agent 的兜底默认策略。

## 4. 判定流水线

输入：`ToolPolicyContext`（toolName、toolArgs、agentWorkspacePath、agentUid、agentName、conversationUid、messageUid、stepUid 等）

流程：

1. `hardGuard.evaluate(contextDetails)`
- 命中直接返回 `DENY` 或 `ASK`，后续规则不再执行。

2. 匹配 `DENY` 行为
- 来源顺序：`session -> command -> agentSettings -> userSettings`。
- 任一命中立即返回 `DENY`。

3. 匹配 `ASK` 行为
- 来源顺序同上。
- 任一命中立即返回 `ASK`。

4. 匹配 `ALLOW` 行为
- 来源顺序同上。
- 任一命中立即返回 `ALLOW`。

5. 默认决策
- 内置只读工具白名单：`MemorySearchTool`、`CurrentTimeTool`、`TokenUsageTool`、`FileSearchTool`，默认 `ALLOW`。
- 其他工具默认 `ASK`（`DEFAULT_REQUIRE_APPROVAL`）。

## 5. hard guard 设计

### 5.1 受保护目录（强制 ASK）

- `.git`
- `.nomoclaw`
- `.vscode`

### 5.2 系统关键路径（强制 DENY）

Unix/macOS：

- `/etc`
- `/usr`
- `/bin`
- `/sbin`
- `/var`
- `/System`
- `/Library`
- `/private`
- `/opt`
- `/boot`
- `/dev`
- `/proc`

Windows：

- `<Drive>:\\Windows`
- `<Drive>:\\Program Files`
- `<Drive>:\\Program Files (x86)`
- `<Drive>:\\ProgramData`
- `<Drive>:\\System Volume Information`
- `<Drive>:\\$Recycle.Bin`

### 5.3 绕过防护

1. 路径比较使用候选集：`normalized path + realpath(存在时)`。
2. 目录名命中在 Windows/macOS 按大小写不敏感处理。

## 6. 规则模型

`PermissionRule` 字段：

- `ruleId`
- `source` (`SESSION | COMMAND | AGENT_SETTINGS | USER_SETTINGS`)
- `effect` (`ALLOW | ASK | DENY`)
- `tool`（支持 `*`）
- `action`（支持 `*`）
- `resourceType` (`FILE | COMMAND | BROWSER | CRON | ANY`)
- `pathPattern`（可选）
- `commandPattern`（可选）
- `expiresAt`（可选）
- `enabled`

匹配语义：

1. `tool` 必须匹配（支持通配和前缀）。
2. `action` 非空时必须匹配。
3. `resourceType` 非 `ANY` 时必须匹配。
4. `pathPattern` 存在时，至少一个解析路径命中。
5. `commandPattern` 存在时，正则匹配命令文本。

## 7. 路径规范化与匹配

1. `~` 展开。
2. 绝对化并 `normalize`。
3. 路径存在时加入 `realpath` 作为额外比较路径。
4. Windows/macOS 场景下路径比较支持大小写不敏感。
5. `pathPattern` 支持目录前缀匹配与 glob 匹配。

## 8. 审批与记忆策略

UI 仅暴露 3 个动作：

1. 允许本次（`allow + once`）
2. 允许并记住到当前 Agent（`allow + agent`）
3. 拒绝本次（`deny + once`）

服务端 scope 约束：

- 外部请求只接受 `once / agent` 语义；其他值回退 `once`。

规则生成策略：

1. `CommandTool` 记忆规则优先生成可复用 `pathPattern`。
2. 默认不写完整 `commandPattern`，避免生成不可复用的超具体规则。

## 9. 存储设计

1. `userSettings`
- 路径：`~/.nomoclaw/permission-settings.json`
- 节点：`permission.userSettings.rules[]`

2. `agentSettings`
- 路径：`~/.nomoclaw/agents/{agentName}/settings.local.json`
- 节点：`permission.agentSettings.rules[]`

3. `session`
- 内存存储，按 `conversationUid` 维度管理。
- 会话结束自动失效。

## 10. API 设计

1. 审批决策
- `POST /api/conversations/{conversationUid}/approvals/{stepUid}/decision`
- 请求：`action`, `scope`, `note`
- 响应：`status`, `appliedScope`, `persisted`, `matchedRuleId`

2. 生效规则查询
- `GET /api/permissions/effective?conversationUid=...&agentUid=...`
- 返回：四层规则视图 + hard guard 摘要。

3. 规则更新
- `PUT /api/permissions/agent-settings/{agentUid}`
- `PUT /api/permissions/user-settings`

4. 兼容接口
- `POST /api/conversations/{conversationUid}/approvals/{stepUid}`
- `POST /api/conversations/{conversationUid}/approvals/{stepUid}/reject`

## 11. reasonCode 规范

- `HARD_GUARD_PROTECTED_PATH_ASK`
- `HARD_GUARD_SYSTEM_PATH_DENY`
- `RULE_DENY_MATCHED`
- `RULE_ASK_MATCHED`
- `RULE_ALLOW_MATCHED`
- `DEFAULT_REQUIRE_APPROVAL`
- `INVALID_RULE_IGNORED`

## 12. 可观测性与审计建议

日志字段建议：

- `conversationUid / messageUid / stepUid`
- `toolName / action / pathSummary`
- `decision / reasonCode / matchedSource / matchedRuleId / hardGuardHit`

指标建议：

- `permission_decision_total{effect,reason,source}`
- `permission_approval_latency_ms`
- `permission_persist_total{scope,result}`

## 13. 测试要求

1. 规则优先级矩阵
- 行为优先：`deny > ask > allow`
- 来源优先：`session > command > agent > user`

2. 路径安全
- 大小写、符号链接、相对路径、glob、Windows 路径场景。

3. 工具专项
- 只读工具默认放行。
- `CommandTool` 目标路径提取准确。
- 解析失败场景 fail-safe 到 `ASK`。

4. 端到端
- `STEP_WAITING_APPROVAL` -> 审批决策 -> 继续执行。
- `allow+agent` 后同类操作不重复弹审批。
