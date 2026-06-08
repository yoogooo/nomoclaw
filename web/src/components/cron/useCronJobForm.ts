import { computed, reactive, ref, watch } from "vue";
import { getSortLocale } from "@/i18n";
import { humanizeCronExpression } from "@/utils/format";
import type {
  CronJobFormDependencies,
  CronJobFormInitInput,
  CronJobFormOption,
  CronJobFormSubmitPayload,
  CronJobFormValue
} from "./cronJobForm";

function createDefaultRunAt() {
  const value = new Date();
  value.setMinutes(value.getMinutes() + 10);
  value.setSeconds(0, 0);
  return {
    date: formatDateValue(value),
    time: formatTimeValue(value)
  };
}

function createEmptyForm(fallbackTimezone: string): CronJobFormValue {
  const defaultRunAt = createDefaultRunAt();
  return {
    agentUid: "",
    title: "",
    taskContent: "",
    status: "ACTIVE",
    timezone: fallbackTimezone,
    modelProvider: "",
    modelName: "",
    executionType: "recurring",
    recurringMode: "day",
    time: "08:30",
    weekdays: ["2"],
    monthlyDay: 1,
    monthField: "*",
    minuteInterval: 30,
    hourInterval: 1,
    hourlyMinute: 0,
    onceDate: defaultRunAt.date,
    onceTime: defaultRunAt.time,
    endAtLocal: ""
  };
}

function assignForm(target: CronJobFormValue, next: CronJobFormValue) {
  Object.assign(target, next);
}

export function useCronJobForm(deps: CronJobFormDependencies) {
  const form = reactive(createEmptyForm(deps.fallbackTimezone));
  const modelConfig = ref<{ providers: Array<{ id: string; name: string; models: Array<{ id: string; name: string }> }> }>({ providers: [] });
  const modelConfigLoaded = ref(false);

  const weekdayOptions = computed(() => [
    { label: deps.t("cron.weekday.monday"), short: deps.t("cron.weekdayShort.monday"), value: "2" },
    { label: deps.t("cron.weekday.tuesday"), short: deps.t("cron.weekdayShort.tuesday"), value: "3" },
    { label: deps.t("cron.weekday.wednesday"), short: deps.t("cron.weekdayShort.wednesday"), value: "4" },
    { label: deps.t("cron.weekday.thursday"), short: deps.t("cron.weekdayShort.thursday"), value: "5" },
    { label: deps.t("cron.weekday.friday"), short: deps.t("cron.weekdayShort.friday"), value: "6" },
    { label: deps.t("cron.weekday.saturday"), short: deps.t("cron.weekdayShort.saturday"), value: "7" },
    { label: deps.t("cron.weekday.sunday"), short: deps.t("cron.weekdayShort.sunday"), value: "1" }
  ]);
  const recurringModeOptions = computed<Array<{ value: CronJobFormValue["recurringMode"]; label: string }>>(() => [
    { value: "minute", label: deps.t("cron.recurringMode.minute") },
    { value: "hour", label: deps.t("cron.recurringMode.hour") },
    { value: "day", label: deps.t("cron.recurringMode.day") },
    { value: "week", label: deps.t("cron.recurringMode.week") },
    { value: "month", label: deps.t("cron.recurringMode.month") }
  ]);

  const providerOptions = computed<CronJobFormOption[]>(() =>
    modelConfig.value.providers
      .filter((provider) => String(provider.id || "").trim())
      .map((provider) => ({ label: provider.name || provider.id, value: provider.id }))
  );

  const modelOptions = computed<CronJobFormOption[]>(() => {
    const provider = modelConfig.value.providers.find((item) => item.id === form.modelProvider);
    if (!provider) {
      return [];
    }
    return provider.models
      .filter((model) => String(model.id || "").trim())
      .map((model) => ({ label: model.name || model.id, value: model.id }));
  });

  const scheduleExpression = computed(() => {
    if (form.executionType === "once") {
      const onceAt = parseDateTime(form.onceDate, form.onceTime);
      if (!onceAt) {
        return "";
      }
      return `0 ${onceAt.getMinutes()} ${onceAt.getHours()} ${onceAt.getDate()} ${onceAt.getMonth() + 1} ? ${onceAt.getFullYear()}`;
    }
    const [hour, minute] = parseTime(form.time);
    switch (form.recurringMode) {
      case "minute":
        return `0 0/${normalizeInteger(form.minuteInterval, 1, 59)} * * * ?`;
      case "hour":
        return `0 ${normalizeInteger(form.hourlyMinute, 0, 59)} 0/${normalizeInteger(form.hourInterval, 1, 23)} * * ?`;
      case "week":
        if (!form.weekdays.length) {
          return "";
        }
        return `0 ${minute} ${hour} ? ${normalizeMonthField(form.monthField)} ${normalizeWeekdaysForCron(form.weekdays).join(",")}`;
      case "month":
        return `0 ${minute} ${hour} ${normalizeInteger(form.monthlyDay, 1, 31)} ${normalizeMonthField(form.monthField)} ?`;
      case "day":
      default:
        return `0 ${minute} ${hour} * ${normalizeMonthField(form.monthField)} ?`;
    }
  });

  const scheduleSummary = computed(() => humanizeCronExpression(scheduleExpression.value));

  const firstRunAt = computed(() => {
    if (form.executionType === "once") {
      return parseDateTime(form.onceDate, form.onceTime);
    }
    const now = new Date();
    switch (form.recurringMode) {
      case "minute":
        return computeNextMinuteRun(now, normalizeInteger(form.minuteInterval, 1, 59));
      case "hour":
        return computeNextHourRun(now, normalizeInteger(form.hourInterval, 1, 23), normalizeInteger(form.hourlyMinute, 0, 59));
      case "week":
        return computeNextWeekRun(now, form.weekdays, form.time, form.monthField);
      case "month":
        return computeNextMonthRun(now, normalizeInteger(form.monthlyDay, 1, 31), form.time, form.monthField);
      case "day":
      default:
        return computeNextDayRun(now, form.time, form.monthField);
    }
  });

  const endAtValue = computed(() => {
    if (form.executionType !== "recurring" || !form.endAtLocal) {
      return undefined;
    }
    const value = new Date(form.endAtLocal);
    if (Number.isNaN(value.getTime())) {
      return undefined;
    }
    return form.endAtLocal;
  });

  const scheduleError = computed(() => {
    if (form.executionType === "once") {
      const onceAt = parseDateTime(form.onceDate, form.onceTime);
      if (!onceAt) {
        return deps.t("cron.validation.pickDateTime");
      }
      if (onceAt.getTime() <= Date.now()) {
        return deps.t("cron.validation.onceAfterNow");
      }
    }
    if (form.recurringMode === "week" && !form.weekdays.length) {
      return deps.t("cron.validation.pickOneWeekday");
    }
    if (form.endAtLocal) {
      const endAt = new Date(form.endAtLocal);
      if (Number.isNaN(endAt.getTime())) {
        return deps.t("cron.validation.invalidEndAt");
      }
      const first = firstRunAt.value;
      if (first && endAt.getTime() <= first.getTime()) {
        return deps.t("cron.validation.endAtAfterFirstRun");
      }
    }
    return "";
  });

  const canSubmit = computed(() =>
    !!String(form.agentUid || "").trim()
    && !!form.taskContent.trim()
    && !!form.timezone.trim()
    && !!scheduleExpression.value
    && !scheduleError.value
  );

  const schedulePreviewRuns = computed(() => {
    const output: Date[] = [];
    const maxCount = 5;
    const endAt = endAtValue.value ? new Date(endAtValue.value) : null;
    let cursor = new Date();
    for (let i = 0; i < maxCount; i += 1) {
      const next = computeNextRunAfter(cursor, form);
      if (!next) {
        break;
      }
      if (endAt && next.getTime() > endAt.getTime()) {
        break;
      }
      output.push(next);
      cursor = next;
    }
    return output.map((item) => formatPreviewTime(item));
  });

  function applyDefaultAgentSelection() {
    if (!deps.agentOptions.value.length) {
      form.agentUid = "";
      return;
    }
    if (!String(form.agentUid || "").trim()) {
      form.agentUid = String(deps.agentOptions.value[0].value || "");
      return;
    }
    if (!deps.agentOptions.value.some((item) => String(item.value) === String(form.agentUid))) {
      form.agentUid = String(deps.agentOptions.value[0].value || "");
    }
  }

  function applyDefaultModelSelection() {
    if (!providerOptions.value.length) {
      form.modelProvider = "";
      form.modelName = "";
      return;
    }
    if (!providerOptions.value.some((item) => item.value === form.modelProvider)) {
      form.modelProvider = String(providerOptions.value[0].value || "");
    }
    const currentModels = modelOptions.value;
    if (!currentModels.length) {
      form.modelName = "";
      return;
    }
    if (!currentModels.some((item) => item.value === form.modelName)) {
      form.modelName = String(currentModels[0].value || "");
    }
  }

  function buildCreateDefaults() {
    const next = createEmptyForm(deps.fallbackTimezone);
    next.agentUid = deps.agentOptions.value.length ? String(deps.agentOptions.value[0].value || "") : "";
    return next;
  }

  function initializeForCreate(initialTemplateId?: string | null) {
    const next = buildCreateDefaults();
    const template = deps.taskTemplates?.value.find((item) => item.id === initialTemplateId);
    if (template) {
      next.title = template.title;
      next.taskContent = template.taskContent;
      next.executionType = template.executionType;
      next.recurringMode = template.recurringMode;
      next.time = template.time;
      next.weekdays = [...template.weekdays];
      next.monthlyDay = template.monthlyDay;
      next.minuteInterval = template.minuteInterval;
      next.hourInterval = template.hourInterval;
      next.hourlyMinute = template.hourlyMinute;
    }
    assignForm(form, next);
    applyDefaultAgentSelection();
    applyDefaultModelSelection();
  }

  function initializeForEdit(job: CronJobFormInitInput["job"]) {
    if (!job) {
      assignForm(form, buildCreateDefaults());
      applyDefaultAgentSelection();
      applyDefaultModelSelection();
      return;
    }
    assignForm(form, {
      ...buildCreateDefaults(),
      agentUid: job.agentUid || "",
      title: job.title || "",
      taskContent: job.taskContent || "",
      status: job.status || "ACTIVE",
      timezone: job.timezone || deps.fallbackTimezone,
      modelProvider: job.modelProvider || "",
      modelName: job.modelName || "",
      endAtLocal: toLocalDateTimeInput(job.endAt || "")
    });
    applyScheduleFromExpression(job.expression || "");
    applyDefaultAgentSelection();
    applyDefaultModelSelection();
  }

  async function loadModelConfigIfNeeded() {
    if (modelConfigLoaded.value) {
      return;
    }
    const available = await deps.loadModelConfig();
    modelConfig.value = available || { providers: [] };
    modelConfigLoaded.value = true;
    applyDefaultModelSelection();
  }

  function buildSubmitPayload(status: string): CronJobFormSubmitPayload {
    return {
      agentUid: String(form.agentUid || "").trim(),
      title: form.title.trim(),
      expression: scheduleExpression.value,
      timezone: form.timezone.trim(),
      endAt: form.executionType === "recurring" ? endAtValue.value : undefined,
      modelProvider: form.modelProvider || undefined,
      modelName: form.modelName || undefined,
      taskContent: form.taskContent.trim(),
      status
    };
  }

  function toggleWeekday(value: string) {
    const next = new Set(form.weekdays);
    if (next.has(value)) {
      next.delete(value);
    } else {
      next.add(value);
    }
    form.weekdays = normalizeWeekdaysForCron(Array.from(next));
  }

  function selectRecurringMode(value: CronJobFormValue["recurringMode"]) {
    if (form.recurringMode !== value) {
      form.monthField = "*";
    }
    form.recurringMode = value;
  }

  function applyScheduleFromExpression(expression: string) {
    form.monthField = "*";
    const normalized = (expression || "").trim().replace(/\s+/g, " ");
    const parts = normalized.split(" ");
    if (parts.length === 7 && /^\d+$/.test(parts[6])) {
      form.executionType = "once";
      form.recurringMode = "day";
      form.onceDate = `${parts[6].padStart(4, "0")}-${String(Number(parts[4])).padStart(2, "0")}-${String(Number(parts[3])).padStart(2, "0")}`;
      form.onceTime = `${String(Number(parts[2])).padStart(2, "0")}:${String(Number(parts[1])).padStart(2, "0")}`;
      return;
    }
    form.executionType = "recurring";
    if (parts.length !== 6) {
      form.recurringMode = "day";
      return;
    }
    const [, minute, hour, dayOfMonth, month, dayOfWeek] = parts;
    form.monthField = normalizeMonthField(month);
    if (/^0\/\d+$/.test(minute) && hour === "*" && dayOfMonth === "*" && dayOfWeek === "?") {
      form.recurringMode = "minute";
      form.minuteInterval = normalizeInteger(Number(minute.split("/")[1]), 1, 59);
      return;
    }
    if (/^0\/\d+$/.test(hour) && /^\d+$/.test(minute) && dayOfMonth === "*" && dayOfWeek === "?") {
      form.recurringMode = "hour";
      form.hourInterval = normalizeInteger(Number(hour.split("/")[1]), 1, 23);
      form.hourlyMinute = normalizeInteger(Number(minute), 0, 59);
      return;
    }
    if (/^\d+$/.test(dayOfMonth) && dayOfWeek === "?") {
      form.recurringMode = "month";
      form.monthlyDay = normalizeInteger(Number(dayOfMonth), 1, 31);
      form.time = `${String(Number(hour)).padStart(2, "0")}:${String(Number(minute)).padStart(2, "0")}`;
      return;
    }
    if (dayOfMonth === "?" && dayOfWeek !== "*" && dayOfWeek !== "?") {
      form.recurringMode = "week";
      form.weekdays = parseWeekdays(dayOfWeek);
      form.time = `${String(Number(hour)).padStart(2, "0")}:${String(Number(minute)).padStart(2, "0")}`;
      return;
    }
    form.recurringMode = "day";
    form.time = `${String(Number(hour)).padStart(2, "0")}:${String(Number(minute)).padStart(2, "0")}`;
  }

  watch(
    () => form.modelProvider,
    () => {
      const currentModels = modelOptions.value;
      if (!currentModels.some((item) => item.value === form.modelName)) {
        form.modelName = currentModels.length ? String(currentModels[0].value || "") : "";
      }
    }
  );

  watch(
    deps.agentOptions,
    () => {
      applyDefaultAgentSelection();
    },
    { immediate: true }
  );

  return {
    form,
    weekdayOptions,
    recurringModeOptions,
    providerOptions,
    modelOptions,
    scheduleExpression,
    scheduleSummary,
    scheduleError,
    schedulePreviewRuns,
    canSubmit,
    loadModelConfigIfNeeded,
    initializeForCreate,
    initializeForEdit,
    buildSubmitPayload,
    toggleWeekday,
    selectRecurringMode
  };
}

function parseTime(value: string) {
  const matched = /^(\d{1,2}):(\d{2})$/.exec((value || "").trim());
  if (!matched) {
    return ["08", "30"];
  }
  const hours = Math.min(23, Math.max(0, Number(matched[1])));
  const minutes = Math.min(59, Math.max(0, Number(matched[2])));
  return [String(hours), String(minutes)];
}

function parseDateTime(dateText: string, timeText: string) {
  if (!dateText || !timeText) {
    return null;
  }
  const parsed = new Date(`${dateText}T${timeText}:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function formatDateValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatTimeValue(date: Date) {
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${hours}:${minutes}`;
}

function normalizeInteger(value: number | null, min: number, max: number) {
  const next = Number.isFinite(value) ? Number(value) : min;
  return Math.min(max, Math.max(min, Math.round(next)));
}

function normalizeMonthField(value: string) {
  const normalized = (value || "*").trim().toUpperCase();
  if (normalized === "?" || normalized === "") {
    return "*";
  }
  if (normalized === "*") {
    return normalized;
  }
  if (/^\d{1,2}$/.test(normalized)) {
    return String(normalizeInteger(Number(normalized), 1, 12));
  }
  if (/^\d{1,2}(?:,\d{1,2})+$/.test(normalized)) {
    return Array.from(new Set(normalized.split(",").map((item) => String(normalizeInteger(Number(item), 1, 12)))))
      .sort((left, right) => Number(left) - Number(right))
      .join(",");
  }
  return "*";
}

function allowedMonthIndexes(monthField: string) {
  const normalized = normalizeMonthField(monthField);
  if (normalized === "*") {
    return null;
  }
  return new Set(normalized.split(",").map((item) => Number(item) - 1));
}

function normalizeWeekdaysForCron(values: string[]) {
  return Array.from(new Set(values))
    .filter((item) => /^(?:[1-7])$/.test(item))
    .sort((left, right) => Number(left) - Number(right));
}

function parseWeekdays(value: string) {
  const result = new Set<string>();
  value.split(",").map((item) => item.trim()).forEach((item) => {
    if (/^[1-7]$/.test(item)) {
      result.add(item);
      return;
    }
    const range = /^([1-7])-([1-7])$/.exec(item);
    if (!range) return;
    const start = Number(range[1]);
    const end = Number(range[2]);
    if (start <= end) {
      for (let i = start; i <= end; i += 1) {
        result.add(String(i));
      }
    }
  });
  const normalized = normalizeWeekdaysForCron(Array.from(result));
  return normalized.length ? normalized : ["2"];
}

function toLocalDateTimeInput(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function computeNextRunAfter(from: Date, form: CronJobFormValue) {
  if (form.executionType === "once") {
    const onceAt = parseDateTime(form.onceDate, form.onceTime);
    if (!onceAt || onceAt.getTime() <= from.getTime()) {
      return null;
    }
    return onceAt;
  }
  switch (form.recurringMode) {
    case "minute":
      return computeNextMinuteRun(from, normalizeInteger(form.minuteInterval, 1, 59));
    case "hour":
      return computeNextHourRun(from, normalizeInteger(form.hourInterval, 1, 23), normalizeInteger(form.hourlyMinute, 0, 59));
    case "week":
      return computeNextWeekRun(from, form.weekdays, form.time, form.monthField);
    case "month":
      return computeNextMonthRun(from, normalizeInteger(form.monthlyDay, 1, 31), form.time, form.monthField);
    case "day":
    default:
      return computeNextDayRun(from, form.time, form.monthField);
  }
}

function computeNextMinuteRun(from: Date, intervalMinutes: number) {
  const candidate = new Date(from);
  candidate.setSeconds(0, 0);
  candidate.setMinutes(candidate.getMinutes() + 1);
  for (let i = 0; i < 24 * 60 + 5; i += 1) {
    if (candidate.getMinutes() % intervalMinutes === 0 && candidate.getTime() > from.getTime()) {
      return new Date(candidate);
    }
    candidate.setMinutes(candidate.getMinutes() + 1);
  }
  return null;
}

function computeNextHourRun(from: Date, hourInterval: number, minute: number) {
  const candidate = new Date(from);
  candidate.setSeconds(0, 0);
  candidate.setMinutes(minute, 0, 0);
  if (candidate.getTime() <= from.getTime()) {
    candidate.setHours(candidate.getHours() + 1);
    candidate.setMinutes(minute, 0, 0);
  }
  for (let i = 0; i < 24 * 31; i += 1) {
    if (candidate.getHours() % hourInterval === 0 && candidate.getTime() > from.getTime()) {
      return new Date(candidate);
    }
    candidate.setHours(candidate.getHours() + 1);
    candidate.setMinutes(minute, 0, 0);
  }
  return null;
}

function computeNextDayRun(from: Date, time: string, monthField: string) {
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const allowedMonths = allowedMonthIndexes(monthField);
  const candidate = new Date(from);
  candidate.setHours(hour, minute, 0, 0);
  if (candidate.getTime() <= from.getTime()) {
    candidate.setDate(candidate.getDate() + 1);
  }
  if (allowedMonths) {
    for (let i = 0; i < 370; i += 1) {
      if (allowedMonths.has(candidate.getMonth()) && candidate.getTime() > from.getTime()) {
        return candidate;
      }
      candidate.setDate(candidate.getDate() + 1);
      candidate.setHours(hour, minute, 0, 0);
    }
    return null;
  }
  return candidate;
}

function cronWeekdayToJs(value: string) {
  const cron = Number(value);
  if (cron === 1) return 0;
  if (cron >= 2 && cron <= 7) return cron - 1;
  return 1;
}

function computeNextWeekRun(from: Date, weekdayValues: string[], time: string, monthField: string) {
  const targets = new Set(weekdayValues.map(cronWeekdayToJs));
  if (!targets.size) {
    return null;
  }
  const allowedMonths = allowedMonthIndexes(monthField);
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const candidate = new Date(from);
  candidate.setSeconds(0, 0);
  for (let i = 0; i < 370; i += 1) {
    const dayCandidate = new Date(candidate);
    dayCandidate.setDate(candidate.getDate() + i);
    dayCandidate.setHours(hour, minute, 0, 0);
    if ((!allowedMonths || allowedMonths.has(dayCandidate.getMonth())) && targets.has(dayCandidate.getDay()) && dayCandidate.getTime() > from.getTime()) {
      return dayCandidate;
    }
  }
  return null;
}

function computeNextMonthRun(from: Date, monthlyDay: number, time: string, monthField = "*") {
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const allowedMonths = allowedMonthIndexes(monthField);
  const candidate = new Date(from);
  for (let i = 0; i < 120; i += 1) {
    const monthDate = new Date(candidate.getFullYear(), candidate.getMonth() + i, 1, hour, minute, 0, 0);
    if (allowedMonths && !allowedMonths.has(monthDate.getMonth())) {
      continue;
    }
    const days = new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0).getDate();
    if (monthlyDay > days) {
      continue;
    }
    monthDate.setDate(monthlyDay);
    if (monthDate.getTime() > from.getTime()) {
      return monthDate;
    }
  }
  return null;
}

function formatPreviewTime(value: Date) {
  return value.toLocaleString(getSortLocale(), {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}
