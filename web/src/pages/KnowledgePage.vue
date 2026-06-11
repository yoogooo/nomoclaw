<script setup lang="ts">
import { Database, Sparkles } from "lucide-vue-next";
import { NButton, NTag } from "naive-ui";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";

type KnowledgeVisibility = "private" | "team" | "restricted";
type KnowledgeStatus = "healthy" | "indexing" | "warning";

interface KnowledgeBaseCard {
  id: string;
  name: string;
  description: string;
  visibility: KnowledgeVisibility;
  status: KnowledgeStatus;
  documents: number;
  hitRate: number;
  updatedAt: string;
}

const { t } = useI18n();
const router = useRouter();

const knowledgeBases: KnowledgeBaseCard[] = [
  {
    id: "kb_legal_contracts",
    name: "合同知识库",
    description: "覆盖采购、付款、终止、违约与模板条款，适合法务与采购问题检索。",
    visibility: "restricted",
    status: "healthy",
    documents: 182,
    hitRate: 92,
    updatedAt: "10 分钟前"
  },
  {
    id: "kb_product_faq",
    name: "产品 FAQ",
    description: "面向客服与运营的标准问答、发布说明和变更历史。",
    visibility: "team",
    status: "indexing",
    documents: 96,
    hitRate: 88,
    updatedAt: "2 小时前"
  },
  {
    id: "kb_customer_a",
    name: "客户 A 项目库",
    description: "客户 A 的需求纪要、接口文档、验收标准与周报沉淀。",
    visibility: "private",
    status: "warning",
    documents: 41,
    hitRate: 95,
    updatedAt: "昨天 18:20"
  }
];

function visibilityLabel(visibility: KnowledgeVisibility) {
  return t(`pages.knowledge.visibility.${visibility}`);
}

function openKnowledgeBase(item: KnowledgeBaseCard) {
  void router.push({
    path: "/",
    query: {
      knowledgeBaseId: item.id
    }
  });
}

function createKnowledgeBase() {
  void router.push({
    path: "/agents",
    query: { tab: "docs" }
  });
}
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content knowledge-page">
          <AppPageHeader
            :title="t('pages.knowledge.title')"
            :subtitle="t('pages.knowledge.subtitle')"
          />

          <section class="knowledge-grid">
            <button class="knowledge-create-card" type="button" @click="createKnowledgeBase">
              <div class="knowledge-create-title">{{ t("pages.knowledge.createCard.title") }}</div>
              <p class="knowledge-create-copy">{{ t("pages.knowledge.createCard.subtitle") }}</p>
              <div class="knowledge-create-cta">
                <NButton type="primary" strong>
                  {{ t("pages.knowledge.createCard.action") }}
                </NButton>
              </div>
            </button>

            <button
              v-for="item in knowledgeBases"
              :key="item.id"
              class="knowledge-card"
              type="button"
              @click="openKnowledgeBase(item)"
            >
              <div class="knowledge-card-title">{{ item.name }}</div>
              <p class="knowledge-card-copy">{{ item.description }}</p>

              <div class="knowledge-card-metrics">
                <div class="knowledge-metric">
                  <Database :size="14" />
                  <span>{{ item.documents }} {{ t("pages.knowledge.units.documents") }}</span>
                </div>
                <div class="knowledge-metric">
                  <Sparkles :size="14" />
                  <span>{{ t("pages.knowledge.list.hitRate", { value: item.hitRate }) }}</span>
                </div>
              </div>

              <div class="knowledge-card-footer">
                <n-tag size="small" round>{{ visibilityLabel(item.visibility) }}</n-tag>
                <span>{{ item.updatedAt }}</span>
              </div>
            </button>
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.knowledge-page {
  gap: var(--space-5);
}

.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(var(--size-280), 1fr));
  gap: var(--space-4_5);
}

.knowledge-create-card,
.knowledge-card {
  display: flex;
  flex-direction: column;
  padding: var(--space-5);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-2xl);
  background: color-mix(in srgb, var(--color-bg-surface) 95%, var(--color-bg-surface-soft));
  text-align: left;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.knowledge-create-card:hover,
.knowledge-card:hover {
  transform: translateY(calc(var(--size-1) * -1));
  border-color: color-mix(in srgb, var(--color-accent-brand) 42%, var(--color-border-soft));
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.06);
}

.knowledge-create-card {
  min-height: var(--size-280);
  justify-content: space-between;
}

.knowledge-card-metrics,
.knowledge-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.knowledge-create-title,
.knowledge-card-title {
  margin-top: 0;
  font-size: var(--space-5_5);
  font-weight: 700;
  color: var(--color-text-primary);
}

.knowledge-create-copy,
.knowledge-card-copy {
  margin: var(--space-3) 0 0;
  color: var(--color-text-secondary);
  line-height: 1.75;
}

.knowledge-card {
  gap: var(--space-2);
}

.knowledge-card-metrics {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 0;
  padding-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--text-caption-size);
}

.knowledge-metric {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
}

.knowledge-card-footer {
  margin-top: auto;
  padding-top: var(--space-4);
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
}

.knowledge-create-cta {
  margin-top: var(--space-5);
}

@media (max-width: 720px) {
  .knowledge-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .knowledge-create-card,
  .knowledge-card {
    min-height: auto;
  }

  .knowledge-create-card {
    min-height: var(--size-240);
  }
}
</style>
