<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { NPagination, NSelect } from "naive-ui";

const props = withDefaults(defineProps<{
  page: number;
  pageSize: number;
  total: number;
  pageSlot?: number;
  pageSizeOptions?: number[];
}>(), {
  pageSlot: 7,
  pageSizeOptions: () => [10, 20, 50]
});

const emit = defineEmits<{
  (e: "update:page", value: number): void;
  (e: "update:pageSize", value: number): void;
}>();

const { t } = useI18n();

const selectOptions = computed(() =>
  props.pageSizeOptions.map((item) => ({
    label: String(item),
    value: item
  }))
);

function handlePageUpdate(nextPage: number) {
  emit("update:page", nextPage);
}

function handlePageSizeUpdate(nextPageSize: number) {
  emit("update:pageSize", nextPageSize);
}
</script>

<template>
  <div class="app-pagination-bar">
    <n-pagination
      :page="page"
      :item-count="total"
      :page-size="pageSize"
      :page-slot="pageSlot"
      @update:page="handlePageUpdate"
    />
    <div class="app-pagination-page-size-control">
      <span class="app-pagination-page-size-label">{{ t("settings.paginationShowPerPage") }}</span>
      <n-select
        :value="pageSize"
        size="small"
        class="app-pagination-page-size-select"
        :options="selectOptions"
        @update:value="handlePageSizeUpdate"
      />
    </div>
  </div>
</template>

<style scoped>
.app-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.app-pagination-page-size-control {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.app-pagination-page-size-label {
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
}

.app-pagination-page-size-select {
  width: 64px;
}
</style>
