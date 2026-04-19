<script setup lang="ts">
import { NButton, NSelect, NSwitch } from "naive-ui";
import { useI18n } from "vue-i18n";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import { useUpdateManagerStore } from "@/stores/updateManager";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { conversationApi } from "@/api/conversationApi";
import { message } from "@/discrete";
import type { AppLocale } from "@/i18n";
import { computed, onMounted, ref, watch } from "vue";
import type { PermissionRulePayload } from "@/types/api";

const uiPreferencesStore = useUiPreferencesStore();
const updateManagerStore = useUpdateManagerStore();
const agentCatalogStore = useAgentCatalogStore();
uiPreferencesStore.init();
const { t } = useI18n();
const localeOptions = computed(() => [
  { label: t("settings.languageZhCN"), value: "zh-CN" },
  { label: t("settings.languageEnUS"), value: "en-US" }
]);
const downloadPercentText = computed(() => {
  const value = updateManagerStore.downloadProgress;
  if (value === null || value === undefined) return "";
  return `${Math.round(Math.min(Math.max(value, 0), 1) * 100)}%`;
});
const loadingPermissions = ref(false);
const savingPermissions = ref(false);
const selectedAgentUid = computed(() => agentCatalogStore.selectedAgentUid || "");
const selectedAgentName = ref("");
const hardGuardSummary = ref("");
const agentRulesJson = ref("[]");
const userRulesJson = ref("[]");

onMounted(() => {
  void updateManagerStore.init();
  void loadPermissions();
});

watch(selectedAgentUid, () => {
  void loadPermissions();
});

function onLocaleChange(value: AppLocale | null) {
  if (!value) return;
  uiPreferencesStore.setLocale(value);
}

async function loadPermissions() {
  try {
    loadingPermissions.value = true;
    const result = await conversationApi.getEffectivePermissions("", selectedAgentUid.value);
    selectedAgentName.value = result.agentName || "";
    hardGuardSummary.value = `Protected: ${(result.hardGuardProtectedNames || []).join(", ")} | System: ${(result.hardGuardSystemRoots || []).slice(0, 6).join(", ")}...`;
    agentRulesJson.value = JSON.stringify(result.agentSettingsRules || [], null, 2);
    userRulesJson.value = JSON.stringify(result.userSettingsRules || [], null, 2);
  } catch (error) {
    message.error("加载权限规则失败");
  } finally {
    loadingPermissions.value = false;
  }
}

function parseRules(raw: string): PermissionRulePayload[] {
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error("rules must be array");
  }
  return parsed as PermissionRulePayload[];
}

async function saveAgentRules() {
  if (!selectedAgentUid.value) {
    message.warning("请先在左侧选择一个 Agent");
    return;
  }
  try {
    savingPermissions.value = true;
    const rules = parseRules(agentRulesJson.value);
    await conversationApi.updateAgentPermissions(selectedAgentUid.value, rules);
    message.success("Agent 权限规则已保存");
    await loadPermissions();
  } catch (error) {
    message.error("保存 Agent 权限规则失败，请检查 JSON 格式");
  } finally {
    savingPermissions.value = false;
  }
}

async function saveUserRules() {
  try {
    savingPermissions.value = true;
    const rules = parseRules(userRulesJson.value);
    await conversationApi.updateUserPermissions(selectedAgentUid.value, rules);
    message.success("全局权限规则已保存");
    await loadPermissions();
  } catch (error) {
    message.error("保存全局权限规则失败，请检查 JSON 格式");
  } finally {
    savingPermissions.value = false;
  }
}
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content">
          <AppPageHeader
            :title="t('settings.title')"
            :subtitle="t('settings.subtitle')"
          >
            <template #actions>
              <div v-if="updateManagerStore.supported" class="settings-updater-actions">
                <span v-if="updateManagerStore.latestVersion" class="settings-updater-version">
                  {{ t("settings.updateVersion", { version: updateManagerStore.latestVersion }) }}
                </span>
                <span v-if="updateManagerStore.status === 'downloading'" class="settings-updater-progress">
                  {{ t("settings.updateDownloading", { progress: downloadPercentText || "--" }) }}
                </span>
                <span v-if="updateManagerStore.status === 'error' && updateManagerStore.errorMessage" class="settings-updater-error">
                  {{ t("settings.updateFailedWithReason", { reason: updateManagerStore.errorMessage }) }}
                </span>

                <n-button
                  v-if="updateManagerStore.status === 'downloaded'"
                  type="primary"
                  :loading="updateManagerStore.busy"
                  @click="() => updateManagerStore.installDownloaded()"
                >
                  {{ t("settings.updateInstallNow") }}
                </n-button>
                <n-button
                  v-else
                  tertiary
                  :loading="updateManagerStore.status === 'checking' || updateManagerStore.busy"
                  @click="() => updateManagerStore.checkNow()"
                >
                  {{
                    updateManagerStore.status === "error"
                      ? t("settings.updateRetry")
                      : t("settings.updateCheckNow")
                  }}
                </n-button>
              </div>
            </template>
          </AppPageHeader>
          <div class="page-section-grid settings-grid">
          <section class="surface-card">
            <div class="surface-card-title">{{ t("settings.account") }}</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">{{ t("settings.currentIdentity") }}</div>
                <div class="ui-value-strong">NomoClaw Operator</div>
              </div>
              <div>
                <div class="meta-label">{{ t("settings.defaultTimezone") }}</div>
                <div class="ui-value-strong">Asia/Shanghai</div>
              </div>
            </div>
          </section>

          <section class="surface-card">
            <div class="surface-card-title">{{ t("settings.preferences") }}</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">{{ t("settings.language") }}</div>
                <div class="ui-value-strong settings-language-select-wrap">
                  <n-select
                    class="settings-language-select"
                    :value="uiPreferencesStore.locale"
                    :options="localeOptions"
                    :clearable="false"
                    @update:value="onLocaleChange"
                  />
                </div>
              </div>
              <div>
                <div class="meta-label">{{ t("settings.runtimeLogVisibility") }}</div>
                <div class="settings-switch-row">
                  <n-switch
                    :value="uiPreferencesStore.chatRuntimeLogVisible"
                    @update:value="(value) => uiPreferencesStore.setChatRuntimeLogVisible(Boolean(value))"
                  />
                  <span class="settings-switch-state">{{ uiPreferencesStore.chatRuntimeLogVisible ? t("settings.runtimeLogOn") : t("settings.runtimeLogOff") }}</span>
                </div>
              </div>
            </div>
          </section>
          <section class="surface-card settings-permissions-card">
            <div class="surface-card-title">Permissions</div>
            <div class="ui-detail-list">
              <div>
                <div class="meta-label">当前 Agent</div>
                <div class="ui-value-strong">{{ selectedAgentName || "未选择" }}</div>
              </div>
              <div>
                <div class="meta-label">HardGuard</div>
                <div class="ui-value-strong settings-hardguard">{{ hardGuardSummary }}</div>
              </div>
            </div>
            <div class="settings-permission-editors">
              <div class="settings-permission-block">
                <div class="meta-label">agentSettings.rules</div>
                <textarea v-model="agentRulesJson" class="settings-permission-editor mono" :disabled="loadingPermissions || savingPermissions" />
                <div class="settings-permission-actions">
                  <n-button type="primary" :loading="savingPermissions" :disabled="loadingPermissions" @click="saveAgentRules">
                    保存 Agent 规则
                  </n-button>
                </div>
              </div>
              <div class="settings-permission-block">
                <div class="meta-label">userSettings.rules</div>
                <textarea v-model="userRulesJson" class="settings-permission-editor mono" :disabled="loadingPermissions || savingPermissions" />
                <div class="settings-permission-actions">
                  <n-button tertiary :loading="savingPermissions" :disabled="loadingPermissions" @click="saveUserRules">
                    保存全局规则
                  </n-button>
                </div>
              </div>
            </div>
          </section>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
}

.settings-permissions-card {
  grid-column: 1 / -1;
}

.settings-hardguard {
  overflow-wrap: anywhere;
}

.settings-permission-editors {
  margin-top: var(--space-3);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.settings-permission-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.settings-permission-editor {
  width: 100%;
  min-height: 220px;
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  background: var(--color-bg-surface-mute);
  color: var(--color-text-primary);
}

.settings-permission-actions {
  display: flex;
  justify-content: flex-end;
}

.settings-language-select-wrap {
  margin-top: var(--space-1_5);
  display: inline-block;
}

.settings-language-select {
  width: var(--size-180);
  min-width: var(--size-170);
}

.settings-switch-row {
  margin-top: var(--space-1_5);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--space-8);
}

.settings-switch-state {
  font-size: var(--text-body-size);
  font-weight: 400;
  line-height: 1.2;
}

.settings-updater-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.settings-updater-version,
.settings-updater-progress,
.settings-updater-error {
  font-size: var(--text-caption-size);
  color: var(--color-text-secondary);
}

.settings-updater-error {
  color: var(--color-danger);
}

@media (max-width: var(--size-breakpoint-lg)) {
  .settings-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: var(--size-breakpoint-md)) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .settings-permission-editors {
    grid-template-columns: 1fr;
  }
}
</style>
