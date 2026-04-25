<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AgentSkillsTab from "@/components/agents/AgentSkillsTab.vue";
import GlobalSkillDrawer from "@/components/agents/GlobalSkillDrawer.vue";
import ImportSkillModal from "@/components/agents/ImportSkillModal.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { conversationApi } from "@/api/conversationApi";
import type { AgentCatalogGroup, ImportedSkillResponse } from "@/types/api";
import type { ManagedGlobalSkill, ManagedGlobalSkillAgentBinding } from "@/components/agents/agentManagementTypes";
import { mapApiGlobalSkill } from "@/components/agents/domain/agentManagementDomain";
import { dialog, message, warningDialogPreset } from "@/discrete";

const { t } = useI18n();
const loading = ref(false);
const skills = ref<ManagedGlobalSkill[]>([]);
const skillDrawerVisible = ref(false);
const selectedSkillId = ref("");
const nomoclawRootDir = ref("");
const agentsRootDir = ref("");
const skillsRootDir = ref("");
const importSkillVisible = ref(false);
const importAgentUid = ref("");
const actingSkillId = ref("");
const bindingsLoading = ref(false);
const bindingsSaving = ref(false);
const originalEnabled = ref(false);
const originalEnabledAgentCount = ref(0);
const originalAgentBindings = ref<Record<string, boolean>>({});
const draftEnabled = ref(false);
const draftAgentBindings = ref<ManagedGlobalSkillAgentBinding[]>([]);

const selectedSkill = computed(() =>
  skills.value.find((item) => item.id === selectedSkillId.value) || null
);
const hasDraftChanges = computed(() => {
  if (!selectedSkill.value) {
    return false;
  }
  if (draftEnabled.value !== originalEnabled.value) {
    return true;
  }
  const originMap = new Map<string, boolean>();
  Object.entries(originalAgentBindings.value).forEach(([agentUid, enabled]) => {
    originMap.set(agentUid, enabled);
  });
  return draftAgentBindings.value.some((item) => originMap.get(item.agentUid) !== item.enabled);
});

async function loadSkills() {
  loading.value = true;
  try {
    const systemConfig = await conversationApi.getSystemConfig();
    nomoclawRootDir.value = (systemConfig.nomoclawRootDir || "").trim();
    agentsRootDir.value = (systemConfig.agentsRootDir || "").trim();
    skillsRootDir.value = (systemConfig.skillsRootDir || "").trim();

    const groups = await conversationApi.listAgentGroups();
    const allAgents = flattenAgents(groups);
    importAgentUid.value = resolveImportAgentUid(allAgents);
    const globalSkills = await conversationApi.listSkills();
    skills.value = globalSkills
      .map((skill) => mapApiGlobalSkill(skill, {
        nomoclawRootDir: nomoclawRootDir.value,
        agentsRootDir: agentsRootDir.value,
        skillsRootDir: skillsRootDir.value,
        avatarIconKeys: [],
        avatarColorOptions: [],
        defaultAvatarIcon: "bot",
        defaultAvatarColor: "#2F6FED"
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
    if (!skills.value.some((item) => item.id === selectedSkillId.value)) {
      skillDrawerVisible.value = false;
      selectedSkillId.value = "";
    }
  } catch {
    message.error(t("toast.refreshFailed"));
  } finally {
    loading.value = false;
  }
}

function flattenAgents(groups: AgentCatalogGroup[]) {
  return groups.flatMap((group) => group.agents);
}

function resolveImportAgentUid(agents: ReturnType<typeof flattenAgents>) {
  return agents.find((item) => item.agentUid === "agent_general_assistant")?.agentUid || agents[0]?.agentUid || "";
}

function openSkillDrawer(skillId: string) {
  selectedSkillId.value = skillId;
  skillDrawerVisible.value = true;
  void loadSkillBindings();
}

function deleteGlobalSkill(skillId: string) {
  const skill = skills.value.find((item) => item.id === skillId);
  if (!skill || actingSkillId.value) {
    return;
  }
  dialog.error({
    title: t("dialogs.deleteSkillTitle"),
    content: t("dialogs.deleteSkillContent", { name: skill.name }),
    ...warningDialogPreset(),
    positiveText: t("dialogs.confirmPermanentDelete"),
    negativeText: t("common.cancel"),
    onPositiveClick: async () => {
      actingSkillId.value = skillId;
      try {
        await conversationApi.deleteSkill(skill.skillKey);
        await loadSkills();
        message.success(t("toast.skillDeleted"));
      } finally {
        actingSkillId.value = "";
      }
    }
  });
}

async function loadSkillBindings() {
  if (!selectedSkill.value) {
    return;
  }
  bindingsLoading.value = true;
  try {
    const bindings = await conversationApi.getSkillBindings(selectedSkill.value.skillKey);
    originalEnabled.value = (bindings.status || "").toUpperCase() === "ACTIVE";
    originalEnabledAgentCount.value = bindings.enabledAgentCount || 0;
    draftEnabled.value = originalEnabled.value;
    draftAgentBindings.value = (bindings.agentBindings || [])
      .map((item) => ({
        agentUid: item.agentUid,
        agentName: item.agentName,
        displayName: item.displayName,
        enabled: item.enabled
      }))
      .sort((a, b) => (a.displayName || a.agentName).localeCompare(b.displayName || b.agentName));
    originalAgentBindings.value = draftAgentBindings.value.reduce<Record<string, boolean>>((acc, item) => {
      acc[item.agentUid] = item.enabled;
      return acc;
    }, {});
  } finally {
    bindingsLoading.value = false;
  }
}

function updateDraftAgentBinding(payload: { agentUid: string; enabled: boolean }) {
  const item = draftAgentBindings.value.find((agent) => agent.agentUid === payload.agentUid);
  if (!item) {
    return;
  }
  item.enabled = payload.enabled;
}

async function saveSkillBindings() {
  if (!selectedSkill.value || bindingsSaving.value || !hasDraftChanges.value) {
    return;
  }
  const requiresDisableWarning = originalEnabled.value
    && originalEnabledAgentCount.value > 0
    && !draftEnabled.value;
  if (requiresDisableWarning) {
    dialog.warning({
      title: t("dialogs.disableSkillImpactTitle"),
      content: t("dialogs.disableSkillImpactContent", { count: originalEnabledAgentCount.value }),
      ...warningDialogPreset(),
      positiveText: t("common.confirm"),
      negativeText: t("common.cancel"),
      onPositiveClick: () => {
        void submitSkillBindings();
      }
    });
    return;
  }
  await submitSkillBindings();
}

async function submitSkillBindings() {
  if (!selectedSkill.value) {
    return;
  }
  bindingsSaving.value = true;
  try {
    await conversationApi.updateSkillBindings(selectedSkill.value.skillKey, {
      enabled: draftEnabled.value,
      agentBindings: draftAgentBindings.value.map((item) => ({
        agentUid: item.agentUid,
        enabled: item.enabled
      }))
    });
    await Promise.all([loadSkills(), loadSkillBindings()]);
    message.success(t("toast.skillSaved"));
  } finally {
    bindingsSaving.value = false;
  }
}

async function handleSkillImported(skill: ImportedSkillResponse) {
  await loadSkills();
  selectedSkillId.value = `skill_${skill.skillKey || skill.displayName}`;
  skillDrawerVisible.value = true;
  await loadSkillBindings();
}

onMounted(async () => {
  await loadSkills();
});
</script>

<template>
  <div class="page-frame app-page-shell">
    <div class="app-layout app-layout-responsive">
      <DirectoryRail />
      <main class="app-main-content">
        <div class="app-page-content skills-page">
          <AppPageHeader
            :title="t('pages.skills.title')"
            :subtitle="t('pages.skills.subtitle')"
          >
            <template #actions>
              <n-button :loading="loading" @click="loadSkills">
                {{ t("common.refresh") }}
              </n-button>
            </template>
          </AppPageHeader>

          <AgentSkillsTab
            :skills="skills"
            :import-disabled="!importAgentUid"
            :show-linked-agents="true"
            :show-toggle="false"
            :show-path="false"
            :show-description-copy="false"
            :show-hover-edit="true"
            @open="openSkillDrawer"
            @toggle="() => undefined"
            @import="importSkillVisible = true"
          />
        </div>
      </main>
    </div>
  </div>

  <GlobalSkillDrawer
    :show="skillDrawerVisible"
    :skill="selectedSkill"
    :loading="bindingsLoading"
    :saving="bindingsSaving"
    :enabled-draft="draftEnabled"
    :agent-bindings="draftAgentBindings"
    :save-disabled="!hasDraftChanges"
    @update:show="skillDrawerVisible = $event"
    @update:enabled="draftEnabled = $event"
    @update:agent="updateDraftAgentBinding"
    @save="saveSkillBindings"
    @delete="selectedSkillId && deleteGlobalSkill(selectedSkillId)"
  />

  <ImportSkillModal
    :show="importSkillVisible"
    :agent-uid="importAgentUid"
    @update:show="importSkillVisible = $event"
    @success="handleSkillImported"
  />
</template>

<style scoped>
.skills-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.skills-page :deep(.ui-card-grid) {
  gap: var(--space-4);
}
</style>
