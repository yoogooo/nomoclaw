<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const detailMessage = computed(() => {
  const message = route.query.message;
  return typeof message === "string" && message.trim()
    ? message
    : t("pages.forbidden.defaultReason");
});
</script>

<template>
  <div class="forbidden-page">
    <div class="forbidden-shell">
      <div class="ui-status-chip-danger">403 · ACCESS DENIED</div>
      <h1 class="forbidden-title">{{ t("pages.forbidden.title") }}</h1>
      <p class="forbidden-subtitle">
        {{ t("pages.forbidden.description") }}
      </p>

      <div class="forbidden-panel">
        <div class="ui-label-caps-danger">{{ t("pages.forbidden.reason") }}</div>
        <div class="panel-message">{{ detailMessage }}</div>
      </div>

      <div class="forbidden-actions">
        <button class="primary-action" type="button" @click="router.replace({ name: 'chat' })">
          {{ t("pages.forbidden.backHome") }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--space-8);
  background:
    radial-gradient(circle at top, var(--color-overlay-danger-soft), transparent 36%),
    var(--color-gradient-forbidden-bg);
}

.forbidden-shell {
  width: min(47.5rem, 100%);
  padding: calc(var(--space-6) + var(--space-4_5));
  border: var(--size-1) solid var(--color-border-fade);
  border-radius: var(--radius-2xl);
  background: var(--color-overlay-white-88);
  box-shadow: var(--color-shadow-xl);
  backdrop-filter: blur(var(--size-16));
}

.forbidden-title {
  margin: var(--space-4_5) 0 0;
  font-size: clamp(2.625rem, 8vw, 4.5rem);
  line-height: 0.95;
  letter-spacing: -0.06em;
  color: var(--color-text-primary);
}

.forbidden-subtitle {
  margin: var(--space-4_5) 0 0;
  max-width: var(--container-sm);
  color: var(--color-text-body-muted);
  font-size: var(--text-title-sm-size);
  line-height: 1.8;
}

.forbidden-panel {
  margin-top: var(--space-7);
  padding: var(--space-5_5) var(--space-6);
  border-radius: var(--radius-xl);
  background: var(--color-gradient-forbidden-panel);
  border: var(--size-1) solid var(--color-border-danger-soft);
}

.panel-message {
  margin-top: var(--space-2_5);
  color: var(--color-text-danger-body);
  font-size: var(--text-title-sm-size);
  line-height: 1.75;
  word-break: break-word;
}

.forbidden-actions {
  margin-top: var(--space-7);
}

.primary-action {
  border: 0;
  border-radius: var(--radius-pill);
  padding: var(--space-3_5) var(--space-5_5);
  background: var(--color-gradient-forbidden-cta);
  color: var(--color-text-inverse);
  font-size: var(--text-body-size);
  font-weight: 700;
  cursor: pointer;
  transition: transform 140ms ease, box-shadow 140ms ease;
  box-shadow: var(--shadow-button-brand);
}

.primary-action:hover {
  transform: translateY(calc(var(--size-1) * -1));
}

@media (max-width: 45rem) {
  .forbidden-page {
    padding: var(--space-4_5);
  }

  .forbidden-shell {
    padding: var(--space-7) var(--space-5_5);
    border-radius: var(--radius-xl);
  }
}
</style>
