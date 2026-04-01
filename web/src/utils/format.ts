import type { AgentCatalogGroup, CronJob } from "@/types/api";
import cronstrue from "cronstrue";
import "cronstrue/locales/zh_CN";

export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

export function formatFriendlyDateTime(value?: string | null, emptyFallback = "-") {
  if (!value) return emptyFallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfYesterday = new Date(startOfToday);
  startOfYesterday.setDate(startOfYesterday.getDate() - 1);
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());

  const timeText = date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
  if (startOfDate.getTime() === startOfToday.getTime()) {
    return timeText;
  }
  if (startOfDate.getTime() === startOfYesterday.getTime()) {
    return `昨日 ${timeText}`;
  }

  const dateText = date.getFullYear() === now.getFullYear()
    ? `${date.getMonth() + 1}/${date.getDate()}`
    : `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`;
  return `${dateText} ${timeText}`;
}

export function formatMessageTime(value?: string | null) {
  return formatFriendlyDateTime(value, "");
}

export function appendTimestampPrefix(text: string) {
  return `[${new Date().toLocaleTimeString()}] ${text}`;
}

export function conversationSelectionLabel(
  groups: AgentCatalogGroup[],
  entryType: "agent" | "group",
  agentGroupUid: string,
  agentUid: string
) {
  const group = groups.find((item) => item.agentGroupUid === agentGroupUid);

  if (entryType === "group") {
    return group?.displayName || agentGroupUid;
  }

  const agent = group?.agents.find((item) => item.agentUid === agentUid)
    ?? groups.flatMap((item) => item.agents).find((item) => item.agentUid === agentUid);
  return agent?.displayName || agent?.agentName || agentUid;
}

export function cronStatusLabel(status?: string) {
  switch ((status || "").toUpperCase()) {
    case "ACTIVE":
      return "已启用";
    case "PAUSED":
      return "已暂停";
    default:
      return status || "未知状态";
  }
}

export function humanizeTimezone(timezone?: string | null) {
  switch ((timezone || "").trim()) {
    case "Asia/Shanghai":
      return "中国标准时间";
    case "UTC":
      return "协调世界时";
    default:
      return timezone || "-";
  }
}

const CRON_FALLBACK_TEXT = "按自定义计划执行";

function normalizeCronExpression(expression?: string | null) {
  return (expression || "").trim().replace(/\s+/g, " ");
}

function cronToNaturalText(expression?: string | null) {
  const normalized = normalizeCronExpression(expression);
  if (!normalized) return null;
  try {
    return cronstrue.toString(normalized, {
      locale: "zh_CN",
      use24HourTimeFormat: true,
      throwExceptionOnParseError: true
    });
  } catch {
    return null;
  }
}

export function humanizeCronExpression(expression?: string | null) {
  return cronToNaturalText(expression) || CRON_FALLBACK_TEXT;
}

export function fallbackAgentLabel(job: CronJob) {
  return job.agentDisplayName || job.agentName || job.agentUid || "未绑定 Agent";
}

export function displayCronJobTitle(job: CronJob) {
  return job.title || job.taskContent || "未命名任务";
}

export function approvalActionSummary(payload: Record<string, any>) {
  const toolName = payload.toolName || "工具";
  const args = payload.toolArgs || {};

  if (toolName === "browser_tool" || toolName === "browser_control_tool") {
    const action = args.action || "操作";
    if (action === "open" || action === "navigate") return `即将打开网页：${args.url || "未提供 URL"}`;
    if (action === "click") return `即将点击页面元素：${args.selector || "未提供 selector"}`;
    if (action === "type") return `即将在 ${args.selector || "未提供 selector"} 输入：${args.text || "未提供文本"}`;
    if (action === "extract_text") return `即将读取页面元素文字：${args.selector || "body"}`;
    if (action === "screenshot") return `即将保存页面截图到：${args.output || "默认临时路径"}`;
    if (action === "wait_for") return `即将等待页面元素出现：${args.selector || "未提供 selector"}`;
    if (action === "press_key") return `即将向页面发送按键：${args.key || "未提供 key"}`;
    return `即将执行浏览器动作：${action}`;
  }

  if (toolName === "command_tool") {
    return `即将执行本地命令：${args.command || "未提供命令"}${args.cwd ? `\n工作目录：${args.cwd}` : ""}`;
  }

  if (toolName === "file_tool" || toolName === "file_io_tool") {
    return `即将对本地文件执行 ${args.action || "操作"}：${args.path || "未提供路径"}`;
  }

  if (toolName === "cron_tool") {
    return `即将创建定时任务：${args.task || "未提供任务说明"}\n执行表达式：${args.expression || "未提供 cron"}`;
  }

  return `即将调用工具 ${toolName} 执行一步高风险操作。`;
}
