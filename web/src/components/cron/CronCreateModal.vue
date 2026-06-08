<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NEmpty, NModal } from "naive-ui";
import { useCronJobsStore } from "@/stores/cronJobs";
import { modelApi } from "@/api/modelApi";
import CronJobFormContent from "./CronJobFormContent.vue";
import { buildCronTaskTemplates } from "./cronTaskTemplates";
import { useCronJobForm } from "./useCronJobForm";

const props = defineProps<{
  show: boolean;
  initialTemplateId?: string | null;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
}>();

const cronJobsStore = useCronJobsStore();
const { t } = useI18n();
const cronTaskTemplates = computed(() => buildCronTaskTemplates((key) => t(key)));
const fallbackTimezone = typeof Intl !== "undefined"
  ? Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai"
  : "Asia/Shanghai";
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
  initializeForCreate,
  buildSubmitPayload,
  toggleWeekday,
  selectRecurringMode
} = useCronJobForm({
  agentOptions,
  taskTemplates: cronTaskTemplates,
  fallbackTimezone,
  t,
  loadModelConfig: () => modelApi.getAvailableModelConfig()
});

watch(
  () => props.show,
  async (show) => {
    if (!show) return;
    await loadModelConfigIfNeeded();
    initializeForCreate(props.initialTemplateId);
  }
);

async function createCronJob() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    await cronJobsStore.createJob(buildSubmitPayload("ACTIVE"));
    emit("update:show", false);
  } finally {
    submitting.value = false;
  }
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
        layout="create"
        :t="t"
        :toggle-weekday="toggleWeekday"
        :select-recurring-mode="selectRecurringMode"
      />
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
.cron-create-content {
  margin-top: 0;
}

.cron-create-empty {
  margin-top: var(--space-5_5);
  padding: var(--space-7) 0;
}

.cron-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
