# Cron 运行手册（Runbook）

本文档面向开发与运维，描述 NomoClaw 当前定时任务（Cron）能力、执行链路、配置项、状态流转与排障方法。  
如与旧文档冲突，以当前代码实现为准。

## 1. 能力概览

Cron 子系统提供以下能力：

- 任务创建、更新、暂停、恢复、删除、立即触发
- 执行历史与详情查询
- 执行报告落盘（Markdown）
- 按订阅将执行结果推送到渠道（飞书/钉钉）
- 审批等待与超时收敛
- 服务重启后的调度恢复与活动执行状态收敛

核心组件：

- `CronJobSchedulerService`：Quartz 调度注册、恢复、触发、暂停、删除
- `QuartzCronJob`：Quartz Job 入口
- `CronJobExecutionService`：执行上下文创建、状态对齐、完成收口、报告写入
- `CronExecutionResumeWorker`：轮询活动执行并对齐状态
- `CronNotificationFanoutService`：执行结果渠道推送

## 2. 关键配置

### 2.1 Quartz 启动与恢复

- `nomoclaw.quartz.restore-delay-seconds`（默认 `0`）  
  应用 ready 后延迟恢复 `ACTIVE` cron 任务秒数。

- `spring.quartz.startup-delay-seconds`（默认 `2`）  
  Quartz 启动延迟（见 `QuartzConfig`）。

- `spring.quartz.properties.org.quartz.threadPool.threadCount`（默认 `3`）  
  Quartz 线程池大小。

### 2.2 Cron 执行与收敛

- `nomoclaw.cron.resume-interval-seconds`（默认 `3`）  
  活动执行轮询间隔。

- `nomoclaw.cron.resume-batch-size`（默认 `100`）  
  每轮最多处理的活动执行数量。

- `nomoclaw.cron.approval-timeout-seconds`（默认 `1800`）  
  WAITING_APPROVAL 超时秒数，超时后转 `TIMED_OUT_APPROVAL`。

- `nomoclaw.cron.running-stale-timeout-seconds`（默认 `60`）  
  重启后僵尸运行态收敛阈值：超过该值仍无有效运行上下文则转 `FAILED`。

## 3. 执行状态模型

`agent_cron_job_execution.status` 当前支持：

- `RUNNING`
- `WAITING_APPROVAL`
- `COMPLETED`
- `FAILED`
- `CANCELED`
- `TIMED_OUT_APPROVAL`

状态语义：

- `RUNNING`：任务已提交并处于执行中
- `WAITING_APPROVAL`：命中高风险步骤，等待审批
- `TIMED_OUT_APPROVAL`：等待审批超时，自动终止
- `FAILED`：执行失败，或重启后活动执行无法恢复并超过 stale 阈值

## 4. 端到端执行链路

1. 创建/更新任务写入 `agent_cron_job`  
2. 调度器将任务注册到 Quartz（Job + Trigger）  
3. 触发后由 `QuartzCronJob` 调用 `CronJobExecutionService.executeJob(jobUid)`  
4. 创建或复用本次执行记录（`agent_cron_job_execution`）  
5. 创建对话与消息（如尚未创建），设置执行为 `RUNNING`  
6. `CronExecutionResumeWorker` 周期性对齐活动执行：
   - 消息 `WAITING_APPROVAL` -> 执行置为 `WAITING_APPROVAL`
   - 审批超时 -> 执行置为 `TIMED_OUT_APPROVAL`
   - 消息 `COMPLETED/FAILED/CANCELED` -> 写报告并收口终态
   - 消息丢失或运行上下文丢失且超时 -> 收敛为 `FAILED`
7. 完成后更新：
   - `agent_cron_job_execution`（summary/report/status/finished_time）
   - `agent_cron_job`（last_run_time/next_run_time/last_result）
8. 按订阅推送执行结果到 channel（若配置）

## 5. 重启恢复策略

### 5.1 任务恢复

应用启动后，`CronJobSchedulerService.restoreJobsAfterReady()` 会恢复数据库中 `agent_cron_job.status = ACTIVE` 的任务到 Quartz。

### 5.2 活动执行恢复

`CronExecutionResumeWorker` 持续轮询 `RUNNING/WAITING_APPROVAL` 执行记录并对齐真实消息状态。

### 5.3 僵尸运行态收敛

当出现“执行记录为 RUNNING，但实际进程/上下文已不存在”时：

- 若消息不存在，或消息仍是运行态但 `ExecutionRuntimeStateStore` 无运行上下文
- 且 `updated_time`（或 `started_time`）超过 `nomoclaw.cron.running-stale-timeout-seconds`
- 执行将自动收敛为 `FAILED`

该策略用于避免重启后长期卡在假 `RUNNING`。

## 6. 数据模型速查

### 6.1 `agent_cron_job`

- 用于保存任务定义与最近运行概览。
- 字段细节以 `schema-mysql.sql` 为准。

### 6.2 `agent_cron_job_execution`

- 用于保存每次执行实例、状态流转与结果摘要。
- 字段细节以 `schema-mysql.sql` 为准。

### 6.3 `agent_cron_subscription`

- 用于保存任务到渠道目标的推送订阅关系。
- 字段细节以 `schema-mysql.sql` 为准。

## 7. 常用排障 SQL

以下 SQL 以 MySQL 为例。

### 7.1 查看活动执行（RUNNING/WAITING_APPROVAL）

```sql
SELECT execution_uid, job_uid, status, message_uid, started_time, updated_time
FROM agent_cron_job_execution
WHERE status IN ('RUNNING', 'WAITING_APPROVAL')
ORDER BY started_time DESC
LIMIT 200;
```

### 7.2 定位疑似僵尸 RUNNING（超过 60 秒无更新）

```sql
SELECT execution_uid, job_uid, status, message_uid, started_time, updated_time
FROM agent_cron_job_execution
WHERE status = 'RUNNING'
  AND updated_time < (NOW(3) - INTERVAL 60 SECOND)
ORDER BY updated_time ASC
LIMIT 200;
```

### 7.3 查看某个 job 最近执行历史

```sql
SELECT execution_uid, status, summary, started_time, finished_time, report_path
FROM agent_cron_job_execution
WHERE job_uid = ?
ORDER BY started_time DESC
LIMIT 50;
```

### 7.4 查看审批超时执行

```sql
SELECT execution_uid, job_uid, message_uid, approval_wait_started_time, approval_timeout_seconds, finished_time
FROM agent_cron_job_execution
WHERE status = 'TIMED_OUT_APPROVAL'
ORDER BY finished_time DESC
LIMIT 100;
```

### 7.5 紧急人工收敛僵尸执行（仅应急）

```sql
UPDATE agent_cron_job_execution
SET status = 'FAILED',
    summary = 'Manual failover: stale running execution',
    finished_time = NOW(3),
    updated_time = NOW(3)
WHERE status = 'RUNNING'
  AND updated_time < (NOW(3) - INTERVAL 10 MINUTE);
```

说明：应急 SQL 不会自动同步生成报告文件；建议优先依赖系统自动收敛。

## 8. 运维建议

- 将 `nomoclaw.cron.running-stale-timeout-seconds` 设置为 60~180 秒，避免误判短暂抖动
- 对 `TIMED_OUT_APPROVAL` 与持续 `FAILED` 建立告警
- 定期清理长期历史执行记录与报告文件，避免无限增长
- 对高副作用任务要求幂等，避免人工重试造成重复影响
