<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NButton, NCard, NEmpty, NSpin, NTag } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { cronApi } from "@/api/cronApi";
import type { CronExecutionDetail } from "@/types/api";
import { formatDateTime } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<CronExecutionDetail | null>(null);
const errorText = ref("");

const executionUid = computed(() => String(route.params.executionUid || ""));

async function loadDetail() {
  if (!executionUid.value) {
    errorText.value = "executionUid missing";
    return;
  }
  loading.value = true;
  errorText.value = "";
  try {
    detail.value = await cronApi.getExecutionDetail(executionUid.value);
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : "load failed";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content cron-page-content">
          <AppPageHeader title="执行详情" subtitle="查看任务执行过程与输出">
            <template #actions>
              <n-button @click="router.push('/cron')">返回任务页</n-button>
            </template>
          </AppPageHeader>

          <n-spin :show="loading">
            <div v-if="errorText" class="panel"><div class="panel-body">{{ errorText }}</div></div>
            <template v-else-if="detail">
              <n-card embedded>
                <div class="exec-head">
                  <div>
                    <div class="exec-title">{{ detail.jobTitle || detail.jobUid }}</div>
                    <div class="exec-meta">{{ detail.agentDisplayName }} · {{ formatDateTime(detail.executedTime) }}</div>
                  </div>
                  <n-tag :type="detail.status === 'FAILED' || detail.status === 'TIMED_OUT_APPROVAL' ? 'error' : 'success'">{{ detail.status }}</n-tag>
                </div>
                <div class="exec-summary">{{ detail.summary }}</div>
              </n-card>

              <n-card embedded class="exec-card">
                <div class="exec-section-title">执行步骤</div>
                <n-empty v-if="!detail.runs.length" description="暂无运行步骤" />
                <div v-else class="run-list">
                  <div v-for="run in detail.runs" :key="run.messageUid" class="run-item">
                    <div class="run-head">{{ run.summary || run.status }}（{{ run.completedSteps }}/{{ run.totalSteps }}）</div>
                    <div v-if="run.steps?.length" class="step-list">
                      <div v-for="step in run.steps" :key="step.stepUid" class="step-item">
                        <div class="step-title">{{ step.displayTitle }}</div>
                        <div class="step-meta">{{ step.status }} · {{ formatDateTime(step.updatedTime) }}</div>
                        <div v-if="step.displaySummary" class="step-summary">{{ step.displaySummary }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </n-card>

              <n-card embedded class="exec-card">
                <div class="exec-section-title">输出结果</div>
                <pre class="exec-report">{{ detail.reportContent || "暂无报告内容" }}</pre>
              </n-card>
            </template>
          </n-spin>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.cron-page-content {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-5);
}
.exec-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}
.exec-title { font-size: var(--text-title-lg-size); font-weight: 700; }
.exec-meta { margin-top: var(--space-1); color: var(--color-text-secondary); }
.exec-summary { margin-top: var(--space-3); white-space: pre-wrap; }
.exec-card { margin-top: var(--space-4); }
.exec-section-title { font-weight: 600; margin-bottom: var(--space-3); }
.run-list { display: flex; flex-direction: column; gap: var(--space-3); }
.run-item { border: var(--size-1) solid var(--color-border-soft); border-radius: var(--radius-md); padding: var(--space-3); }
.run-head { font-weight: 600; }
.step-list { margin-top: var(--space-2); display: flex; flex-direction: column; gap: var(--space-2); }
.step-item { border-left: 2px solid var(--color-border-brand-light); padding-left: var(--space-2); }
.step-title { font-size: var(--text-body-size); }
.step-meta { font-size: var(--text-caption-size); color: var(--color-text-secondary); }
.step-summary { margin-top: var(--space-1); color: var(--color-text-primary); white-space: pre-wrap; }
.exec-report { white-space: pre-wrap; margin: 0; }
</style>
