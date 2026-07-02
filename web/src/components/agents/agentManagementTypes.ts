import type { Component } from "vue";
import type { AgentCatalogAgent } from "@/types/api";

export interface AgentDocConfig {
  soul: string;
  agent: string;
  memory: string;
  tools: string;
  identity: string;
  user: string;
}

export type DocKey = keyof AgentDocConfig;

export interface DocState {
  enabled: boolean;
  updatedAt: string;
}

export interface AgentTip {
  id: string;
  title: string;
  content: string;
}

export interface ManagedSkill {
  id: string;
  skillKey: string;
  name: string;
  description: string;
  path: string;
  enabled: boolean;
}

export interface SkillLinkedAgent {
  agentUid: string;
  agentName: string;
  displayName: string;
}

export interface ManagedSharedSkill extends ManagedSkill {
  linkedAgents: SkillLinkedAgent[];
}

export interface ManagedGlobalSkill extends ManagedSharedSkill {
  status: string;
}

export interface ManagedGlobalSkillAgentBinding {
  agentUid: string;
  agentName: string;
  displayName: string;
  enabled: boolean;
}

export interface ManagedTool {
  id: string;
  toolKey: string;
  name: string;
  description: string;
  serverUid?: string;
  serverName?: string;
  serverDisplayName?: string;
  enabled: boolean;
}

export interface ManagedAgent extends AgentCatalogAgent {
  agentGroupUid: string;
  avatarColor: string;
  managedSkills: ManagedSkill[];
  managedTools: ManagedTool[];
  managedMcpTools: ManagedTool[];
  tips: AgentTip[];
  docs: AgentDocConfig;
  docStates: Record<DocKey, DocState>;
}

export interface AvatarIconOption {
  key: string;
  label: string;
  icon: Component;
}

export interface BasicFormModel {
  displayName: string;
  agentType: string;
  description: string;
  avatar: string;
  avatarColor: string;
  workspace: string;
  codexWorkdir: string;
}
