<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { BellRing, Clock3 } from "lucide-vue-next";
import { NButton, NForm, NFormItem, NInput, NInputNumber, NModal } from "naive-ui";
import type { CronJob } from "@/types/api";
import { humanizeCronExpression } from "@/utils/format";

type ExecutionType = "once" | "recurring";
type RecurringMode = "minute" | "hour" | "day" | "week" | "month";

const props = defineProps<{
  job: CronJob | null;
  show: boolean;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
  (event: "submit", payload: {
    title: string;
    expression: string;
    timezone: string;
    endAt?: string;
    taskContent: string;
    status: string;
  }): void;
}>();

const weekdayOptions = [
  { label: "周一", short: "一", value: "2" },
  { label: "周二", short: "二", value: "3" },
  { label: "周三", short: "三", value: "4" },
  { label: "周四", short: "四", value: "5" },
  { label: "周五", short: "五", value: "6" },
  { label: "周六", short: "六", value: "7" },
  { label: "周日", short: "日", value: "1" }
] as const;
const executionTypeOptions: Array<{ value: ExecutionType; label: string }> = [
  { value: "once", label: "只执行一次" },
  { value: "recurring", label: "周期性执行" }
];
const recurringModeOptions: Array<{ value: RecurringMode; label: string }> = [
  { value: "minute", label: "按分钟" },
  { value: "hour", label: "按小时" },
  { value: "day", label: "按天" },
  { value: "week", label: "按周" },
  { value: "month", label: "按月" }
];

const form = reactive({
  title: "",
  taskContent: "",
  status: "ACTIVE",
  timezone: "Asia/Shanghai",
  executionType: "recurring" as ExecutionType,
  recurringMode: "day" as RecurringMode,
  time: "08:30",
  weekdays: ["2"] as string[],
  monthlyDay: 1,
  minuteInterval: 30,
  hourInterval: 1,
  hourlyMinute: 0,
  onceDate: "",
  onceTime: "",
  endAtLocal: ""
});
const modalStyle = {
  width: "min(880px, calc(100vw - 32px))",
  height: "min(860px, calc(100dvh - 24px))",
  maxHeight: "calc(100dvh - 32px)",
  display: "flex",
  flexDirection: "column",
  overflow: "hidden"
} as const;
const modalContentStyle = {
  flex: 1,
  minHeight: 0,
  overflowY: "auto"
} as const;

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
      return `0 ${minute} ${hour} ? * ${normalizeWeekdaysForCron(form.weekdays).join(",")}`;
    case "month":
      return `0 ${minute} ${hour} ${normalizeInteger(form.monthlyDay, 1, 31)} * ?`;
    case "day":
    default:
      return `0 ${minute} ${hour} * * ?`;
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
      return computeNextWeekRun(now, form.weekdays, form.time);
    case "month":
      return computeNextMonthRun(now, normalizeInteger(form.monthlyDay, 1, 31), form.time);
    case "day":
    default:
      return computeNextDayRun(now, form.time);
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
      return "请选择执行日期和时间";
    }
    if (onceAt.getTime() <= Date.now()) {
      return "只执行一次的时间必须晚于当前时间";
    }
  }
  if (form.recurringMode === "week" && !form.weekdays.length) {
    return "按周执行至少选择一天";
  }
  if (form.endAtLocal) {
    const endAt = new Date(form.endAtLocal);
    if (Number.isNaN(endAt.getTime())) {
      return "截止日期格式不正确";
    }
    const first = firstRunAt.value;
    if (first && endAt.getTime() <= first.getTime()) {
      return "截止日期必须晚于首次触发时间";
    }
  }
  return "";
});

const canSubmit = computed(() =>
  !!form.taskContent.trim()
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
    const next = computeNextRunAfter(cursor);
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

watch(
  () => props.job,
  (job) => {
    form.title = job?.title || "";
    form.taskContent = job?.taskContent || "";
    form.status = job?.status || "ACTIVE";
    form.timezone = job?.timezone || "Asia/Shanghai";
    form.endAtLocal = toLocalDateTimeInput(job?.endAt || "");
    applyScheduleFromExpression(job?.expression || "");
  },
  { immediate: true }
);

function applyScheduleFromExpression(expression: string) {
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
  const [, minute, hour, dayOfMonth, , dayOfWeek] = parts;
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

function submit() {
  if (!canSubmit.value) return;
  emit("submit", {
    title: form.title.trim(),
    expression: scheduleExpression.value,
    timezone: form.timezone.trim(),
    endAt: form.executionType === "recurring" ? endAtValue.value : undefined,
    taskContent: form.taskContent.trim(),
    status: form.status
  });
  emit("update:show", false);
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

function normalizeInteger(value: number | null, min: number, max: number) {
  const next = Number.isFinite(value) ? Number(value) : min;
  return Math.min(max, Math.max(min, Math.round(next)));
}

function normalizeWeekdaysForCron(values: string[]) {
  return Array.from(new Set(values))
    .filter((item) => /^(?:[1-7])$/.test(item))
    .sort((left, right) => Number(left) - Number(right));
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

function toLocalDateTimeInput(value: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function computeNextRunAfter(from: Date) {
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
      return computeNextWeekRun(from, form.weekdays, form.time);
    case "month":
      return computeNextMonthRun(from, normalizeInteger(form.monthlyDay, 1, 31), form.time);
    case "day":
    default:
      return computeNextDayRun(from, form.time);
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

function computeNextDayRun(from: Date, time: string) {
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const candidate = new Date(from);
  candidate.setHours(hour, minute, 0, 0);
  if (candidate.getTime() <= from.getTime()) {
    candidate.setDate(candidate.getDate() + 1);
  }
  return candidate;
}

function cronWeekdayToJs(value: string) {
  const cron = Number(value);
  if (cron === 1) return 0;
  if (cron >= 2 && cron <= 7) return cron - 1;
  return 1;
}

function computeNextWeekRun(from: Date, weekdayValues: string[], time: string) {
  const targets = new Set(weekdayValues.map(cronWeekdayToJs));
  if (!targets.size) {
    return null;
  }
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const candidate = new Date(from);
  candidate.setSeconds(0, 0);
  for (let i = 0; i < 14; i += 1) {
    const dayCandidate = new Date(candidate);
    dayCandidate.setDate(candidate.getDate() + i);
    dayCandidate.setHours(hour, minute, 0, 0);
    if (targets.has(dayCandidate.getDay()) && dayCandidate.getTime() > from.getTime()) {
      return dayCandidate;
    }
  }
  return null;
}

function computeNextMonthRun(from: Date, monthlyDay: number, time: string) {
  const [hour, minute] = parseTime(time).map((item) => Number(item));
  const candidate = new Date(from);
  for (let i = 0; i < 24; i += 1) {
    const monthDate = new Date(candidate.getFullYear(), candidate.getMonth() + i, 1, hour, minute, 0, 0);
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
  return value.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}
</script>

<template>
  <n-modal
    :show="show"
    preset="card"
    title="修改任务"
    class="cron-create-modal ui-scrollable-card-modal"
    :style="modalStyle"
    :content-style="modalContentStyle"
    @update:show="emit('update:show', $event)"
  >
    <n-form label-placement="top">
      <div class="cron-form-grid">
        <div class="cron-form-main">
          <div class="basic-card">
            <div class="section-title">
              <span>任务信息</span>
            </div>
            <div class="basic-meta-grid">
              <n-form-item label="任务标题">
                <n-input
                  v-model:value="form.title"
                  placeholder="例如：工作日报、每日市场简报、服务器巡检"
                />
              </n-form-item>
            </div>

            <n-form-item label="任务内容" class="task-content-item" :show-feedback="false">
              <n-input
                v-model:value="form.taskContent"
                type="textarea"
                placeholder="直接写你希望它定时帮你做什么。越具体，执行结果越稳定。"
                :autosize="{ minRows: 5, maxRows: 8 }"
              />
            </n-form-item>
          </div>
        </div>

        <div class="cron-form-side">
          <div class="schedule-card">
            <div class="section-title">
              <BellRing :size="16" />
              <span>执行计划</span>
            </div>

            <div class="schedule-field">
              <div class="field-label">执行类型</div>
              <div class="segmented-group">
                <button
                  v-for="option in executionTypeOptions"
                  :key="option.value"
                  type="button"
                  class="segmented-item"
                  :class="{ active: form.executionType === option.value }"
                  @click="form.executionType = option.value"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div class="schedule-dynamic-block">
              <template v-if="form.executionType === 'once'">
                <div class="schedule-field">
                  <label class="field-label">执行日期与时间</label>
                  <div class="inline-fields">
                    <input v-model="form.onceDate" class="native-date-input" type="date" />
                    <input v-model="form.onceTime" class="native-time-input" type="time" step="60" />
                  </div>
                </div>

              </template>

              <template v-else>
                <div class="schedule-field">
                  <div class="field-label">重复频率</div>
                  <div class="segmented-group segmented-group-multi">
                    <button
                      v-for="option in recurringModeOptions"
                      :key="option.value"
                      type="button"
                      class="segmented-item"
                      :class="{ active: form.recurringMode === option.value }"
                      @click="form.recurringMode = option.value"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>

                <div v-if="form.recurringMode === 'minute'" class="schedule-field">
                  <label class="field-label">每隔几分钟执行一次</label>
                  <n-input-number v-model:value="form.minuteInterval" :min="1" :max="59" :precision="0" class="minute-input" />
                </div>

                <div v-if="form.recurringMode === 'hour'" class="schedule-field">
                  <label class="field-label">每隔几小时执行一次</label>
                  <n-input-number v-model:value="form.hourInterval" :min="1" :max="23" :precision="0" class="minute-input" />
                  <label class="field-label hour-sub-label">每小时的第几分钟执行</label>
                  <n-input-number v-model:value="form.hourlyMinute" :min="0" :max="59" :precision="0" class="minute-input" />
                  <div class="field-hint">例如填 22，表示在每小时的 22 分执行。</div>
                </div>

                <div v-if="form.recurringMode === 'day' || form.recurringMode === 'week' || form.recurringMode === 'month'" class="schedule-field">
                  <label class="field-label">执行时间</label>
                  <input v-model="form.time" class="native-time-input" type="time" step="60" />
                </div>

                <div v-if="form.recurringMode === 'week'" class="schedule-field">
                  <div class="field-label">每周哪一天</div>
                  <div class="weekday-grid">
                    <button
                      v-for="weekday in weekdayOptions"
                      :key="weekday.value"
                      class="weekday-pill"
                      :class="{ active: form.weekdays.includes(weekday.value) }"
                      type="button"
                      @click="toggleWeekday(weekday.value)"
                    >
                      {{ weekday.short }}
                    </button>
                  </div>
                </div>

                <div v-if="form.recurringMode === 'month'" class="schedule-field">
                  <label class="field-label">每月几号执行</label>
                  <n-input-number v-model:value="form.monthlyDay" :min="1" :max="31" :precision="0" class="minute-input" />
                </div>

                <div class="schedule-field">
                  <label class="field-label">截止日期（可选）</label>
                  <input v-model="form.endAtLocal" class="native-date-input" type="datetime-local" step="60" />
                </div>
              </template>
            </div>

            <div class="schedule-field">
              <label class="field-label">时区</label>
              <n-input :value="form.timezone" readonly />
            </div>

            <div v-if="scheduleError" class="field-error">{{ scheduleError }}</div>
          </div>

          <div class="preview-card">
            <div class="preview-head">
              <div>
                <div class="section-title">
                  <Clock3 :size="16" />
                  <span>计划预览</span>
                </div>
                <div class="preview-summary">{{ scheduleSummary }}</div>
              </div>
            </div>
            <div class="preview-expression">{{ scheduleExpression || "-" }}</div>
            <div v-if="form.executionType === 'recurring'" class="preview-next">
              <div class="preview-next-title">最近 5 次执行计划</div>
              <div v-if="schedulePreviewRuns.length" class="preview-timeline">
                <div v-for="(item, index) in schedulePreviewRuns" :key="`${item}-${index}`" class="preview-timeline-item">
                  <Clock3 :size="12" class="preview-timeline-icon" />
                  <span class="preview-timeline-text">{{ item }}</span>
                </div>
              </div>
              <div v-else class="preview-next-empty">暂无可执行时间</div>
            </div>
            <div v-else class="preview-next">
              <div class="preview-next-title">执行时间</div>
              <div class="preview-once-time">{{ schedulePreviewRuns[0] || "暂无可执行时间" }}</div>
            </div>
          </div>
        </div>
      </div>
    </n-form>

    <template #action>
      <div class="cron-edit-actions">
        <n-button @click="emit('update:show', false)">取消</n-button>
        <n-button type="primary" :disabled="!canSubmit" @click="submit">保存</n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
@import "./cronTaskModalShared.css";

.cron-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
