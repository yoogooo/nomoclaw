<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ChevronLeft, Copy } from "lucide-vue-next";
import { NButton, NCard, NEmpty, NSelect, NSpin, NTag, NTree } from "naive-ui";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { knowledgeApi, type KnowledgeChunk, type KnowledgeDocument, type KnowledgeDocumentNode } from "@/api/knowledgeApi";
import { message } from "@/discrete";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const baseUid = computed(() => String(route.params.knowledgeBaseUid || ""));
const documentUid = computed(() => String(route.params.documentUid || ""));
const document = ref<KnowledgeDocument | null>(null);
const chunks = ref<KnowledgeChunk[]>([]);
const structure = ref<KnowledgeDocumentNode[]>([]);
const structureFilter = ref<"VALID" | "DIAGNOSTIC" | "ALL">("VALID");
const loading = ref(false);

const structureStats = computed(() => {
  const nodes = flatten(structure.value);
  return {
    total: nodes.length,
    valid: nodes.filter(node => node.indexable).length,
    diagnostic: nodes.filter(node => !node.indexable).length
  };
});
const structureOptions = computed(() => structure.value.map(node => filterNode(node))
  .filter((node): node is KnowledgeDocumentNode => node !== null).map(toTreeOption));
const structureFilterOptions = computed(() => [
  { label: t("pages.knowledge.chunks.validNodes"), value: "VALID" },
  { label: t("pages.knowledge.chunks.diagnosticNodes"), value: "DIAGNOSTIC" },
  { label: t("pages.knowledge.chunks.allNodes"), value: "ALL" }
]);

function toTreeOption(node: KnowledgeDocumentNode): { key: string; label: string; children?: ReturnType<typeof toTreeOption>[] } {
  const heading = [node.code, node.title].filter(Boolean).join(" ") || node.type;
  const filtered = node.indexable ? "" : ` · ${t("pages.knowledge.chunks.filtered")}`;
  return {
    key: node.nodeUid,
    label: `${heading} · ${node.nodeRole || node.type} · ${node.detectionSource} · ${Math.round(node.confidence * 100)}%${filtered}`,
    children: node.children?.map(toTreeOption)
  };
}

function flatten(nodes: KnowledgeDocumentNode[]): KnowledgeDocumentNode[] {
  return nodes.flatMap(node => [node, ...flatten(node.children || [])]);
}

function filterNode(node: KnowledgeDocumentNode): KnowledgeDocumentNode | null {
  const children = (node.children || []).map(child => filterNode(child)).filter(Boolean) as KnowledgeDocumentNode[];
  const matches = structureFilter.value === "ALL"
    || (structureFilter.value === "VALID" ? node.indexable : !node.indexable);
  if (!matches && !children.length) return null;
  return { ...node, children };
}

async function load() {
  loading.value = true;
  try {
    const [documentResponse, chunksResponse, structureResponse] = await Promise.all([
      knowledgeApi.document(baseUid.value, documentUid.value),
      knowledgeApi.chunks(baseUid.value, documentUid.value),
      knowledgeApi.structure(baseUid.value, documentUid.value)
    ]);
    document.value = documentResponse;
    chunks.value = chunksResponse.items;
    structure.value = structureResponse.items;
  } finally {
    loading.value = false;
  }
}

function returnToBase() {
  return router.push({ name: "knowledge", query: { base: baseUid.value } });
}

async function copyChunk(chunk: KnowledgeChunk) {
  await navigator.clipboard.writeText(chunk.content);
  message.success(t("pages.knowledge.chunks.copied"));
}

function pageLabel(chunk: KnowledgeChunk) {
  if (!chunk.pageFrom) return "-";
  return chunk.pageTo && chunk.pageTo !== chunk.pageFrom ? `${chunk.pageFrom}-${chunk.pageTo}` : String(chunk.pageFrom);
}

onMounted(() => void load());
</script>

<template>
  <div class="page-frame app-page-shell"><div class="app-layout app-layout-responsive"><DirectoryRail />
    <main class="app-main-content"><div class="app-page-content chunks-page">
      <div class="detail-back-row">
        <button type="button" class="detail-back-btn" @click="returnToBase">
          <ChevronLeft :size="14" />
          <span>{{ t("pages.knowledge.backShort") }}</span>
        </button>
      </div>
      <AppPageHeader
        :title="t('pages.knowledge.chunks.title')"
        :subtitle="document?.displayName || t('pages.knowledge.chunks.subtitle')"
      />

      <NSpin :show="loading">
        <NCard v-if="document" class="summary-card">
          <div class="summary-grid">
            <div><span>{{ t("pages.knowledge.documentDetail.status") }}</span><strong>{{ document.status }}</strong></div>
            <div><span>{{ t("pages.knowledge.documentDetail.pages") }}</span><strong>{{ document.pageCount }}</strong></div>
            <div><span>{{ t("pages.knowledge.documentDetail.chunks") }}</span><strong>{{ chunks.length }}</strong></div>
          </div>
        </NCard>

        <NCard v-if="structure.length" class="structure-card">
          <template #header>
            <div class="structure-header">
              <strong>{{ t("pages.knowledge.chunks.structure") }}</strong>
              <NSelect v-model:value="structureFilter" size="small" :options="structureFilterOptions" />
            </div>
          </template>
          <div class="structure-stats">
            <span>{{ t("pages.knowledge.chunks.totalNodes") }} {{ structureStats.total }}</span>
            <span>{{ t("pages.knowledge.chunks.validNodesCount") }} {{ structureStats.valid }}</span>
            <span>{{ t("pages.knowledge.chunks.diagnosticNodesCount") }} {{ structureStats.diagnostic }}</span>
          </div>
          <NTree block-line :data="structureOptions" />
        </NCard>

        <NEmpty v-if="!chunks.length" :description="t('pages.knowledge.chunks.empty')" />
        <div class="chunk-list">
          <NCard v-for="chunk in chunks" :key="chunk.chunkUid" class="chunk-card">
            <template #header>
              <div class="chunk-header">
                <strong>#{{ chunk.chunkIndex + 1 }}</strong>
                <NTag size="small">{{ t("pages.knowledge.page") }} {{ pageLabel(chunk) }}</NTag>
                <NTag size="small">{{ chunk.tokenCount }} tokens</NTag>
                <NTag v-if="chunk.sectionPath" size="small" :bordered="false">{{ chunk.sectionPath }}</NTag>
              </div>
            </template>
            <template #header-extra>
              <NButton quaternary circle :aria-label="t('pages.knowledge.chunks.copy')" @click="copyChunk(chunk)">
                <Copy :size="16" />
              </NButton>
            </template>
            <pre class="chunk-content">{{ chunk.content }}</pre>
            <div class="chunk-meta">
              <span>{{ chunk.chunkUid }}</span>
              <span>{{ chunk.charStart ?? "-" }}-{{ chunk.charEnd ?? "-" }}</span>
              <span v-if="chunk.metadata?.documentNodeUid">node: {{ chunk.metadata.documentNodeUid }}</span>
              <span>{{ chunk.status }}</span>
            </div>
          </NCard>
        </div>
      </NSpin>
    </div></main>
  </div></div>
</template>

<style scoped>
.chunks-page{gap:var(--space-4)}
.detail-back-row{display:flex;align-items:center}
.detail-back-btn{display:inline-flex;align-items:center;gap:var(--space-1);padding:0;border:0;background:transparent;color:var(--color-text-secondary);font-size:var(--text-body-size);cursor:pointer}
.detail-back-btn:hover{color:var(--color-brand-400)}
.summary-card{margin-bottom:var(--space-4)}
.structure-card{margin-bottom:var(--space-4)}
.structure-header{display:flex;align-items:center;justify-content:space-between;gap:var(--space-3)}
.structure-header .n-select{width:180px}
.structure-stats{display:flex;flex-wrap:wrap;gap:var(--space-3);margin-bottom:var(--space-3);color:var(--color-text-muted);font-size:var(--font-size-sm)}
.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--space-4)}
.summary-grid div{display:flex;flex-direction:column;gap:var(--space-1)}
.summary-grid span,.chunk-meta{color:var(--color-text-muted);font-size:var(--font-size-sm)}
.chunk-list{display:flex;flex-direction:column;gap:var(--space-4)}
.chunk-header{display:flex;align-items:center;flex-wrap:wrap;gap:var(--space-2)}
.chunk-content{margin:0;white-space:pre-wrap;word-break:break-word;font-family:inherit;line-height:1.75;color:var(--color-text-primary)}
.chunk-meta{display:flex;flex-wrap:wrap;gap:var(--space-3);margin-top:var(--space-3);padding-top:var(--space-3);border-top:1px solid var(--color-border-soft)}
@media(max-width:760px){.summary-grid{grid-template-columns:1fr}.chunk-header{align-items:flex-start}}
</style>
