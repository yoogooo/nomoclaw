<script setup lang="ts">
import { BellRing, Clock3 } from "lucide-vue-next";
import { NForm, NFormItem, NInput, NInputNumber, NSelect, NTabPane, NTabs } from "naive-ui";
import type { CronJobFormOption, CronJobFormValue } from "./cronJobForm";

defineProps<{
  form: CronJobFormValue;
  agentOptions: CronJobFormOption[];
  providerOptions: CronJobFormOption[];
  modelOptions: CronJobFormOption[];
  weekdayOptions: Array<{ label: string; short: string; value: string }>;
  recurringModeOptions: Array<{ value: CronJobFormValue["recurringMode"]; label: string }>;
  scheduleSummary: string;
  scheduleExpression: string;
  schedulePreviewRuns: string[];
  scheduleError: string;
  layout: "create" | "edit";
  showAgentFilter?: boolean;
  t: (key: string) => string;
  toggleWeekday: (value: string) => void;
  selectRecurringMode: (value: CronJobFormValue["recurringMode"]) => void;
}>();
</script>

<template>
  <n-form label-placement="top">
    <div class="cron-form-grid">
      <div class="cron-form-main">
        <div class="basic-card">
          <div class="section-title">
            <span>{{ t("cron.form.taskInfo") }}</span>
          </div>

          <div class="basic-meta-grid" :class="{ 'is-edit-layout': layout === 'edit' }">
            <template v-if="layout === 'edit'">
              <div class="meta-inline-row">
                <n-form-item :label="t('cron.form.executeAgent')" class="meta-inline-item">
                  <n-select
                    v-model:value="form.agentUid"
                    :placeholder="t('cron.form.executeAgentPlaceholder')"
                    :options="agentOptions"
                    :filterable="showAgentFilter"
                  />
                </n-form-item>
                <n-form-item :label="t('cron.form.taskTitle')" class="meta-inline-item">
                  <n-input
                    v-model:value="form.title"
                    :placeholder="t('cron.form.taskTitlePlaceholder')"
                  />
                </n-form-item>
              </div>
              <div class="model-inline-row">
                <n-form-item :label="t('cron.form.modelProvider')" class="model-inline-item">
                  <n-select
                    v-model:value="form.modelProvider"
                    :placeholder="t('cron.form.modelProviderPlaceholder')"
                    :options="providerOptions"
                  />
                </n-form-item>
                <n-form-item :label="t('cron.form.modelName')" class="model-inline-item">
                  <n-select
                    v-model:value="form.modelName"
                    :placeholder="t('cron.form.modelNamePlaceholder')"
                    :options="modelOptions"
                  />
                </n-form-item>
              </div>
            </template>

            <template v-else>
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

              <n-form-item :label="t('cron.form.modelProvider')">
                <n-select
                  v-model:value="form.modelProvider"
                  :placeholder="t('cron.form.modelProviderPlaceholder')"
                  :options="providerOptions"
                />
              </n-form-item>

              <n-form-item :label="t('cron.form.modelName')">
                <n-select
                  v-model:value="form.modelName"
                  :placeholder="t('cron.form.modelNamePlaceholder')"
                  :options="modelOptions"
                />
              </n-form-item>
            </template>
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
            <n-tabs v-model:value="form.executionType" type="segment" animated size="small">
              <n-tab-pane name="once" :tab="t('cron.executionType.once')" />
              <n-tab-pane name="recurring" :tab="t('cron.executionType.recurring')" />
            </n-tabs>
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
                    @click="selectRecurringMode(option.value)"
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
</template>

<style scoped>
@import "./cronTaskModalShared.css";

.basic-meta-grid.is-edit-layout {
  grid-template-columns: 1fr;
}

.model-inline-row {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--space-3);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.meta-inline-row {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--space-3);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.meta-inline-item {
  margin-bottom: 0;
}

.model-inline-item {
  margin-bottom: 0;
}

@media (max-width: 1280px) {
  .basic-meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .meta-inline-row,
  .model-inline-row {
    grid-template-columns: 1fr;
  }
}
</style>
