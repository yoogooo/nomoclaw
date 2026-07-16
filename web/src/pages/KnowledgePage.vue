<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { Database, FileText, Plus, RefreshCw, Search, Upload } from "lucide-vue-next";
import { NAlert, NButton, NCard, NEmpty, NInput, NInputNumber, NModal, NProgress, NSpin, NTag } from "naive-ui";
import { useI18n } from "vue-i18n";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { knowledgeApi, type KnowledgeBase, type KnowledgeDocument, type KnowledgeHit } from "@/api/knowledgeApi";
import { message } from "@/discrete";

const { t } = useI18n();
const bases = ref<KnowledgeBase[]>([]);
const documents = ref<KnowledgeDocument[]>([]);
const hits = ref<KnowledgeHit[]>([]);
const selected = ref<KnowledgeBase | null>(null);
const loading = ref(false);
const vectorAvailable = ref(true);
const createVisible = ref(false);
const searchQuery = ref("");
const fileInput = ref<HTMLInputElement>();
const form = reactive({ name: "", description: "", embeddingProviderId: "dashscope", embeddingModelId: "text-embedding-v3", embeddingDimension: 1024 });
let pollTimer: number | undefined;
const processing = computed(() => documents.value.some(item => ["UPLOADED", "PROCESSING"].includes(item.status)));

async function loadBases() { loading.value = true; try { const response = await knowledgeApi.list(); bases.value = response.items; vectorAvailable.value = response.vectorAvailable; } finally { loading.value = false; } }
async function openBase(base: KnowledgeBase) { selected.value = base; hits.value = []; await loadDocuments(); }
async function loadDocuments() { if (!selected.value) return; documents.value = (await knowledgeApi.documents(selected.value.knowledgeBaseUid)).items; schedulePoll(); }
function schedulePoll() { if (pollTimer) window.clearTimeout(pollTimer); if (processing.value) pollTimer = window.setTimeout(() => void loadDocuments(), 1500); }
async function createBase() { const created = await knowledgeApi.create(form); createVisible.value = false; message.success(t("pages.knowledge.messages.created")); await loadBases(); await openBase(created); }
function chooseFiles() { fileInput.value?.click(); }
async function uploadFiles(event: Event) { const input = event.target as HTMLInputElement; const files = Array.from(input.files || []); input.value = ""; if (!selected.value || !files.length) return; await knowledgeApi.upload(selected.value.knowledgeBaseUid, files); message.success(t("pages.knowledge.messages.uploaded")); await loadDocuments(); }
async function retry(document: KnowledgeDocument) { if (!selected.value) return; await knowledgeApi.retry(selected.value.knowledgeBaseUid, document.documentUid); await loadDocuments(); }
async function search() { if (!selected.value || !searchQuery.value.trim()) return; hits.value = await knowledgeApi.search(selected.value.knowledgeBaseUid, searchQuery.value.trim()); }
function statusType(status: string) { return status === "READY" || status === "ACTIVE" ? "success" : status === "FAILED" || status === "ERROR" ? "error" : "warning"; }
function retrievalSourceLabel(source: string) { return t(`pages.knowledge.retrievalSources.${source}`); }
onMounted(() => void loadBases());
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer); });
</script>

<template>
  <div class="page-frame app-page-shell"><div class="app-layout app-layout-responsive"><DirectoryRail />
    <main class="app-main-content"><div class="app-page-content knowledge-page">
      <AppPageHeader :title="t('pages.knowledge.title')" :subtitle="t('pages.knowledge.subtitle')" />
      <NAlert v-if="!vectorAvailable" type="warning" :title="t('pages.knowledge.vectorUnavailable')" />
      <NSpin :show="loading"><section v-if="!selected" class="knowledge-grid">
        <button class="knowledge-create-card" type="button" @click="createVisible = true"><Plus :size="28"/><strong>{{ t("pages.knowledge.createCard.title") }}</strong><span>{{ t("pages.knowledge.createCard.subtitle") }}</span></button>
        <button v-for="base in bases" :key="base.knowledgeBaseUid" class="knowledge-card" type="button" @click="openBase(base)">
          <div class="card-heading"><Database :size="20"/><strong>{{ base.name }}</strong><NTag size="small" :type="statusType(base.status)">{{ base.status }}</NTag></div>
          <p>{{ base.description }}</p><div class="metrics"><span>{{ base.documentCount }} {{ t("pages.knowledge.units.documents") }}</span><span>{{ base.chunkCount }} {{ t("pages.knowledge.units.chunks") }}</span></div>
        </button><NEmpty v-if="!bases.length" :description="t('pages.knowledge.empty')" />
      </section></NSpin>

      <template v-if="selected"><div class="detail-header"><NButton quaternary @click="selected = null">{{ t("pages.knowledge.back") }}</NButton><div><h2>{{ selected.name }}</h2><p>{{ selected.description }}</p></div><NButton type="primary" @click="chooseFiles"><template #icon><Upload/></template>{{ t("pages.knowledge.upload") }}</NButton><input ref="fileInput" hidden type="file" multiple accept=".pdf,.docx,.txt,.md" @change="uploadFiles"></div>
        <div class="detail-grid"><NCard :title="t('pages.knowledge.documents')"><NEmpty v-if="!documents.length" :description="t('pages.knowledge.noDocuments')"/>
          <div v-for="document in documents" :key="document.documentUid" class="document-row"><FileText/><div class="document-main"><strong>{{ document.displayName }}</strong><span v-if="document.failureMessage" class="error">{{ document.failureMessage }}</span><NProgress v-else-if="document.progressPercent < 100" type="line" :percentage="document.progressPercent" :show-indicator="false"/></div><NTag size="small" :type="statusType(document.status)">{{ document.status }}</NTag><NButton v-if="document.status === 'FAILED'" size="small" @click="retry(document)"><RefreshCw/> {{ t("pages.knowledge.retry") }}</NButton></div>
        </NCard><NCard :title="t('pages.knowledge.searchTest')"><div class="search-bar"><NInput v-model:value="searchQuery" :placeholder="t('pages.knowledge.searchPlaceholder')" @keyup.enter="search"/><NButton type="primary" @click="search"><Search/></NButton></div><NEmpty v-if="!hits.length" :description="t('pages.knowledge.noHits')"/><div v-for="hit in hits" :key="hit.citationId" class="hit"><strong>[{{ hit.citationId }}] {{ hit.documentName }}</strong><span>{{ hit.pageFrom ? `${t('pages.knowledge.page')} ${hit.pageFrom}` : '' }} {{ hit.sectionPath }}</span><p>{{ hit.excerpt }}</p><div class="hit-scores"><NTag size="small">{{ t('pages.knowledge.fusedScore') }} {{ hit.score.toFixed(3) }}</NTag><NTag v-if="hit.denseScore != null" size="small" type="info">{{ t('pages.knowledge.denseScore') }} {{ hit.denseScore.toFixed(3) }}</NTag><NTag v-if="hit.bm25Score != null" size="small" type="success">{{ t('pages.knowledge.bm25Score') }} {{ hit.bm25Score.toFixed(3) }}</NTag><NTag v-for="source in hit.retrievalSources" :key="source" size="small" :bordered="false">{{ retrievalSourceLabel(source) }}</NTag></div></div></NCard></div>
      </template>
    </div></main>
  </div></div>
  <NModal v-model:show="createVisible" preset="card" :title="t('pages.knowledge.createCard.title')" class="create-modal"><div class="form"><NInput v-model:value="form.name" :placeholder="t('pages.knowledge.form.name')"/><NInput v-model:value="form.description" type="textarea" :placeholder="t('pages.knowledge.form.description')"/><NInput v-model:value="form.embeddingProviderId" :placeholder="t('pages.knowledge.form.provider')"/><NInput v-model:value="form.embeddingModelId" :placeholder="t('pages.knowledge.form.model')"/><NInputNumber v-model:value="form.embeddingDimension" :min="1" :placeholder="t('pages.knowledge.form.dimension')"/><NButton type="primary" :disabled="!form.name || !form.embeddingProviderId || !form.embeddingModelId" @click="createBase">{{ t("pages.knowledge.createCard.action") }}</NButton></div></NModal>
</template>

<style scoped>
.knowledge-page{gap:var(--space-5)}.knowledge-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:var(--space-4)}.knowledge-create-card,.knowledge-card{min-height:220px;padding:var(--space-5);border:1px solid var(--color-border-soft);border-radius:var(--radius-2xl);background:var(--color-bg-surface);text-align:left;display:flex;flex-direction:column;gap:var(--space-3);cursor:pointer}.knowledge-create-card{align-items:center;justify-content:center;text-align:center;border-style:dashed}.card-heading,.metrics,.detail-header,.document-row,.search-bar,.hit-scores{display:flex;align-items:center;gap:var(--space-3)}.card-heading strong{flex:1}.metrics{margin-top:auto;color:var(--color-text-muted);justify-content:space-between}.detail-header>div{flex:1}.detail-header h2,.detail-header p{margin:0}.detail-grid{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(320px,.8fr);gap:var(--space-4)}.document-row{padding:var(--space-3) 0;border-bottom:1px solid var(--color-border-soft)}.document-main{display:flex;flex:1;min-width:0;flex-direction:column;gap:var(--space-2)}.error{color:var(--color-danger)}.search-bar{margin-bottom:var(--space-4)}.hit{padding:var(--space-3);margin-bottom:var(--space-3);border:1px solid var(--color-border-soft);border-radius:var(--radius-lg);display:flex;flex-direction:column;gap:var(--space-2)}.hit p{margin:0;white-space:pre-wrap}.hit-scores{flex-wrap:wrap}.form{display:flex;flex-direction:column;gap:var(--space-3)}.create-modal{width:min(560px,90vw)}@media(max-width:900px){.detail-grid{grid-template-columns:1fr}.detail-header{flex-wrap:wrap}}
</style>
