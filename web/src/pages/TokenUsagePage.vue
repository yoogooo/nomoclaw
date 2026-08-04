<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { NButton, NDataTable, NDatePicker, NInput, NSelect, NTag } from "naive-ui";
import type { DataTableColumns } from "naive-ui";
import { useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import AppPaginationBar from "@/components/layout/AppPaginationBar.vue";
import { tokenUsageApi } from "@/api/tokenUsageApi";
import type { TokenUsageOverview, TokenUsageRecord } from "@/types/api";

const router = useRouter();
const period = ref("30"); const scene = ref(""); const provider = ref(""); const model = ref(""); const customRange = ref<[number, number] | null>(null); const page = ref(1); const pageSize = ref(20);
const overview = ref<TokenUsageOverview | null>(null); const records = ref<TokenUsageRecord[]>([]); const total = ref(0); const loading = ref(false);
const sceneOptions = [{ label: "全部场景", value: "" }, { label: "聊天推理", value: "CHAT_REASONING" }, { label: "聊天总结", value: "CHAT_SUMMARY" }, { label: "锦囊总结", value: "TIP_SUMMARY" }];
const filter = computed(() => ({
  from: customRange.value ? new Date(customRange.value[0]).toISOString().slice(0, 10) : new Date(Date.now() - (Number(period.value) - 1) * 86400000).toISOString().slice(0, 10),
  to: customRange.value ? new Date(customRange.value[1]).toISOString().slice(0, 10) : "",
  scene: scene.value, provider: provider.value.trim(), model: model.value.trim()
}));
const columns = computed<DataTableColumns<TokenUsageRecord>>(() => [
  { title: "时间", key: "occurredTime", width: 175 }, { title: "场景", key: "scene", width: 130 },
  { title: "模型", key: "modelName", render: row => `${row.provider}/${row.modelName}` },
  { title: "输入", key: "inputTokens", width: 80 }, { title: "缓存", key: "cachedInputTokens", width: 80 },
  { title: "输出", key: "outputTokens", width: 80 }, { title: "总计", key: "totalTokens", width: 90 },
  { title: "状态", key: "usageAvailable", width: 100, render: row => row.usageAvailable ? "已记录" : "未知" }
]);
async function load() { loading.value = true; try { const [summary, result] = await Promise.all([tokenUsageApi.overview(filter.value), tokenUsageApi.page(filter.value, page.value, pageSize.value)]); overview.value = summary; records.value = result.items; total.value = result.total; } finally { loading.value = false; } }
function changeFilter() { page.value = 1; void load(); }
function openRecord(row: TokenUsageRecord) { if (row.conversationUid) void router.push({ path: "/", query: { conversationUid: row.conversationUid } }); }
onMounted(() => void load());
</script>
<template>
  <div class="page-frame app-page-shell"><div class="app-layout app-layout-responsive"><DirectoryRail /><main class="app-main-content"><div class="app-page-content">
    <AppPageHeader title="Token 用量" subtitle="聊天与锦囊总结的模型 token 消耗" />
    <section class="surface-card usage-filter"><n-select v-model:value="period" :options="[{label:'今天',value:'1'},{label:'最近 7 天',value:'7'},{label:'最近 30 天',value:'30'}]" @update:value="() => { customRange = null; changeFilter(); }" /><n-date-picker v-model:value="customRange" type="daterange" clearable @update:value="changeFilter" /><n-select v-model:value="scene" :options="sceneOptions" @update:value="changeFilter" /><n-input v-model:value="provider" placeholder="Provider" clearable @keyup.enter="changeFilter" /><n-input v-model:value="model" placeholder="模型" clearable @keyup.enter="changeFilter" /><n-button :loading="loading" @click="load">刷新</n-button></section>
    <section v-if="overview" class="usage-cards"><div v-for="item in [{label:'总 Token',value:overview.totalTokens},{label:'输入',value:overview.inputTokens},{label:'缓存输入',value:overview.cachedInputTokens},{label:'输出',value:overview.outputTokens},{label:'调用',value:overview.invocationCount}]" :key="item.label" class="surface-card"><div class="meta-label">{{ item.label }}</div><strong>{{ item.value.toLocaleString() }}</strong></div></section>
    <section v-if="overview" class="surface-card"><div class="surface-card-title">按模型</div><div class="usage-breakdown"><NTag v-for="item in overview.byModel" :key="item.key">{{ item.key }} · {{ item.totalTokens.toLocaleString() }}</NTag></div><div class="surface-card-title usage-title">按场景</div><div class="usage-breakdown"><NTag v-for="item in overview.byScene" :key="item.key">{{ item.key }} · {{ item.totalTokens.toLocaleString() }}</NTag></div><div class="surface-card-title usage-title">每日趋势</div><div class="usage-breakdown"><NTag v-for="item in overview.byDay" :key="item.key">{{ item.key }} · {{ item.totalTokens.toLocaleString() }}</NTag></div><p v-if="overview.unavailableUsageCount" class="usage-unknown">{{ overview.unavailableUsageCount }} 次调用未返回 token 用量。</p></section>
    <section class="surface-card"><div class="surface-card-title">调用明细</div><n-data-table :columns="columns" :data="records" :loading="loading" :row-props="row => ({ onClick: () => openRecord(row) })" /><AppPaginationBar :page="page" :page-size="pageSize" :total="total" @update:page="value => { page = value; load(); }" @update:page-size="value => { pageSize = value; changeFilter(); }" /></section>
  </div></main></div></div>
</template>
<style scoped>.usage-filter,.usage-cards{display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap}.usage-filter :deep(.n-select),.usage-filter :deep(.n-input){width:160px}.usage-cards{align-items:stretch}.usage-cards .surface-card{flex:1}.usage-cards strong{font-size:24px}.usage-breakdown{display:flex;flex-wrap:wrap;gap:8px}.usage-title{margin-top:16px}.usage-unknown{color:var(--text-color-3)}</style>
