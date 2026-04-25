import type { AgentCatalogGroup, CronJob } from "@/types/api";
import { getCronLocale, getSortLocale, tr } from "@/i18n";
import cronstrue from "cronstrue";
import "cronstrue/locales/zh_CN";

export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString(getSortLocale());
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

  const timeText = date.toLocaleTimeString(getSortLocale(), {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
  if (startOfDate.getTime() === startOfToday.getTime()) {
    return timeText;
  }
  if (startOfDate.getTime() === startOfYesterday.getTime()) {
    return tr("format.yesterday", { time: timeText });
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
  return `[${new Date().toLocaleTimeString(getSortLocale())}] ${text}`;
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
      return tr("format.statusActive");
    case "PAUSED":
      return tr("format.statusPaused");
    default:
      return status || tr("format.statusUnknown");
  }
}

export function humanizeTimezone(timezone?: string | null) {
  switch ((timezone || "").trim()) {
    case "Asia/Shanghai":
      return tr("format.timezoneShanghai");
    case "UTC":
      return tr("format.timezoneUtc");
    default:
      return timezone || "-";
  }
}

function normalizeCronExpression(expression?: string | null) {
  return (expression || "").trim().replace(/\s+/g, " ");
}

function cronToNaturalText(expression?: string | null) {
  const normalized = normalizeCronExpression(expression);
  if (!normalized) return null;
  try {
    return cronstrue.toString(normalized, {
      locale: getCronLocale(),
      use24HourTimeFormat: true,
      dayOfWeekStartIndexZero: false,
      throwExceptionOnParseError: true
    });
  } catch {
    return null;
  }
}

export function humanizeCronExpression(expression?: string | null) {
  return cronToNaturalText(expression) || tr("format.cronFallback");
}

export function fallbackAgentLabel(job: CronJob) {
  return job.agentDisplayName || job.agentName || job.agentUid || tr("format.fallbackNoAgent");
}

export function displayCronJobTitle(job: CronJob) {
  return job.title || job.taskContent || tr("format.fallbackNoName");
}

export function approvalActionSummary(payload: Record<string, any>) {
  const toolName = payload.toolName || tr("format.approval.tool");
  const args = payload.toolArgs || {};

  if (toolName === "browser_tool" || toolName === "browser_control_tool") {
    const action = args.action || tr("format.approval.action");
    if (action === "open" || action === "navigate") return tr("format.approval.browser.open", { url: args.url || tr("format.approval.urlMissing") });
    if (action === "click") return tr("format.approval.browser.click", { selector: args.selector || tr("format.approval.selectorMissing") });
    if (action === "type") return tr("format.approval.browser.type", {
      selector: args.selector || tr("format.approval.selectorMissing"),
      text: args.text || tr("format.approval.textMissing")
    });
    if (action === "extract_text") return tr("format.approval.browser.extract", { selector: args.selector || "body" });
    if (action === "screenshot") return tr("format.approval.browser.screenshot", { output: args.output || tr("format.approval.defaultTempPath") });
    if (action === "wait_for") return tr("format.approval.browser.waitFor", { selector: args.selector || tr("format.approval.selectorMissing") });
    if (action === "press_key") return tr("format.approval.browser.pressKey", { key: args.key || tr("format.approval.keyMissing") });
    return tr("format.approval.browser.generic", { action });
  }

  if (toolName === "command_tool") {
    return tr("format.approval.command", {
      command: args.command || tr("format.approval.commandMissing"),
      cwd: args.cwd ? `\n${tr("format.approval.workingDirectory")}：${args.cwd}` : ""
    });
  }

  if (toolName === "file_tool" || toolName === "file_io_tool") {
    return tr("format.approval.file", {
      action: args.action || tr("format.approval.action"),
      path: args.path || tr("format.approval.pathMissing")
    });
  }

  if (toolName === "cron_tool") {
    return tr("format.approval.cron", {
      task: args.task || tr("format.approval.taskMissing"),
      expression: args.expression || tr("format.approval.cronMissing")
    });
  }

  return tr("format.approval.generic", { toolName });
}
