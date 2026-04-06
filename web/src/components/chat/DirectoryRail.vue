<script setup lang="ts">
import { computed } from "vue";
import { Bot, Brain, CalendarClock, MessageCircleMore, Settings, PlugZap } from "lucide-vue-next";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const navItems = computed(() => [
  { key: "chat", label: t("nav.chat"), icon: MessageCircleMore, to: "/" },
  { key: "cron", label: t("nav.cron"), icon: CalendarClock, to: "/cron" },
  { key: "agents", label: t("nav.agents"), icon: Bot, to: "/agents" },
  { key: "channels", label: t("nav.channels"), icon: PlugZap, to: "/channels" },
  { key: "models", label: t("nav.models"), icon: Brain, to: "/models" },
  { key: "settings", label: t("nav.settings"), icon: Settings, to: "/settings" }
]);

function isActive(path: string) {
  return route.path === path;
}

function openNavItem(item: { to: string; newPage?: boolean }) {
  if (item.newPage) {
    window.open(item.to, "_blank", "noopener,noreferrer");
    return;
  }
  void router.push(item.to);
}
</script>

<template>
  <aside class="directory-rail">
    <div class="rail-top">
      <button class="rail-brand" type="button" aria-label="NomoClaw">
        <span class="rail-brand-mark">N</span>
      </button>
    </div>

    <nav class="rail-nav" aria-label="Primary">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="rail-item"
        :class="{ active: isActive(item.to) }"
        type="button"
        :aria-label="item.label"
        :title="item.label"
        @click="openNavItem(item)"
      >
        <component :is="item.icon" class="rail-icon" :size="22" :stroke-width="1.9" />
      </button>
    </nav>

    <div class="rail-bottom">
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
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-text-rail);
  cursor: pointer;
}

.rail-brand {
  border: var(--size-2) solid var(--color-border-rail-brand);
  background: var(--color-bg-rail-brand);
  color: var(--color-text-rail-brand);
}

.rail-brand-mark {
  font-size: var(--size-24);
  font-weight: 800;
  letter-spacing: 0.04em;
}

.rail-item.active {
  background: var(--color-bg-rail-active);
  color: var(--color-text-inverse);
  box-shadow: var(--color-shadow-rail-active);
}

.rail-avatar {
  display: flex;
  width: var(--size-54);
  height: var(--size-54);
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  background: var(--color-bg-rail-avatar);
  border: var(--size-1) solid var(--color-border-rail-avatar);
  color: var(--color-text-rail-avatar);
  font-size: var(--font-size-md);
  font-weight: 700;
  letter-spacing: 0.01em;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .directory-rail {
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
  }

  .rail-bottom {
    display: none;
  }

  .rail-brand,
  .rail-item {
    width: var(--size-44);
    height: var(--size-44);
    border-radius: var(--radius-m-lg);
  }

  .rail-brand-mark {
    font-size: var(--size-20);
  }
}
</style>
