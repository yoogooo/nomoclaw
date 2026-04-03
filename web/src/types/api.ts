export interface AgentCatalogAgent {
  agentUid: string;
  agentName: string;
  displayName: string;
  avatar: string;
  avatarColor: string;
  description: string;
  modelProvider: string;
  modelName: string;
  modelNames: string[];
  sortIndex: number;
  capabilityTags: string[];
  memberRole: string;
  responsibility: string;
  primary: boolean;
}

export interface AgentSkill {
  skillKey: string;
  displayName: string;
  description: string;
  skillPath: string;
  enabled: boolean;
  updatedTime: string;
}

export interface ImportSkillFromUrlPayload {
  url: string;
  attachToAgent: boolean;
}

export interface CreateSkillPayload {
  skillKey: string;
  displayName: string;
  description: string;
  purpose: string;
  attachToAgent: boolean;
}

export interface ImportedSkillResponse extends AgentSkill {}

export interface AgentTool {
  toolKey: string;
  displayName: string;
  description: string;
  enabled: boolean;
  updatedTime: string;
}

export interface AgentTip {
  tipUid: string;
  agentUid: string;
  title: string;
  summary: string;
  sourceContent: string;
  sourceConversationUid?: string | null;
  sourceMessageUid?: string | null;
  sourceTime?: string | null;
  createdTime: string;
  updatedTime: string;
}

export interface AgentDocFile {
  key: string;
  fileName: string;
  content: string;
  updatedTime: string;
}

export interface SystemConfig {
  nomoclawRootDir: string;
  agentsRootDir: string;
  skillsRootDir: string;
}

export interface ChannelConfig {
  channels: {
    feishu: {
      enabled: boolean;
      requireMention: boolean;
      allowList: string[];
      appId: string;
      appSecret: string;
      processingAckReactionEnabled: boolean;
      processingAckReactionType: string;
    };
    dingtalk: {
      enabled: boolean;
      requireMention: boolean;
      allowList: string[];
      clientId: string;
      clientSecret: string;
      robotCode: string;
    };
  };
}

export interface ModelProviderOption {
  id: string;
  name: string;
  capabilities: string[];
  reasoning: boolean;
  contextWindow: number;
  maxInputTokens: number;
  maxOutputTokens: number;
  uploadPolicy?: UploadPolicy;
}

export interface ModelProvider {
  id: string;
  name: string;
  protocol: string;
  local: boolean;
  requireApiKey: boolean;
  freezeUrl: boolean;
  baseUrl: string;
  apiKey: string;
  defaultModel: string;
  models: ModelProviderOption[];
}

export interface ModelConfig {
  providers: ModelProvider[];
}

export interface UploadPolicy {
  enabled: boolean;
  allowedMimeGroups: string[];
  maxFilesPerMessage: number;
  maxImagesPerMessage: number;
  singleMimeGroupOnly: boolean;
  allowMixedImageAndFile: boolean;
}

export interface AgentCatalogGroup {
  agentGroupUid: string;
  groupName: string;
  displayName: string;
  avatar: string;
  description: string;
  sceneTags: string[];
  collaborationMode: string;
  agents: AgentCatalogAgent[];
}

export interface ConversationSummary {
  conversationUid: string;
  agentGroupUid: string;
  agentUid: string;
  title: string;
  createdTime: string;
  updatedTime: string;
}

export interface MessageFileLink {
  name: string;
  path: string;
}

export interface ConversationMessage {
  messageUid?: string;
  parentMessageUid?: string | null;
  role: string;
  content: string;
  status?: string;
  provider?: string;
  modelName?: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  createdTime: string;
  fileLinks?: MessageFileLink[];
  attachments?: ConversationAttachment[];
}

export interface ConversationAttachment {
  attachmentUid: string;
  name: string;
  contentType: string;
  mimeGroup: string;
  sizeBytes: number;
  fileUrl: string;
  previewable: boolean;
}

export interface ConversationRunStep {
  stepUid: string;
  roundIndex: number;
  stepIndex: number;
  status: string;
  displayTitle: string;
  displaySummary: string;
  displayDetails: string;
  updatedTime: string;
}

export interface ConversationMessageRun {
  messageUid: string;
  status: string;
  summary: string;
  completedSteps: number;
  totalSteps: number;
  updatedTime: string;
  steps: ConversationRunStep[];
}

export interface CronJob {
  jobUid: string;
  agentUid: string;
  agentName: string;
  agentDisplayName: string;
  agentAvatar: string;
  title: string;
  registered: boolean;
  triggerState: string;
  expression: string;
  timezone: string;
  endAt?: string | null;
  taskContent: string;
  status: string;
  lastRunTime: string | null;
  nextRunTime: string | null;
  lastResult: string;
  lastReportPath: string;
  createdTime: string;
  updatedTime: string;
}

export interface CronJobReport {
  jobUid: string;
  reportPath: string;
  content: string;
  updatedTime: string;
}

export interface CronJobExecutionResult {
  executedTime: string;
  status: string;
  summary: string;
  reportPath?: string | null;
  reportContent?: string | null;
}

export interface CronSubscription {
  subscriptionUid: string;
  jobUid: string;
  channel: string;
  target: string;
  enabled: boolean;
  createdTime: string;
  updatedTime: string;
}

export interface SimpleResponse {
  status: string;
}

export interface MessageResponse {
  messageUid: string;
  status: string;
  roundsUsed: number | null;
  maxRounds: number | null;
  stopReason: string | null;
}

export interface BatchDeleteCronJobsFailedItem {
  jobUid: string;
  reason: string;
}

export interface BatchDeleteCronJobsResponse {
  requestedCount: number;
  deletedJobUids: string[];
  failedItems: BatchDeleteCronJobsFailedItem[];
}

export interface CreateConversationResponse {
  conversationUid: string;
}

export interface UploadFilesResponse {
  items: ConversationAttachment[];
}

export interface AgentEvent {
  conversationUid?: string;
  messageUid?: string;
  stepUid?: string;
  eventType: string;
  payload: Record<string, any>;
}

export type EntryType = "agent" | "group";
