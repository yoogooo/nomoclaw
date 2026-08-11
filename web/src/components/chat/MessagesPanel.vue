<script setup lang="ts">
import { computed, h, nextTick, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ArrowDown, ArrowUp, Check, ChevronDown, ChevronUp, Copy, Sparkles } from "lucide-vue-next";
import { NButton, NCard, NCollapse, NCollapseItem, NFlex, NTag } from "naive-ui";
import ApprovalBanner from "./ApprovalBanner.vue";
import MessageCopyButton from "./MessageCopyButton.vue";
import ComposerPanel from "./composer/ComposerPanel.vue";
import UiInstantTooltip from "@/components/UiInstantTooltip.vue";
import UiSpinner from "@/components/UiSpinner.vue";
import { message as discreteMessage } from "@/discrete";
import { notification as discreteNotification } from "@/discrete";
import { getSortLocale } from "@/i18n";
import { useConversationStore } from "@/stores/conversation";
import { useConversationRunsStore } from "@/stores/conversationRuns";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useJinnangStore } from "@/stores/jinnang";
import { copyText } from "@/utils/clipboard";
import { formatMessageTime } from "@/utils/format";
import { renderMarkdown } from "@/utils/markdown";
import type { ConversationAttachment, ConversationMessage, ConversationRunStep, MessageFileLink } from "@/types/api";

type RunStepRenderData = {
  cacheKey: string;
  isCommand: boolean;
  command: string;
  output: string;
  details: string;
};

const props = withDefaults(defineProps<{
  hideComposer?: boolean;
  focusMessageUid?: string;
  forceTypingIndicator?: boolean;
  hideTypingRoleLabel?: boolean;
  disableAutoTypingIndicator?: boolean;
}>(), {
  hideComposer: false,
  focusMessageUid: "",
  forceTypingIndicator: false,
  hideTypingRoleLabel: false,
  disableAutoTypingIndicator: false
});

const conversationStore = useConversationStore();
const conversationRunsStore = useConversationRunsStore();
const agentCatalogStore = useAgentCatalogStore();
const jinnangStore = useJinnangStore();
const { t } = useI18n();
const copiedMessageMap = ref<Record<string, boolean>>({});
const savingTipMap = ref<Record<string, boolean>>({});
const savedTipMap = ref<Record<string, boolean>>({});
const expandedUserMessageMap = ref<Record<string, boolean>>({});
const expandedRunOutputMap = ref<Record<string, boolean>>({});
const copiedRunCommandMap = ref<Record<string, boolean>>({});
const messageListRef = ref<HTMLElement | null>(null);
const shouldScrollToBottomOnNextRender = ref(true);
const userMessageCollapseLineLimit = 10;
const runOutputCollapsedLineLimit = 20;
const runStepRenderCache = new Map<string, RunStepRenderData>();
const savedTipMessageUidSet = computed(() => {
  const uidSet = new Set<string>();
  for (const tip of jinnangStore.tips) {
    const uid = (tip.sourceMessageUid || "").trim();
    if (uid) uidSet.add(uid);
  }
  return uidSet;
});

const latestMessageUid = computed(() => {
  const items = conversationStore.messages;
  return (items.length ? items[items.length - 1].messageUid : "") || "";
});
const effectiveFocusMessageUid = computed(() =>
  String(props.focusMessageUid || conversationStore.anchorMessageUid || "").trim()
);
const displayedMessages = computed(() => {
  const allMessages = conversationStore.messages;
  const focusMessageUid = effectiveFocusMessageUid.value;
  if (!focusMessageUid) {
    return allMessages;
  }
  const focusIndex = allMessages.findIndex((item) => item.messageUid === focusMessageUid);
  if (focusIndex < 0) {
    return allMessages;
  }
  const focusMessage = allMessages[focusIndex];
  let start = focusIndex;
  if (focusMessage.role !== "user") {
    const nearestUserIndex = allMessages.slice(0, focusIndex + 1).map((item) => item.role).lastIndexOf("user");
    if (nearestUserIndex >= 0) {
      start = nearestUserIndex;
    }
  }
  return allMessages.slice(start);
});
const isRunningCurrentConversation = computed(() =>
  (() => {
    const conversationUid = conversationStore.currentConversationUid;
    return Boolean(conversationUid && conversationStore.runningConversationUids[conversationUid]);
  })()
);
const hasWaitingApprovalInCurrentConversation = computed(() => {
  if (conversationStore.approval.stepUid) return true;
  return Object.values(conversationRunsStore.runsByMessageUid).some((run) =>
    (run.steps || []).some((step) => step.status === "waiting_approval")
  );
});
const shouldShowTypingIndicator = computed(() => {
  if (props.forceTypingIndicator) return true;
  if (props.disableAutoTypingIndicator) return false;
  return isRunningCurrentConversation.value && !hasWaitingApprovalInCurrentConversation.value;
});
const starterTemplates = computed(() => [
  {
    id: "project-plan",
    title: t("chat.messages.starterTemplate1Title"),
    summary: t("chat.messages.starterTemplate1Summary"),
    prompt: t("chat.messages.starterTemplate1Prompt")
  },
  {
    id: "clarify-then-solve",
    title: t("chat.messages.starterTemplate2Title"),
    summary: t("chat.messages.starterTemplate2Summary"),
    prompt: t("chat.messages.starterTemplate2Prompt")
  },
  {
    id: "overseas-landing",
    title: t("chat.messages.starterTemplate3Title"),
    summary: t("chat.messages.starterTemplate3Summary"),
    prompt: t("chat.messages.starterTemplate3Prompt")
  }
]);

function runTone(status: string) {
  if (status === "completed") return "success";
  if (status === "failed" || status === "rejected" || status === "canceled") return "error";
  if (status === "waiting_approval") return "warning";
  if (status === "running") return "info";
  return "default";
}

function isReasoningStep(step: ConversationRunStep) {
  const stepUid = (step.stepUid || "").toLowerCase();
  return (step.toolName || "") === "Reasoning" || stepUid.startsWith("reasoning-");
}

function runStepTone(step: ConversationRunStep) {
  if (isReasoningStep(step)) return "default";
  return runTone(step.status);
}

function isReasoningRetryStep(step: ConversationRunStep) {
  if (!isReasoningStep(step) || step.status !== "running") {
    return false;
  }
  const summary = String(step.displaySummary || "").trim();
  const details = String(step.displayDetails || "").trim();
  return /网络重试|reconnect|reconnecting|retry/i.test(`${summary}\n${details}`);
}

function reasoningRetryTitle(step: ConversationRunStep) {
  return String(step.displaySummary || step.displayTitle || "").trim() || t("chat.runtime.stepTitle.reasoning");
}

function reasoningRetryDetails(step: ConversationRunStep) {
  return String(step.displayDetails || "").trim();
}

function browserModeBadgeText(step: ConversationRunStep) {
  if ((step.toolName || "") !== "BrowserTool") {
    return "";
  }
  const toolArgs = step.toolArgs || {};
  const selectedMode = String(toolArgs._browserSelectedMode || "").trim().toLowerCase();
  if (!selectedMode) {
    return "";
  }
  return selectedMode === "local_bridge"
    ? t("chat.messages.browserModeBadge.chrome")
    : t("chat.messages.browserModeBadge.agentManaged");
}

function runDisplayStatus(messageUid: string) {
  const run = conversationRunsStore.runsByMessageUid[messageUid];
  if (!run) return "planned";
  if (run.status !== "planned") {
    return run.status;
  }
  const hasWaitingApproval = Boolean(run?.steps?.some((item) => item.status === "waiting_approval"));
  return hasWaitingApproval ? "waiting_approval" : run.status;
}

function runProgressText(messageUid: string) {
  const run = conversationRunsStore.runsByMessageUid[messageUid];
  if (!run) return "";
  return t("chat.messages.runProgressOnly", {
    completedSteps: run.completedSteps ?? 0,
    totalSteps: run.totalSteps ?? 0
  });
}

function visibleRunSteps(messageUid: string) {
  return conversationRunsStore.runsByMessageUid[messageUid]?.steps || [];
}

function runStatusText(status: string) {
  if (status === "completed") return t("chat.messages.runStatus.completed");
  if (status === "failed") return t("chat.messages.runStatus.failed");
  if (status === "rejected") return t("chat.messages.runStatus.rejected");
  if (status === "canceled") return t("chat.messages.runStatus.canceled");
  if (status === "waiting_approval") return t("chat.messages.runStatus.waitingApproval");
  if (status === "running") return t("chat.messages.runStatus.running");
  if (status === "planned") return t("chat.messages.runStatus.planned");
  if (status === "preparing") return t("chat.messages.runStatus.preparing");
  return status;
}

function isMessageCanceled(message: ConversationMessage) {
  const status = (message.status || "").trim().toLowerCase();
  return status === "canceled";
}

function shouldShowCanceledSystemBubble(message: ConversationMessage) {
  if (message.role !== "user" || !message.messageUid) return false;
  const run = conversationRunsStore.runsByMessageUid[message.messageUid];
  const runCanceled = (run?.status || "").trim().toLowerCase() === "canceled";
  return runCanceled || isMessageCanceled(message);
}

function canceledSystemBubbleTime(message: ConversationMessage) {
  if (!message.messageUid) return "";
  const run = conversationRunsStore.runsByMessageUid[message.messageUid];
  return run?.updatedTime || message.createdTime;
}

function runStepStateKey(messageUid: string | undefined, stepUid: string) {
  return `${messageUid || "unknown"}:${stepUid}`;
}

function formatCurrentTimeToolDetails(step: ConversationRunStep, details: string) {
  if ((step.toolName || "") !== "CurrentTimeTool") {
    return details;
  }
  const match = details.match(/^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}) UTC \(([^)]+)\)$/);
  if (!match) {
    return details;
  }
  const [, datePart, timePart] = match;
  const utcDate = new Date(`${datePart}T${timePart}Z`);
  if (Number.isNaN(utcDate.getTime())) {
    return details;
  }
  const locale = getSortLocale();
  const localDate = utcDate.toLocaleDateString("sv-SE");
  const localTime = utcDate.toLocaleTimeString(locale, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  });
  const localWeekday = utcDate.toLocaleDateString(locale, { weekday: "long" });
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "Local";
  return `${localDate} ${localTime} ${timezone} (${localWeekday})`;
}

function getRunStepRenderData(step: ConversationRunStep): RunStepRenderData {
  const locale = getSortLocale();
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "Local";
  const details = formatCurrentTimeToolDetails(step, (step.displayDetails || step.displaySummary || "").trim());
  const commandArg = String((step.toolArgs || {}).command || "").trim();
  const cacheKey = `${step.stepUid}|${step.displayTitle}|${step.displayDetails}|${step.displaySummary}|${commandArg}|${locale}|${timezone}`;
  const cached = runStepRenderCache.get(step.stepUid);
  if (cached && cached.cacheKey === cacheKey) {
    return cached;
  }

  if (!details) {
    const emptyData: RunStepRenderData = {
      cacheKey,
      isCommand: false,
      command: "",
      output: "",
      details: t("chat.messages.noExtraDetails")
    };
    runStepRenderCache.set(step.stepUid, emptyData);
    return emptyData;
  }

  if (!isCommandToolStep(step)) {
    const plainData: RunStepRenderData = {
      cacheKey,
      isCommand: false,
      command: "",
      output: "",
      details
    };
    runStepRenderCache.set(step.stepUid, plainData);
    return plainData;
  }

  const command = extractCommand(details, step.displayTitle || "", step.toolArgs);
  let output = extractCommandOutput(details, command);
  if (!output) {
    output = details;
  }
  const commandData: RunStepRenderData = {
    cacheKey,
    isCommand: true,
    command,
    output,
    details
  };
  runStepRenderCache.set(step.stepUid, commandData);
  return commandData;
}

function runStepDetailsMarkdown(step: ConversationRunStep) {
  return getRunStepRenderData(step).details || t("chat.messages.noExtraDetails");
}

function runStepTitle(step: ConversationRunStep) {
  if (getRunStepRenderData(step).isCommand) {
    return t("chat.runtime.stepTitle.commandRun");
  }
  return step.displayTitle || t("chat.runtime.processingStep");
}

function isCommandToolStep(step: ConversationRunStep) {
  return (step.toolName || "").trim() === "CommandTool";
}

function extractCommand(details: string, title: string, toolArgs?: Record<string, any>) {
  const fromArgs = String((toolArgs || {}).command || "").trim();
  if (fromArgs) {
    return fromArgs;
  }
  const text = `${details}\n${title}`;
  const quoted = text.match(/(?:执行命令[:：]\s*|本地命令已执行完成[:：]\s*|命令执行失败[:：]\s*)[“"]([\s\S]*?)[”"]/);
  if (quoted?.[1]) {
    return quoted[1].trim();
  }

  const fromDetail = details.match(/执行命令[:：]\s*([^\n]+?)(?:，工作目录|。|$)/);
  if (fromDetail?.[1]) {
    return fromDetail[1].trim();
  }

  const fromTitle = title.match(/(?:正在执行命令|执行命令)[:：]\s*(.+)$/);
  if (fromTitle?.[1]) {
    return fromTitle[1].trim();
  }

  return "";
}

function extractCommandOutput(details: string, command: string) {
  const normalized = details.replace(/\r\n/g, "\n").trim();
  const markers = ["\n结果:\n", "\n输出如下：\n", "\n输出如下:\n", "结果:\n", "输出如下：\n", "输出如下:\n"];
  for (const marker of markers) {
    const index = normalized.indexOf(marker);
    if (index >= 0) {
      return normalized.slice(index + marker.length).trim();
    }
  }

  const errorIndex = normalized.indexOf("错误：");
  if (errorIndex >= 0) {
    return normalized.slice(errorIndex).trim();
  }

  let fallback = normalized;
  if (command) {
    fallback = fallback.replace(command, "");
  }
  fallback = fallback
    .replace(/本地命令已执行完成[:：]\s*[“"][\s\S]*?[”"]。?\s*/g, "")
    .replace(/命令执行失败[:：]\s*[“"][\s\S]*?[”"]。?\s*/g, "")
    .replace(/执行命令[:：]\s*[^\n]+(?:\n|$)/, "")
    .replace(/^结果[:：]\s*/g, "")
    .trim();
  return fallback;
}

function outputLineCount(output: string) {
  const normalized = (output || "").replace(/\r\n/g, "\n").trim();
  if (!normalized) return 0;
  return normalized.split("\n").length;
}

function shouldShowRunOutputExpand(output: string) {
  return outputLineCount(output) > runOutputCollapsedLineLimit;
}

function isRunOutputExpanded(messageUid: string | undefined, stepUid: string) {
  return Boolean(expandedRunOutputMap.value[runStepStateKey(messageUid, stepUid)]);
}

function toggleRunOutputExpanded(messageUid: string | undefined, stepUid: string) {
  const key = runStepStateKey(messageUid, stepUid);
  expandedRunOutputMap.value = {
    ...expandedRunOutputMap.value,
    [key]: !expandedRunOutputMap.value[key]
  };
}

function visibleRunOutput(messageUid: string | undefined, step: ConversationRunStep) {
  const output = getRunStepRenderData(step).output || "";
  if (!shouldShowRunOutputExpand(output) || isRunOutputExpanded(messageUid, step.stepUid)) {
    return output;
  }
  const lines = output.replace(/\r\n/g, "\n").split("\n");
  return `${lines.slice(0, runOutputCollapsedLineLimit).join("\n")}\n...`;
}

function isRunCommandCopied(messageUid: string | undefined, stepUid: string) {
  return Boolean(copiedRunCommandMap.value[runStepStateKey(messageUid, stepUid)]);
}

async function copyRunCommand(messageUid: string | undefined, step: ConversationRunStep) {
  const command = getRunStepRenderData(step).command.trim();
  if (!command) return;
  const key = runStepStateKey(messageUid, step.stepUid);
  try {
    await copyText(command);
    copiedRunCommandMap.value = {
      ...copiedRunCommandMap.value,
      [key]: true
    };
    window.setTimeout(() => {
      copiedRunCommandMap.value = {
        ...copiedRunCommandMap.value,
        [key]: false
      };
    }, 1200);
  } catch {
    discreteMessage.error(t("chat.messages.copyFailed"));
  }
}

function messageActionKey(messageItem: ConversationMessage) {
  return messageItem.messageUid || `${messageItem.createdTime}-${messageItem.content}`;
}

function humanizeTipSaveError(message: string) {
  const normalized = (message || "").trim();
  if (!normalized) {
    return t("chat.messages.saveTipFailed");
  }
  if (/单次资讯报告|单次.*结果展示|缺失具体执行步骤|缺少可复用|无法复用|信息不足/.test(normalized)) {
    return t("chat.messages.saveTipRejectedNoReusableFlow");
  }
  if (/source message must be assistant role|source message not found|conversation not found/.test(normalized)) {
    return t("chat.messages.saveTipRejectedUnavailable");
  }
  return normalized;
}

function tipSaveNotificationAvatar(type: "success" | "warning") {
  return () => h(
    "span",
    {
      class: ["tip-save-notification-avatar", `tip-save-notification-avatar--${type}`]
    },
    [
      h(type === "success" ? Check : Sparkles, {
        size: 14,
        strokeWidth: 2.1
      })
    ]
  );
}

function showTipSaveNotification(type: "success" | "warning", title: string, content: string) {
  discreteNotification[type]({
    title,
    description: content,
    avatar: tipSaveNotificationAvatar(type),
    duration: 0,
    keepAliveOnHover: true,
    closable: true
  });
}

function runHasWaitingApprovalStep(messageItem: ConversationMessage) {
  if (!messageItem.messageUid) return false;
  const run = conversationRunsStore.runsByMessageUid[messageItem.messageUid];
  if (!run) return false;
  return run.steps.some((step) => step.status === "waiting_approval");
}

function runHasActiveApproval(messageItem: ConversationMessage) {
  if (!messageItem.messageUid) return false;
  const run = conversationRunsStore.runsByMessageUid[messageItem.messageUid];
  if (!run) return false;
  const waitingSteps = run.steps.filter((step) => step.status === "waiting_approval");
  if (!waitingSteps.length) return false;
  if (!conversationStore.approval.stepUid) return true;
  return run.steps.some(
    (step) => step.stepUid === conversationStore.approval.stepUid && step.status === "waiting_approval"
  );
}

async function copyMessage(messageItem: ConversationMessage) {
  const content = messageItem.content || "";
  if (!content.trim()) return;
  try {
    await copyText(content);
    const key = messageActionKey(messageItem);
    copiedMessageMap.value = {
      ...copiedMessageMap.value,
      [key]: true
    };
    window.setTimeout(() => {
      copiedMessageMap.value = {
        ...copiedMessageMap.value,
        [key]: false
      };
    }, 1200);
  } catch {
    discreteMessage.error(t("chat.messages.copyFailed"));
  }
}

async function saveJinnang(messageItem: ConversationMessage) {
  const key = messageActionKey(messageItem);
  if (savingTipMap.value[key] || isTipSaved(messageItem)) return;
  const conversationAgentUid = conversationStore.conversations.find((item) => item.conversationUid === conversationStore.currentConversationUid)?.agentUid || "";
  const targetAgentUid = (conversationAgentUid || agentCatalogStore.selectedAgentUid || "").trim();
  if (!targetAgentUid) {
    showTipSaveNotification("warning", t("chat.messages.saveTipFailureTitle"), t("chat.messages.saveTipNoAgent"));
    return;
  }
  savingTipMap.value = {
    ...savingTipMap.value,
    [key]: true
  };
  let saved = false;
  try {
    await jinnangStore.addTip({
      agentUid: targetAgentUid,
      messageUid: messageItem.messageUid,
      content: messageItem.content,
      sourceTime: messageItem.createdTime,
      conversationUid: conversationStore.currentConversationUid || "",
      generateBestPractice: true,
      suppressErrorToast: true
    });
    saved = true;
  } catch (error) {
    saved = false;
    const message = error instanceof Error ? humanizeTipSaveError(error.message) : t("chat.messages.saveTipFailed");
    showTipSaveNotification("warning", t("chat.messages.saveTipFailureTitle"), message);
  } finally {
    savingTipMap.value = {
      ...savingTipMap.value,
      [key]: false
    };
  }
  if (!saved) {
    return;
  }
  savedTipMap.value = {
    ...savedTipMap.value,
    [key]: true
  };
  showTipSaveNotification("success", t("chat.messages.saveTipSuccessTitle"), t("toast.tipSavedToDb"));
}

function formatAttachmentSize(sizeBytes = 0) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (sizeBytes >= 1024) {
    return `${Math.round(sizeBytes / 1024)} KB`;
  }
  return `${sizeBytes} B`;
}

function isImageAttachment(contentType?: string, mimeGroup?: string) {
  return mimeGroup === "image" || Boolean(contentType?.startsWith("image/"));
}

function basenameFromPathLike(value: string) {
  const normalized = (value || "").trim().replace(/\\/g, "/");
  if (!normalized) return "";
  const segments = normalized.split("/");
  return decodeURIComponent(segments[segments.length - 1] || "").trim();
}

function attachmentMatchKeys(attachment: ConversationAttachment) {
  const keys = new Set<string>();
  const name = (attachment.name || "").trim();
  const fileName = basenameFromPathLike(attachment.fileUrl || "");
  if (name) keys.add(name.toLowerCase());
  if (fileName) keys.add(fileName.toLowerCase());
  return keys;
}

function extractReferencedFileNames(text: string) {
  const matches = new Set<string>();
  const content = (text || "").trim();
  if (!content) return matches;
  const pathPattern = /(?:\/|[A-Za-z]:\\)[^\s"'”]+?\.(?:png|jpe?g|gif|webp|bmp|svg)/gi;
  for (const match of content.matchAll(pathPattern)) {
    const fileName = basenameFromPathLike(match[0] || "");
    if (fileName) matches.add(fileName.toLowerCase());
  }
  return matches;
}

function isImageFileLink(fileLink: MessageFileLink) {
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test((fileLink.path || fileLink.name || "").trim());
}

function fileLinkMatchKeys(fileLink: MessageFileLink) {
  const keys = new Set<string>();
  const name = (fileLink.name || "").trim();
  const fileName = basenameFromPathLike(fileLink.path || "");
  if (name) keys.add(name.toLowerCase());
  if (fileName) keys.add(fileName.toLowerCase());
  return keys;
}

function previewFileUrl(path: string) {
  return `/api/files/content?path=${encodeURIComponent(path)}`;
}

function messageDisplayAttachments(messageItem: ConversationMessage) {
  const attachments = messageItem.attachments || [];
  if (!attachments.length || !messageItem.parentMessageUid) {
    return attachments;
  }
  const run = conversationRunsStore.runsByMessageUid[messageItem.parentMessageUid];
  if (!run?.steps?.length) {
    return attachments;
  }
  const consumedNames = new Set<string>();
  for (const step of run.steps) {
    const stepNames = extractReferencedFileNames(`${step.displaySummary || ""}\n${step.displayDetails || ""}`);
    for (const fileName of stepNames) {
      consumedNames.add(fileName);
    }
  }
  if (!consumedNames.size) {
    return attachments;
  }
  return attachments.filter((attachment) => {
    if (!isImageAttachment(attachment.contentType, attachment.mimeGroup)) {
      return true;
    }
    const keys = attachmentMatchKeys(attachment);
    for (const key of keys) {
      if (consumedNames.has(key)) {
        return false;
      }
    }
    return true;
  });
}

function messageDisplayFileLinks(messageItem: ConversationMessage) {
  const fileLinks = messageItem.fileLinks || [];
  if (!fileLinks.length || !messageItem.parentMessageUid) {
    return fileLinks;
  }
  const run = conversationRunsStore.runsByMessageUid[messageItem.parentMessageUid];
  if (!run?.steps?.length) {
    return fileLinks;
  }
  const consumedNames = new Set<string>();
  for (const step of run.steps) {
    const stepNames = extractReferencedFileNames(`${step.displaySummary || ""}\n${step.displayDetails || ""}`);
    for (const fileName of stepNames) {
      consumedNames.add(fileName);
    }
  }
  if (!consumedNames.size) {
    return fileLinks;
  }
  return fileLinks.filter((fileLink) => {
    if (!isImageFileLink(fileLink)) {
      return true;
    }
    const keys = fileLinkMatchKeys(fileLink);
    for (const key of keys) {
      if (consumedNames.has(key)) {
        return false;
      }
    }
    return true;
  });
}

function inlineStepAttachments(messageUid: string | undefined, step: ConversationRunStep) {
  const userMessageUid = (messageUid || "").trim();
  if (!userMessageUid) {
    return [];
  }
  const referencedNames = extractReferencedFileNames(`${step.displaySummary || ""}\n${step.displayDetails || ""}`);
  if (!referencedNames.size) {
    return [];
  }
  const relatedMessages = conversationStore.messages.filter((messageItem) =>
    messageItem.parentMessageUid === userMessageUid && messageItem.role !== "user"
  );
  const matched = new Map<string, ConversationAttachment>();
  for (const messageItem of relatedMessages) {
    for (const attachment of messageItem.attachments || []) {
      if (!isImageAttachment(attachment.contentType, attachment.mimeGroup)) {
        continue;
      }
      const keys = attachmentMatchKeys(attachment);
      for (const key of keys) {
        if (referencedNames.has(key)) {
          matched.set(attachment.attachmentUid || attachment.fileUrl, attachment);
          break;
        }
      }
    }
  }
  return [...matched.values()];
}

function inlineStepFileLinks(messageUid: string | undefined, step: ConversationRunStep) {
  const userMessageUid = (messageUid || "").trim();
  if (!userMessageUid) {
    return [];
  }
  const referencedNames = extractReferencedFileNames(`${step.displaySummary || ""}\n${step.displayDetails || ""}`);
  if (!referencedNames.size) {
    return [];
  }
  const relatedMessages = conversationStore.messages.filter((messageItem) =>
    messageItem.parentMessageUid === userMessageUid && messageItem.role !== "user"
  );
  const matched = new Map<string, MessageFileLink>();
  for (const messageItem of relatedMessages) {
    for (const fileLink of messageItem.fileLinks || []) {
      if (!isImageFileLink(fileLink)) {
        continue;
      }
      const keys = fileLinkMatchKeys(fileLink);
      for (const key of keys) {
        if (referencedNames.has(key)) {
          matched.set(fileLink.path, fileLink);
          break;
        }
      }
    }
  }
  return [...matched.values()];
}

function openAttachment(fileUrl: string) {
  window.open(fileUrl, "_blank", "noopener,noreferrer");
}

function resolveMessageTokenUsage(messageItem: ConversationMessage) {
  const input = Number(messageItem.inputTokens || 0);
  const cachedInput = Number(messageItem.cachedInputTokens || 0);
  const output = Number(messageItem.outputTokens || 0);
  const total = Number(messageItem.totalTokens || 0);
  if (input > 0 || cachedInput > 0 || output > 0 || total > 0) {
    return { input, cachedInput, output, total };
  }
  if (messageItem.role === "assistant" && messageItem.parentMessageUid) {
    const parent = conversationStore.messages.find((item) => item.messageUid === messageItem.parentMessageUid);
    if (!parent) return null;
    const parentInput = Number(parent.inputTokens || 0);
    const parentCachedInput = Number(parent.cachedInputTokens || 0);
    const parentOutput = Number(parent.outputTokens || 0);
    const parentTotal = Number(parent.totalTokens || 0);
    if (parentInput > 0 || parentCachedInput > 0 || parentOutput > 0 || parentTotal > 0) {
      return {
        input: parentInput,
        cachedInput: parentCachedInput,
        output: parentOutput,
        total: parentTotal
      };
    }
  }
  return null;
}

function messageTokenUsageText(messageItem: ConversationMessage) {
  const usage = resolveMessageTokenUsage(messageItem);
  if (!usage) return "";
  return `Total ${usage.total}`;
}

function isTipSaved(messageItem: ConversationMessage) {
  if (savedTipMap.value[messageActionKey(messageItem)]) {
    return true;
  }
  const messageUid = (messageItem.messageUid || "").trim();
  return !!messageUid && savedTipMessageUidSet.value.has(messageUid);
}

function userMessageLineCount(messageItem: ConversationMessage) {
  const content = (messageItem.content || "").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  if (!content.trim()) return 0;
  return content.split("\n").length;
}

function shouldShowUserMessageToggle(messageItem: ConversationMessage) {
  return messageItem.role === "user" && userMessageLineCount(messageItem) > userMessageCollapseLineLimit;
}

function isUserMessageExpanded(messageItem: ConversationMessage) {
  return Boolean(expandedUserMessageMap.value[messageActionKey(messageItem)]);
}

function toggleUserMessageExpanded(messageItem: ConversationMessage) {
  const key = messageActionKey(messageItem);
  expandedUserMessageMap.value = {
    ...expandedUserMessageMap.value,
    [key]: !expandedUserMessageMap.value[key]
  };
}

function applyStarterPrompt(prompt: string) {
  conversationStore.draftMessage = prompt;
  window.requestAnimationFrame(() => {
    const input = document.getElementById("messageInput") as HTMLTextAreaElement | null;
    if (!input) return;
    input.focus();
    const length = input.value.length;
    input.setSelectionRange(length, length);
  });
}

async function scrollToConversationBottom() {
  await nextTick();
  // Rich content such as tables and images can expand after the first paint,
  // so keep nudging the scroll position for a few frames until layout settles.
  const attemptScroll = (remainingFrames: number) => {
    window.requestAnimationFrame(() => {
      const element = messageListRef.value;
      if (!element) return;
      element.scrollTop = element.scrollHeight;
      if (remainingFrames > 0) {
        attemptScroll(remainingFrames - 1);
      }
    });
  };
  attemptScroll(6);
}

async function loadOlderMessagesAndPreserveViewport() {
  const element = messageListRef.value;
  if (!element || !conversationStore.messageHistoryHasMore || conversationStore.messageHistoryLoading) {
    return;
  }
  const previousScrollHeight = element.scrollHeight;
  const previousScrollTop = element.scrollTop;
  shouldScrollToBottomOnNextRender.value = false;
  await conversationStore.loadOlderMessages();
  await nextTick();
  const nextElement = messageListRef.value;
  if (!nextElement) {
    return;
  }
  const heightDelta = nextElement.scrollHeight - previousScrollHeight;
  nextElement.scrollTop = previousScrollTop + Math.max(0, heightDelta);
}

function scrollToFocusedMessage() {
  const focusedMessageUid = effectiveFocusMessageUid.value;
  if (!focusedMessageUid) {
    return;
  }
  window.requestAnimationFrame(() => {
    const element = messageListRef.value?.querySelector(`[data-message-uid="${focusedMessageUid}"]`) as HTMLElement | null;
    element?.scrollIntoView({ block: "center", inline: "nearest", behavior: "smooth" });
  });
}

function handleMessageListScroll(event: Event) {
  const element = event.target as HTMLElement | null;
  if (!element) {
    return;
  }
  if (element.scrollTop <= 80) {
    void loadOlderMessagesAndPreserveViewport();
  }
}

// Entering a conversation should always jump to the latest message immediately.
watch(
  () => conversationStore.currentConversationUid,
  () => {
    shouldScrollToBottomOnNextRender.value = true;
    void scrollToConversationBottom();
  }
);

// Message arrays are replaced after history loads, even when the message count
// stays the same, so watch the list reference instead of only its length.
watch(
  () => conversationStore.messages,
  () => {
    if (effectiveFocusMessageUid.value) {
      shouldScrollToBottomOnNextRender.value = false;
      scrollToFocusedMessage();
      return;
    }
    if (!shouldScrollToBottomOnNextRender.value) return;
    void scrollToConversationBottom();
    shouldScrollToBottomOnNextRender.value = false;
  },
  { deep: false }
);

watch(
  () => effectiveFocusMessageUid.value,
  () => {
    if (!effectiveFocusMessageUid.value) {
      return;
    }
    shouldScrollToBottomOnNextRender.value = false;
    scrollToFocusedMessage();
  }
);

onMounted(() => {
  void scrollToConversationBottom();
  shouldScrollToBottomOnNextRender.value = false;
});
</script>

<template>
  <section class="panel message-panel">
    <div
      ref="messageListRef"
      class="panel-body message-list scroll-area"
      @scroll.passive="handleMessageListScroll"
    >
      <div
        v-if="!displayedMessages.length && !isRunningCurrentConversation"
        class="conversation-empty-state"
      >
        <div class="conversation-empty-hero">
          <div class="conversation-empty-title">{{ t("chat.messages.newConversationTitle") }}</div>
          <div class="conversation-empty-hint">{{ t("chat.messages.newConversationHint") }}</div>
          <div class="conversation-starter-prompts">
            <button
              v-for="template in starterTemplates"
              :key="template.id"
              type="button"
              class="starter-prompt-btn"
              @click="applyStarterPrompt(template.prompt)"
            >
              <span class="starter-prompt-title">{{ template.title }}</span>
              <span class="starter-prompt-summary">{{ template.summary }}</span>
            </button>
          </div>
        </div>
      </div>
      <template v-else>
        <div
          v-if="conversationStore.messageHistoryHasMore || conversationStore.messageHistoryLoading"
          class="message-history-status"
        >
          {{ conversationStore.messageHistoryLoading ? t("chat.messages.loadingHistory") : t("chat.messages.scrollForHistory") }}
        </div>
        <div
          v-for="message in displayedMessages"
          :key="`${message.createdTime}-${message.messageUid || message.content}`"
          class="message-wrap"
          :class="{ user: message.role === 'user', 'message-wrap-focused': effectiveFocusMessageUid === (message.messageUid || '') }"
          :data-message-uid="message.messageUid || ''"
        >
          <div
            class="message-bubble"
            :class="{
              user: message.role === 'user',
              'has-expand-toggle': shouldShowUserMessageToggle(message),
              'is-user-collapsed': message.role === 'user' && shouldShowUserMessageToggle(message) && !isUserMessageExpanded(message)
            }"
          >
            <div v-if="messageDisplayAttachments(message).length" class="message-attachments">
              <button
                v-for="attachment in messageDisplayAttachments(message)"
                :key="attachment.fileUrl"
                type="button"
                class="message-attachment-card"
                @click="openAttachment(attachment.fileUrl)"
              >
                <img
                  v-if="isImageAttachment(attachment.contentType, attachment.mimeGroup)"
                  :src="attachment.fileUrl"
                  :alt="attachment.name"
                  class="message-attachment-image"
                />
                <div v-else class="message-attachment-file">FILE</div>
                <div class="message-attachment-meta">
                  <div class="message-attachment-name">{{ attachment.name }}</div>
                  <div class="message-attachment-subtitle">
                    {{ attachment.mimeGroup }} · {{ formatAttachmentSize(attachment.sizeBytes) }}
                  </div>
                </div>
              </button>
            </div>
            <div
              class="message-html"
              :class="{
                'is-collapsed': shouldShowUserMessageToggle(message) && !isUserMessageExpanded(message),
                'is-user-collapsed': message.role === 'user' && shouldShowUserMessageToggle(message) && !isUserMessageExpanded(message)
              }"
              v-html="renderMarkdown(message.content)"
            />
            <button
              v-if="shouldShowUserMessageToggle(message)"
              class="message-expand-btn"
              type="button"
              :aria-expanded="isUserMessageExpanded(message)"
              @click="toggleUserMessageExpanded(message)"
            >
              <ChevronUp v-if="isUserMessageExpanded(message)" :size="14" />
              <ChevronDown v-else :size="14" />
              <span>{{ isUserMessageExpanded(message) ? t("chat.messages.collapseMessage") : t("chat.messages.expandMessage") }}</span>
            </button>
            <n-flex v-if="messageDisplayFileLinks(message).length" wrap :size="8" class="message-file-links">
              <n-button
                v-for="fileLink in messageDisplayFileLinks(message)"
                :key="fileLink.path"
                size="small"
                secondary
                @click="conversationStore.openFile(fileLink.path)"
              >
                {{ t("chat.messages.openFile", { name: fileLink.name }) }}
              </n-button>
            </n-flex>
            <details v-if="message.knowledgeCitations?.length" class="knowledge-citations">
              <summary>{{ t("chat.messages.knowledgeCitations", { count: message.knowledgeCitations.length }) }}</summary>
              <div v-for="citation in message.knowledgeCitations" :key="citation.citationId" class="knowledge-citation">
                <strong>[{{ citation.citationId }}] {{ citation.documentName }}</strong>
                <span v-if="citation.pageFrom">{{ t("chat.messages.knowledgePage", { page: citation.pageFrom }) }}</span>
                <p>{{ citation.excerpt }}</p>
              </div>
            </details>
          </div>
          <div v-if="message.role !== 'user'" class="message-meta">
            <div class="message-time">{{ formatMessageTime(message.createdTime) }}</div>
            <div v-if="resolveMessageTokenUsage(message)" class="message-token-inline">
              <span class="message-token-item">
                <UiInstantTooltip :content="t('chat.messages.outputTokens')">
                  <ArrowDown :size="12" />
                  <span>{{ resolveMessageTokenUsage(message)?.output ?? 0 }}</span>
                </UiInstantTooltip>
              </span>
              <span class="message-token-item">
                <UiInstantTooltip :content="t('chat.messages.inputTokens')">
                  <ArrowUp :size="12" />
                  <span>{{ resolveMessageTokenUsage(message)?.input ?? 0 }}</span>
                </UiInstantTooltip>
              </span>
              <span
                v-if="(resolveMessageTokenUsage(message)?.cachedInput ?? 0) > 0"
                class="message-token-item"
              >
                <span>Cache</span>
                <span>{{ resolveMessageTokenUsage(message)?.cachedInput ?? 0 }}</span>
              </span>
              <span class="message-token-total">{{ messageTokenUsageText(message) }}</span>
            </div>
            <div class="message-actions">
              <MessageCopyButton
                :copied="copiedMessageMap[messageActionKey(message)]"
                :tooltip="t('chat.messages.copy')"
                :aria-label="t('chat.messages.copy')"
                @click="copyMessage(message)"
              />
              <div class="save-tip-wrap">
                <UiInstantTooltip :content="isTipSaved(message) ? t('chat.messages.tipSaved') : t('chat.messages.saveTip')">
                  <button
                    class="message-action-btn icon-only"
                    :class="{
                      loading: savingTipMap[messageActionKey(message)],
                      saved: isTipSaved(message)
                    }"
                    :disabled="savingTipMap[messageActionKey(message)] || isTipSaved(message)"
                    type="button"
                    :aria-label="isTipSaved(message) ? t('chat.messages.tipSaved') : t('chat.messages.saveTip')"
                    @click="saveJinnang(message)"
                  >
                    <UiSpinner
                      v-if="savingTipMap[messageActionKey(message)]"
                      :size="14"
                      thickness="1.8px"
                    />
                    <Check v-if="isTipSaved(message) && !savingTipMap[messageActionKey(message)]" :size="14" />
                    <Sparkles v-else-if="!savingTipMap[messageActionKey(message)]" :size="14" />
                  </button>
                </UiInstantTooltip>
              </div>
            </div>
          </div>
          <div v-else class="user-message-meta">
            <MessageCopyButton
              :copied="copiedMessageMap[messageActionKey(message)]"
              :tooltip="t('chat.messages.copy')"
              :aria-label="t('chat.messages.copy')"
              tone="subtle"
              @click="copyMessage(message)"
            />
            <div class="message-time user-message-time">{{ formatMessageTime(message.createdTime) }}</div>
          </div>

          <n-card
            v-if="message.role === 'user' && message.messageUid && conversationRunsStore.runsByMessageUid[message.messageUid]"
            size="small"
            class="run-card"
            embedded
          >
            <n-collapse>
              <n-collapse-item :name="`run-${message.messageUid}`">
                <template #header>
                  <div class="run-header">
                    <div class="run-title">{{ t("chat.messages.runTitle") }}</div>
                  </div>
                </template>
                <div class="run-summary">{{ runProgressText(message.messageUid || "") }}</div>
                <n-collapse class="run-steps-collapse">
                  <n-collapse-item
                    v-for="(step, stepPosition) in visibleRunSteps(message.messageUid)"
                    :key="step.stepUid"
                    :name="step.stepUid"
                  >
                    <template #header>
                      <div
                        v-if="isReasoningRetryStep(step)"
                        class="run-retry-notice"
                      >
                        <div class="run-retry-notice-title">{{ reasoningRetryTitle(step) }}</div>
                        <div v-if="reasoningRetryDetails(step)" class="run-retry-notice-details">
                          {{ reasoningRetryDetails(step) }}
                        </div>
                      </div>
                      <div v-else class="run-step-header-title">
                        {{ `${stepPosition + 1}. ${runStepTitle(step)}` }}
                      </div>
                    </template>
                    <template #header-extra>
                      <div v-if="!isReasoningStep(step) && !isReasoningRetryStep(step)" class="run-step-header-tags">
                        <n-tag size="small" :type="runStepTone(step)">{{ runStatusText(step.status) }}</n-tag>
                      </div>
                    </template>
                    <template v-if="isReasoningRetryStep(step)">
                      <div class="run-retry-notice-body">
                        <span class="run-retry-notice-body-label">{{ t("chat.runtime.stepTitle.reasoning") }}</span>
                      </div>
                    </template>
                    <template v-else-if="getRunStepRenderData(step).isCommand">
                      <div class="run-command-blocks">
                        <div v-if="browserModeBadgeText(step)" class="run-step-browser-mode">
                          <n-tag size="small" :bordered="false" class="run-browser-mode-badge">
                            {{ browserModeBadgeText(step) }}
                          </n-tag>
                        </div>
                        <section v-if="getRunStepRenderData(step).command" class="run-command-section">
                          <div class="run-code-head">
                            <span class="run-code-label">{{ t("chat.messages.commandBlockLabel") }}</span>
                            <UiInstantTooltip :content="t('chat.messages.copyCommand')">
                              <button
                                class="run-code-icon-btn"
                                :class="{ copied: isRunCommandCopied(message.messageUid, step.stepUid) }"
                                type="button"
                                :aria-label="t('chat.messages.copyCommand')"
                                @click="copyRunCommand(message.messageUid, step)"
                              >
                                <Check v-if="isRunCommandCopied(message.messageUid, step.stepUid)" :size="13" />
                                <Copy v-else :size="13" />
                              </button>
                            </UiInstantTooltip>
                          </div>
                          <pre class="run-code-pre"><code>{{ getRunStepRenderData(step).command }}</code></pre>
                        </section>

                        <section class="run-command-section">
                          <div class="run-code-head">
                            <span class="run-code-label">{{ t("chat.messages.outputBlockLabel") }}</span>
                          </div>
                          <div class="run-output-shell">
                            <pre
                              class="run-code-pre"
                              :class="{ 'run-code-pre-output-collapsed': shouldShowRunOutputExpand(getRunStepRenderData(step).output) }"
                            ><code>{{ visibleRunOutput(message.messageUid, step) }}</code></pre>
                            <button
                              v-if="shouldShowRunOutputExpand(getRunStepRenderData(step).output)"
                              class="run-output-toggle"
                              type="button"
                              :aria-expanded="isRunOutputExpanded(message.messageUid, step.stepUid)"
                              @click="toggleRunOutputExpanded(message.messageUid, step.stepUid)"
                            >
                              <ChevronsUp
                                v-if="isRunOutputExpanded(message.messageUid, step.stepUid)"
                                :size="12"
                              />
                              <ChevronsDown v-else :size="12" />
                              <span>
                                {{ isRunOutputExpanded(message.messageUid, step.stepUid)
                                  ? t("chat.messages.collapseOutput")
                                  : t("chat.messages.expandOutput") }}
                              </span>
                            </button>
                          </div>
                        </section>
                      </div>
                    </template>
                    <template v-else>
                      <div v-if="browserModeBadgeText(step)" class="run-step-browser-mode">
                        <n-tag size="small" :bordered="false" class="run-browser-mode-badge run-browser-mode-badge-inline">
                          {{ browserModeBadgeText(step) }}
                        </n-tag>
                      </div>
                      <div
                        class="run-details message-html"
                        v-html="renderMarkdown(runStepDetailsMarkdown(step))"
                      />
                      <div v-if="inlineStepAttachments(message.messageUid, step).length || inlineStepFileLinks(message.messageUid, step).length" class="message-attachments run-step-inline-attachments">
                        <button
                          v-for="attachment in inlineStepAttachments(message.messageUid, step)"
                          :key="attachment.fileUrl"
                          type="button"
                          class="message-attachment-card"
                          @click="openAttachment(attachment.fileUrl)"
                        >
                          <img
                            :src="attachment.fileUrl"
                            :alt="attachment.name"
                            class="message-attachment-image"
                          />
                          <div class="message-attachment-meta">
                            <div class="message-attachment-name">{{ attachment.name }}</div>
                            <div class="message-attachment-subtitle">
                              {{ attachment.mimeGroup }} · {{ formatAttachmentSize(attachment.sizeBytes) }}
                            </div>
                          </div>
                        </button>
                        <button
                          v-for="fileLink in inlineStepFileLinks(message.messageUid, step)"
                          :key="fileLink.path"
                          type="button"
                          class="message-attachment-card"
                          @click="conversationStore.openFile(fileLink.path)"
                        >
                          <img
                            :src="previewFileUrl(fileLink.path)"
                            :alt="fileLink.name"
                            class="message-attachment-image"
                          />
                          <div class="message-attachment-meta">
                            <div class="message-attachment-name">{{ fileLink.name }}</div>
                            <div class="message-attachment-subtitle">image · local file</div>
                          </div>
                        </button>
                      </div>
                    </template>
                  </n-collapse-item>
                </n-collapse>
              </n-collapse-item>
            </n-collapse>
          </n-card>
          <ApprovalBanner
            v-if="runHasActiveApproval(message)"
            :force-visible="runHasWaitingApprovalStep(message)"
            class="run-approval-banner"
          />
          <div v-if="shouldShowCanceledSystemBubble(message)" class="message-wrap canceled-assistant-message">
            <div class="message-bubble">
              <span class="message-system-event-text">{{ t("chat.messages.userCanceledTask") }}</span>
            </div>
            <div class="message-time message-system-event-time">{{ formatMessageTime(canceledSystemBubbleTime(message)) }}</div>
          </div>
        </div>

        <div v-if="conversationStore.currentConversationUid && shouldShowTypingIndicator" class="message-wrap">
          <div class="typing-indicator">
            <span v-for="index in 3" :key="index" />
          </div>
        </div>
      </template>
    </div>
    <ComposerPanel v-if="!props.hideComposer" />
  </section>
</template>

<style scoped>
.message-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.message-list {
  flex: 1;
}

.message-history-status {
  padding: var(--space-2) 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  text-align: center;
}

.conversation-empty-state {
  display: grid;
  gap: var(--space-4_5);
  place-items: center;
  min-height: 100%;
}

.conversation-empty-hero {
  width: min(100%, var(--container-md));
  border-radius: var(--radius-xl);
  padding: var(--space-6) var(--space-6);
  background: color-mix(in srgb, var(--color-bg-surface) 74%, transparent);
  backdrop-filter: blur(var(--size-8));
  display: grid;
  place-items: center;
  gap: var(--space-4);
}

.conversation-empty-title {
  color: var(--color-text-heading);
  font-size: clamp(var(--space-6), 2.8vw, var(--size-26));
  line-height: 1.12;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.conversation-empty-hint {
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
}

.conversation-starter-prompts {
  margin-top: var(--space-2);
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.starter-prompt-btn {
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--color-bg-surface-soft) 76%, transparent);
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  padding: var(--space-4);
  min-height: var(--size-108);
  text-align: left;
  display: grid;
  align-content: start;
  gap: var(--space-1_5);
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.starter-prompt-btn:hover {
  border-color: var(--color-border-active);
  background: var(--color-bg-soft-hover);
  color: var(--color-text-primary);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.1);
  transform: translateY(calc(var(--space-0_5) * -1));
}

.starter-prompt-title {
  color: var(--color-text-primary);
  font-size: var(--text-body-size);
  line-height: 1.35;
  font-weight: 600;
}

.starter-prompt-summary {
  color: var(--color-text-muted);
  font-size: var(--text-caption-size);
  line-height: 1.45;
  min-height: calc(1.45em * 3);
}

@media (max-width: 1120px) {
  .conversation-empty-hero {
    width: min(100%, var(--container-sm));
  }

  .conversation-starter-prompts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .conversation-empty-hero {
    width: 100%;
    padding: var(--space-5);
  }

  .conversation-starter-prompts {
    grid-template-columns: 1fr;
  }

  .starter-prompt-btn {
    min-height: var(--size-96);
  }
}

.message-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: var(--space-4_5);
}

.message-wrap.user {
  align-items: flex-end;
}

.message-wrap-focused .message-bubble {
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.14), 0 16px 40px rgba(0, 0, 0, 0.18);
}

.message-bubble {
  position: relative;
  max-width: 92%;
  padding: var(--space-2_5) var(--space-4);
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-xl) var(--control-radius-md);
  background: var(--color-chat-bubble-assistant-bg);
  line-height: 1.75;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.message-bubble.user {
  border-radius: var(--radius-xl) var(--radius-xl) var(--control-radius-md) var(--radius-xl);
  background: var(--color-chat-bubble-user-bg);
  color: var(--color-text-inverse);
}

.message-bubble .message-html {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.message-bubble .message-html.is-collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 10;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-bubble.user .message-html.is-user-collapsed {
  -webkit-mask-image: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 1) 0,
    rgba(0, 0, 0, 1) 76%,
    rgba(0, 0, 0, 0.42) 92%,
    rgba(0, 0, 0, 0) 100%
  );
  mask-image: linear-gradient(
    to bottom,
    rgba(0, 0, 0, 1) 0,
    rgba(0, 0, 0, 1) 76%,
    rgba(0, 0, 0, 0.42) 92%,
    rgba(0, 0, 0, 0) 100%
  );
}

.message-bubble.has-expand-toggle {
  padding-bottom: calc(var(--space-3) + var(--space-8));
}

.message-expand-btn {
  position: absolute;
  left: var(--space-3);
  bottom: var(--space-2_5);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: auto;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  font-size: var(--text-body-size);
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.16s ease;
}

.message-expand-btn:hover {
  opacity: 0.88;
}

.message-expand-btn :deep(svg) {
  flex: none;
}

.message-bubble:not(.user) .message-expand-btn {
  color: var(--color-text-brand-strong);
}

.message-bubble:not(.user) .message-expand-btn:hover {
  color: var(--color-text-brand);
}

.message-attachments {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.run-step-inline-attachments {
  margin-top: var(--space-4);
  margin-bottom: 0;
}

.message-attachment-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: var(--size-1) solid var(--color-chat-attachment-border);
  border-radius: var(--radius-xl);
  background: var(--color-chat-attachment-bg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  text-align: left;
  cursor: pointer;
}

.message-attachment-image,
.message-attachment-file {
  width: var(--size-48);
  height: var(--size-48);
  border-radius: var(--radius-lg);
  flex: none;
}

.message-attachment-image {
  object-fit: cover;
}

.message-attachment-file {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-chat-attachment-file-bg);
  color: var(--color-chat-attachment-file-text);
  font-size: var(--text-caption-size);
  font-weight: 800;
  letter-spacing: 0.08em;
}

.message-attachment-meta {
  min-width: 0;
}

.message-attachment-name {
  font-size: var(--text-body-size);
  font-weight: 700;
  color: var(--color-chat-attachment-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-attachment-subtitle {
  margin-top: var(--space-1);
  font-size: var(--text-caption-size);
  color: var(--color-chat-attachment-muted);
}

.message-time {
  font-size: var(--text-caption-size);
  color: var(--text-muted);
}

.user-message-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  width: min(var(--size-percent-message-max), var(--container-xl));
  margin-top: var(--space-1);
  padding-right: var(--space-1_5);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(calc(var(--size-1) * -1));
  transition: opacity 0.14s ease, transform 0.14s ease, visibility 0.14s ease;
}

.user-message-time {
  margin-top: 0;
  padding-right: 0;
  text-align: right;
}

.message-wrap.user:hover .user-message-meta,
.message-wrap.user:focus-within .user-message-meta {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
}

.user-message-time {
  width: auto;
  opacity: 1;
  visibility: visible;
  transform: none;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--space-3);
  width: min(var(--size-percent-message-max), var(--container-xl));
  margin-top: var(--space-1);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(calc(var(--size-1) * -1));
  transition: opacity 0.14s ease, transform 0.14s ease, visibility 0.14s ease;
}

.message-wrap:hover .message-meta,
.message-wrap:focus-within .message-meta {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
}

.message-actions {
  display: flex;
  gap: var(--space-2);
}

@media (hover: none) {
  .message-meta {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: none;
  }

  .user-message-time {
    width: auto;
  }

  .user-message-meta {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: none;
  }
}

.message-token-inline {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-cool-gray);
  font-size: var(--text-caption-size);
  line-height: 1;
}

.message-token-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.message-token-total {
  color: var(--text-muted);
}

.message-action-btn {
  border: var(--size-1) solid var(--color-border-ghost);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-2_5);
  background: var(--color-bg-overlay-light);
  color: var(--color-text-subtle);
  font-size: var(--text-caption-size);
  line-height: 1.2;
  cursor: pointer;
  transition: all 0.16s ease;
}

.message-action-btn.icon-only {
  width: var(--space-6);
  height: var(--space-6);
  padding: 0;
  border: none;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
}

.message-action-btn.icon-only:hover {
  border-color: transparent;
  background: var(--color-bg-soft-hover);
}

.message-action-btn:hover {
  border-color: var(--color-border-brand-hover);
  background: var(--color-bg-brand-tint-12);
  color: var(--color-text-brand-strong);
}

.message-action-btn:disabled {
  cursor: not-allowed;
  opacity: 0.74;
}

.message-action-btn.copied {
  border-color: var(--color-border-brand-strong);
  background: var(--color-bg-brand-tint-14);
  color: var(--color-text-brand-strong);
  animation: copied-flash 0.25s ease-out;
}

.message-action-btn.icon-only.copied,
.message-action-btn.icon-only.saved,
.message-action-btn.icon-only.loading {
  border-color: transparent;
}

.message-action-btn.loading {
  border-color: var(--color-border-brand-hover);
  color: var(--color-text-brand-strong);
}

.message-action-btn.saved {
  background: color-mix(in srgb, var(--color-accent-brand) 12%, transparent);
  color: var(--color-accent-brand);
  opacity: 1;
}

.save-tip-wrap {
  position: relative;
  display: inline-flex;
}

@keyframes copied-flash {
  0% {
    transform: scale(0.96);
  }
  100% {
    transform: scale(1);
  }
}

.run-card {
  width: min(var(--size-percent-message-max), var(--container-xl));
  margin-top: var(--space-3_5);
  min-width: 0;
}

.message-wrap.user .run-card {
  align-self: flex-start;
}

.run-title {
  flex: 1;
  text-align: left;
  font-size: var(--text-body-size);
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.run-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.run-summary {
  margin-top: var(--space-1_5);
  line-height: 1.7;
}

.run-step-header-tags {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-1_5);
}

.run-step-header-title {
  min-width: 0;
}

.run-retry-notice {
  display: grid;
  gap: var(--space-1_5);
  width: 100%;
  min-width: 0;
  padding: var(--space-1) 0;
}

.run-retry-notice-title {
  color: var(--color-text-primary);
  font-size: var(--text-body-size);
  font-weight: 600;
  line-height: 1.5;
}

.run-retry-notice-details {
  color: var(--color-text-secondary);
  font-size: var(--text-body-size);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.run-retry-notice-body {
  padding-top: var(--space-1);
}

.run-retry-notice-body-label {
  color: var(--color-text-subtle);
  font-size: var(--text-caption-size);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.run-step-browser-mode {
  margin-bottom: var(--space-3);
}

.run-browser-mode-badge {
  background: color-mix(in srgb, var(--color-bg-surface-soft) 88%, transparent);
  color: var(--color-text-secondary);
}

.run-browser-mode-badge-inline {
  flex-shrink: 0;
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-soft) 82%, transparent);
  background: color-mix(in srgb, var(--color-bg-card) 72%, var(--color-bg-surface-soft));
  color: var(--color-text-muted);
}

.canceled-assistant-message {
  align-self: flex-start;
  margin-top: var(--space-4);
  width: min(var(--size-percent-message-max), var(--container-xl));
}

.canceled-assistant-message .message-time {
  margin-top: var(--space-1_5);
  opacity: 0;
  visibility: hidden;
  transform: translateY(calc(var(--size-1) * -1));
  transition: opacity 0.14s ease, transform 0.14s ease, visibility 0.14s ease;
}

.canceled-assistant-message:hover .message-time,
.canceled-assistant-message:focus-within .message-time {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.run-details {
  line-height: 1.7;
}

.run-command-blocks {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.run-command-section {
  display: grid;
  gap: var(--space-1_5);
  min-width: 0;
}

.run-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.run-code-label {
  font-size: var(--text-caption-size);
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.run-code-icon-btn {
  width: var(--space-6);
  height: var(--space-6);
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.16s ease;
}

.run-code-icon-btn:hover {
  background: var(--color-bg-soft-hover);
  color: var(--color-text-brand-strong);
}

.run-code-icon-btn.copied {
  background: var(--color-bg-brand-tint-14);
  color: var(--color-text-brand-strong);
}

.run-output-toggle {
  appearance: none;
  -webkit-appearance: none;
  border: none;
  background: transparent;
  color: #4fdbc7;
  font-size: var(--text-caption-size);
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  line-height: 1.3;
  cursor: pointer;
  padding: 0;
  box-shadow: none;
  text-decoration: none;
  position: absolute;
  left: var(--space-2);
  bottom: var(--space-2);
}

.run-output-toggle:hover {
  color: #6fe5d4;
  text-decoration: underline;
}

.run-output-toggle:focus-visible {
  outline: none;
  color: #6fe5d4;
  text-decoration: underline;
}

.run-output-shell {
  position: relative;
  min-width: 0;
  max-width: 100%;
}

.run-code-pre {
  margin: 0;
  padding: var(--space-3_5) var(--space-4);
  border-radius: var(--radius-md);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: auto;
  overflow-y: hidden;
  line-height: 1.65;
  background: var(--color-run-code-bg);
  color: var(--color-text-code-block);
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-soft) 88%, transparent);
}

.run-code-pre code {
  display: block;
  width: max-content;
  min-width: 100%;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  white-space: pre;
}

.run-code-pre-output-collapsed {
  padding-bottom: calc(var(--space-3_5) + var(--space-6));
}

.run-details :deep(strong) {
  display: inline-block;
  margin-bottom: var(--space-1_5);
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.run-details :deep(pre) {
  margin: 0 0 var(--space-3);
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-soft) 88%, transparent);
}

.typing-indicator {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-4_5);
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-xl) var(--control-radius-md);
  background: var(--color-chat-bubble-assistant-bg);
}

.typing-indicator span {
  width: var(--space-2_5);
  height: var(--space-2_5);
  border-radius: var(--radius-pill);
  background: var(--color-indicator-muted);
  animation: pulse 1.2s ease-in-out infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.18s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes pulse {
  0%, 80%, 100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(calc(var(--space-1) * -1));
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1120px) {
  .message-list {
    padding-bottom: calc(var(--space-chat-mobile-input-offset) + env(safe-area-inset-bottom));
    -webkit-overflow-scrolling: touch;
  }
}

.message-file-links {
  margin-top: var(--space-3);
}

.knowledge-citations {
  margin-top: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-lg);
}

.knowledge-citation {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
}

.knowledge-citation p {
  margin: 0;
  white-space: pre-wrap;
}

.run-steps-collapse {
  margin-top: var(--space-3_5);
}

.run-approval-banner {
  margin-top: var(--space-3);
  align-self: flex-start;
}
</style>
