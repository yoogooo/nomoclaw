<script setup lang="ts">
import { computed } from "vue";
import { NConfigProvider, NGlobalStyle, darkTheme } from "naive-ui";
import { RouterView } from "vue-router";
import { resolveThemeOverrides } from "@/theme";
import { useUiPreferencesStore } from "@/stores/uiPreferences";

const uiPreferencesStore = useUiPreferencesStore();
uiPreferencesStore.init();

const appThemeOverrides = computed(() => resolveThemeOverrides(uiPreferencesStore.themeMode));
const naiveTheme = computed(() => (uiPreferencesStore.themeMode === "dark" ? darkTheme : undefined));
</script>

<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="appThemeOverrides">
    <n-global-style />
    <RouterView />
  </n-config-provider>
</template>
