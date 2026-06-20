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
  workspace: string;
  reportDir: string;
  tmpDir: string;
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

export interface GlobalSkillLinkedAgent {
  agentUid: string;
  agentName: string;
  displayName: string;
}

export interface GlobalSkill {
  skillKey: string;
  displayName: string;
  description: string;
  skillPath: string;
  status: string;
  updatedTime: string;
  linkedAgents: GlobalSkillLinkedAgent[];
}

export interface GlobalSkillAgentBinding {
  agentUid: string;
  agentName: string;
  displayName: string;
  enabled: boolean;
}

export interface GlobalSkillBindings {
  skillKey: string;
  displayName: string;
  description: string;
  skillPath: string;
  status: string;
  updatedTime: string;
  enabledAgentCount: number;
  agentBindings: GlobalSkillAgentBinding[];
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

export interface AgentMcpTool {
  toolKey: string;
  serverUid: string;
  serverName: string;
  serverDisplayName: string;
  originalToolName: string;
  displayName: string;
  description: string;
  enabled: boolean;
  updatedTime: string;
}

export interface McpServer {
  serverUid: string;
  serverName: string;
  transport: "HTTP" | "STDIO";
  status: string;
  timeoutSeconds: number;
  autoStart: boolean;
  endpoint: string;
  headers: Record<string, string>;
  command: string;
  args: string[];
  env: Record<string, string>;
  cwd: string;
  lastConnectedTime?: string | null;
  lastError?: string | null;
  toolCount: number;
  createdTime: string;
  updatedTime: string;
}

export interface McpTool {
  toolKey: string;
  serverUid: string;
  originalToolName: string;
  displayName: string;
  description: string;
  inputSchemaJson?: string;
  status: string;
  lastSyncedTime: string;
}

export interface SaveMcpServerPayload {
  serverName: string;
  transport: "HTTP" | "STDIO";
  timeoutSeconds: number;
  autoStart: boolean;
  endpoint?: string;
  headers?: Record<string, string>;
  command?: string;
  args?: string[];
  env?: Record<string, string>;
  cwd?: string;
}

export interface SystemErrorLog {
  logUid: string;
  level: "ERROR" | "WARN";
  source: string;
  code: string;
  title: string;
  message: string;
  detail: string;
  occurredTime: string;
}

export interface SystemErrorLogSummary {
  hasErrors: boolean;
  recent24hCount: number;
  latestOccurredTime: string | null;
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
      bots: ChannelFeishuBotConfig[];
    };
    dingtalk: {
      enabled: boolean;
      bots: ChannelDingTalkBotConfig[];
    };
    discord: {
      enabled: boolean;
      bots: ChannelDiscordBotConfig[];
    };
    telegram: {
      enabled: boolean;
      bots: ChannelTelegramBotConfig[];
    };
    qq: {
      enabled: boolean;
      bots: ChannelQqBotConfig[];
    };
    wecom: {
      enabled: boolean;
      bots: ChannelWeComBotConfig[];
    };
    weixin: {
      enabled: boolean;
      bots: ChannelWeixinBotConfig[];
    };
  };
}

export interface ChannelFeishuBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  appId: string;
  appSecret: string;
  processingAckReactionEnabled: boolean;
  processingAckReactionType: string;
  defaultTarget: string;
  defaultTargetDisplayName: string;
  targetResolvedAt: string;
}

export interface ChannelDingTalkBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  clientId: string;
  clientSecret: string;
  robotCode: string;
}

export interface ChannelDiscordBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  token: string;
  botUserId: string;
  acceptBotMessages: boolean;
}

export interface ChannelTelegramBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  token: string;
  botUsername: string;
}

export interface ChannelQqBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  appId: string;
  clientSecret: string;
  botUserId: string;
  sandbox: boolean;
  markdownEnabled: boolean;
}

export interface ChannelWeComBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  wecomBotId: string;
  secret: string;
}

export interface ChannelWeixinBotConfig {
  botId: string;
  displayName: string;
  enabled: boolean;
  isDefault: boolean;
  requireMention: boolean;
  allowList: string[];
  agentUid: string;
  defaultModelProvider: string;
  defaultModelName: string;
  botToken: string;
  botTokenFile: string;
  baseUrl: string;
}

export interface ChannelTargetOption {
  label: string;
  target: string;
  kind: "group" | "user" | "webhook";
  source: "platform" | "history";
}

export interface ChannelTargetSearchResponse {
  items: ChannelTargetOption[];
  error?: string | null;
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
  catalogMatched?: boolean;
  catalogSource?: string;
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
  configured: boolean;
  authStatus: string;
  authMessage: string;
  defaultModel: string;
  models: ModelProviderOption[];
}

export interface ModelConfig {
  providers: ModelProvider[];
}

export interface TestModelProviderRequest {
  providerId: string;
  baseUrl: string;
  apiKey: string;
}

export interface ModelProviderTestResult {
  success: boolean;
  message: string;
}

export interface UploadPolicy {
  enabled: boolean;
  allowedMimeGroups: string[];
  maxFilesPerMessage: number;
  maxImagesPerMessage: number;
  maxFileBytes?: number;
  maxTotalBytes?: number;
  singleMimeGroupOnly: boolean;
  allowMixedImageAndFile: boolean;
}

export interface PermissionRulePayload {
  ruleId: string;
  effect: "ALLOW" | "ASK" | "DENY";
  tool: string;
  action: string;
  resourceType: "FILE" | "COMMAND" | "BROWSER" | "CRON" | "ANY";
  pathPattern: string;
  commandPattern: string;
  expiresAt: string;
  enabled: boolean;
}

export interface PermissionRulesResponse {
  agentUid: string;
  agentName: string;
  sessionRules: PermissionRulePayload[];
  commandRules: PermissionRulePayload[];
  agentSettingsRules: PermissionRulePayload[];
  userSettingsRules: PermissionRulePayload[];
  hardGuardProtectedNames: string[];
  hardGuardSystemRoots: string[];
}

export interface ApprovalDecisionResponse {
  status: string;
  appliedScope: "once" | "session" | "agent" | "user";
  persisted: boolean;
  matchedRuleId?: string;
}

export type ApprovalMode = "default" | "full_access";

export interface ModelCatalogStatus {
  catalogVersion: string;
  generatedAt: string;
  source: string;
  stale: boolean;
  message: string;
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
  pinned: boolean;
  running?: boolean;
  waitingApproval: boolean;
  unread: boolean;
  lastTaskTerminalTime?: string | null;
  lastUserMessageTime: string;
  createdTime: string;
  updatedTime: string;
}

export interface ConversationSummaryPage {
  items: ConversationSummary[];
  hasMore: boolean;
  nextBeforeSortKey?: string | null;
  asOf: string;
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
  cachedInputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  createdTime: string;
  fileLinks?: MessageFileLink[];
  attachments?: ConversationAttachment[];
}

export interface ConversationMessagePage {
  items: ConversationMessage[];
  hasMore: boolean;
  nextBeforeMessageUid?: string | null;
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
  toolName?: string;
  toolArgs?: Record<string, any>;
  displayTitle: string;
  displaySummary: string;
  displayDetails: string;
  policyReasonCode?: string;
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
  modelProvider?: string | null;
  modelName?: string | null;
  taskContent: string;
  status: string;
  currentExecutionUid?: string | null;
  currentConversationUid?: string | null;
  currentMessageUid?: string | null;
  currentExecutionStatus?: string | null;
  currentExecutionStartedTime?: string | null;
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
  executionUid?: string | null;
  unread?: boolean;
  jobUid?: string | null;
  jobTitle?: string | null;
  agentUid?: string | null;
  agentDisplayName?: string | null;
  conversationUid?: string | null;
  messageUid?: string | null;
  executedTime: string;
  status: string;
  summary: string;
  reportPath?: string | null;
  reportContent?: string | null;
}

export interface CronJobExecutionHistoryPage {
  items: CronJobExecutionResult[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface SystemErrorLogPage {
  items: SystemErrorLog[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface CronExecutionDetail {
  executionUid: string;
  jobUid: string;
  jobTitle: string;
  agentUid: string;
  agentDisplayName: string;
  conversationUid: string;
  messageUid: string;
  status: string;
  summary: string;
  reportPath?: string | null;
  reportContent?: string | null;
  executedTime: string;
  runs: ConversationMessageRun[];
}

export interface CronSubscription {
  subscriptionUid: string;
  jobUid: string;
  channel: string;
  target: string;
  botId: string;
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
  id?: string;
  conversationUid?: string;
  messageUid?: string;
  stepUid?: string;
  timestamp?: string;
  eventType: string;
  payload: Record<string, any>;
}

export type EntryType = "agent" | "group";
