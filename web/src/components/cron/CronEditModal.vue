<script setup lang="ts">
import { computed, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NModal } from "naive-ui";
import { useCronJobsStore } from "@/stores/cronJobs";
import { modelApi } from "@/api/modelApi";
import type { CronJob } from "@/types/api";
import CronJobFormContent from "./CronJobFormContent.vue";
import { useCronJobForm } from "./useCronJobForm";

const props = defineProps<{
  job: CronJob | null;
  show: boolean;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
  (event: "submit", payload: {
    agentUid: string;
    title: string;
    expression: string;
    timezone: string;
    endAt?: string;
    modelProvider?: string;
    modelName?: string;
    taskContent: string;
    status: string;
  }): void;
}>();

const { t } = useI18n();
const cronJobsStore = useCronJobsStore();
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

const {
  form,
  weekdayOptions,
  recurringModeOptions,
  providerOptions,
  modelOptions,
  scheduleSummary,
  scheduleExpression,
  scheduleError,
  schedulePreviewRuns,
  canSubmit,
  loadModelConfigIfNeeded,
  initializeForEdit,
  buildSubmitPayload,
  toggleWeekday,
  selectRecurringMode
} = useCronJobForm({
  agentOptions,
  fallbackTimezone: "Asia/Shanghai",
  t,
  loadModelConfig: () => modelApi.getAvailableModelConfig()
});

watch(
  () => props.job,
  (job) => {
    initializeForEdit(job);
  },
  { immediate: true }
);

watch(
  () => props.show,
  async (show) => {
    if (!show) return;
    await loadModelConfigIfNeeded();
    initializeForEdit(props.job);
  }
);

function submit() {
  if (!canSubmit.value) return;
  emit("submit", buildSubmitPayload(form.status));
  emit("update:show", false);
}
</script>

<template>
  <n-modal
    :show="show"
    preset="card"
    :title="t('cron.edit.title')"
    class="cron-create-modal ui-scrollable-card-modal"
    :style="modalStyle"
    :content-style="modalContentStyle"
    @update:show="emit('update:show', $event)"
  >
    <CronJobFormContent
      :form="form"
      :agent-options="agentOptions"
      :provider-options="providerOptions"
      :model-options="modelOptions"
      :weekday-options="weekdayOptions"
      :recurring-mode-options="recurringModeOptions"
      :schedule-summary="scheduleSummary"
      :schedule-expression="scheduleExpression"
      :schedule-preview-runs="schedulePreviewRuns"
      :schedule-error="scheduleError"
      layout="edit"
      :show-agent-filter="true"
      :t="t"
      :toggle-weekday="toggleWeekday"
      :select-recurring-mode="selectRecurringMode"
    />

    <template #action>
      <div class="cron-edit-actions">
        <n-button @click="emit('update:show', false)">{{ t("common.cancel") }}</n-button>
        <n-button type="primary" :disabled="!canSubmit" @click="submit">{{ t("common.save") }}</n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
.cron-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
