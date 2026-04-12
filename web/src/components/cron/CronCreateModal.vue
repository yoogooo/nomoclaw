<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { BellRing, Clock3 } from "lucide-vue-next";
import { NButton, NEmpty, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect } from "naive-ui";
import { useCronJobsStore } from "@/stores/cronJobs";
import { buildCronTaskTemplates, type ExecutionType, type RecurringMode } from "./cronTaskTemplates";
import { humanizeCronExpression, humanizeTimezone } from "@/utils/format";

const props = defineProps<{
  show: boolean;
  initialTemplateId?: string | null;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
}>();

const cronJobsStore = useCronJobsStore();
const { t, locale } = useI18n();
const cronTaskTemplates = computed(() => buildCronTaskTemplates((key) => t(key)));
const fallbackTimezone = typeof Intl !== "undefined"
  ? Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai"
  : "Asia/Shanghai";

const weekdayOptions = computed(() => [
  { label: t("cron.weekday.monday"), short: t("cron.weekdayShort.monday"), value: "2" },
  { label: t("cron.weekday.tuesday"), short: t("cron.weekdayShort.tuesday"), value: "3" },
  { label: t("cron.weekday.wednesday"), short: t("cron.weekdayShort.wednesday"), value: "4" },
  { label: t("cron.weekday.thursday"), short: t("cron.weekdayShort.thursday"), value: "5" },
  { label: t("cron.weekday.friday"), short: t("cron.weekdayShort.friday"), value: "6" },
  { label: t("cron.weekday.saturday"), short: t("cron.weekdayShort.saturday"), value: "7" },
  { label: t("cron.weekday.sunday"), short: t("cron.weekdayShort.sunday"), value: "1" }
]);
const executionTypeOptions = computed<Array<{ value: ExecutionType; label: string }>>(() => [
  { value: "once", label: t("cron.executionType.once") },
  { value: "recurring", label: t("cron.executionType.recurring") }
]);
const recurringModeOptions = computed<Array<{ value: RecurringMode; label: string }>>(() => [
  { value: "minute", label: t("cron.recurringMode.minute") },
  { value: "hour", label: t("cron.recurringMode.hour") },
  { value: "day", label: t("cron.recurringMode.day") },
  { value: "week", label: t("cron.recurringMode.week") },
  { value: "month", label: t("cron.recurringMode.month") }
]);

const form = reactive({
  agentUid: "",
  title: "",
  taskContent: "",
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
  endAtLocal: "",
  timezone: fallbackTimezone
});

const submitting = ref(false);
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

const agentOptions = computed(() =>
  cronJobsStore.agentGroups.flatMap((group) =>
    group.agents.map((agent) => ({
      label: agent.displayName || agent.agentName,
      value: agent.agentUid
    }))
  )
);

watch(
  agentOptions,
  (options) => {
    if (!form.agentUid && options.length) {
      form.agentUid = String(options[0].value);
    }
  },
  { immediate: true }
);

watch(
  () => props.show,
  (show) => {
    if (!show) return;
    resetCreateForm();
  }
);

function resetCreateForm() {
  const defaultRunAt = createDefaultRunAt();
  form.agentUid = agentOptions.value.length ? String(agentOptions.value[0].value) : "";
  form.title = "";
  form.taskContent = "";
  form.executionType = "recurring";
  form.recurringMode = "day";
  form.time = "08:30";
  form.weekdays = ["2"];
  form.monthlyDay = 1;
  form.minuteInterval = 30;
  form.hourInterval = 1;
  form.hourlyMinute = 0;
  form.onceDate = defaultRunAt.date;
  form.onceTime = defaultRunAt.time;
  form.endAtLocal = "";
  form.timezone = fallbackTimezone;

  if (props.initialTemplateId) {
    const template = cronTaskTemplates.value.find((item) => item.id === props.initialTemplateId);
    if (template) {
      form.title = template.title;
      form.taskContent = template.taskContent;
      form.executionType = template.executionType;
      form.recurringMode = template.recurringMode;
      form.time = template.time;
      form.weekdays = [...template.weekdays];
      form.monthlyDay = template.monthlyDay;
      form.minuteInterval = template.minuteInterval;
      form.hourInterval = template.hourInterval;
      form.hourlyMinute = template.hourlyMinute;
    }
  }
}

function createDefaultRunAt() {
  const value = new Date();
  value.setMinutes(value.getMinutes() + 10);
  value.setSeconds(0, 0);
  return {
    date: formatDateValue(value),
    time: formatTimeValue(value)
  };
}

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
  const nowDate = new Date();
  switch (form.recurringMode) {
    case "minute":
      return computeNextMinuteRun(nowDate, normalizeInteger(form.minuteInterval, 1, 59));
    case "hour":
      return computeNextHourRun(nowDate, normalizeInteger(form.hourInterval, 1, 23), normalizeInteger(form.hourlyMinute, 0, 59));
    case "week":
      return computeNextWeekRun(nowDate, form.weekdays, form.time);
    case "month":
      return computeNextMonthRun(nowDate, normalizeInteger(form.monthlyDay, 1, 31), form.time);
    case "day":
    default:
      return computeNextDayRun(nowDate, form.time);
  }
});

const endAtValue = computed(() => {
  if (form.executionType !== "recurring" || !form.endAtLocal) {
    return undefined;
  }
  const value = new Date(form.endAtLocal);
  if (!value) {
    return undefined;
  }
  if (Number.isNaN(value.getTime())) {
    return undefined;
  }
  return form.endAtLocal;
});

const scheduleError = computed(() => {
  const nowDate = new Date();
  if (form.executionType === "once") {
    const onceAt = parseDateTime(form.onceDate, form.onceTime);
    if (!onceAt) {
      return t("cron.validation.pickDateTime");
    }
    if (onceAt.getTime() <= nowDate.getTime()) {
      return t("cron.validation.onceAfterNow");
    }
    return "";
  }

  if (form.recurringMode === "minute" && !Number.isFinite(form.minuteInterval)) {
    return t("cron.validation.invalidMinuteInterval");
  }
  if (form.recurringMode === "hour" && !Number.isFinite(form.hourInterval)) {
    return t("cron.validation.invalidHourInterval");
  }
  if (form.recurringMode === "week" && !form.weekdays.length) {
    return t("cron.validation.pickOneWeekday");
  }
  if (form.endAtLocal) {
    const endAt = new Date(form.endAtLocal);
    if (!endAt) {
      return t("cron.validation.invalidEndAt");
    }
    if (Number.isNaN(endAt.getTime())) {
      return t("cron.validation.invalidEndAt");
    }
    const first = firstRunAt.value;
    if (first && endAt.getTime() <= first.getTime()) {
      return t("cron.validation.endAtAfterFirstRun");
    }
  }
  return "";
});

const canSubmit = computed(() =>
  !submitting.value
  && !!form.agentUid
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

async function createCronJob() {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    await cronJobsStore.createJob({
      agentUid: form.agentUid,
      title: form.title.trim(),
      expression: scheduleExpression.value,
      timezone: form.timezone.trim(),
      endAt: form.executionType === "recurring" ? endAtValue.value : undefined,
      taskContent: form.taskContent.trim(),
      status: "ACTIVE"
    });
    emit("update:show", false);
  } finally {
    submitting.value = false;
  }
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

function normalizeInteger(value: number | null, min: number, max: number) {
  const next = Number.isFinite(value) ? Number(value) : min;
  return Math.min(max, Math.max(min, Math.round(next)));
}

function parseDateTime(dateText: string, timeText: string) {
  if (!dateText || !timeText) {
    return null;
  }
  const parsed = new Date(`${dateText}T${timeText}:00`);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed;
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

function cronWeekdayToJs(value: string) {
  const cron = Number(value);
  if (cron === 1) return 0;
  if (cron >= 2 && cron <= 7) return cron - 1;
  return 1;
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

function formatPreviewTime(value: Date) {
  return value.toLocaleString(locale.value === "zh-CN" ? "zh-CN" : "en-US", {
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
    :title="t('cron.create.title')"
    class="cron-create-modal ui-scrollable-card-modal"
    :style="modalStyle"
    :content-style="modalContentStyle"
    @update:show="emit('update:show', $event)"
  >
    <div v-if="agentOptions.length" class="cron-create-content">
      <n-form label-placement="top">
        <div class="cron-form-grid">
          <div class="cron-form-main">
            <div class="basic-card">
              <div class="section-title">
                <span>{{ t("cron.form.taskInfo") }}</span>
              </div>

              <div class="basic-meta-grid">
                <n-form-item :label="t('cron.form.executeAgent')">
                  <n-select
                    v-model:value="form.agentUid"
                    :placeholder="t('cron.form.executeAgentPlaceholder')"
                    :options="agentOptions"
                  />
                </n-form-item>

                <n-form-item :label="t('cron.form.taskTitle')">
                  <n-input
                    v-model:value="form.title"
                    :placeholder="t('cron.form.taskTitlePlaceholder')"
                  />
                </n-form-item>
              </div>

              <n-form-item :label="t('cron.form.taskContent')" class="task-content-item" :show-feedback="false">
                <n-input
                  v-model:value="form.taskContent"
                  type="textarea"
                  :placeholder="t('cron.form.taskContentPlaceholder')"
                  :autosize="{ minRows: 5, maxRows: 8 }"
                />
              </n-form-item>
            </div>
          </div>

          <div class="cron-form-side">
            <div class="schedule-card">
              <div class="section-title">
                <BellRing :size="16" />
                <span>{{ t("cron.form.plan") }}</span>
              </div>

              <div class="schedule-field">
                <div class="field-label">{{ t("cron.form.executionType") }}</div>
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
                    <label class="field-label">{{ t("cron.form.executeDateTime") }}</label>
                    <div class="inline-fields">
                      <input v-model="form.onceDate" class="native-date-input" type="date" />
                      <input v-model="form.onceTime" class="native-time-input" type="time" step="60" />
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="schedule-field">
                    <div class="field-label">{{ t("cron.form.recurringFrequency") }}</div>
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
                    <label class="field-label">{{ t("cron.form.everyMinutes") }}</label>
                    <n-input-number v-model:value="form.minuteInterval" :min="1" :max="59" :precision="0" class="minute-input" />
                  </div>

                  <div v-if="form.recurringMode === 'hour'" class="schedule-field">
                    <label class="field-label">{{ t("cron.form.everyHours") }}</label>
                    <n-input-number v-model:value="form.hourInterval" :min="1" :max="23" :precision="0" class="minute-input" />
                    <label class="field-label hour-sub-label">{{ t("cron.form.minuteOfHour") }}</label>
                    <n-input-number v-model:value="form.hourlyMinute" :min="0" :max="59" :precision="0" class="minute-input" />
                    <div class="field-hint">{{ t("cron.form.minuteOfHourHint") }}</div>
                  </div>

                  <div v-if="form.recurringMode === 'day' || form.recurringMode === 'week' || form.recurringMode === 'month'" class="schedule-field">
                    <label class="field-label">{{ t("cron.form.executeTime") }}</label>
                    <input v-model="form.time" class="native-time-input" type="time" step="60" />
                  </div>

                  <div v-if="form.recurringMode === 'week'" class="schedule-field">
                    <div class="field-label">{{ t("cron.form.weekday") }}</div>
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
                    <label class="field-label">{{ t("cron.form.dayOfMonth") }}</label>
                    <n-input-number v-model:value="form.monthlyDay" :min="1" :max="31" :precision="0" class="minute-input" />
                  </div>

                  <div class="schedule-field">
                    <label class="field-label">{{ t("cron.form.endAtOptional") }}</label>
                    <input v-model="form.endAtLocal" class="native-date-input" type="datetime-local" step="60" />
                  </div>
                </template>
              </div>

              <div class="schedule-field">
                <label class="field-label">{{ t("cron.form.timezone") }}</label>
                <n-input :value="form.timezone" readonly />
              </div>

              <div v-if="scheduleError" class="field-error">{{ scheduleError }}</div>
            </div>

            <div class="preview-card">
              <div class="preview-head">
                <div>
                  <div class="section-title">
                    <Clock3 :size="16" />
                    <span>{{ t("cron.form.planPreview") }}</span>
                  </div>
                  <div class="preview-summary">{{ scheduleSummary }}</div>
                </div>
              </div>
              <div class="preview-expression">{{ scheduleExpression || "-" }}</div>
              <div v-if="form.executionType === 'recurring'" class="preview-next">
                <div class="preview-next-title">{{ t("cron.form.nextRuns") }}</div>
                <div v-if="schedulePreviewRuns.length" class="preview-timeline">
                  <div v-for="(item, index) in schedulePreviewRuns" :key="`${item}-${index}`" class="preview-timeline-item">
                    <Clock3 :size="12" class="preview-timeline-icon" />
                    <span class="preview-timeline-text">{{ item }}</span>
                  </div>
                </div>
                <div v-else class="preview-next-empty">{{ t("cron.form.noRunnableTime") }}</div>
              </div>
              <div v-else class="preview-next">
                <div class="preview-next-title">{{ t("cron.form.executeTime") }}</div>
                <div class="preview-once-time">{{ schedulePreviewRuns[0] || t("cron.form.noRunnableTime") }}</div>
              </div>
            </div>
          </div>
        </div>
      </n-form>
    </div>

    <div v-else class="cron-create-empty">
      <n-empty :description="t('cron.form.noAvailableAgent')" />
    </div>

    <template #action>
      <div class="cron-create-actions">
        <n-button @click="emit('update:show', false)">{{ t("common.cancel") }}</n-button>
        <n-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="createCronJob">
          {{ t("cron.form.createTask") }}
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
@import "./cronTaskModalShared.css";

.cron-create-content {
  margin-top: 0;
}

.basic-meta-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.cron-create-empty {
  margin-top: var(--size-22);
  padding: var(--size-28) 0;
}

.cron-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

@media (max-width: 1280px) {
  .basic-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
