<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ChevronLeft, Copy, Database, Plus, Search, Upload } from "lucide-vue-next";
import { NAlert, NButton, NCard, NDrawer, NDrawerContent, NEmpty, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, NSpin, NTag } from "naive-ui";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import KnowledgeDocumentRow from "@/components/knowledge/KnowledgeDocumentRow.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { knowledgeApi, type KnowledgeBase, type KnowledgeDocument, type KnowledgeHit, type KnowledgeSearchDiagnostic } from "@/api/knowledgeApi";
import { modelApi } from "@/api/modelApi";
import { message } from "@/discrete";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const bases = ref<KnowledgeBase[]>([]);
const documents = ref<KnowledgeDocument[]>([]);
const hits = ref<KnowledgeHit[]>([]);
const searchDiagnostics = ref<KnowledgeSearchDiagnostic[]>([]);
const selected = ref<KnowledgeBase | null>(null);
const loading = ref(false);
const searching = ref(false);
const vectorAvailable = ref(true);
const createVisible = ref(false);
const configDrawerVisible = ref(false);
const documentDrawerVisible = ref(false);
const searchQuery = ref("");
const detailDocument = ref<KnowledgeDocument | null>(null);
const embeddingProviders = ref<Array<{ providerId: string; providerName: string; models: Array<{ id: string; name: string }> }>>([]);
const form = reactive({ name: "", description: "", embeddingProviderId: "dashscope", embeddingModelId: "text-embedding-v4", embeddingDimension: 1024 });
let pollTimer: number | undefined;
const processing = computed(() => documents.value.some(item => ["PENDING", "RUNNING", "RETRY_WAIT"].includes(item.jobStatus)));
const embeddingFailureDocument = computed(() => documents.value.find(item => {
  const failure = `${item.failureCode || ""} ${item.failureMessage || ""}`.toUpperCase();
  return item.status === "FAILED" && failure.includes("EMBEDD");
}));
const embeddingProviderOptions = computed(() =>
  embeddingProviders.value.map((provider) => ({
    label: provider.providerName,
    value: provider.providerId
  }))
);
const selectedEmbeddingProvider = computed(() =>
  embeddingProviders.value.find((provider) => provider.providerId === form.embeddingProviderId) ?? null
);
const embeddingModelOptions = computed(() =>
  (selectedEmbeddingProvider.value?.models ?? []).map((model) => ({
    label: model.id,
    value: model.id
  }))
);
const hasEmbeddingModels = computed(() => embeddingProviders.value.some((provider) => provider.models.length > 0));

async function loadBases() { loading.value = true; try { const response = await knowledgeApi.list(); bases.value = response.items; vectorAvailable.value = response.vectorAvailable; const requested = String(route.query.base || ""); if (requested && !selected.value) { const base = bases.value.find(item => item.knowledgeBaseUid === requested); if (base) await openBase(base); } } finally { loading.value = false; } }
async function loadEmbeddingModels() {
  const config = await modelApi.getAvailableModelConfig();
  embeddingProviders.value = config.providers
    .map((provider) => ({
      providerId: provider.id,
      providerName: provider.name,
      models: provider.models
        .filter((model) => model.modelType === "EMBEDDING")
        .map((model) => ({
          id: model.id,
          name: model.name
        }))
    }))
    .filter((provider) => provider.models.length > 0);
  syncEmbeddingSelection();
}
async function openBase(base: KnowledgeBase) { selected.value = base; hits.value = []; searchDiagnostics.value = []; await loadDocuments(); }
async function loadDocuments() { if (!selected.value) return; documents.value = (await knowledgeApi.documents(selected.value.knowledgeBaseUid)).items; schedulePoll(); }
function schedulePoll() { if (pollTimer) window.clearTimeout(pollTimer); if (processing.value) pollTimer = window.setTimeout(() => void loadDocuments(), 1500); }
async function createBase() { const created = await knowledgeApi.create(form); createVisible.value = false; message.success(t("pages.knowledge.messages.created")); await loadBases(); await openBase(created); }
async function startImport() { if (!selected.value) return; const batch = await knowledgeApi.createImport(selected.value.knowledgeBaseUid); await router.push({ name: "knowledge-import", params: { knowledgeBaseUid: selected.value.knowledgeBaseUid, batchUid: batch.batchUid } }); }
async function retry(document: KnowledgeDocument) { if (!selected.value) return; await knowledgeApi.retry(selected.value.knowledgeBaseUid, document.documentUid); await loadDocuments(); }
async function openBuild(document: KnowledgeDocument) { if (!selected.value || !document.importBatchUid) return; await router.push({ name: "knowledge-import", params: { knowledgeBaseUid: selected.value.knowledgeBaseUid, batchUid: document.importBatchUid } }); }
async function reindex(document: KnowledgeDocument) { if (!selected.value) return; const batch = await knowledgeApi.reindexSession(selected.value.knowledgeBaseUid, document.documentUid); await router.push({ name: "knowledge-import", params: { knowledgeBaseUid: selected.value.knowledgeBaseUid, batchUid: batch.batchUid } }); }
async function removeUploaded(document: KnowledgeDocument) { if (!selected.value) return; await knowledgeApi.deleteUploadedDocument(selected.value.knowledgeBaseUid, document.documentUid); await loadDocuments(); }
async function openChunks(document: KnowledgeDocument) { if (!selected.value) return; await router.push({ name: "knowledge-chunks", params: { knowledgeBaseUid: selected.value.knowledgeBaseUid, documentUid: document.documentUid } }); }
async function search() { if (!selected.value || !searchQuery.value.trim() || searching.value) return; searching.value = true; searchDiagnostics.value = []; try { const response = await knowledgeApi.search(selected.value.knowledgeBaseUid, searchQuery.value.trim()); hits.value = response.hits || []; searchDiagnostics.value = response.diagnostics || []; } finally { searching.value = false; } }
async function copyCollectionName() { if (!selected.value?.vectorCollectionName) return; await navigator.clipboard.writeText(selected.value.vectorCollectionName); message.success(t("pages.knowledge.messages.collectionCopied")); }
function openDocumentDetails(document: KnowledgeDocument) { detailDocument.value = document; documentDrawerVisible.value = true; }
function statusType(status: string) { return status === "READY" || status === "ACTIVE" ? "success" : status === "FAILED" || status === "ERROR" ? "error" : "warning"; }
function retrievalSourceLabel(source: string) { return t(`pages.knowledge.retrievalSources.${source}`); }
function searchDiagnosticLabel(code: string) { return t(`pages.knowledge.searchDiagnostics.${code}`); }
function dismissSearchDiagnostic(code: string) { searchDiagnostics.value = searchDiagnostics.value.filter(item => item.code !== code); }
function closeBaseDetail() { selected.value = null; }
function syncEmbeddingSelection() {
  const nextProvider = embeddingProviders.value.find((provider) => provider.providerId === form.embeddingProviderId)
    ?? embeddingProviders.value[0]
    ?? null;
  form.embeddingProviderId = nextProvider?.providerId ?? "";
  const nextModel = nextProvider?.models.find((model) => model.id === form.embeddingModelId)
    ?? nextProvider?.models[0]
    ?? null;
  form.embeddingModelId = nextModel?.id ?? "";
}
function updateEmbeddingProvider(providerId: string) {
  form.embeddingProviderId = providerId;
  form.embeddingModelId = "";
  syncEmbeddingSelection();
}
function openCreateModal() {
  syncEmbeddingSelection();
  createVisible.value = true;
}
onMounted(async () => {
  await Promise.all([loadBases(), loadEmbeddingModels()]);
});
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer); });
</script>

<template>
  <div class="page-frame app-page-shell"><div class="app-layout app-layout-responsive"><DirectoryRail />
    <main class="app-main-content"><div class="app-page-content knowledge-page" :class="{ 'knowledge-page--detail': !!selected }">
      <AppPageHeader v-if="!selected" :title="t('pages.knowledge.title')" :subtitle="t('pages.knowledge.subtitle')" />
      <NAlert v-if="!selected && !vectorAvailable" type="warning" :title="t('pages.knowledge.vectorUnavailable')" />
      <NSpin v-if="!selected" :show="loading"><section class="knowledge-grid">
        <button class="knowledge-create-card" type="button" @click="openCreateModal"><Plus :size="28"/><strong>{{ t("pages.knowledge.createCard.title") }}</strong><span>{{ t("pages.knowledge.createCard.subtitle") }}</span></button>
        <button v-for="base in bases" :key="base.knowledgeBaseUid" class="knowledge-card" type="button" @click="openBase(base)">
          <div class="card-heading"><Database :size="20"/><strong>{{ base.name }}</strong><NTag size="small" :type="statusType(base.status)">{{ base.status }}</NTag></div>
          <p>{{ base.description }}</p><div class="metrics"><span>{{ base.documentCount }} {{ t("pages.knowledge.units.documents") }}</span><span>{{ base.chunkCount }} {{ t("pages.knowledge.units.chunks") }}</span></div>
        </button><NEmpty v-if="!bases.length" :description="t('pages.knowledge.empty')" />
      </section></NSpin>

      <template v-if="selected">
        <div class="detail-back-row">
          <button type="button" class="detail-back-btn" @click="closeBaseDetail">
            <ChevronLeft :size="14" />
            <span>{{ t("pages.knowledge.backShort") }}</span>
          </button>
        </div>
        <AppPageHeader :title="selected.name" :subtitle="selected.description || undefined">
          <template #actions>
            <div class="detail-header-actions">
              <NButton secondary @click="configDrawerVisible = true">{{ t("pages.knowledge.indexConfig.open") }}</NButton>
            </div>
          </template>
        </AppPageHeader>
        <div class="detail-grid"><NCard :title="t('pages.knowledge.documents')">
          <template #header-extra>
            <NButton type="primary" size="small" @click="startImport"><template #icon><Upload/></template>{{ t("pages.knowledge.upload") }}</NButton>
          </template>
          <NEmpty v-if="!documents.length" :description="t('pages.knowledge.noDocuments')"/>
          <KnowledgeDocumentRow v-for="document in documents" :key="document.documentUid" :document="document" @retry="retry(document)" @build="openBuild(document)" @reindex="reindex(document)" @remove="removeUploaded(document)" @details="openDocumentDetails(document)" @chunks="openChunks(document)" />
        </NCard><NCard :title="t('pages.knowledge.searchTest')"><div class="search-bar"><NInput v-model:value="searchQuery" :placeholder="t('pages.knowledge.searchPlaceholder')" :disabled="searching" @keyup.enter="search"/><NButton type="primary" :loading="searching" :disabled="!searchQuery.trim()" @click="search"><template v-if="!searching" #icon><Search/></template></NButton></div><NSpin :show="searching"><NAlert v-for="diagnostic in searchDiagnostics" :key="diagnostic.code" type="warning" closable class="search-diagnostic" @close="dismissSearchDiagnostic(diagnostic.code)">{{ searchDiagnosticLabel(diagnostic.code) }}</NAlert><NEmpty v-if="!hits.length" :description="t('pages.knowledge.noHits')"/><div v-for="hit in hits" :key="hit.citationId" class="hit"><strong>[{{ hit.citationId }}] {{ hit.documentName }}</strong><span>{{ hit.pageFrom ? `${t('pages.knowledge.page')} ${hit.pageFrom}` : '' }} {{ hit.sectionPath }}</span><p>{{ hit.excerpt }}</p><div class="hit-scores"><NTag size="small">{{ t('pages.knowledge.fusedScore') }} {{ (hit.rrfScore ?? hit.score).toFixed(3) }}</NTag><NTag v-if="hit.rerankScore != null" size="small" type="warning">{{ t('pages.knowledge.rerankScore') }} {{ hit.rerankScore.toFixed(3) }}</NTag><NTag v-if="hit.denseScore != null" size="small" type="info">{{ t('pages.knowledge.denseScore') }} {{ hit.denseScore.toFixed(3) }}</NTag><NTag v-if="hit.bm25Score != null" size="small" type="success">{{ t('pages.knowledge.bm25Score') }} {{ hit.bm25Score.toFixed(3) }}</NTag><NTag v-for="source in hit.retrievalSources" :key="source" size="small" :bordered="false">{{ retrievalSourceLabel(source) }}</NTag></div></div></NSpin></NCard></div>
      </template>
    </div></main>
  </div></div>
  <NDrawer v-model:show="configDrawerVisible" placement="right" :width="420" resizable>
    <NDrawerContent v-if="selected" :title="t('pages.knowledge.indexConfig.title')" closable>
      <NAlert
        v-if="embeddingFailureDocument"
        type="warning"
        class="knowledge-meta-alert"
        :title="t('pages.knowledge.indexConfig.warningTitle')"
      >
        {{ t("pages.knowledge.indexConfig.warningBody") }}
      </NAlert>
      <div class="knowledge-meta-grid">
        <div class="knowledge-meta-item">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.indexConfig.model") }}</span>
          <strong>{{ selected.embeddingModelId }}</strong>
        </div>
        <div class="knowledge-meta-item">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.indexConfig.provider") }}</span>
          <strong>{{ selected.embeddingProviderId }}</strong>
        </div>
        <div class="knowledge-meta-item">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.indexConfig.dimension") }}</span>
          <strong>{{ selected.embeddingDimension }}</strong>
        </div>
      </div>
      <div class="knowledge-meta-secondary">
        <div class="knowledge-meta-collection">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.indexConfig.collection") }}</span>
          <code>{{ selected.vectorCollectionName }}</code>
        </div>
        <NButton secondary size="small" @click="copyCollectionName">
          <template #icon><Copy :size="14" /></template>
          {{ t("pages.knowledge.indexConfig.copy") }}
        </NButton>
      </div>
    </NDrawerContent>
  </NDrawer>
  <NDrawer v-model:show="documentDrawerVisible" placement="right" :width="420" resizable>
    <NDrawerContent v-if="detailDocument" :title="detailDocument.displayName" closable>
      <div class="knowledge-meta-grid">
        <div class="knowledge-meta-item">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.status") }}</span>
          <strong>{{ t(`pages.knowledge.jobStatuses.${detailDocument.jobStatus || "COMPLETED"}`) }}</strong>
        </div>
        <div class="knowledge-meta-item">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.stage") }}</span>
          <strong>{{ detailDocument.status === "UPLOADED" ? t("pages.knowledge.documentStatuses.awaitingBuild") : t(`pages.knowledge.stages.${detailDocument.stage || "QUEUED"}`) }}</strong>
        </div>
        <div class="knowledge-meta-item" v-if="detailDocument.totalPages">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.pages") }}</span>
          <strong>{{ detailDocument.processedPages }}/{{ detailDocument.totalPages }}</strong>
        </div>
        <div class="knowledge-meta-item" v-if="detailDocument.totalChunks">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.chunks") }}</span>
          <strong>{{ detailDocument.processedChunks }}/{{ detailDocument.totalChunks }}</strong>
        </div>
        <div class="knowledge-meta-item" v-if="detailDocument.attemptCount || detailDocument.maxAttempts">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.attempts") }}</span>
          <strong>{{ detailDocument.attemptCount }}/{{ detailDocument.maxAttempts }}</strong>
        </div>
        <div class="knowledge-meta-item" v-if="detailDocument.nextRetryTime">
          <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.nextRetry") }}</span>
          <strong>{{ detailDocument.nextRetryTime }}</strong>
        </div>
      </div>
      <div v-if="detailDocument.failureMessage" class="knowledge-detail-block">
        <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.failure") }}</span>
        <p class="knowledge-detail-text error">{{ detailDocument.failureMessage }}</p>
      </div>
      <div v-if="detailDocument.parseWarnings?.filter(item => item !== 'PDF_PREPROCESSING_APPLIED').length" class="knowledge-detail-block">
        <span class="knowledge-meta-label">{{ t("pages.knowledge.documentDetail.warnings") }}</span>
        <p v-for="warning in detailDocument.parseWarnings.filter(item => item !== 'PDF_PREPROCESSING_APPLIED')" :key="warning" class="knowledge-detail-text">
          {{ t(`pages.knowledge.parseWarnings.${warning}`) }}
        </p>
      </div>
    </NDrawerContent>
  </NDrawer>
  <NModal v-model:show="createVisible" preset="card" :title="t('pages.knowledge.createCard.title')" :style="{ width: 'min(560px, calc(100vw - 32px))' }"><div class="form"><NForm label-placement="top"><NFormItem :show-label="false"><NInput v-model:value="form.name" :placeholder="t('pages.knowledge.form.name')"/></NFormItem><NFormItem :show-label="false"><NInput v-model:value="form.description" type="textarea" :placeholder="t('pages.knowledge.form.description')"/></NFormItem><NFormItem :label="t('pages.knowledge.form.provider')"><NSelect :value="form.embeddingProviderId" :options="embeddingProviderOptions" :placeholder="t('pages.knowledge.form.provider')" :disabled="!hasEmbeddingModels" @update:value="updateEmbeddingProvider(String($event || ''))"/></NFormItem><NFormItem :label="t('pages.knowledge.form.model')"><NSelect :value="form.embeddingModelId" :options="embeddingModelOptions" :placeholder="t('pages.knowledge.form.model')" :disabled="!form.embeddingProviderId || !hasEmbeddingModels" @update:value="form.embeddingModelId = String($event || '')"/></NFormItem><NFormItem :label="t('pages.knowledge.form.dimension')"><NInputNumber v-model:value="form.embeddingDimension" :min="1" :placeholder="t('pages.knowledge.form.dimension')"/></NFormItem></NForm><NAlert v-if="!hasEmbeddingModels" type="warning">{{ t("errors.noAvailableModel") }}</NAlert><div class="form-actions"><NButton @click="createVisible = false">{{ t("common.cancel") }}</NButton><NButton type="primary" :disabled="!form.name || !form.embeddingProviderId || !form.embeddingModelId" @click="createBase">{{ t("pages.knowledge.createCard.action") }}</NButton></div></div></NModal>
</template>

<style scoped>
.knowledge-page{gap:var(--space-5);min-width:0}.knowledge-page--detail{gap:var(--space-4)}.knowledge-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:var(--space-4)}.knowledge-create-card,.knowledge-card{min-height:220px;padding:var(--space-5);border:1px solid var(--color-border-soft);border-radius:var(--radius-2xl);background:var(--color-bg-surface);text-align:left;display:flex;flex-direction:column;gap:var(--space-3);cursor:pointer}.knowledge-create-card{align-items:center;justify-content:center;text-align:center;border-style:dashed}.card-heading,.metrics,.detail-header-actions,.document-row,.search-bar,.hit-scores,.knowledge-meta-secondary{display:flex;align-items:center;gap:var(--space-3)}.card-heading strong{flex:1}.metrics{margin-top:auto;color:var(--color-text-muted);justify-content:space-between}.detail-back-row{display:flex;align-items:center}.detail-back-btn{display:inline-flex;align-items:center;gap:var(--space-1);padding:0;border:0;background:transparent;color:var(--color-text-secondary);font-size:var(--text-body-size);cursor:pointer}.detail-back-btn:hover{color:var(--color-brand-400)}.knowledge-meta-grid{display:grid;grid-template-columns:1fr;gap:var(--space-4)}.knowledge-meta-item{display:flex;flex-direction:column;gap:var(--space-1)}.knowledge-meta-label{font-size:var(--font-size-sm);color:var(--color-text-muted)}.knowledge-meta-secondary{justify-content:space-between;margin-top:var(--space-4);padding-top:var(--space-4);border-top:1px solid var(--color-border-soft)}.knowledge-meta-collection{display:flex;flex-direction:column;gap:var(--space-1);min-width:0}.knowledge-meta-collection code{display:block;max-width:100%;overflow:auto;white-space:nowrap}.knowledge-meta-alert{margin-bottom:var(--space-4)}.knowledge-detail-block{margin-top:var(--space-4);padding-top:var(--space-4);border-top:1px solid var(--color-border-soft)}.knowledge-detail-text{margin:var(--space-2) 0 0;white-space:pre-wrap}.upload-results{display:flex;flex-direction:column;gap:var(--space-2);padding:var(--space-3);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg);background:var(--color-bg-surface)}.upload-result{display:flex;align-items:center;gap:var(--space-3);font-size:var(--font-size-sm)}.upload-result>span:first-child{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.detail-grid{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(320px,.8fr);gap:var(--space-4);min-width:0}.detail-grid > *{min-width:0}.detail-grid :deep(.n-card){min-width:0}.document-row{padding:var(--space-3) 0;border-bottom:1px solid var(--color-border-soft)}.document-main{display:flex;flex:1;min-width:0;flex-direction:column;gap:var(--space-2)}.job-detail{color:var(--color-text-muted);font-size:var(--font-size-sm)}.error{color:var(--color-danger)}.search-bar{margin-bottom:var(--space-4)}.search-bar :deep(.n-input){min-width:0;flex:1}.search-diagnostic{margin-bottom:var(--space-3)}.hit{padding:var(--space-3);margin-bottom:var(--space-3);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg);display:flex;flex-direction:column;gap:var(--space-2)}.hit p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.hit-scores{flex-wrap:wrap}.form{display:flex;flex-direction:column;gap:var(--space-3)}.form :deep(.n-form-item){margin-bottom:0}.form :deep(.n-form-item.n-form-item--top-labelled .n-form-item-blank){padding-top:0}.form-actions{display:flex;justify-content:flex-end;gap:var(--space-3)}@media(max-width:900px){.detail-grid{grid-template-columns:1fr}.detail-header-actions,.knowledge-meta-secondary{flex-wrap:wrap}}
</style>
