<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { NButton } from "naive-ui";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AgentSkillDrawer from "@/components/agents/AgentSkillDrawer.vue";
import AgentSkillsTab from "@/components/agents/AgentSkillsTab.vue";
import ImportSkillModal from "@/components/agents/ImportSkillModal.vue";
import AppPageHeader from "@/components/layout/AppPageHeader.vue";
import { conversationApi } from "@/api/conversationApi";
import type { AgentCatalogGroup, ImportedSkillResponse } from "@/types/api";
import type { ManagedSharedSkill } from "@/components/agents/agentManagementTypes";
import { mapApiSkill } from "@/components/agents/domain/agentManagementDomain";
import { message } from "@/discrete";

const { t } = useI18n();
const loading = ref(false);
const skills = ref<ManagedSharedSkill[]>([]);
const skillDrawerVisible = ref(false);
const selectedSkillId = ref("");
const nomoclawRootDir = ref("");
const skillsRootDir = ref("");
const importSkillVisible = ref(false);
const importAgentUid = ref("");

const selectedSkill = computed(() =>
  skills.value.find((item) => item.id === selectedSkillId.value) || null
);

async function loadSkills() {
  loading.value = true;
  try {
    const systemConfig = await conversationApi.getSystemConfig();
    nomoclawRootDir.value = (systemConfig.nomoclawRootDir || "").trim();
    skillsRootDir.value = (systemConfig.skillsRootDir || "").trim();

    const groups = await conversationApi.listAgentGroups();
    const allAgents = flattenAgents(groups);
    importAgentUid.value = resolveImportAgentUid(allAgents);
    const skillEntries = await Promise.all(
      allAgents.map(async (agent) => ({
        agent,
        skills: await conversationApi.listAgentSkills(agent.agentUid)
      }))
    );

    const skillsMap = new Map<string, ManagedSharedSkill>();
    skillEntries.forEach(({ agent, skills: agentSkills }) => {
      agentSkills.forEach((skill) => {
        const mapped = mapApiSkill(skill, {
          nomoclawRootDir: nomoclawRootDir.value,
          skillsRootDir: skillsRootDir.value,
          avatarIconKeys: [],
          avatarColorOptions: [],
          defaultAvatarIcon: "bot",
          defaultAvatarColor: "#2F6FED"
        });
        const existing = skillsMap.get(mapped.skillKey);
        const linkedAgents = skill.enabled
          ? [
              {
                agentUid: agent.agentUid,
                agentName: agent.agentName,
                displayName: agent.displayName
              }
            ]
          : [];
        if (!existing) {
          skillsMap.set(mapped.skillKey, {
            ...mapped,
            enabled: linkedAgents.length > 0,
            linkedAgents
          });
          return;
        }
        if (linkedAgents.length > 0) {
          existing.linkedAgents.push(...linkedAgents);
          existing.enabled = true;
        }
      });
    });

    skills.value = [...skillsMap.values()].sort((a, b) => a.name.localeCompare(b.name));
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
}

async function handleSkillImported(skill: ImportedSkillResponse) {
  await loadSkills();
  selectedSkillId.value = `skill_${skill.skillKey || skill.displayName}`;
  skillDrawerVisible.value = true;
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
            @open="openSkillDrawer"
            @toggle="() => undefined"
            @import="importSkillVisible = true"
          />
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
