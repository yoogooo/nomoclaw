<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import {
  Bot,
  Briefcase,
  Brain,
  BookOpen,
  Code2,
  Cpu,
  Database,
  FileSearch,
  Globe,
  Headphones,
  Lightbulb,
  Palette,
  PenTool,
  Rocket,
  Scale,
  Shield,
  Sparkles,
  User,
  Wrench
} from "lucide-vue-next";
import { NButton, NTabPane, NTabs } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import AgentBasicTab from "@/components/agents/AgentBasicTab.vue";
import AgentCreateModal from "@/components/agents/AgentCreateModal.vue";
import AgentDetailPanel from "@/components/agents/AgentDetailPanel.vue";
import AgentDocsTab from "@/components/agents/AgentDocsTab.vue";
import AgentListPane from "@/components/agents/AgentListPane.vue";
import AgentSkillDrawer from "@/components/agents/AgentSkillDrawer.vue";
import ImportSkillModal from "@/components/agents/ImportSkillModal.vue";
import AgentSkillsTab from "@/components/agents/AgentSkillsTab.vue";
import AgentTipsTab from "@/components/agents/AgentTipsTab.vue";
import AgentToolsTab from "@/components/agents/AgentToolsTab.vue";
import type {
  AvatarIconOption,
  DocKey
} from "@/components/agents/agentManagementTypes";
import { themeTokens } from "@/themeTokens";
import type { ImportedSkillResponse } from "@/types/api";
import { approxBytes, formatRelativeTime, joinPath } from "@/components/agents/domain/agentManagementDomain";
import { useAgentsManagement } from "@/components/agents/useAgentsManagement";
import { getSortLocale } from "@/i18n";
import { message } from "@/discrete";

const AGENTS_PAGE_STATE_STORAGE_KEY = "agents-page:selection:v1";
const AGENT_DETAIL_TABS = ["basic", "skills", "tools", "tips", "docs"] as const;

interface AgentsPagePersistedState {
  selectedAgentUid: string;
  detailTab: string;
}

function isValidDetailTab(value: string) {
  return AGENT_DETAIL_TABS.includes(value as (typeof AGENT_DETAIL_TABS)[number]);
}

function restoreAgentsPageState(): AgentsPagePersistedState | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(AGENTS_PAGE_STATE_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<AgentsPagePersistedState>;
    const selectedAgentUid = String(parsed.selectedAgentUid || "").trim();
    const detailTab = isValidDetailTab(String(parsed.detailTab || "")) ? String(parsed.detailTab) : "basic";
    return {
      selectedAgentUid,
      detailTab
    };
  } catch {
    return null;
  }
}

function persistAgentsPageState(payload: AgentsPagePersistedState) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(AGENTS_PAGE_STATE_STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore local persistence errors to avoid breaking page interactions.
  }
}

const detailTab = ref("basic");
const selectedAgentUid = ref("");
const showEditor = ref(false);
const selectedDocKey = ref<DocKey>("soul");
const docEditable = ref(false);
const skillDrawerVisible = ref(false);
const selectedSkillId = ref("");
const importSkillVisible = ref(false);
const refreshingConfig = ref(false);
const { t } = useI18n();

const avatarIconOptions: AvatarIconOption[] = [
  { key: "bot", label: "Bot", icon: Bot },
  { key: "user", label: "User", icon: User },
  { key: "sparkles", label: "Sparkles", icon: Sparkles },
  { key: "brain", label: "Brain", icon: Brain },
  { key: "book", label: "Book", icon: BookOpen },
  { key: "tool", label: "Tool", icon: Wrench },
  { key: "shield", label: "Shield", icon: Shield },
  { key: "briefcase", label: "Briefcase", icon: Briefcase },
  { key: "code", label: "Code", icon: Code2 },
  { key: "database", label: "Database", icon: Database },
  { key: "globe", label: "Globe", icon: Globe },
  { key: "search", label: "Search", icon: FileSearch },
  { key: "rocket", label: "Rocket", icon: Rocket },
  { key: "palette", label: "Palette", icon: Palette },
  { key: "idea", label: "Idea", icon: Lightbulb },
  { key: "audio", label: "Audio", icon: Headphones },
  { key: "legal", label: "Legal", icon: Scale },
  { key: "chip", label: "Chip", icon: Cpu },
  { key: "design", label: "Design", icon: PenTool }
];
const DEFAULT_AVATAR_ICON = "bot";
const DEFAULT_AVATAR_COLOR: string = themeTokens.component.agent.avatarColors[0];
const avatarColorOptions: string[] = [...themeTokens.component.agent.avatarColors];

const management = useAgentsManagement({
  avatarIconOptions,
  avatarColorOptions,
  defaultAvatarIcon: DEFAULT_AVATAR_ICON,
  defaultAvatarColor: DEFAULT_AVATAR_COLOR
});

const sortedAgents = computed(() =>
  [...management.agents.value].sort((a, b) => {
    if (a.sortIndex !== b.sortIndex) return a.sortIndex - b.sortIndex;
    return (a.displayName || a.agentName).localeCompare(b.displayName || b.agentName, getSortLocale());
  })
);

const selectedAgent = computed(() =>
  sortedAgents.value.find((item) => item.agentUid === selectedAgentUid.value) || null
);

const docItems = computed(() => [
  { key: "soul" as DocKey, label: "SOUL.md" },
  { key: "agent" as DocKey, label: "AGENT.md" },
  { key: "memory" as DocKey, label: "MEMORY.md" },
  { key: "tools" as DocKey, label: "TOOLS.md" },
  { key: "identity" as DocKey, label: "IDENTITY.md" },
  { key: "user" as DocKey, label: "USER.md" }
]);

const selectedDocLabel = computed(() =>
  docItems.value.find((item) => item.key === selectedDocKey.value)?.label || "SOUL.md"
);

const selectedDocPath = computed(() => {
  const agentName = selectedAgent.value?.agentName || "default";
  return joinPath(management.agentsRootDir.value, agentName, selectedDocLabel.value);
});

const selectedDocEnabled = computed(() =>
  management.docEnabledOf(selectedAgent.value, selectedDocKey.value)
);

const selectedDocContent = computed(() =>
  management.docContentOf(selectedAgent.value, selectedDocKey.value)
);

const docListItems = computed(() =>
  docItems.value.map((item) => ({
    ...item,
    enabled: management.docEnabledOf(selectedAgent.value, item.key),
    sizeBytes: approxBytes(management.docContentOf(selectedAgent.value, item.key)),
    updatedText: formatRelativeTime(management.docUpdatedAtOf(selectedAgent.value, item.key))
  }))
);

const selectedSkill = computed(() =>
  selectedAgent.value?.managedSkills.find((item) => item.id === selectedSkillId.value) || null
);

function onSelectAgent(agentUid: string) {
  selectedAgentUid.value = agentUid;
  management.syncDocsFormFromSelection(selectedAgent.value);
  management.syncBasicFormFromSelection(selectedAgent.value);
}

function openCreate() {
  management.resetCreateForm();
  showEditor.value = true;
}

async function onSaveAgent() {
  const createdUid = await management.saveAgent();
  if (!createdUid) return;
  selectedAgentUid.value = management.ensureSelectedAgent(createdUid);
  management.syncDocsFormFromSelection(selectedAgent.value);
  management.syncBasicFormFromSelection(selectedAgent.value);
  if (selectedAgentUid.value) {
    await management.loadAgentWorkspace(selectedAgentUid.value);
  }
  showEditor.value = false;
}

function onRemoveAgent() {
  if (!selectedAgent.value) return;
  management.removeAgent(selectedAgent.value, () => {
    selectedAgentUid.value = management.ensureSelectedAgent(selectedAgentUid.value);
    management.syncDocsFormFromSelection(selectedAgent.value);
    management.syncBasicFormFromSelection(selectedAgent.value);
  });
}

function openSkillDrawer(skillId: string) {
  selectedSkillId.value = skillId;
  skillDrawerVisible.value = true;
}

function onToggleSkill(skillId: string, enabled: boolean) {
  management.setSkillEnabled(selectedAgent.value, skillId, enabled);
}

function onToggleTool(toolId: string, enabled: boolean) {
  management.setToolEnabled(selectedAgent.value, toolId, enabled);
}

function onToggleDoc(key: string, enabled: boolean) {
  management.setDocEnabled(selectedAgent.value, key as DocKey, enabled);
}

async function handleSkillImported(skill: ImportedSkillResponse) {
  if (!selectedAgent.value) return;
  await management.handleSkillImported(selectedAgent.value, skill);
  selectedSkillId.value = `skill_${skill.skillKey || skill.displayName}`;
  skillDrawerVisible.value = true;
  detailTab.value = "skills";
}

async function refreshAgentsConfig(notify = true) {
  if (refreshingConfig.value) {
    return;
  }
  refreshingConfig.value = true;
  const preferredAgentUid = selectedAgentUid.value;
  try {
    await management.init();
    selectedAgentUid.value = management.ensureSelectedAgent(preferredAgentUid);
    management.syncDocsFormFromSelection(selectedAgent.value);
    management.syncBasicFormFromSelection(selectedAgent.value);
    if (selectedAgentUid.value) {
      await management.loadAgentWorkspace(selectedAgentUid.value);
    }
    if (notify) {
      message.success(t("toast.configRefreshed"));
    }
  } catch {
    if (notify) {
      message.error(t("toast.refreshFailed"));
    }
  } finally {
    refreshingConfig.value = false;
  }
}

onMounted(async () => {
  const restoredState = restoreAgentsPageState();
  if (restoredState) {
    detailTab.value = restoredState.detailTab;
    selectedAgentUid.value = restoredState.selectedAgentUid;
  }
  await refreshAgentsConfig(false);
});

watch([selectedDocKey, detailTab, selectedAgentUid], () => {
  docEditable.value = false;
  if (detailTab.value !== "skills" || !selectedAgent.value?.managedSkills.some((item) => item.id === selectedSkillId.value)) {
    skillDrawerVisible.value = false;
    selectedSkillId.value = "";
  }
});

watch(selectedAgentUid, async (agentUid) => {
  if (!agentUid) return;
  await management.loadAgentWorkspace(agentUid);
  management.syncDocsFormFromSelection(selectedAgent.value);
});

watch([selectedAgentUid, detailTab], ([agentUid, tab]) => {
  persistAgentsPageState({
    selectedAgentUid: agentUid || "",
    detailTab: isValidDetailTab(tab || "") ? tab : "basic"
  });
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content agents-page">
          <AppPageHeader
            :title="t('pages.agents.title')"
            :subtitle="t('pages.agents.subtitle')"
          >
            <template #actions>
              <n-button :loading="refreshingConfig" @click="refreshAgentsConfig()">
                {{ t("common.refresh") }}
              </n-button>
            </template>
          </AppPageHeader>

          <div class="agent-main-grid">
            <AgentListPane
              :agents="sortedAgents"
              :selected-agent-uid="selectedAgentUid"
              :avatar-icon-options="avatarIconOptions"
              :default-avatar-color="DEFAULT_AVATAR_COLOR"
              @create="openCreate"
              @select="onSelectAgent"
            />

            <AgentDetailPanel :selected-agent="selectedAgent">
              <n-tabs v-if="selectedAgent" v-model:value="detailTab" type="line" animated>
                <n-tab-pane name="basic" :tab="t('pages.agents.tabBasic')">
                  <AgentBasicTab
                    :selected-agent="selectedAgent"
                    :basic-form="management.basicForm"
                    :avatar-icon-options="avatarIconOptions"
                    :avatar-color-options="avatarColorOptions"
                    @save="management.saveBasicInfo(selectedAgent)"
                    @remove="onRemoveAgent"
                    @update:display-name="management.basicForm.displayName = $event"
                    @update:description="management.basicForm.description = $event"
                    @update:avatar="management.basicForm.avatar = $event"
                    @update:avatar-color="management.basicForm.avatarColor = $event"
                    @update:workspace="management.basicForm.workspace = $event"
                  />
                </n-tab-pane>

                <n-tab-pane name="skills" :tab="t('pages.agents.tabSkills')">
                  <AgentSkillsTab
                    :skills="selectedAgent.managedSkills"
                    :show-toolbar="false"
                    @import="importSkillVisible = true"
                    @open="openSkillDrawer"
                    @toggle="onToggleSkill"
                  />
                </n-tab-pane>

                <n-tab-pane name="tools" :tab="t('pages.agents.tabTools')">
                  <AgentToolsTab
                    :tools="selectedAgent.managedTools"
                    @toggle="onToggleTool"
                  />
                </n-tab-pane>

                <n-tab-pane name="tips" :tab="t('pages.agents.tabTips')">
                  <AgentTipsTab
                    :tips="selectedAgent.tips"
                    :tip-title="management.tipForm.title"
                    :tip-content="management.tipForm.content"
                    @add="management.addTip(selectedAgent)"
                    @update-tip="management.updateTip(selectedAgent, $event)"
                    @remove="management.removeTip(selectedAgent, $event)"
                    @update:title="management.tipForm.title = $event"
                    @update:content="management.tipForm.content = $event"
                  />
                </n-tab-pane>

                <n-tab-pane name="docs" :tab="t('pages.agents.tabDocs')">
                  <AgentDocsTab
                    :doc-items="docListItems"
                    :selected-doc-key="selectedDocKey"
                    :selected-doc-label="selectedDocLabel"
                    :selected-doc-path="selectedDocPath"
                    :selected-doc-enabled="selectedDocEnabled"
                    :doc-editable="docEditable"
                    :selected-doc-content="selectedDocContent"
                    @select-doc="selectedDocKey = $event as DocKey"
                    @toggle-doc="onToggleDoc"
                    @toggle-edit="docEditable = !docEditable"
                    @save="management.saveDocs(selectedAgent, selectedDocKey)"
                    @update-content="management.updateDocContent(selectedDocKey, $event)"
                  />
                </n-tab-pane>
              </n-tabs>
            </AgentDetailPanel>
          </div>
        </div>
      </main>
    </div>
  </div>

  <AgentSkillDrawer
    :show="skillDrawerVisible"
    :skill="selectedSkill"
    @update:show="skillDrawerVisible = $event"
  />

  <ImportSkillModal
    :show="importSkillVisible"
    :agent-uid="selectedAgent?.agentUid || ''"
    @update:show="importSkillVisible = $event"
    @success="handleSkillImported"
  />

  <AgentCreateModal
    :show="showEditor"
    :form="management.createForm"
    :avatar-icon-options="avatarIconOptions"
    :avatar-color-options="avatarColorOptions"
    @update:show="showEditor = $event"
    @save="onSaveAgent"
    @update:display-name="management.createForm.displayName = $event"
    @update:agent-name="management.createForm.agentName = $event"
    @update:description="management.createForm.description = $event"
    @update:avatar="management.createForm.avatar = $event"
    @update:avatar-color="management.createForm.avatarColor = $event"
    @update:workspace="management.createForm.workspace = $event"
  />
</template>

<style scoped>
.agent-main-grid {
  display: grid;
  grid-template-columns: var(--size-320) minmax(0, 1fr);
  gap: var(--space-4);
  min-height: 0;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .agent-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
