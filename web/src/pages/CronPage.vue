<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import CronCreateModal from "@/components/cron/CronCreateModal.vue";
import CronDetailPanel from "@/components/cron/CronDetailPanel.vue";
import CronListPanel from "@/components/cron/CronListPanel.vue";
import { buildCronTaskTemplates } from "@/components/cron/cronTaskTemplates";
import { message } from "@/discrete";
import { useCronJobsStore } from "@/stores/cronJobs";

const cronJobsStore = useCronJobsStore();
const showCreateModal = ref(false);
const createTemplateId = ref<string | null>(null);
const hasJobs = computed(() => cronJobsStore.jobs.length > 0);
const { t } = useI18n();
const cronTaskTemplates = computed(() => buildCronTaskTemplates(t));

function openCreateModal(templateId?: string) {
  createTemplateId.value = templateId ?? null;
  showCreateModal.value = true;
}

async function refreshJobs() {
  try {
    await cronJobsStore.refresh();
    message.success(t("toast.configRefreshed"));
  } catch (error) {
    const text = error instanceof Error ? error.message : t("toast.refreshFailed");
    message.error(text);
  }
}

onMounted(() => {
  if (!cronJobsStore.jobs.length) {
    void cronJobsStore.refresh();
  }
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout">
      <DirectoryRail />
      <div class="cron-page-content">
        <AppPageHeader
          :title="t('pages.cron.title')"
          :subtitle="t('pages.cron.subtitle')"
        >
          <template #actions>
            <n-button :loading="cronJobsStore.loading" @click="refreshJobs">{{ t("common.refresh") }}</n-button>
          </template>
        </AppPageHeader>

        <div v-if="hasJobs" class="grid-cron cron-page-grid">
          <CronListPanel @create="openCreateModal()" />
          <CronDetailPanel />
        </div>

        <div v-else class="panel cron-empty-layout">
          <div class="cron-empty-head">
            <div class="panel-title ui-title-xl">{{ t("cron.empty.title") }}</div>
            <div class="panel-subtitle ui-subtitle">{{ t("cron.empty.subtitle") }}</div>
          </div>
          <div class="cron-empty-template-grid">
            <button
              v-for="template in cronTaskTemplates"
              :key="template.id"
              class="cron-empty-template-card"
              type="button"
              @click="openCreateModal(template.id)"
            >
              <div class="cron-empty-template-icon" :style="{ color: String(template.accent) }">
                <component :is="template.icon" :size="18" />
              </div>
              <div class="cron-empty-template-copy">
                <div class="cron-empty-template-title">{{ template.label }}</div>
                <div class="cron-empty-template-description">{{ template.description }}</div>
              </div>
            </button>
          </div>
          <div class="cron-empty-actions">
            <n-button type="primary" @click="openCreateModal()">{{ t("cron.empty.createBlank") }}</n-button>
          </div>
        </div>
      </div>
    </div>

    <CronCreateModal
      :show="showCreateModal"
      :initial-template-id="createTemplateId"
      @update:show="showCreateModal = $event; if (!$event) createTemplateId = null"
    />
  </div>
</template>

<style scoped>
.cron-page-content {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-4);
  min-height: 0;
  height: 100vh;
  padding: var(--space-6) var(--space-7) var(--space-7);
  overflow: hidden;
}

.cron-page-grid {
  min-height: 0;
  height: auto;
  max-height: none;
  padding: 0;
}

.cron-empty-layout {
  padding: var(--space-6);
}

.cron-empty-head {
  margin-bottom: var(--space-5);
}

.cron-empty-head .panel-subtitle {
  margin-top: var(--space-2);
}

.cron-empty-template-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
}

.cron-empty-template-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border: var(--size-1) solid var(--color-border-panel);
  border-radius: var(--space-4_5);
  background: var(--color-bg-surface-soft);
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.cron-empty-template-card:hover {
  transform: translateY(calc(var(--size-1) * -1));
  border-color: var(--color-border-brand-light);
  box-shadow: var(--shadow-card-hover);
}

.cron-empty-template-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--size-40);
  height: var(--size-40);
  border-radius: var(--radius-pill);
  background: transparent;
  flex-shrink: 0;
}

.cron-empty-template-copy {
  min-width: 0;
}

.cron-empty-template-title {
  font-size: var(--text-title-sm-size);
  font-weight: 700;
  color: var(--color-text-heading);
}

.cron-empty-template-description {
  margin-top: var(--space-2);
  color: var(--color-text-cool-gray);
  font-size: var(--text-body-size);
  line-height: 1.7;
}

.cron-empty-actions {
  margin-top: var(--space-5);
}

@media (max-width: 768px) {
  .cron-page-content {
    height: auto;
    min-height: 100dvh;
    padding: var(--space-4);
    overflow: visible;
  }

  .cron-empty-layout {
    padding: var(--space-4);
  }

  .cron-empty-template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
