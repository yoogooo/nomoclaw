import type { AgentCatalogGroup, CronJob } from "@/types/api";
import { getCronLocale, getSortLocale, tr } from "@/i18n";
import cronstrue from "cronstrue";
import "cronstrue/locales/zh_CN";

export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString(getSortLocale());
}

export function formatRelativeTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 60_000) {
    return tr("agents.time.justNow");
  }
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 60) {
    return tr("agents.time.minutesAgo", { count: minutes });
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return tr("agents.time.hoursAgo", { count: hours });
  }
  const days = Math.floor(hours / 24);
  if (days < 30) {
    return tr("agents.time.daysAgo", { count: days });
  }
  const months = Math.floor(days / 30);
  if (months < 12) {
    return tr("agents.time.monthsAgo", { count: months });
  }
  return tr("agents.time.yearsAgo", { count: Math.floor(months / 12) });
}

export function formatConversationListTime(value?: string | null, locale?: string) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "--";
  }
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) {
    return "--";
  }

  const isZh = String(locale || getSortLocale()).toLowerCase().startsWith("zh");
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfYesterday = new Date(startOfToday);
  startOfYesterday.setDate(startOfYesterday.getDate() - 1);
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  if (startOfDate.getTime() === startOfToday.getTime()) {
    return isZh ? "今天" : "1d";
  }
  if (startOfDate.getTime() === startOfYesterday.getTime()) {
    return isZh ? "昨天" : "1d";
  }

  const hours = Math.floor(diffMs / 3_600_000);
  const days = Math.floor(hours / 24);
  if (days < 7) {
    return isZh ? `${days} 天前` : `${days}d`;
  }

  const weeks = Math.floor(days / 7);
  if (days < 30) {
    return isZh ? `${weeks} 周前` : `${weeks}w`;
  }

  const months = Math.floor(days / 30);
  if (days < 365) {
    return isZh ? `${months} 个月前` : `${months}m`;
  }

  const years = Math.floor(days / 365);
  return isZh ? `${years} 年前` : `${years}y`;
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

function toInt(value: string) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function pad2(value: number) {
  return String(value).padStart(2, "0");
}

function formatHm(hour: number, minute: number) {
  return `${pad2(hour)}:${pad2(minute)}`;
}

function weekdaySetFromCronField(field: string) {
  const tokenToNum: Record<string, number> = {
    SUN: 1, MON: 2, TUE: 3, WED: 4, THU: 5, FRI: 6, SAT: 7,
    "1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, "0": 1
  };
  const set = new Set<number>();
  for (const raw of field.split(",")) {
    const token = raw.trim().toUpperCase();
    if (!token) continue;
    if (token.includes("-")) {
      const [startRaw, endRaw] = token.split("-");
      const start = tokenToNum[startRaw];
      const end = tokenToNum[endRaw];
      if (!start || !end) continue;
      if (start <= end) {
        for (let i = start; i <= end; i += 1) set.add(i);
      } else {
        for (let i = start; i <= 7; i += 1) set.add(i);
        for (let i = 1; i <= end; i += 1) set.add(i);
      }
      continue;
    }
    const num = tokenToNum[token];
    if (num) set.add(num);
  }
  return set;
}

function weekdayLabel(day: number, zh = true) {
  if (zh) {
    return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][day - 1] || "周?";
  }
  return ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][day - 1] || "?";
}

function humanizeWeekdays(weekdayField: string, hour: number, minute: number, zh = true) {
  const set = weekdaySetFromCronField(weekdayField);
  if (!set.size) return null;
  const time = formatHm(hour, minute);
  const weekdayOrder = [2, 3, 4, 5, 6, 7, 1];
  const sorted = [...set].sort((a, b) => weekdayOrder.indexOf(a) - weekdayOrder.indexOf(b));
  const isWeekdays = sorted.length === 5 && sorted.join(",") === "2,3,4,5,6";
  const isWeekends = sorted.length === 2 && sorted.join(",") === "1,7";
  const isEveryday = sorted.length === 7;
  if (zh) {
    if (isEveryday) return `每天 ${time}`;
    if (isWeekdays) return `工作日 ${time}`;
    if (isWeekends) return `周末 ${time}`;
    return `每周${sorted.map((day) => weekdayLabel(day, true)).join("、")} ${time}`;
  }
  if (isEveryday) return `Every day ${time}`;
  if (isWeekdays) return `Weekdays ${time}`;
  if (isWeekends) return `Weekends ${time}`;
  return `Weekly ${sorted.map((day) => weekdayLabel(day, false)).join(", ")} ${time}`;
}

function humanizeCronFriendly(expression?: string | null) {
  const normalized = normalizeCronExpression(expression);
  if (!normalized) return null;
  const parts = normalized.split(" ");
  if (parts.length < 6) return null;

  const minuteField = parts[1];
  const hourField = parts[2];
  const dayOfMonthField = parts[3];
  const monthField = parts[4];
  const dayOfWeekField = parts[5];
  const zh = getCronLocale() === "zh_CN";

  // Every N minutes: 0 */N * ? * *
  if (hourField === "*" && dayOfMonthField === "?" && monthField === "*" && dayOfWeekField === "*" && minuteField.startsWith("*/")) {
    const interval = toInt(minuteField.slice(2));
    if (interval && interval > 0) {
      return zh ? `每${interval}分钟` : `Every ${interval} minutes`;
    }
  }

  // Every N hours at minute M: 0 M */N ? * *
  if (dayOfMonthField === "?" && monthField === "*" && dayOfWeekField === "*" && hourField.startsWith("*/")) {
    const minute = toInt(minuteField);
    const interval = toInt(hourField.slice(2));
    if (minute !== null && minute >= 0 && minute <= 59 && interval && interval > 0) {
      return zh ? `每${interval}小时（每小时 ${pad2(minute)} 分）` : `Every ${interval} hours (at minute ${pad2(minute)})`;
    }
  }

  const minute = toInt(minuteField);
  const hour = toInt(hourField);
  if (minute === null || hour === null || minute < 0 || minute > 59 || hour < 0 || hour > 23) return null;

  const dayOfMonthAny = dayOfMonthField === "?" || dayOfMonthField === "*";
  const dayOfWeekAny = dayOfWeekField === "?" || dayOfWeekField === "*";

  // Daily: 0 M H ? * *  /  0 M H * * ?
  if (dayOfMonthAny && monthField === "*" && dayOfWeekAny) {
    return zh ? `每天 ${formatHm(hour, minute)}` : `Every day ${formatHm(hour, minute)}`;
  }

  // Weekly: 0 M H ? * 2,3,4...
  if (dayOfMonthAny && monthField === "*" && !dayOfWeekAny) {
    return humanizeWeekdays(dayOfWeekField, hour, minute, zh);
  }

  // Monthly: 0 M H D * ?
  if (dayOfWeekAny && monthField === "*") {
    const day = toInt(dayOfMonthField);
    if (day !== null && day >= 1 && day <= 31) {
      return zh ? `每月${day}日 ${formatHm(hour, minute)}` : `Day ${day} of every month ${formatHm(hour, minute)}`;
    }
  }

  return null;
}

export function humanizeCronExpression(expression?: string | null) {
  const friendly = humanizeCronFriendly(expression);
  if (friendly) return friendly;
  const fallback = cronToNaturalText(expression);
  if (fallback) {
    const zhTimeOnly = fallback.match(/^在(\d{1,2}:\d{2})$/);
    if (zhTimeOnly) {
      return `每天 ${zhTimeOnly[1]}`;
    }
    return fallback;
  }
  return tr("format.cronFallback");
}

export function fallbackAgentLabel(job: CronJob) {
  return job.agentDisplayName || job.agentName || job.agentUid || tr("format.fallbackNoAgent");
}

export function displayCronJobTitle(job: CronJob) {
  return job.title || job.taskContent || tr("format.fallbackNoName");
}

export function approvalActionSummary(payload: Record<string, any>) {
  const toolName = String(payload.toolName || "").trim() || tr("format.approval.tool");
  const args = payload.toolArgs || {};

  if (toolName === "BrowserTool") {
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

  if (toolName === "CommandTool") {
    return tr("format.approval.command", {
      command: args.command || tr("format.approval.commandMissing"),
      cwd: args.cwd ? `\n${tr("format.approval.workingDirectory")}：${args.cwd}` : ""
    });
  }

  if (toolName === "ReadFileTool" || toolName === "ListFileTool" || toolName === "CreateFileTool" || toolName === "EditFileTool") {
    const action = toolName === "ReadFileTool"
      ? "read"
      : toolName === "ListFileTool"
        ? "list"
        : toolName === "CreateFileTool"
          ? (args.mode === "append" ? "append" : "write")
          : "edit";
    return tr("format.approval.file", {
      action,
      path: args.path || tr("format.approval.pathMissing")
    });
  }

  if (toolName === "CronCreateTool") {
    return tr("format.approval.cron", {
      task: args.task || tr("format.approval.taskMissing"),
      expression: args.expression || tr("format.approval.cronMissing")
    });
  }

  if (toolName === "WebSearchTool") {
    return tr("format.approval.generic", { toolName: `WebSearch: ${args.query || tr("format.approval.action")}` });
  }

  if (toolName === "WebFetchTool") {
    return tr("format.approval.generic", { toolName: `WebFetch: ${args.url || tr("format.approval.urlMissing")}` });
  }

  return tr("format.approval.generic", { toolName });
}
