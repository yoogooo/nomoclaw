import type {
  AgentCatalogAgent,
  AgentDocFile,
  AgentCatalogGroup,
  AgentSkill,
  GlobalSkill,
  GlobalSkillBindings,
  AgentTip,
  AgentTool,
  AgentMcpTool,
  CreateSkillPayload,
  ConversationMessage,
  ConversationMessageAnchor,
  ConversationMessagePage,
  ConversationMessageRun,
  ConversationSearchPage,
  ConversationSummaryPage,
  ConversationSummary,
  ApprovalDecisionResponse,
  CreateConversationResponse,
  ImportedSkillResponse,
  ImportSkillFromUrlPayload,
  MessageResponse,
  PermissionRulesResponse,
  PermissionRulePayload,
  SimpleResponse,
  SystemConfig,
  UploadFilesResponse
} from "@/types/api";
import { requestJson } from "@/utils/http";
import type { RequestJsonOptions } from "@/utils/http";

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
  listConversationPage(params: {
    agentUid?: string;
    limit?: number;
    beforeSortKey?: string;
    asOf?: string;
  }) {
    const query = new URLSearchParams();
    if (params.agentUid) query.set("agentUid", params.agentUid);
    if (params.limit) query.set("limit", String(params.limit));
    if (params.beforeSortKey) query.set("beforeSortKey", params.beforeSortKey);
    if (params.asOf) query.set("asOf", params.asOf);
    return requestJson<ConversationSummaryPage>(`/api/conversations/page?${query.toString()}`);
  },
  searchConversations(params: {
    agentUid?: string;
    keyword: string;
    limit?: number;
    beforeSortKey?: string;
  }) {
    const query = new URLSearchParams();
    if (params.agentUid) query.set("agentUid", params.agentUid);
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.limit) query.set("limit", String(params.limit));
    if (params.beforeSortKey) query.set("beforeSortKey", params.beforeSortKey);
    return requestJson<ConversationSearchPage>(`/api/conversations/search?${query.toString()}`);
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
  updateConversationPin(conversationUid: string, pinned: boolean) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/pin`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pinned })
    });
  },
  markConversationRead(conversationUid: string) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/read`, {
      method: "PATCH"
    });
  },
  listAgentGroups() {
    return requestJson<AgentCatalogGroup[]>("/api/agent-groups");
  },
  listSkills() {
    return requestJson<GlobalSkill[]>("/api/skills");
  },
  getSkillBindings(skillKey: string) {
    return requestJson<GlobalSkillBindings>(`/api/skills/${skillKey}/bindings`);
  },
  updateSkillBindings(skillKey: string, payload: {
    enabled: boolean;
    agentBindings: Array<{
      agentUid: string;
      enabled: boolean;
    }>;
  }) {
    return requestJson<GlobalSkillBindings>(`/api/skills/${skillKey}/bindings`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  updateSkillStatus(skillKey: string, enabled: boolean) {
    return requestJson<GlobalSkill>(`/api/skills/${skillKey}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled })
    });
  },
  deleteSkill(skillKey: string) {
    return requestJson<SimpleResponse>(`/api/skills/${skillKey}`, {
      method: "DELETE"
    });
  },
  updateAgentBasicInfo(agentUid: string, payload: {
    displayName: string;
    agentType: string;
    description: string;
    avatar: string;
    avatarColor: string;
    modelProvider: string;
    modelName: string;
    modelNames: string[];
    workspace: string;
    codexWorkdir: string;
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
    agentType: string;
    description: string;
    avatar: string;
    avatarColor: string;
    modelProvider: string;
    modelName: string;
    modelNames: string[];
    workspace: string;
    codexWorkdir: string;
  }) {
    return requestJson<AgentCatalogAgent>("/api/agents", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  deleteAgent(agentUid: string) {
    return requestJson<SimpleResponse>(`/api/agents/${agentUid}`, {
      method: "DELETE"
    });
  },
  listAgentSkills(agentUid: string) {
    return requestJson<AgentSkill[]>(`/api/agents/${agentUid}/skills`);
  },
  listAgentTools(agentUid: string) {
    return requestJson<AgentTool[]>(`/api/agents/${agentUid}/tools`);
  },
  listAgentMcpTools(agentUid: string) {
    return requestJson<AgentMcpTool[]>(`/api/agents/${agentUid}/mcp-tools`);
  },
  listAgentDocs(agentUid: string) {
    return requestJson<AgentDocFile[]>(`/api/agents/${agentUid}/docs`);
  },
  updateAgentDoc(agentUid: string, docKey: string, payload: { content: string }) {
    return requestJson<AgentDocFile>(`/api/agents/${agentUid}/docs/${docKey}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
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
  }, options?: RequestJsonOptions) {
    return requestJson<AgentTip>(`/api/agents/${agentUid}/tips`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }, options);
  },
  deleteAgentTip(agentUid: string, tipUid: string) {
    return requestJson<SimpleResponse>(`/api/agents/${agentUid}/tips/${tipUid}`, {
      method: "DELETE"
    });
  },
  updateAgentTip(agentUid: string, tipUid: string, payload: {
    title?: string;
    summary?: string;
    sourceContent?: string;
  }) {
    return requestJson<AgentTip>(`/api/agents/${agentUid}/tips/${tipUid}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
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
  updateAgentMcpToolStatus(agentUid: string, toolKey: string, enabled: boolean) {
    return requestJson<AgentMcpTool>(`/api/agents/${agentUid}/mcp-tools/${toolKey}`, {
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
  listMessagesPage(conversationUid: string, params: {
    limit?: number;
    beforeMessageUid?: string;
  }) {
    const query = new URLSearchParams();
    if (params.limit) query.set("limit", String(params.limit));
    if (params.beforeMessageUid) query.set("beforeMessageUid", params.beforeMessageUid);
    return requestJson<ConversationMessagePage>(`/api/conversations/${conversationUid}/messages/page?${query.toString()}`);
  },
  getMessagesAroundAnchor(conversationUid: string, params: {
    keyword: string;
    beforeLimit?: number;
    afterLimit?: number;
  }) {
    const query = new URLSearchParams();
    query.set("keyword", params.keyword);
    if (params.beforeLimit) query.set("beforeLimit", String(params.beforeLimit));
    if (params.afterLimit) query.set("afterLimit", String(params.afterLimit));
    return requestJson<ConversationMessageAnchor>(`/api/conversations/${conversationUid}/messages/anchor?${query.toString()}`);
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
    approvalMode: "default" | "full_access";
  }) {
    return requestJson<MessageResponse>(`/api/conversations/${conversationUid}/messages`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  updateApprovalMode(conversationUid: string, payload: {
    approvalMode: "default" | "full_access";
    applyToRunning?: boolean;
  }) {
    return requestJson<SimpleResponse>(`/api/conversations/${conversationUid}/approval-mode`, {
      method: "PATCH",
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
  decideStep(conversationUid: string, stepUid: string, payload: {
    action: "allow" | "deny";
    scope: "once" | "session" | "agent" | "user";
    note?: string;
  }) {
    return requestJson<ApprovalDecisionResponse>(`/api/conversations/${conversationUid}/approvals/${stepUid}/decision`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  getEffectivePermissions(conversationUid: string, agentUid: string) {
    const query = new URLSearchParams();
    if (conversationUid) query.set("conversationUid", conversationUid);
    if (agentUid) query.set("agentUid", agentUid);
    return requestJson<PermissionRulesResponse>(`/api/permissions/effective?${query.toString()}`);
  },
  updateAgentPermissions(agentUid: string, rules: PermissionRulePayload[]) {
    return requestJson<PermissionRulesResponse>(`/api/permissions/agent-settings/${agentUid}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rules })
    });
  },
  updateUserPermissions(agentUid: string, rules: PermissionRulePayload[]) {
    const query = new URLSearchParams();
    if (agentUid) query.set("agentUid", agentUid);
    return requestJson<PermissionRulesResponse>(`/api/permissions/user-settings?${query.toString()}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rules })
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
