<script setup lang="ts">
import { computed } from "vue";
import { FileText, MoreHorizontal } from "lucide-vue-next";
import {
  NButton, NDropdown, NProgress, NTag, type DropdownOption, type TagProps
} from "naive-ui";
import { useI18n } from "vue-i18n";
import type { KnowledgeDocument } from "@/api/knowledgeApi";
import { formatDateTime } from "@/utils/format";

const props = defineProps<{ document: KnowledgeDocument }>();
const emit = defineEmits<{ retry: []; build: []; reindex: []; remove: []; details: [] }>();
const { t } = useI18n();

const processing = computed(() => ["PENDING", "RUNNING", "RETRY_WAIT"].includes(props.document.jobStatus));
const detailVisible = computed(() => processing.value || props.document.jobStatus === "FAILED");
const actions = computed<DropdownOption[]>(() => {
  if (props.document.status === "UPLOADED") {
    return [
      { label: t("pages.knowledge.actions.configureBuild"), key: "build" },
      { label: t("common.delete"), key: "remove" }
    ];
  }
  if (props.document.jobStatus === "FAILED") return [{ label: t("pages.knowledge.retry"), key: "retry" }];
  if (props.document.status === "READY") return [{ label: t("pages.knowledge.actions.reindex"), key: "reindex" }];
  return [];
});

function selectAction(key: string | number) {
  if (key === "build") emit("build");
  else if (key === "remove") emit("remove");
  else if (key === "retry") emit("retry");
  else if (key === "reindex") emit("reindex");
}

function statusType(status: string): TagProps["type"] {
  if (status === "COMPLETED" || (!status && props.document.status === "READY")) return "success";
  if (status === "FAILED") return "error";
  return status === "RETRY_WAIT" || props.document.status === "UPLOADED" ? "warning" : "info";
}

function statusLabel() {
  if (props.document.status === "UPLOADED") return t("pages.knowledge.documentStatuses.UPLOADED");
  return t(`pages.knowledge.jobStatuses.${props.document.jobStatus || "COMPLETED"}`);
}

function stageLabel() {
  if (props.document.status === "UPLOADED") return t("pages.knowledge.documentStatuses.awaitingBuild");
  return t(`pages.knowledge.stages.${props.document.stage || "QUEUED"}`);
}

function shouldShowAttempts() {
  return props.document.attemptCount > 1 || ["RETRY_WAIT", "FAILED"].includes(props.document.jobStatus);
}
</script>

<template>
  <div class="document-row">
    <FileText :size="22" />
    <div class="document-main">
      <strong>{{ document.displayName }}</strong>
      <span v-if="document.status === 'UPLOADED'" class="job-detail">{{ stageLabel() }}</span>
      <button v-if="detailVisible" type="button" class="document-detail-link" @click="emit('details')">
        {{ t("pages.knowledge.actions.viewDetails") }}
      </button>
      <span v-if="document.jobStatus === 'RETRY_WAIT' && document.nextRetryTime" class="job-detail">
        {{ t("pages.knowledge.nextRetry", { time: formatDateTime(document.nextRetryTime) }) }}
      </span>
      <span
        v-for="warning in (document.parseWarnings || []).filter(item => item !== 'PDF_PREPROCESSING_APPLIED')"
        :key="warning"
        class="warning"
      >
        {{ t(`pages.knowledge.parseWarnings.${warning}`) }}
      </span>
      <span v-if="document.failureMessage" :class="{ error: document.jobStatus === 'FAILED' }">
        {{ document.failureMessage }}
      </span>
      <NProgress v-if="processing" type="line" :percentage="document.progressPercent" :show-indicator="false" />
    </div>
    <NTag size="small" :type="statusType(document.jobStatus)">{{ statusLabel() }}</NTag>
    <NDropdown v-if="actions.length" trigger="click" :options="actions" @select="selectAction">
      <NButton quaternary circle :aria-label="t('pages.knowledge.actions.more')"><MoreHorizontal :size="18" /></NButton>
    </NDropdown>
  </div>
</template>

<style scoped>
.document-row{display:flex;align-items:center;gap:var(--space-3);padding:var(--space-3) 0;border-bottom:1px solid var(--color-border-soft)}
.document-main{display:flex;flex:1;min-width:0;flex-direction:column;gap:var(--space-2)}
.document-main strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.job-detail{color:var(--color-text-muted);font-size:var(--font-size-sm)}
.document-detail-link{padding:0;border:0;background:transparent;color:var(--color-brand-400);font-size:var(--font-size-sm);text-align:left;cursor:pointer}
.document-detail-link:hover{text-decoration:underline}
.warning{color:var(--color-warning);font-size:var(--font-size-sm)}
.error{color:var(--color-danger)}
</style>
