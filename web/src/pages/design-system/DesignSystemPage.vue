<script setup lang="ts">
import { computed, ref } from "vue";
import {
  NAlert,
  NBadge,
  NButton,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NConfigProvider,
  NDataTable,
  NDrawer,
  NDrawerContent,
  NInput,
  NList,
  NListItem,
  NModal,
  NPopover,
  NPopconfirm,
  NPopselect,
  NProgress,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  NTag,
  createDiscreteApi,
  darkTheme,
  type GlobalThemeOverrides
} from "naive-ui";
import { themeOverrides } from "@/theme";
import { themeTokens } from "@/themeTokens";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

interface ColorComparisonRow {
  name: string;
  light: string;
  dark: string;
}

interface ColorComparisonGroup {
  title: string;
  rows: ColorComparisonRow[];
}

const uiPreferencesStore = useUiPreferencesStore();
const isDarkMode = computed(() => uiPreferencesStore.themeMode === "dark");
const modeVars = computed(() =>
  isDarkMode.value
    ? {
        "--ds-bg-card": themeTokens.semantic.dark.bgSurface,
        "--ds-bg-card-soft": themeTokens.semantic.dark.bgSurfaceSoft,
        "--ds-text-primary": themeTokens.semantic.dark.textPrimary,
        "--ds-text-secondary": themeTokens.semantic.dark.textSecondary,
        "--ds-text-muted": themeTokens.semantic.dark.textTertiary,
        "--ds-border": themeTokens.semantic.dark.border
      }
    : {
        "--ds-bg-card": themeTokens.semantic.light.bgSurface,
        "--ds-bg-card-soft": themeTokens.semantic.light.bgSurfaceSoft,
        "--ds-text-primary": themeTokens.semantic.light.textPrimary,
        "--ds-text-secondary": themeTokens.semantic.light.textSecondary,
        "--ds-text-muted": themeTokens.semantic.light.textTertiary,
        "--ds-border": themeTokens.semantic.light.border
      }
);
const pageTitle = computed(() => (isDarkMode.value ? "Design System · Dark Spec" : "Design System · Light Spec"));
const pageSubtitle = computed(() =>
  isDarkMode.value
    ? "Dark 主题设计规范展示页，覆盖 Foundations、Business Components 与整页拼装示例。"
    : "Light 主题设计规范展示页，覆盖 Foundations、Business Components 与整页拼装示例。"
);
const currentButtonTokens = computed(() => (isDarkMode.value ? themeTokens.component.button.dark : themeTokens.component.button.light));
const currentInputTokens = computed(() => (isDarkMode.value ? themeTokens.component.input.dark : themeTokens.component.input.light));
const buttonStateVars = computed(() => ({ "--btn-focus-ring": currentButtonTokens.value.focusRing }));
const buttonToneRows = [
  { label: "Primary", type: "primary" as const },
  { label: "Secondary", type: "primary" as const, secondary: true },
  { label: "Info", type: "info" as const },
  { label: "Success", type: "success" as const },
  { label: "Warning", type: "warning" as const },
  { label: "Error", type: "error" as const }
];
const inputStateVars = computed(() => ({
  "--input-default-bg": currentInputTokens.value.color,
  "--input-default-border": currentInputTokens.value.border,
  "--input-hover-border": currentInputTokens.value.borderHover,
  "--input-focus-bg": currentInputTokens.value.colorFocus,
  "--input-focus-border": currentInputTokens.value.borderFocus
}));

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: themeTokens.semantic.dark.primary,
    primaryColorHover: themeTokens.semantic.dark.primaryHover,
    primaryColorPressed: themeTokens.semantic.dark.primaryPressed,
    primaryColorSuppl: themeTokens.semantic.dark.primary,
    infoColor: themeTokens.semantic.dark.info,
    successColor: themeTokens.semantic.dark.success,
    warningColor: themeTokens.semantic.dark.warning,
    errorColor: themeTokens.semantic.dark.error,
    bodyColor: themeTokens.semantic.dark.bgPage,
    cardColor: themeTokens.semantic.dark.bgSurface,
    modalColor: themeTokens.semantic.dark.bgSurface,
    popoverColor: themeTokens.semantic.dark.bgSurface,
    borderColor: themeTokens.semantic.dark.border,
    textColorBase: themeTokens.semantic.dark.textPrimary,
    textColor1: themeTokens.semantic.dark.textPrimary,
    textColor2: themeTokens.semantic.dark.textSecondary,
    textColor3: themeTokens.semantic.dark.textTertiary,
    borderRadius: themeTokens.radius.base,
    fontFamily: themeTokens.font.sans,
    fontFamilyMono: themeTokens.font.mono
  },
  Card: {
    color: themeTokens.semantic.dark.bgSurface,
    colorEmbedded: themeTokens.semantic.dark.bgSurfaceSoft
  },
  Input: {
    color: themeTokens.component.input.dark.color,
    colorFocus: themeTokens.component.input.dark.colorFocus,
    colorFocusError: themeTokens.component.input.dark.colorFocusError,
    textColor: themeTokens.component.input.dark.textColor,
    border: themeTokens.component.input.dark.border,
    borderHover: themeTokens.component.input.dark.borderHover,
    borderFocus: themeTokens.component.input.dark.borderFocus,
    borderError: themeTokens.component.input.dark.borderError,
    borderFocusError: themeTokens.component.input.dark.borderFocusError
  }
};

const colorGroups: ColorComparisonGroup[] = [
  {
    title: "品牌色",
    rows: [
      { name: "primary", light: themeTokens.semantic.light.primary, dark: themeTokens.semantic.dark.primary },
      { name: "primary.hover", light: themeTokens.semantic.light.primaryHover, dark: themeTokens.semantic.dark.primaryHover },
      { name: "primary.pressed", light: themeTokens.semantic.light.primaryPressed, dark: themeTokens.semantic.dark.primaryPressed }
    ]
  },
  {
    title: "背景色",
    rows: [
      { name: "bg.canvas", light: themeTokens.semantic.light.bgCanvas, dark: themeTokens.semantic.dark.bgCanvas },
      { name: "bg.page", light: themeTokens.semantic.light.bgPage, dark: themeTokens.semantic.dark.bgPage },
      { name: "bg.surface", light: themeTokens.semantic.light.bgSurface, dark: themeTokens.semantic.dark.bgSurface }
    ]
  },
  {
    title: "文字色",
    rows: [
      { name: "text.primary", light: themeTokens.semantic.light.textPrimary, dark: themeTokens.semantic.dark.textPrimary },
      { name: "text.secondary", light: themeTokens.semantic.light.textSecondary, dark: themeTokens.semantic.dark.textSecondary },
      { name: "text.tertiary", light: themeTokens.semantic.light.textTertiary, dark: themeTokens.semantic.dark.textTertiary }
    ]
  },
  {
    title: "边框色",
    rows: [{ name: "border.default", light: themeTokens.semantic.light.border, dark: themeTokens.semantic.dark.border }]
  },
  {
    title: "语义状态色",
    rows: [
      { name: "info", light: themeTokens.semantic.light.info, dark: themeTokens.semantic.dark.info },
      { name: "success", light: themeTokens.semantic.light.success, dark: themeTokens.semantic.dark.success },
      { name: "warning", light: themeTokens.semantic.light.warning, dark: themeTokens.semantic.dark.warning },
      { name: "error", light: themeTokens.semantic.light.error, dark: themeTokens.semantic.dark.error }
    ]
  }
];

const typographyRows = [
  { label: "页面标题", sample: "运营总览 Dashboard", meta: "32px / 700", className: "typo-page-title" },
  { label: "区块标题", sample: "今日关键指标", meta: "24px / 600", className: "typo-section-title" },
  { label: "正文", sample: "系统会按策略自动聚合近 24 小时执行结果并给出诊断建议。", meta: "14px / 400", className: "typo-body" },
  { label: "占位符", sample: "请输入关键字…", meta: "14px / 400", className: "typo-placeholder" },
  { label: "辅助文案", sample: "更新时间：2026-03-22 11:40", meta: "12px / 400", className: "typo-helper" },
  { label: "数字指标文案", sample: "1,248", meta: "36px / 700", className: "typo-metric" }
];
const headingRows = [
  { tag: "<h1></h1>", sample: "h1. Agent Dashboard", className: "typo-h1" },
  { tag: "<h2></h2>", sample: "h2. Agent Dashboard", className: "typo-h2" },
  { tag: "<h3></h3>", sample: "h3. Agent Dashboard", className: "typo-h3" },
  { tag: "<h4></h4>", sample: "h4. Agent Dashboard", className: "typo-h4" },
  { tag: "<h5></h5>", sample: "h5. Agent Dashboard", className: "typo-h5" },
  { tag: "<h6></h6>", sample: "h6. Agent Dashboard", className: "typo-h6" }
];

const spacingRows = [
  { token: "space-gap-xs", value: "4px", width: "var(--space-gap-xs)" },
  { token: "space-gap-sm", value: "8px", width: "var(--space-gap-sm)" },
  { token: "space-gap-md", value: "12px", width: "var(--space-gap-md)" },
  { token: "space-gap-lg", value: "16px", width: "var(--space-gap-lg)" },
  { token: "space-gap-xl", value: "20px", width: "var(--space-gap-xl)" },
  { token: "space-gap-2xl", value: "24px", width: "var(--space-gap-2xl)" }
];

const radiusRows = [
  { token: "radius-sm", value: "6px", radius: "var(--radius-sm)" },
  { token: "radius-m", value: "12px", radius: "var(--radius-m)" },
  { token: "radius-md", value: "16px", radius: "var(--radius-md)" },
  { token: "radius-xl", value: "24px", radius: "var(--radius-xl)" },
  { token: "radius-pill", value: "999px", radius: "var(--radius-pill)" }
];
const shadowRows = [
  { token: "shadow-card-primary", className: "shadow-level-primary", interaction: "static" },
  { token: "shadow-card-hover", className: "shadow-level-hover", interaction: "hover" }
];

const deptOptions = [
  { label: "Engineering", value: "engineering" },
  { label: "Product", value: "product" },
  { label: "Design", value: "design" }
];
const selectedDept = ref("engineering");
const showcaseModalVisible = ref(false);
const showcaseDialogVisible = ref(false);
const showcaseDrawerVisible = ref(false);
const popselectValue = ref("alpha");
const multiSelectedValues = ref<string[]>(["monitor", "alert"]);
const singleSelectedValue = ref("stable");
const { message, notification } = createDiscreteApi(["message", "notification"]);
const tableColumns = [
  { title: "字段", key: "field" },
  { title: "值", key: "value" },
  { title: "状态", key: "status" }
];
const tableData = [
  { field: "QPS", value: "248", status: "Normal" },
  { field: "Latency", value: "83ms", status: "Stable" },
  { field: "ErrorRate", value: "0.8%", status: "Watch" }
];
const demoListItems = [
  { title: "Agent 执行日志聚合", subtitle: "最近 5 分钟有 2 条 warning" },
  { title: "审批队列处理", subtitle: "当前等待人工确认 1 条" },
  { title: "检索索引更新", subtitle: "已完成增量同步" }
];
const popselectOptions = [
  { label: "Alpha", value: "alpha" },
  { label: "Beta", value: "beta" },
  { label: "Gamma", value: "gamma" }
];

function openMessageDemo() {
  message.success("Message 示例：操作已提交");
}

function openNotificationDemo() {
  notification.info({
    title: "Notification 示例",
    content: "任务已进入排队队列。",
    duration: 3000
  });
}

function switchTheme(mode: "light" | "dark") {
  uiPreferencesStore.setThemeMode(mode);
}
</script>

<template>
  <n-config-provider :theme="isDarkMode ? darkTheme : undefined" :theme-overrides="isDarkMode ? darkThemeOverrides : themeOverrides">
    <div class="design-spec-shell" :class="{ 'dark-shell': isDarkMode }">
      <div class="design-spec-page page-card" :class="{ 'dark-page': isDarkMode }" :style="modeVars">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="page-subtitle">{{ pageSubtitle }}</div>

        <n-space class="mode-switch" :size="8">
          <n-button :type="isDarkMode ? 'default' : 'primary'" @click="switchTheme('light')">Light 页面</n-button>
          <n-button :type="isDarkMode ? 'primary' : 'default'" @click="switchTheme('dark')">Dark 页面</n-button>
        </n-space>

        <section class="spec-section">
          <h3 class="ui-section-title-md">1. Foundations / Colors</h3>
          <div class="foundation-color-grid ui-grid-auto-280">
            <n-card v-for="group in colorGroups" :key="group.title" size="small" :title="group.title" class="foundation-card" :class="{ 'dark-card': isDarkMode }">
              <div class="color-compare-list">
                <div v-for="row in group.rows" :key="`${group.title}-${row.name}`" class="color-row">
                  <div class="color-token">{{ row.name }}</div>
                  <div class="color-pairs">
                    <div class="color-chip">
                      <span class="chip-title">Light</span>
                      <span class="chip-swatch" :style="{ background: row.light }" />
                      <code>{{ row.light }}</code>
                    </div>
                    <div class="color-chip">
                      <span class="chip-title">Dark</span>
                      <span class="chip-swatch" :style="{ background: row.dark }" />
                      <code>{{ row.dark }}</code>
                    </div>
                  </div>
                </div>
              </div>
            </n-card>
          </div>
        </section>

        <section class="spec-section">
          <h3 class="ui-section-title-md">2. Foundations / Typography</h3>
          <n-card size="small" class="foundation-card" :class="{ 'dark-card': isDarkMode }">
            <div class="typo-foundation-grid">
              <div class="typo-panel">
                <div class="typo-panel-title">语义排版</div>
                <div class="typo-list">
                  <div v-for="item in typographyRows" :key="item.label" class="typo-row">
                    <div class="typo-label">{{ item.label }}</div>
                    <div class="typo-main">
                      <div :class="item.className">{{ item.sample }}</div>
                      <div class="typo-meta">{{ item.meta }}</div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="typo-panel">
                <div class="typo-panel-title">Headings</div>
                <div class="typo-heading-intro">
                  All heading levels, <span>&lt;h1&gt;</span> through <span>&lt;h6&gt;</span>, are available.
                </div>
                <div class="typo-heading-table">
                  <div class="typo-heading-head">Heading</div>
                  <div class="typo-heading-head">Example</div>
                  <template v-for="row in headingRows" :key="row.tag">
                    <code class="typo-heading-tag">{{ row.tag }}</code>
                    <div :class="['typo-heading-sample', row.className]">{{ row.sample }}</div>
                  </template>
                </div>
                <div class="typo-heading-meta">
                  h1: 32px / 700 · h2: 24px / 600 · h3: 20px / 600 · h4: 18px / 600 · h5: 16px / 600 · h6: 14px / 600
                </div>
              </div>
            </div>
          </n-card>
        </section>

        <section class="spec-section">
          <h3 class="ui-section-title-md">3. Foundations / Spacing + Radius + Shadow</h3>
          <div class="foundation-mix-grid ui-grid-auto-280">
            <n-card title="间距阶梯" size="small" class="foundation-card" :class="{ 'dark-card': isDarkMode }">
              <div class="scale-list">
                <div v-for="space in spacingRows" :key="space.token" class="scale-row">
                  <span>{{ space.token }}</span>
                  <div class="space-visuals">
                    <div class="space-gap-demo" :style="{ columnGap: space.width }">
                      <span class="space-dot" />
                      <span class="space-dot" />
                    </div>
                    <div class="space-padding-demo" :style="{ padding: space.width }">
                      <span class="space-padding-inner" />
                    </div>
                  </div>
                  <code>{{ space.value }}</code>
                </div>
              </div>
            </n-card>

            <n-card title="圆角阶梯" size="small" class="foundation-card" :class="{ 'dark-card': isDarkMode }">
              <div class="scale-list">
                <div v-for="radius in radiusRows" :key="radius.token" class="scale-row">
                  <span>{{ radius.token }}</span>
                  <div class="radius-preview-wrap">
                    <div class="radius-preview radius-preview-rect" :style="{ borderRadius: radius.radius }" />
                    <div class="radius-preview radius-preview-square" :style="{ borderRadius: radius.radius }" />
                  </div>
                  <code>{{ radius.value }}</code>
                </div>
              </div>
            </n-card>

            <n-card title="卡片阴影层级" size="small" class="foundation-card" :class="{ 'dark-card': isDarkMode }">
              <div class="shadow-stack">
                <div v-for="shadow in shadowRows" :key="shadow.token" class="shadow-row">
                  <div class="shadow-token">{{ shadow.token }}</div>
                  <div class="shadow-compare">
                    <div class="shadow-card shadow-card-control">No Shadow</div>
                    <div class="shadow-card" :class="shadow.className">
                      {{ shadow.interaction === "hover" ? "Hover Me" : "Default On" }}
                    </div>
                  </div>
                </div>
              </div>
            </n-card>
          </div>
        </section>

        <section class="spec-section">
          <h3 class="ui-section-title-md">4. Components</h3>
          <div class="component-overview">
            <div class="component-subgroup">
              <div class="component-subgroup-title">Core UI</div>
              <div class="component-grid ui-grid-auto-280 core-ui-grid">
                <n-card title="Button States" size="small" embedded class="demo-card">
                  <div class="ui-state-showcase" :style="buttonStateVars">
                    <div class="ui-button-state-grid">
                      <div class="ui-button-state-head" />
                      <div class="ui-button-state-head">默认</div>
                      <div class="ui-button-state-head">悬停</div>
                      <div class="ui-button-state-head">禁用</div>
                      <div class="ui-button-state-head">聚焦</div>
                      <template v-for="row in buttonToneRows" :key="`btn-row-${row.label}`">
                        <div class="ui-button-state-label">{{ row.label }}</div>
                        <n-button size="small" :type="row.type" :secondary="Boolean(row.secondary)" class="ui-state-static-btn">Button</n-button>
                        <n-button size="small" :type="row.type" :secondary="Boolean(row.secondary)" class="ui-state-static-btn ui-btn-force-hover">Button</n-button>
                        <n-button size="small" :type="row.type" :secondary="Boolean(row.secondary)" disabled class="ui-state-static-btn">Button</n-button>
                        <n-button size="small" :type="row.type" :secondary="Boolean(row.secondary)" class="ui-state-static-btn ui-btn-force-focus">Button</n-button>
                      </template>
                    </div>
                  </div>
                </n-card>
                <n-card title="Tag" size="small" embedded class="demo-card">
                  <div class="demo-stack">
                    <n-space wrap>
                      <n-tag type="primary">Primary</n-tag>
                      <n-tag>Secondary</n-tag>
                      <n-tag type="info">Info</n-tag>
                      <n-tag type="success">Success</n-tag>
                      <n-tag type="warning">Warning</n-tag>
                      <n-tag type="error">Error</n-tag>
                    </n-space>
                    <n-space wrap>
                      <n-tag type="primary" :bordered="false">Primary Solid</n-tag>
                      <n-tag :bordered="false">Secondary Solid</n-tag>
                      <n-tag type="info" :bordered="false">Info Solid</n-tag>
                      <n-tag type="success" :bordered="false">Success Solid</n-tag>
                      <n-tag type="warning" :bordered="false">Warning Solid</n-tag>
                      <n-tag type="error" :bordered="false">Error Solid</n-tag>
                    </n-space>
                  </div>
                </n-card>
                <n-card title="Input States" size="small" embedded class="demo-card">
                  <div class="ui-state-showcase" :style="inputStateVars">
                    <div class="ui-state-demo-list">
                      <div class="ui-state-input">默认 Default</div>
                      <div class="ui-state-input ui-state-input-hover">悬停 Hover</div>
                      <div class="ui-state-input ui-state-input-disabled">禁用 Disabled</div>
                      <div class="ui-state-input ui-state-input-focus">聚焦 Focus</div>
                    </div>
                  </div>
                </n-card>
                <n-card title="Alert + Badge + Feedback" size="small" embedded class="demo-card">
                  <n-alert type="warning" :show-icon="false" class="demo-alert">
                    Alert 示例：当前环境存在未审批操作。
                  </n-alert>
                  <n-space align="center" class="feedback-actions">
                    <n-badge value="8" :max="99">
                      <n-button secondary>Badge 容器</n-button>
                    </n-badge>
                    <n-button @click="openMessageDemo">触发 Message</n-button>
                    <n-button @click="openNotificationDemo">触发 Notification</n-button>
                  </n-space>
                </n-card>
                <n-card title="Progress" size="small" embedded class="demo-card">
                  <n-space vertical :size="12">
                    <n-progress type="line" :percentage="68" indicator-placement="inside" class="progress-line" />
                    <div class="progress-circle-group">
                      <div class="progress-circle-item">
                        <n-progress type="circle" :percentage="72" :stroke-width="6" :height="56" class="progress-circle" />
                        <div class="ui-caption-muted">S · 56</div>
                      </div>
                      <div class="progress-circle-item">
                        <n-progress type="circle" :percentage="72" :stroke-width="8" :height="72" class="progress-circle" />
                        <div class="ui-caption-muted">M · 72</div>
                      </div>
                      <div class="progress-circle-item">
                        <n-progress type="circle" :percentage="72" :stroke-width="10" :height="88" class="progress-circle" />
                        <div class="ui-caption-muted">L · 88</div>
                      </div>
                    </div>
                  </n-space>
                </n-card>
              </div>
            </div>

            <div class="component-subgroup">
              <div class="component-subgroup-title">Data</div>
              <div class="component-grid ui-grid-auto-280">
                <n-card title="Tabs" size="small" embedded class="demo-card">
                  <n-tabs type="line" animated>
                    <n-tab-pane name="overview" tab="Overview">
                      <div class="ui-caption-muted">Overview 面板内容</div>
                    </n-tab-pane>
                    <n-tab-pane name="activity" tab="Activity">
                      <div class="ui-caption-muted">Activity 面板内容</div>
                    </n-tab-pane>
                  </n-tabs>
                </n-card>
                <n-card title="DataTable" size="small" embedded class="demo-card">
                  <n-data-table :columns="tableColumns" :data="tableData" :pagination="false" :bordered="false" />
                </n-card>
                <n-card title="List" size="small" embedded class="demo-card">
                  <n-list bordered>
                    <n-list-item v-for="item in demoListItems" :key="item.title">
                      <div class="list-item-title">{{ item.title }}</div>
                      <div class="ui-caption-muted">{{ item.subtitle }}</div>
                    </n-list-item>
                  </n-list>
                </n-card>
                <n-card title="Select + Checkbox + Radio" size="small" embedded class="demo-card">
                  <div class="demo-stack">
                    <n-select v-model:value="selectedDept" :options="deptOptions" class="filter-select" />
                    <n-checkbox-group v-model:value="multiSelectedValues">
                      <n-space>
                        <n-checkbox value="monitor" label="监控告警" />
                        <n-checkbox value="alert" label="异常通知" />
                        <n-checkbox value="report" label="日报汇总" />
                      </n-space>
                    </n-checkbox-group>
                    <n-radio-group v-model:value="singleSelectedValue" name="spec-single-select">
                      <n-space>
                        <n-radio value="stable">稳定优先</n-radio>
                        <n-radio value="balanced">平衡模式</n-radio>
                        <n-radio value="fast">速度优先</n-radio>
                      </n-space>
                    </n-radio-group>
                  </div>
                </n-card>
              </div>
            </div>

            <div class="component-subgroup">
              <div class="component-subgroup-title">Overlay</div>
              <div class="component-grid ui-grid-auto-280">
                <n-card title="Dialog / Modal / Drawer" size="small" embedded class="demo-card">
                  <div class="ui-caption-muted">展示三类弹层：确认型 Dialog、内容型 Modal、侧滑 Drawer。</div>
                  <n-space class="overlay-trigger-group">
                    <n-button size="small" @click="showcaseDialogVisible = true">打开 Dialog</n-button>
                    <n-button size="small" @click="showcaseModalVisible = true">打开 Modal</n-button>
                    <n-button size="small" @click="showcaseDrawerVisible = true">打开 Drawer</n-button>
                  </n-space>
                  <n-modal v-model:show="showcaseDialogVisible" preset="dialog" title="Dialog Showcase" positive-text="确认" negative-text="取消">
                    这是 Dialog 示例（用于确认类操作）。
                  </n-modal>
                  <n-modal v-model:show="showcaseModalVisible" preset="card" title="Modal Showcase" style="width: min(var(--size-420), calc(100vw - var(--size-32)))">
                    <div class="ui-caption-muted">这是 Modal 示例（用于承载较完整的内容块）。</div>
                  </n-modal>
                  <n-drawer v-model:show="showcaseDrawerVisible" :width="420" placement="right">
                    <n-drawer-content title="Drawer Showcase" closable>
                      <div class="ui-caption-muted">这是 Drawer 示例（用于侧边流程和编辑场景）。</div>
                    </n-drawer-content>
                  </n-drawer>
                </n-card>
                <n-card title="Popover / Popconfirm / Popselect" size="small" embedded class="demo-card">
                  <n-space>
                    <n-popover trigger="click">
                      <template #trigger>
                        <n-button secondary>打开 Popover</n-button>
                      </template>
                      <div class="overlay-pop-content">Popover 示例：用于补充上下文信息。</div>
                    </n-popover>
                    <n-popconfirm @positive-click="openMessageDemo">
                      <template #trigger>
                        <n-button secondary type="warning">打开 Popconfirm</n-button>
                      </template>
                      确认执行该操作？
                    </n-popconfirm>
                    <n-popselect v-model:value="popselectValue" :options="popselectOptions">
                      <n-button secondary>打开 Popselect</n-button>
                    </n-popselect>
                  </n-space>
                </n-card>
              </div>
            </div>

            <div class="component-subgroup">
              <div class="component-subgroup-title">Business Blocks</div>
              <div class="component-grid ui-grid-auto-280">
                <n-card title="AppPageHeader" size="small" embedded class="demo-card">
                  <div class="demo-page-header">
                    <div>
                      <div class="ui-title-lg">Agent 运行中心</div>
                      <div class="ui-subtitle">跨会话状态追踪与统一检索入口</div>
                    </div>
                    <n-space>
                      <n-button secondary>导出</n-button>
                      <n-button type="primary">新建</n-button>
                    </n-space>
                  </div>
                </n-card>
                <n-card title="MetricCard" size="small" embedded class="demo-card">
                  <div class="metric-grid">
                    <div class="metric-card-item">
                      <div class="metric-label">活跃会话</div>
                      <div class="metric-value">1,248</div>
                      <div class="metric-trend">+12.4%</div>
                    </div>
                    <div class="metric-card-item">
                      <div class="metric-label">成功率</div>
                      <div class="metric-value">98.2%</div>
                      <div class="metric-trend">+0.8%</div>
                    </div>
                  </div>
                </n-card>
                <n-card title="FilterToolbar" size="small" embedded class="demo-card">
                  <div class="filter-toolbar-demo">
                    <n-input placeholder="搜索任务名称" class="filter-input" />
                    <n-select v-model:value="selectedDept" :options="deptOptions" class="filter-select" />
                    <n-tag type="info" :bordered="false">状态：运行中</n-tag>
                    <n-button secondary type="primary">筛选</n-button>
                  </div>
                </n-card>
                <n-card title="EmptyState" size="small" embedded class="demo-card">
                  <div class="empty-state-demo">
                    <div class="empty-icon">○</div>
                    <div class="empty-title">暂无数据</div>
                    <div class="empty-desc">当前筛选条件下没有可展示记录，请调整筛选项后重试。</div>
                    <n-button type="primary" secondary>重置筛选</n-button>
                  </div>
                </n-card>
              </div>
            </div>
          </div>
        </section>

      </div>
    </div>
  </n-config-provider>
</template>

<style scoped>
.design-spec-shell {
  height: 100vh;
  overflow-y: auto;
  padding: var(--size-28);
  background: var(--color-bg-canvas);
}

.dark-shell {
  background:
    radial-gradient(1200px 520px at 12% -10%, rgba(54, 203, 181, 0.18), transparent 58%),
    radial-gradient(900px 420px at 88% 0%, rgba(79, 219, 199, 0.12), transparent 60%),
    var(--color-bg-runtime);
}

.design-spec-page {
  position: relative;
  min-height: calc(100vh - var(--size-56));
}

.dark-page {
  border-color: rgba(255, 255, 255, 0.16);
  background: linear-gradient(180deg, #0E141A 0%, #0B1016 100%);
  color: var(--ds-text-primary);
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.52),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.dark-page .page-title {
  color: var(--ds-text-primary);
}

.mode-switch {
  margin-top: var(--space-3_5);
}

.spec-section {
  margin-top: var(--space-5_5);
}

.foundation-card :deep(.n-card__content) {
  padding-top: var(--space-2_5);
}

.component-overview {
  display: grid;
  gap: var(--space-4);
}

.component-subgroup {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border: var(--size-1) solid var(--ds-border);
  border-radius: var(--radius-m);
  background: var(--ds-bg-card-soft);
}

.component-subgroup-title {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--ds-text-primary);
}

.core-ui-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: var(--space-3);
  row-gap: var(--space-4);
}

.core-ui-grid :deep(.n-card__content) {
  display: grid;
  align-content: start;
  gap: var(--space-3_5);
}

.demo-card {
  min-height: var(--size-170);
}

.demo-stack {
  display: grid;
  gap: var(--space-3);
}

.demo-block {
  display: grid;
  gap: var(--space-2);
}

.demo-block-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-muted);
}

.inner-demo-card {
  border-radius: var(--radius-m);
}

.modal-trigger {
  margin-top: var(--space-2);
}

.overlay-trigger-group {
  margin-top: var(--space-2);
}

.list-item-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.demo-alert {
  margin-bottom: var(--space-3);
}

.feedback-actions {
  margin-top: var(--space-2_5);
}

.overlay-pop-content {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.progress-line {
  width: var(--size-220);
}

.progress-circle {
  display: block;
}

.progress-circle-group {
  display: flex;
  gap: var(--space-3);
  align-items: flex-end;
}

.progress-circle-item {
  display: grid;
  justify-items: center;
  gap: var(--space-1_5);
}

.color-compare-list,
.typo-list,
.scale-list,
.shadow-stack {
  display: grid;
  gap: var(--space-2_5);
}

.color-row {
  display: grid;
  gap: var(--space-2);
}

.color-token {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.color-pairs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.color-chip {
  display: grid;
  gap: var(--space-1_5);
  padding: var(--space-2);
  border: var(--size-1) solid var(--ds-border);
  border-radius: var(--radius-s-md);
  background: var(--ds-bg-card-soft);
}

.chip-title {
  font-size: var(--font-size-2xs);
  color: var(--ds-text-muted);
}

.chip-swatch {
  height: var(--size-16);
  border-radius: var(--radius-sm);
  border: var(--size-1) solid var(--color-overlay-slate-35);
}

.color-chip code,
.scale-row code,
.typo-meta {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--ds-text-secondary);
}

.typo-row {
  display: grid;
  grid-template-columns: var(--size-140) minmax(0, 1fr);
  gap: var(--space-3);
  align-items: center;
}

.typo-foundation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.typo-panel {
  display: grid;
  gap: var(--space-2_5);
  padding: var(--space-3);
  border: var(--size-1) solid var(--ds-border);
  border-radius: var(--radius-m);
  background: var(--ds-bg-card-soft);
}

.typo-panel-title {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--ds-text-primary);
}

.typo-label {
  font-size: var(--font-size-sm);
  color: var(--ds-text-muted);
}

.typo-main {
  display: grid;
  gap: var(--space-1_5);
}

.typo-page-title {
  font-size: var(--size-32);
  font-weight: 700;
  line-height: 1.15;
}

.typo-section-title {
  font-size: var(--size-24);
  font-weight: 600;
  line-height: 1.25;
}

.typo-body {
  font-size: var(--font-size-md);
  line-height: 1.7;
}

.typo-helper {
  font-size: var(--font-size-xs);
  color: var(--ds-text-muted);
}

.typo-placeholder {
  font-size: var(--font-size-md);
  color: var(--ds-text-muted);
  opacity: 0.72;
}

.typo-metric {
  font-size: var(--size-36);
  font-weight: 700;
  line-height: 1;
}

.typo-list {
  display: grid;
  gap: var(--space-2);
}

.typo-list .typo-row {
  padding-bottom: var(--space-2_5);
  border-bottom: var(--size-1) dashed var(--ds-border);
}

.typo-list .typo-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.typo-heading-intro {
  color: var(--ds-text-secondary);
  line-height: 1.6;
}

.typo-heading-intro span {
  color: var(--color-text-brand);
}

.typo-heading-table {
  display: grid;
  grid-template-columns: var(--size-96) minmax(0, 1fr);
  gap: var(--space-2) var(--space-3);
  align-items: center;
}

.typo-heading-head {
  padding-bottom: var(--space-2);
  border-bottom: var(--size-2) solid var(--ds-border);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.typo-heading-tag {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-brand);
}

.typo-heading-sample {
  color: var(--ds-text-primary);
  line-height: 1.25;
  padding: var(--space-2_5) 0;
  border-bottom: var(--size-1) solid var(--ds-border);
}

.typo-h1 {
  font-size: var(--size-32);
  font-weight: 700;
}

.typo-h2 {
  font-size: var(--size-24);
  font-weight: 600;
}

.typo-h3 {
  font-size: var(--size-20);
  font-weight: 600;
}

.typo-h4 {
  font-size: var(--size-18);
  font-weight: 600;
}

.typo-h5 {
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.typo-h6 {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.typo-heading-meta {
  margin-top: var(--space-1_5);
  color: var(--ds-text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.scale-row {
  display: grid;
  grid-template-columns: var(--size-96) minmax(0, 1fr) var(--size-72);
  gap: var(--space-2);
  align-items: center;
  font-size: var(--font-size-sm);
}

.space-bar {
  height: var(--size-8);
  border-radius: var(--radius-pill);
  background: var(--color-brand-500);
  min-width: var(--size-4);
}

.space-visuals {
  display: grid;
  grid-template-columns: var(--size-84) var(--size-84);
  gap: var(--space-2);
  align-items: center;
}

.space-gap-demo {
  display: grid;
  grid-template-columns: var(--size-20) var(--size-20);
  justify-content: start;
  align-items: center;
}

.space-dot {
  width: var(--size-20);
  height: var(--size-20);
  border-radius: var(--radius-sm);
  background: var(--color-brand-500);
}

.space-padding-demo {
  width: var(--size-84);
  height: var(--size-44);
  border: var(--size-1) dashed var(--ds-border);
  border-radius: var(--radius-sm);
  background: var(--ds-bg-card-soft);
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
}

.space-padding-inner {
  flex: 1;
  border-radius: var(--radius-sm);
  background: var(--color-bg-brand-tint-12);
}

.radius-preview {
  border: var(--size-1) solid var(--ds-border);
  background: var(--ds-bg-card-soft);
}

.radius-preview-wrap {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.radius-preview-rect {
  width: var(--size-96);
  height: var(--size-44);
}

.radius-preview-square {
  width: var(--size-44);
  height: var(--size-44);
}

.shadow-card {
  width: var(--size-170);
  padding: var(--space-3);
  border-radius: var(--radius-m);
  background: var(--ds-bg-card);
  border: var(--size-1) solid var(--ds-border);
  text-align: center;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.shadow-stack {
  padding: var(--space-2_5);
  border-radius: var(--radius-m);
  background: linear-gradient(180deg, var(--ds-bg-card-soft) 0%, var(--ds-bg-card) 100%);
}

.shadow-row {
  display: grid;
  gap: var(--space-2);
}

.shadow-token {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.shadow-compare {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-2);
  border: var(--size-1) dashed var(--ds-border);
  border-radius: var(--radius-m);
  background: linear-gradient(180deg, var(--ds-bg-card) 0%, var(--ds-bg-card-soft) 100%);
}

.shadow-card-control {
  box-shadow: none;
}

.shadow-compare .shadow-card {
  background: var(--ds-bg-card-soft);
  color: var(--ds-text-primary);
  border-color: var(--ds-border);
}

.shadow-level-primary {
  box-shadow: var(--shadow-card-primary);
}

.shadow-level-hover {
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.shadow-level-hover:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(calc(var(--size-1) * -1));
}

.demo-page-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: flex-start;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2_5);
}

.metric-card-item {
  padding: var(--space-3);
  border: var(--size-1) solid var(--ds-border);
  border-radius: var(--radius-m);
  background: var(--ds-bg-card-soft);
}

.metric-label {
  font-size: var(--font-size-xs);
  color: var(--ds-text-muted);
}

.metric-value {
  margin-top: var(--space-1_5);
  font-size: var(--size-24);
  font-weight: 700;
}

.metric-trend {
  margin-top: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-brand);
}

.filter-toolbar-demo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--size-220) auto auto;
  gap: var(--space-2);
  align-items: center;
}

.filter-input,
.filter-select {
  width: 100%;
}

.empty-state-demo {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
}

.empty-icon {
  width: var(--size-32);
  height: var(--size-32);
  border-radius: var(--radius-pill);
  border: var(--size-1) solid var(--ds-border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ds-text-muted);
}

.empty-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.empty-desc {
  font-size: var(--font-size-sm);
  color: var(--ds-text-muted);
  line-height: 1.6;
}

.dark-card {
  border-color: rgba(255, 255, 255, 0.14);
  background: linear-gradient(180deg, #111820 0%, #0F161D 100%);
}

.dark-page .page-subtitle {
  color: var(--ds-text-muted);
}

.dark-page .ui-title-lg,
.dark-page .ui-title-xl,
.dark-page .ui-pane-title,
.dark-page .ui-title-strong {
  color: var(--ds-text-primary);
}

.dark-page .ui-subtitle,
.dark-page .ui-copy-muted-block,
.dark-page .ui-pane-subtitle,
.dark-page .metric-label,
.dark-page .overlay-pop-content {
  color: var(--ds-text-secondary);
}

.dark-page .metric-value {
  color: var(--ds-text-primary);
}

.dark-page .component-subgroup {
  border-color: rgba(255, 255, 255, 0.14);
  background: linear-gradient(180deg, #121A23 0%, #0F151D 100%);
}

.dark-page .component-subgroup-title {
  color: var(--ds-text-primary);
}

.dark-page :deep(.n-data-table-th),
.dark-page :deep(.n-data-table-td) {
  color: var(--ds-text-primary) !important;
  border-color: var(--ds-border) !important;
}

.dark-page :deep(.n-data-table-th) {
  background: #121A23 !important;
}

.dark-page :deep(.n-data-table-td) {
  background: #0F151D !important;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .design-spec-shell {
    padding: var(--size-16);
  }

  .core-ui-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .typo-row {
    grid-template-columns: 1fr;
  }

  .typo-foundation-grid {
    grid-template-columns: 1fr;
  }

  .typo-heading-table {
    grid-template-columns: 1fr;
  }

  .typo-heading-head {
    display: none;
  }

  .typo-heading-tag {
    margin-top: var(--space-1_5);
  }

  .typo-heading-sample {
    padding-top: var(--space-1);
  }

  .filter-toolbar-demo {
    grid-template-columns: 1fr;
  }
}

@media (max-width: var(--size-breakpoint-md)) {
  .core-ui-grid {
    grid-template-columns: 1fr;
  }
}
</style>
