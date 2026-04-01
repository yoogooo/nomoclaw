# 前端 API 与事件协议

本文档整理当前静态前端与后端 Agent 服务之间的接口约定，覆盖 REST API、SSE 事件、前端调用方式和主要响应结构。

主架构说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)。

## 1. 接口总览

当前前端主要通过以下接口与后端交互：

- `POST /api/conversations`
- `POST /api/conversations/{conversationUid}/messages`
- `POST /api/conversations/{conversationUid}/approvals/{stepUid}`
- `POST /api/conversations/{conversationUid}/cancel`
- `GET /api/conversations/{conversationUid}/events`

说明：

- 本次 workspace / tool / skill / cron 重构没有新增前端管理 API
- agent workspace、tool/skill 授权、cron 持久化目前都在后端与数据库层生效
- 前端控制台仍沿用现有对话接口

## 2. REST API

### 2.1 创建对话

`POST /api/conversations`

请求体：

- 无

响应体：

```json
{
  "conversationUid": "string"
}
```

用途：

- 创建新的前端对话标识
- 后续消息提交、审批、取消、SSE 订阅都基于该 `conversationUid`

### 2.2 提交任务消息

`POST /api/conversations/{conversationUid}/messages`

请求体：

```json
{
  "message": "打开网页并提取标题"
}
```

响应体：

```json
{
  "messageUid": "string",
  "status": "accepted",
  "roundsUsed": 1,
  "maxRounds": 5,
  "stopReason": null
}
```

用途：

- 触发新任务创建
- 启动异步 loop 执行

### 2.3 审批步骤

`POST /api/conversations/{conversationUid}/approvals/{stepUid}`

请求体：

- 无

响应体：

```json
{
  "status": "approved"
}
```

用途：

- 批准一个等待人工确认的高风险步骤
- 任务如处于 `WAITING_APPROVAL`，批准后会恢复执行

### 2.4 取消任务

`POST /api/conversations/{conversationUid}/cancel`

请求体：

- 无

响应体：

```json
{
  "status": "canceled"
}
```

用途：

- 取消当前对话下的最近任务

## 3. SSE 事件流

### 3.1 订阅地址

`GET /api/conversations/{conversationUid}/events`

响应类型：

- `text/event-stream`

前端使用 `EventSource` 订阅，并按事件名分别监听。

### 3.2 当前事件类型

当前已实现事件包括：

- `plan_created`
- `step_started`
- `step_waiting_approval`
- `step_finished`
- `step_failed`
- `loop_limit_reached`
- `task_completed`
- `task_canceled`

这些事件来自后端枚举 `AgentEventType` 的小写映射。

### 3.3 通用事件结构

每条 SSE 的数据体都会序列化为一个 `AgentEvent`，结构大致如下：

```json
{
  "id": "string",
  "eventType": "PLAN_CREATED",
  "conversationUid": "string",
  "messageUid": "string",
  "stepUid": "string|null",
  "timestamp": "2026-03-13T10:00:00Z",
  "payload": {}
}
```

其中 `payload` 会随事件类型变化。

## 4. 主要事件语义

### 4.1 `plan_created`

用途：

- 前端刷新计划区

主要字段：

```json
{
  "message": "plan created",
  "roundIndex": 1,
  "steps": [
    {
      "stepUid": "string",
      "roundIndex": 1,
      "stepIndex": 1,
      "title": "读取文件",
      "toolName": "file_tool",
      "toolArgs": {},
      "riskLevel": "LOW"
    }
  ]
}
```

### 4.2 `step_started`

用途：

- 前端记录某一步开始执行

常见字段：

- `title`
- `toolName`
- `roundIndex`
- `message`

### 4.3 `step_waiting_approval`

用途：

- 前端提示人工确认
- 为该 `stepUid` 生成审批按钮

常见字段：

- `title`
- `toolName`
- `roundIndex`
- `message`

### 4.4 `step_finished`

用途：

- 展示步骤成功结果

常见字段：

- `title`
- `toolName`
- `output`
- `attempt`
- `roundIndex`

### 4.5 `step_failed`

用途：

- 展示失败信息
- 辅助前端判断是否可能进入重试或下一轮重规划

常见字段：

- `title`
- `toolName`
- `errorMessage`
- `retryable`
- `attempt`

### 4.6 `loop_limit_reached`

用途：

- 前端明确展示“达到最大循环次数，任务终止”

常见字段：

- `status`
- `stopReason`
- `roundsUsed`
- `maxRounds`
- `failedStepId`
- `failedReason`

### 4.7 `task_completed`

用途：

- 统一表示任务最终完成或失败后的结束状态

常见字段：

- `status`
- `message`
- `roundsUsed`
- `maxRounds`
- `stopReason`

### 4.8 `task_canceled`

用途：

- 通知前端任务已取消

## 5. 前端调用流程

当前前端主流程如下：

1. 用户点击“创建对话”，调用 `POST /api/conversations`
2. 前端拿到 `conversationUid` 后立即订阅 `/events`
3. 用户提交消息，调用 `POST /messages`
4. 后端异步执行任务，前端通过 SSE 实时刷新：
   - 对话区
   - 计划区
   - 执行日志区
5. 如果出现 `step_waiting_approval`，前端渲染审批按钮
6. 用户点击审批按钮后，调用审批接口并继续等待 SSE

## 6. 当前约束

- 当前接口面向单页静态前端，不含鉴权
- SSE 为长连接模式，断线重连后的事件补偿能力尚未单独实现
- 当前前端只消费现有字段，没有独立的版本协商机制
- `task_completed` 在成功和失败场景都使用同一事件类型，需要前端依赖 `payload.status` 区分
