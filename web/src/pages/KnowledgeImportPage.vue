<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { Check, ChevronLeft, FilePlus2, Layers3, Play, Trash2 } from "lucide-vue-next";
import {
  NAlert, NButton, NCard, NCheckbox, NEmpty, NInputNumber, NProgress, NRadio, NRadioGroup,
  NSpin, NStep, NSteps, NTag, type TagProps
} from "naive-ui";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import {
  knowledgeApi, type KnowledgeImportBatch, type KnowledgeImportItem
} from "@/api/knowledgeApi";
import { message } from "@/discrete";
import { formatDateTime } from "@/utils/format";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const baseUid = computed(() => String(route.params.knowledgeBaseUid || ""));
const batchUid = computed(() => String(route.params.batchUid || ""));
const batch = ref<KnowledgeImportBatch | null>(null);
const loading = ref(false);
const submitting = ref(false);
const step = ref(1);
const advanced = ref(false);
const fileInput = ref<HTMLInputElement>();
const dragging = ref(false);
const clientRejected = ref<string[]>([]);
const maxUploadBytes = 100 * 1024 * 1024;
const supportedExtensions = new Set(["pdf", "docx", "txt", "md"]);
const config = reactive({
  preset: "balanced",
  chunkStrategy: "TOKEN" as "TOKEN" | "SMART",
  chunkSizeTokens: 500,
  chunkOverlapTokens: 80,
  preprocessing: {
    enabled: true,
    pdf: { removeHeader: true, removeFooter: true, removeWatermark: true, removeTableOfContents: false }
  }
});
let pollTimer: number | undefined;

const accepted = computed(() => batch.value?.items.filter(item => item.outcome === "ACCEPTED" && item.status !== "REMOVED") || []);
const skipped = computed(() => batch.value?.items.filter(item => item.outcome !== "ACCEPTED") || []);
const building = computed(() => batch.value?.status === "BUILDING");
const completed = computed(() => batch.value?.status === "COMPLETED");
function documentProgress(item: KnowledgeImportItem): number {
  const document = item.document;
  if (!document) return 0;
  if (["READY", "FAILED"].includes(item.status) || document.jobStatus === "COMPLETED") return 100;
  const reported = Number.isFinite(document.progressPercent) ? document.progressPercent : 0;
  if (document.stage === "PARSING" && document.totalPages > 0) {
    return Math.max(reported, Math.min(19, Math.round(5 + 15 * document.processedPages / document.totalPages)));
  }
  return Math.max(0, Math.min(99, reported));
}
const progress = computed(() => accepted.value.length
  ? Math.round(accepted.value.reduce((total, item) => total + documentProgress(item), 0) / accepted.value.length)
  : 0);
const currentStepTitle = computed(() => t([
  "pages.knowledge.import.steps.files",
  "pages.knowledge.import.steps.config",
  "pages.knowledge.import.steps.confirm",
  "pages.knowledge.import.steps.build"
][step.value - 1] ?? "pages.knowledge.import.steps.files"));
const validConfig = computed(() => config.chunkStrategy === "SMART" || (config.chunkSizeTokens >= 100 && config.chunkSizeTokens <= 2000
  && config.chunkOverlapTokens >= 0 && config.chunkOverlapTokens < config.chunkSizeTokens
  && config.chunkOverlapTokens <= config.chunkSizeTokens / 2));

function applyPreset(value: string | number | boolean) {
  if (typeof value !== "string") return;
  config.preset = value;
  const presets: Record<string, [number, number]> = {
    precise: [300, 50], balanced: [500, 80], context: [800, 120]
  };
  const selected = presets[value];
  if (selected) [config.chunkSizeTokens, config.chunkOverlapTokens] = selected;
}

async function load() {
  loading.value = true;
  try {
    batch.value = await knowledgeApi.importBatch(baseUid.value, batchUid.value);
    if (batch.value.status !== "DRAFT") step.value = 4;
    config.chunkSizeTokens = batch.value.chunkSizeTokens;
    config.chunkOverlapTokens = batch.value.chunkOverlapTokens;
    config.chunkStrategy = batch.value.chunkStrategy || "TOKEN";
    if (batch.value.preprocessing) {
      config.preprocessing.enabled = batch.value.preprocessing.enabled;
      config.preprocessing.pdf.removeHeader = batch.value.preprocessing.pdf.removeHeader;
      config.preprocessing.pdf.removeFooter = batch.value.preprocessing.pdf.removeFooter;
      config.preprocessing.pdf.removeWatermark = batch.value.preprocessing.pdf.removeWatermark;
      config.preprocessing.pdf.removeTableOfContents = batch.value.preprocessing.pdf.removeTableOfContents;
    }
    schedulePoll();
  } finally {
    loading.value = false;
  }
}

function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer);
  if (building.value) pollTimer = window.setTimeout(() => void load(), 1500);
}

function chooseFiles() { fileInput.value?.click(); }
function fileExtension(file: File): string {
  return file.name.split(".").pop()?.toLowerCase() || "";
}
function validateFiles(files: File[]): File[] {
  const existingNames = new Set(accepted.value.map(item => item.fileName.toLowerCase()));
  const seenNames = new Set<string>();
  const valid: File[] = [];
  const rejected: string[] = [];
  for (const file of files) {
    const normalizedName = file.name.toLowerCase();
    const extension = fileExtension(file);
    if (!supportedExtensions.has(extension)) {
      rejected.push(`${file.name} · ${t("pages.knowledge.import.files.invalidType")}`);
    } else if (file.size > maxUploadBytes) {
      rejected.push(`${file.name} · ${t("pages.knowledge.import.files.tooLarge")}`);
    } else if (existingNames.has(normalizedName) || seenNames.has(normalizedName)) {
      rejected.push(`${file.name} · ${t("pages.knowledge.import.files.duplicate")}`);
    } else {
      seenNames.add(normalizedName);
      valid.push(file);
    }
  }
  clientRejected.value = rejected;
  return valid;
}
async function addFiles(files: File[]) {
  const validFiles = validateFiles(files);
  if (!validFiles.length) return;
  await knowledgeApi.addImportFiles(baseUid.value, batchUid.value, validFiles);
  await load();
}
async function addFilesFromInput(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  if (!files.length) return;
  await addFiles(files);
}
async function handleDrop(event: DragEvent) {
  dragging.value = false;
  const files = Array.from(event.dataTransfer?.files || []);
  if (files.length) await addFiles(files);
}

async function removeItem(item: KnowledgeImportItem) {
  if (!item.document) return;
  await knowledgeApi.deleteUploadedDocument(baseUid.value, item.document.documentUid);
  await load();
}

async function retryItem(item: KnowledgeImportItem) {
  if (!item.document) return;
  await knowledgeApi.retry(baseUid.value, item.document.documentUid);
  await load();
}

async function startBuild() {
  if (!validConfig.value || !accepted.value.length) return;
  submitting.value = true;
  try {
    batch.value = await knowledgeApi.buildImport(baseUid.value, batchUid.value, {
      parserMode: "STRUCTURED",
      chunkStrategy: config.chunkStrategy,
      chunkSizeTokens: config.chunkSizeTokens,
      chunkOverlapTokens: config.chunkOverlapTokens,
      preprocessing: config.preprocessing
    });
    step.value = 4;
    message.success(t("pages.knowledge.import.messages.started"));
    schedulePoll();
  } finally {
    submitting.value = false;
  }
}

async function cancelImport() {
  await knowledgeApi.cancelImport(baseUid.value, batchUid.value);
  await returnToBase();
}

function returnToBase() {
  return router.push({ name: "knowledge", query: { base: baseUid.value } });
}

function itemTagType(item: KnowledgeImportItem): TagProps["type"] {
  if (item.status === "READY" || item.outcome === "ACCEPTED") return "success";
  if (item.status === "FAILED" || ["REJECTED", "FAILED"].includes(item.outcome)) return "error";
  return "info";
}

onMounted(() => void load());
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer); });
</script>

<template>
  <div class="page-frame app-page-shell"><div class="app-layout app-layout-responsive"><DirectoryRail />
    <main class="app-main-content"><div class="app-page-content import-page">
      <div class="detail-back-row">
        <button type="button" class="detail-back-btn" @click="returnToBase">
          <ChevronLeft :size="14" />
          <span>{{ t("pages.knowledge.backShort") }}</span>
        </button>
      </div>
      <AppPageHeader :title="t('pages.knowledge.import.title')" :subtitle="t('pages.knowledge.import.subtitle')" />

      <NSteps :current="step" :vertical="false" class="import-steps">
        <NStep :title="t('pages.knowledge.import.steps.files')" />
        <NStep :title="t('pages.knowledge.import.steps.config')" />
        <NStep :title="t('pages.knowledge.import.steps.confirm')" />
        <NStep :title="t('pages.knowledge.import.steps.build')" />
      </NSteps>
      <div class="mobile-step">{{ step }}/4 · {{ currentStepTitle }}</div>

      <NSpin :show="loading"><NCard class="wizard-card">
        <template v-if="step === 1">
          <div class="section-heading"><div><h2>{{ t("pages.knowledge.import.files.title") }}</h2><p>{{ t("pages.knowledge.import.files.description") }}</p></div><NButton @click="chooseFiles"><template #icon><FilePlus2 /></template>{{ t("pages.knowledge.import.files.add") }}</NButton></div>
          <input ref="fileInput" hidden type="file" multiple accept=".pdf,.docx,.txt,.md" @change="addFilesFromInput">
          <div
            class="upload-dropzone"
            :class="{ 'upload-dropzone--dragging': dragging }"
            role="button"
            tabindex="0"
            @click="chooseFiles"
            @keydown.enter="chooseFiles"
            @dragenter.prevent="dragging = true"
            @dragover.prevent="dragging = true"
            @dragleave.prevent="dragging = false"
            @drop.prevent="handleDrop"
          >
            <strong>{{ t("pages.knowledge.import.files.dropTitle") }}</strong>
            <span>{{ t("pages.knowledge.import.files.dropHint") }}</span>
          </div>
          <div class="file-list">
            <div v-for="item in accepted" :key="item.itemUid" class="file-row">
              <div class="file-info"><strong>{{ item.fileName }}</strong><span>{{ item.document?.contentType || "-" }}</span></div>
              <NTag class="outcome-tag" size="small" :type="itemTagType(item)">{{ t(`pages.knowledge.uploadOutcomes.${item.outcome}`) }}</NTag>
              <NButton v-if="item.mode === 'UPLOAD' && item.document" quaternary circle :aria-label="t('common.delete')" @click="removeItem(item)"><Trash2 :size="17" /></NButton>
            </div>
          </div>
          <NEmpty v-if="!accepted.length" :description="t('pages.knowledge.import.files.empty')" />
          <NAlert v-if="skipped.length || clientRejected.length" type="warning" :title="t('pages.knowledge.import.files.skippedTitle')">
            <div v-for="item in skipped" :key="item.itemUid">{{ item.fileName }} · {{ item.errorMessage || t(`pages.knowledge.uploadOutcomes.${item.outcome}`) }}</div>
            <div v-for="item in clientRejected" :key="item">{{ item }}</div>
          </NAlert>
        </template>

        <template v-else-if="step === 2">
          <h2>{{ t("pages.knowledge.import.config.title") }}</h2>
          <p>{{ t("pages.knowledge.import.config.description") }}</p>
          <NAlert type="info" :title="t('pages.knowledge.import.config.ocrTitle')">{{ t("pages.knowledge.import.config.ocrDescription") }}</NAlert>
          <NRadioGroup v-model:value="config.chunkStrategy" class="preset-grid">
            <NRadio value="TOKEN"><strong>{{ t("pages.knowledge.import.strategies.token") }}</strong></NRadio>
            <NRadio value="SMART"><strong>{{ t("pages.knowledge.import.strategies.smart") }}</strong><span>{{ t("pages.knowledge.import.strategies.smartHint") }}</span></NRadio>
          </NRadioGroup>
          <NRadioGroup v-if="config.chunkStrategy === 'TOKEN'" :value="config.preset" class="preset-grid" @update:value="applyPreset">
            <NRadio value="precise"><strong>{{ t("pages.knowledge.import.presets.precise") }}</strong><span>300 / 50</span></NRadio>
            <NRadio value="balanced"><strong>{{ t("pages.knowledge.import.presets.balanced") }}</strong><span>500 / 80</span></NRadio>
            <NRadio value="context"><strong>{{ t("pages.knowledge.import.presets.context") }}</strong><span>800 / 120</span></NRadio>
          </NRadioGroup>
          <div class="preprocessing-panel">
            <NCheckbox v-model:checked="config.preprocessing.enabled">{{ t("pages.knowledge.import.preprocessing.enabled") }}</NCheckbox>
            <div class="preprocessing-options" :class="{ disabled: !config.preprocessing.enabled }">
              <NCheckbox v-model:checked="config.preprocessing.pdf.removeHeader" :disabled="!config.preprocessing.enabled">{{ t("pages.knowledge.import.preprocessing.pdfHeader") }}</NCheckbox>
              <NCheckbox v-model:checked="config.preprocessing.pdf.removeFooter" :disabled="!config.preprocessing.enabled">{{ t("pages.knowledge.import.preprocessing.pdfFooter") }}</NCheckbox>
              <NCheckbox v-model:checked="config.preprocessing.pdf.removeWatermark" :disabled="!config.preprocessing.enabled">{{ t("pages.knowledge.import.preprocessing.pdfWatermark") }}</NCheckbox>
              <NCheckbox v-model:checked="config.preprocessing.pdf.removeTableOfContents" :disabled="!config.preprocessing.enabled">{{ t("pages.knowledge.import.preprocessing.pdfTableOfContents") }}</NCheckbox>
            </div>
          </div>
          <NButton v-if="config.chunkStrategy === 'TOKEN'" text type="primary" @click="advanced = !advanced">{{ t("pages.knowledge.import.config.advanced") }}</NButton>
          <div v-if="advanced && config.chunkStrategy === 'TOKEN'" class="advanced-grid">
            <label>{{ t("pages.knowledge.import.config.chunkSize") }}<NInputNumber v-model:value="config.chunkSizeTokens" :min="100" :max="2000" /></label>
            <label>{{ t("pages.knowledge.import.config.overlap") }}<NInputNumber v-model:value="config.chunkOverlapTokens" :min="0" :max="Math.floor(config.chunkSizeTokens / 2)" /></label>
          </div>
          <NAlert v-if="!validConfig" type="error">{{ t("pages.knowledge.import.config.invalid") }}</NAlert>
        </template>

        <template v-else-if="step === 3 && batch">
          <h2>{{ t("pages.knowledge.import.confirm.title") }}</h2>
          <div class="confirm-grid">
            <div class="summary-panel"><span>{{ t("pages.knowledge.import.confirm.files") }}</span><strong>{{ accepted.length }}</strong></div>
            <div class="summary-panel"><span>{{ t("pages.knowledge.import.confirm.chunking") }}</span><strong>{{ config.chunkStrategy === "SMART" ? t("pages.knowledge.import.strategies.smart") : `${config.chunkSizeTokens} / ${config.chunkOverlapTokens}` }}</strong></div>
            <div class="summary-panel"><span>{{ t("pages.knowledge.import.confirm.preprocessing") }}</span><strong>{{ config.preprocessing.enabled ? t("common.enabled") : t("common.disabled") }}</strong></div>
            <div class="summary-panel"><span>Embedding</span><strong>{{ batch.embeddingProviderId }} · {{ batch.embeddingModelId }} · {{ batch.embeddingDimension }}</strong></div>
          </div>
          <div class="pipeline"><span><Check />{{ t("pages.knowledge.import.pipeline.parse") }}</span><span><Layers3 />{{ t("pages.knowledge.import.pipeline.chunk") }}</span><span><Play />Embedding / Qdrant / BM25</span></div>
        </template>

        <template v-else-if="step === 4">
          <h2>{{ completed ? t("pages.knowledge.import.progress.completed") : t("pages.knowledge.import.progress.title") }}</h2>
          <NProgress type="line" :percentage="progress" />
          <div class="file-list">
            <div v-for="item in accepted" :key="item.itemUid" class="file-row progress-row">
              <div class="progress-header">
                <div class="file-info"><strong>{{ item.fileName }}</strong><span>{{ item.document?.stage ? t(`pages.knowledge.stages.${item.document.stage}`) : item.status }}</span></div>
                <div class="progress-summary">
                  <strong>{{ documentProgress(item) }}%</strong>
                  <NTag size="small" :type="itemTagType(item)">{{ t(`pages.knowledge.import.itemStatuses.${item.status}`) }}</NTag>
                  <NButton v-if="item.document?.jobStatus === 'FAILED'" size="small" @click="retryItem(item)">{{ t("pages.knowledge.retry") }}</NButton>
                </div>
              </div>
              <div class="progress-metrics">
                <span v-if="item.document?.totalPages">{{ item.document.processedPages }}/{{ item.document.totalPages }} {{ t("pages.knowledge.units.pages") }}</span>
                <span v-if="item.document?.totalChunks">{{ item.document.processedChunks }}/{{ item.document.totalChunks }} {{ t("pages.knowledge.units.chunks") }}</span>
                <span v-if="item.document && (item.document.cacheHitChunks || item.document.cacheMissChunks)">{{ t("pages.knowledge.cacheSummary", { hits: item.document.cacheHitChunks, misses: item.document.cacheMissChunks }) }}</span>
                <span v-if="item.document && (item.document.attemptCount > 1 || item.document.jobStatus === 'FAILED')">{{ t("pages.knowledge.attempt", { current: item.document.attemptCount, max: item.document.maxAttempts }) }}</span>
                <span v-if="item.document?.jobStatus === 'RETRY_WAIT' && item.document.nextRetryTime">{{ t("pages.knowledge.nextRetry", { time: formatDateTime(item.document.nextRetryTime) }) }}</span>
              </div>
              <div v-if="item.document?.failureMessage || item.document?.parseWarnings?.length" class="progress-diagnostics">
                <span v-if="item.document?.failureMessage" class="item-error">{{ item.document.failureMessage }}</span>
                <span v-for="warning in item.document?.parseWarnings || []" :key="warning">{{ t(`pages.knowledge.parseWarnings.${warning}`) }}</span>
              </div>
            </div>
          </div>
        </template>
      </NCard></NSpin>

      <div class="wizard-actions">
        <NButton v-if="step === 1" secondary type="error" @click="cancelImport">{{ t("pages.knowledge.import.cancel") }}</NButton>
        <NButton v-if="step > 1 && step < 4" @click="step--">{{ t("pages.knowledge.import.previous") }}</NButton>
        <NButton v-if="step < 3" type="primary" :disabled="step === 1 ? !accepted.length : !validConfig" @click="step++">{{ t("pages.knowledge.import.next") }}</NButton>
        <NButton v-if="step === 3" type="primary" :loading="submitting" @click="startBuild">{{ t("pages.knowledge.import.start") }}</NButton>
        <NButton v-if="step === 4 && completed" type="primary" @click="returnToBase">{{ t("pages.knowledge.import.return") }}</NButton>
      </div>
    </div></main>
  </div></div>
</template>

<style scoped>
.upload-dropzone{display:flex;flex-direction:column;align-items:center;gap:var(--space-2);padding:var(--space-5);margin:var(--space-4) 0;border:1px dashed var(--color-border-strong);border-radius:var(--radius-lg);color:var(--color-text-secondary);cursor:pointer;transition:border-color 140ms ease,background 140ms ease}.upload-dropzone strong{color:var(--color-text-primary)}.upload-dropzone:hover,.upload-dropzone--dragging{border-color:var(--color-brand-400);background:var(--color-bg-surface-soft)}
.import-page{gap:var(--space-4)}.detail-back-row{display:flex;align-items:center}.detail-back-btn{display:inline-flex;align-items:center;gap:var(--space-1);padding:0;border:0;background:transparent;color:var(--color-text-secondary);font-size:var(--text-body-size);cursor:pointer}.detail-back-btn:hover{color:var(--color-brand-400)}.import-steps{max-width:900px;margin:0 auto;width:100%}.mobile-step{display:none}.wizard-card{max-width:1000px;margin:0 auto;min-height:420px}.section-heading,.file-row,.wizard-actions,.pipeline{display:flex;align-items:center;gap:var(--space-3)}.section-heading{justify-content:space-between}.section-heading h2,.section-heading p,.wizard-card h2{margin:0}.file-list{display:flex;flex-direction:column;margin:var(--space-4) 0}.file-row{padding:var(--space-3);border-bottom:1px solid var(--color-border-soft)}.file-row>.file-info{display:flex;min-width:0;flex:1;flex-direction:column;gap:var(--space-1)}.file-info strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-info>span,.progress-row>span{color:var(--color-text-muted);font-size:var(--font-size-sm)}.file-row>.outcome-tag{flex:0 0 auto;align-self:center}.file-row .item-error{color:var(--color-danger)}.progress-row{display:block}.progress-header{display:flex;align-items:center;justify-content:space-between;gap:var(--space-3)}.progress-header>.file-info{min-width:0}.progress-summary{display:flex;align-items:center;flex:0 0 auto;gap:var(--space-2)}.progress-summary>strong{color:var(--color-text-primary)}.progress-metrics{display:flex;flex-wrap:wrap;gap:var(--space-2) var(--space-4);margin-top:var(--space-2);color:var(--color-text-muted);font-size:var(--font-size-sm)}.progress-diagnostics{display:flex;flex-direction:column;gap:var(--space-1);margin-top:var(--space-2);color:var(--color-text-secondary);font-size:var(--font-size-sm);line-height:1.5}.progress-diagnostics span{overflow-wrap:anywhere}.preset-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--space-3);margin:var(--space-5) 0}.preset-grid :deep(.n-radio){padding:var(--space-4);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg)}.preset-grid :deep(.n-radio__label){display:flex;flex-direction:column;gap:var(--space-1)}.preprocessing-panel{display:flex;flex-direction:column;gap:var(--space-3);padding:var(--space-4);margin-bottom:var(--space-4);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg)}.preprocessing-options{display:flex;flex-wrap:wrap;gap:var(--space-4)}.preprocessing-options.disabled{opacity:.55}.advanced-grid,.confirm-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--space-4);margin:var(--space-4) 0}.advanced-grid label{display:flex;flex-direction:column;gap:var(--space-2)}.confirm-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.summary-panel{display:flex;min-height:110px;flex-direction:column;justify-content:space-between;padding:var(--space-4);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg)}.pipeline{flex-wrap:wrap;margin-top:var(--space-5)}.pipeline span{display:flex;align-items:center;gap:var(--space-2)}.wizard-actions{max-width:1000px;width:100%;justify-content:flex-end;margin:0 auto}.wizard-actions>button:first-child:last-child{margin-left:auto}@media(max-width:760px){.import-steps{display:none}.mobile-step{display:block;padding:0 var(--space-2);color:var(--color-text-muted);font-size:var(--font-size-sm)}.preset-grid,.advanced-grid,.confirm-grid{grid-template-columns:1fr}.wizard-card{min-height:0}.wizard-actions{position:sticky;bottom:0;padding:var(--space-3);background:var(--color-bg-page);z-index:2}.progress-row{align-items:flex-start;flex-wrap:wrap}.progress-header{align-items:flex-start;flex-direction:column}.progress-summary{width:100%;justify-content:flex-start}}
</style>
