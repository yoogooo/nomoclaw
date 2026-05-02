<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { Bot, Brain, CalendarClock, Ellipsis, MessageCircleMore, Moon, Settings, Sun, PlugZap, Puzzle, Workflow } from "lucide-vue-next";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { NPopover } from "naive-ui";
import UiInstantTooltip from "@/components/UiInstantTooltip.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
const uiPreferencesStore = useUiPreferencesStore();
const isTopRail = ref(false);
const moreMenuVisible = ref(false);
const railNavRef = ref<HTMLElement | null>(null);
const topRailVisibleCount = ref<number>(0);

const navItems = computed(() => [
  { key: "chat", label: t("nav.chat"), icon: MessageCircleMore, to: "/" },
  { key: "cron", label: t("nav.cron"), icon: CalendarClock, to: "/cron" },
  { key: "agents", label: t("nav.agents"), icon: Bot, to: "/agents" },
  { key: "skills", label: t("nav.skills"), icon: Puzzle, to: "/skills" },
  { key: "channels", label: t("nav.channels"), icon: PlugZap, to: "/channels" },
  { key: "models", label: t("nav.models"), icon: Brain, to: "/models" },
  { key: "mcp", label: t("nav.mcp"), icon: Workflow, to: "/mcp" },
  { key: "settings", label: t("nav.settings"), icon: Settings, to: "/settings" }
]);

const visibleNavItems = computed(() => {
  if (!isTopRail.value) {
    return navItems.value;
  }
  return navItems.value.slice(0, Math.max(0, topRailVisibleCount.value));
});

const overflowNavItems = computed(() => {
  if (!isTopRail.value) {
    return [];
  }
  return navItems.value.slice(Math.max(0, topRailVisibleCount.value));
});

const isOverflowActive = computed(() =>
  overflowNavItems.value.some((item) => isActive(item.to))
);

const moreLabel = computed(() => (locale.value === "zh-CN" ? "更多" : "More"));

function isActive(path: string) {
  return route.path === path;
}

function openNavItem(item: { to: string; newPage?: boolean }) {
  moreMenuVisible.value = false;
  if (item.newPage) {
    window.open(item.to, "_blank", "noopener,noreferrer");
    return;
  }
  void router.push(item.to);
}

const themeToggleTitle = computed(() =>
  uiPreferencesStore.themeMode === "dark" ? t("settings.themeLight") : t("settings.themeDark")
);

const tooltipPlacement = computed(() => (isTopRail.value ? "bottom" : "right"));

function updateRailMode() {
  if (typeof window === "undefined") {
    isTopRail.value = false;
    return;
  }
  isTopRail.value = window.matchMedia("(max-width: 600px)").matches;
  void nextTick(() => {
    updateTopRailVisibleCount();
  });
}

function updateTopRailVisibleCount() {
  if (!isTopRail.value || !railNavRef.value) {
    topRailVisibleCount.value = navItems.value.length;
    return;
  }
  const navWidth = railNavRef.value.clientWidth;
  if (navWidth <= 0) {
    topRailVisibleCount.value = 0;
    return;
  }
  const itemWidth = 44;
  const gap = 10;
  const slot = itemWidth + gap;
  const totalItems = navItems.value.length;
  const fitAll = Math.floor((navWidth + gap) / slot);
  if (fitAll >= totalItems) {
    topRailVisibleCount.value = totalItems;
    return;
  }
  const fitWithMore = Math.floor((navWidth + gap - slot) / slot);
  topRailVisibleCount.value = Math.max(0, Math.min(totalItems, fitWithMore));
}

onMounted(() => {
  updateRailMode();
  void nextTick(() => {
    updateTopRailVisibleCount();
  });
  window.addEventListener("resize", updateRailMode);
  window.addEventListener("resize", updateTopRailVisibleCount);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", updateRailMode);
  window.removeEventListener("resize", updateTopRailVisibleCount);
});

function toggleThemeMode() {
  uiPreferencesStore.toggleThemeMode();
}
</script>

<template>
  <aside class="directory-rail">
    <div class="rail-top">
      <button class="rail-brand" type="button" aria-label="NomoClaw">
        <span class="rail-brand-mark">N</span>
      </button>
    </div>

    <nav ref="railNavRef" class="rail-nav" aria-label="Primary">
      <UiInstantTooltip
        v-for="item in visibleNavItems"
        :key="item.key"
        :content="item.label"
        :placement="tooltipPlacement"
      >
        <button
          class="rail-item"
          :class="{ active: isActive(item.to) }"
          type="button"
          :aria-label="item.label"
          @click="openNavItem(item)"
        >
          <component :is="item.icon" class="rail-icon" :size="22" :stroke-width="1.9" />
        </button>
      </UiInstantTooltip>

      <n-popover
        v-if="isTopRail && overflowNavItems.length"
        v-model:show="moreMenuVisible"
        trigger="click"
        placement="bottom"
      >
        <template #trigger>
          <button
            class="rail-item"
            :class="{ active: isOverflowActive }"
            type="button"
            :title="moreLabel"
            :aria-label="moreLabel"
            :aria-expanded="moreMenuVisible ? 'true' : 'false'"
            aria-controls="rail-overflow-menu"
          >
            <Ellipsis class="rail-icon" :size="22" :stroke-width="1.9" />
          </button>
        </template>
        <div id="rail-overflow-menu" class="rail-overflow-menu" role="menu" :aria-label="moreLabel">
          <button
            v-for="item in overflowNavItems"
            :key="`overflow_${item.key}`"
            class="rail-overflow-item"
            role="menuitem"
            type="button"
            @click="openNavItem(item)"
          >
            <component :is="item.icon" class="rail-overflow-icon" :size="16" :stroke-width="1.9" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </n-popover>
    </nav>

    <div class="rail-bottom">
      <UiInstantTooltip :content="themeToggleTitle" :placement="tooltipPlacement">
        <button
          class="rail-item rail-theme-toggle"
          type="button"
          :aria-label="themeToggleTitle"
          @click="toggleThemeMode"
        >
          <Sun v-if="uiPreferencesStore.themeMode === 'dark'" class="rail-icon" :size="22" :stroke-width="1.9" />
          <Moon v-else class="rail-icon" :size="22" :stroke-width="1.9" />
        </button>
      </UiInstantTooltip>
      <div class="rail-avatar">AI</div>
    </div>
  </aside>
</template>

<style scoped>
.directory-rail {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  margin: 0;
  padding: var(--space-4_5) 0;
  border-radius: 0;
  background: var(--color-bg-rail);
  border: 0;
  border-right: var(--size-1) solid var(--color-border-rail);
  overflow: auto;
}

.rail-top,
.rail-bottom,
.rail-nav {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.rail-nav {
  gap: var(--space-3_5);
}

.rail-bottom {
  gap: var(--space-4);
}

.rail-brand,
.rail-item {
  display: flex;
  width: var(--size-54);
  height: var(--size-54);
  position: relative;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-text-rail);
  cursor: pointer;
}

.rail-brand {
  width: var(--size-48);
  height: var(--size-48);
  border: var(--size-1) solid var(--color-border-rail-brand);
  background: var(--color-bg-rail-brand);
  color: var(--color-text-rail-brand);
}

.rail-brand-mark {
  font-size: var(--size-24);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.rail-item.active {
  background: transparent;
  color: var(--color-text-inverse);
  box-shadow: none;
}

.rail-item.active::before {
  content: "";
  position: absolute;
  inset: var(--space-1);
  border-radius: var(--radius-md);
  background: var(--color-bg-rail-active);
  box-shadow: var(--color-shadow-rail-active);
  z-index: 0;
}

.rail-item.active .rail-icon {
  position: relative;
  z-index: 1;
}

.rail-theme-toggle {
  background: transparent !important;
  box-shadow: none !important;
  color: var(--color-text-rail);
}

.rail-theme-toggle:hover {
  background: transparent;
  color: var(--color-text-primary);
}

.rail-avatar {
  display: flex;
  width: var(--size-42);
  height: var(--size-42);
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  background: var(--color-bg-rail-avatar);
  border: var(--size-1) solid var(--color-border-rail-avatar);
  color: var(--color-text-rail-avatar);
  font-size: var(--text-body-size);
  font-weight: 700;
  letter-spacing: 0.01em;
}

.rail-overflow-menu {
  display: flex;
  min-width: 148px;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-1);
}

.rail-overflow-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1_5) var(--space-2);
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.rail-overflow-item:hover {
  background: var(--color-bg-soft-hover);
}

.rail-overflow-icon {
  flex: none;
}

@media (max-width: 600px) {
  .directory-rail {
    height: auto;
    min-height: 0;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    padding: var(--space-2_5) var(--space-3);
    overflow-x: auto;
  }

  .rail-top,
  .rail-nav {
    flex-direction: row;
    align-items: center;
  }

  .rail-nav {
    gap: var(--space-2_5);
    min-width: 0;
    flex: 1;
    justify-content: flex-start;
    overflow: hidden;
  }

  .rail-bottom {
    display: none;
  }

  .rail-brand,
  .rail-item {
    width: var(--size-44);
    height: var(--size-44);
    border-radius: var(--radius-md);
  }

  .rail-brand-mark {
    font-size: var(--size-20);
  }
}
</style>
