import type {
  AgentCatalogAgent,
  AgentCatalogGroup,
  AgentSkill,
  AgentTip,
  AgentTool,
  CreateSkillPayload,
  ConversationMessage,
  ConversationMessageRun,
  ConversationSummary,
  CreateConversationResponse,
  ImportedSkillResponse,
  ImportSkillFromUrlPayload,
  MessageResponse,
  SimpleResponse,
  SystemConfig,
  UploadFilesResponse
} from "@/types/api";
import { requestJson } from "@/utils/http";

export const conversationApi = {
  createConversation(agentGroupUid?: string, agentUid?: string) {
    return requestJson<CreateConversationResponse>("/api/conversations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ agentGroupUid, agentUid })
    });
  },
  listConversations() {
    return requestJson<ConversationSummary[]>("/api/conversations");
  },
  deleteConversation(conversationUid: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}`, {
      method: "DELETE"
    });
  },
  updateConversationTitle(conversationUid: string, title: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/title`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title })
    });
  },
  listAgentGroups() {
    return requestJson<AgentCatalogGroup[]>("/api/agent-groups");
  },
  updateAgentBasicInfo(agentUid: string, payload: {
    displayName: string;
    description: string;
    avatar: string;
    avatarColor: string;
    modelProvider: string;
    modelName: string;
    modelNames: string[];
  }) {
    return requestJson<AgentCatalogAgent>(`/api/agents/${agentUid}/basic`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  createAgent(payload: {
    agentName: string;
    displayName: string;
    description: string;
    avatar: string;
    avatarColor: string;
    modelProvider: string;
    modelName: string;
    modelNames: string[];
  }) {
    return requestJson<AgentCatalogAgent>("/api/agents", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  listAgentSkills(agentUid: string) {
    return requestJson<AgentSkill[]>(`/api/agents/${agentUid}/skills`);
  },
  listAgentTools(agentUid: string) {
    return requestJson<AgentTool[]>(`/api/agents/${agentUid}/tools`);
  },
  listAgentTips(agentUid: string) {
    return requestJson<AgentTip[]>(`/api/agents/${agentUid}/tips`);
  },
  createAgentTip(agentUid: string, payload: {
    title?: string;
    summary?: string;
    sourceContent: string;
    sourceConversationUid?: string;
    sourceMessageUid?: string;
    sourceTime?: string;
    generateBestPractice?: boolean;
  }) {
    return requestJson<AgentTip>(`/api/agents/${agentUid}/tips`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  deleteAgentTip(agentUid: string, tipUid: string) {
    return requestJson<SimpleResponse>(`/api/agents/${agentUid}/tips/${tipUid}`, {
      method: "DELETE"
    });
  },
  updateAgentSkillStatus(agentUid: string, skillKey: string, enabled: boolean) {
    return requestJson<AgentSkill>(`/api/agents/${agentUid}/skills/${skillKey}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled })
    });
  },
  importSkillFromUrl(agentUid: string, payload: ImportSkillFromUrlPayload) {
    return requestJson<ImportedSkillResponse>(`/api/agents/${agentUid}/skills/import-url`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  importSkillArchive(agentUid: string, file: File, attachToAgent: boolean) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("attachToAgent", String(attachToAgent));
    return requestJson<ImportedSkillResponse>(`/api/agents/${agentUid}/skills/import-archive`, {
      method: "POST",
      body: formData
    });
  },
  createSkill(agentUid: string, payload: CreateSkillPayload) {
    return requestJson<ImportedSkillResponse>(`/api/agents/${agentUid}/skills/create`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  updateAgentToolStatus(agentUid: string, toolKey: string, enabled: boolean) {
    return requestJson<AgentTool>(`/api/agents/${agentUid}/tools/${toolKey}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled })
    });
  },
  getSystemConfig() {
    return requestJson<SystemConfig>("/api/system/config");
  },
  listMessages(conversationUid: string) {
    return requestJson<ConversationMessage[]>(`/api/conversations/${conversationUid}/messages`);
  },
  listMessageRuns(conversationUid: string) {
    return requestJson<ConversationMessageRun[]>(`/api/conversations/${conversationUid}/message-runs`);
  },
  uploadConversationFiles(conversationUid: string, payload: {
    files: File[];
    modelProvider: string;
    modelName: string;
  }) {
    const formData = new FormData();
    payload.files.forEach((file) => formData.append("files", file));
    formData.append("modelProvider", payload.modelProvider);
    formData.append("modelName", payload.modelName);
    return requestJson<UploadFilesResponse>(`/api/conversations/${conversationUid}/uploads`, {
      method: "POST",
      body: formData
    });
  },
  sendMessage(conversationUid: string, payload: {
    message: string;
    fileUrls: string[];
    modelProvider: string;
    modelName: string;
  }) {
    return requestJson<MessageResponse>(`/api/conversations/${conversationUid}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  approveStep(conversationUid: string, stepUid: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/approvals/${stepUid}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  rejectStep(conversationUid: string, stepUid: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/approvals/${stepUid}/reject`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  },
  cancelConversation(conversationUid: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/cancel`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({})
    });
  }
};
