<script setup lang="ts">
import { NConfigProvider, NGlobalStyle } from "naive-ui";
import { RouterView } from "vue-router";
import { themeOverrides } from "@/theme";
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-global-style />
    <RouterView />
  </n-config-provider>
</template>
