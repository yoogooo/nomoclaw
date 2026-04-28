import type { ConversationRunStep } from "@/types/api";

export type ApprovalPromptKey =
  | "chat.approval.riskPrompt"
  | "chat.approval.riskPromptGeneric"
  | "chat.approval.riskPromptScreenshot";

export type ApprovalLabelKey =
  | "chat.approval.commandLabel"
  | "chat.approval.payloadLabel"
  | "chat.approval.screenshotPathLabel";

export interface ApprovalRenderResult {
  toolName: string;
  command: string;
  path: string;
  body: string;
  promptKey: ApprovalPromptKey;
  labelKey: ApprovalLabelKey;
}

type StepLike = {
  toolName?: string;
  toolArgs?: Record<string, any>;
  displayTitle?: string;
  displaySummary?: string;
  displayDetails?: string;
};

function toTextCodeBlock(text: string) {
  const value = String(text || "").trim();
  if (!value) return "";
  return `\`\`\`text\n${value}\n\`\`\``;
}

function toBashCodeBlock(command: string) {
  const value = String(command || "").trim();
  if (!value) return "";
  return `\`\`\`bash\n${value}\n\`\`\``;
}

function toJsonCodeBlock(toolArgsRaw: unknown) {
  if (!toolArgsRaw || typeof toolArgsRaw !== "object") {
    return "";
  }
  const cloned = JSON.parse(JSON.stringify(toolArgsRaw)) as Record<string, any>;
  if ("_toolCallId" in cloned) {
    delete cloned._toolCallId;
  }
  const serialized = JSON.stringify(cloned, null, 2);
  if (!serialized || serialized === "{}") {
    return "";
  }
  return `\`\`\`json\n${serialized}\n\`\`\``;
}

function readToolArgs(raw: unknown) {
  if (!raw || typeof raw !== "object") {
    return {} as Record<string, any>;
  }
  return raw as Record<string, any>;
}

function extractCommand(payload: Record<string, any>) {
  const toolArgs = readToolArgs(payload.toolArgs);
  const byToolArgs = String(toolArgs.command || "").trim();
  if (byToolArgs) {
    return byToolArgs;
  }
  return String(payload.command || "").trim();
}

function extractPath(payload: Record<string, any>) {
  const toolArgs = readToolArgs(payload.toolArgs);
  const byToolArgs = String(toolArgs.path || "").trim();
  if (byToolArgs) {
    return byToolArgs;
  }
  return String(payload.path || "").trim();
}

function normalizeToolName(raw: string, command: string, stepLike?: StepLike) {
  const normalized = String(raw || "").trim();
  if (normalized) {
    return normalized;
  }
  if (command) {
    return "CommandTool";
  }
  const title = String(stepLike?.displayTitle || "").toLowerCase();
  const details = String(stepLike?.displayDetails || stepLike?.displaySummary || "").toLowerCase();
  if (title.includes("截取桌面画面") || details.includes("截取桌面画面") || title.includes("desktop screenshot")) {
    return "DesktopScreenshotTool";
  }
  return "";
}

function renderByToolName(toolName: string,
                          command: string,
                          path: string,
                          bodyFallback: string,
                          toolArgsRaw: unknown): Pick<ApprovalRenderResult, "body" | "promptKey" | "labelKey"> {
  if (toolName === "CommandTool" || command) {
    return {
      body: toBashCodeBlock(command) || toJsonCodeBlock(toolArgsRaw) || bodyFallback,
      promptKey: "chat.approval.riskPrompt",
      labelKey: "chat.approval.commandLabel"
    };
  }

  if (toolName === "DesktopScreenshotTool") {
    return {
      body: toTextCodeBlock(path) || toJsonCodeBlock(toolArgsRaw) || bodyFallback,
      promptKey: "chat.approval.riskPromptScreenshot",
      labelKey: "chat.approval.screenshotPathLabel"
    };
  }

  return {
    body: toJsonCodeBlock(toolArgsRaw) || bodyFallback,
    promptKey: "chat.approval.riskPromptGeneric",
    labelKey: "chat.approval.payloadLabel"
  };
}

export function resolveApprovalFromPayload(payload: Record<string, any>): ApprovalRenderResult {
  const command = extractCommand(payload);
  const path = extractPath(payload);
  const toolName = normalizeToolName(String(payload.toolName || ""), command);
  const rendered = renderByToolName(
    toolName,
    command,
    path,
    "",
    payload.toolArgs
  );
  return {
    toolName,
    command,
    path,
    body: rendered.body,
    promptKey: rendered.promptKey,
    labelKey: rendered.labelKey
  };
}

export function resolveApprovalFromStep(step: ConversationRunStep): ApprovalRenderResult {
  const toolArgsRaw = readToolArgs(step.toolArgs);
  const command = String(toolArgsRaw.command || "").trim();
  const path = String(toolArgsRaw.path || "").trim();
  const toolName = normalizeToolName(String(step.toolName || ""), command, step);
  const details = String(step.displayDetails || step.displaySummary || "").trim();
  const rendered = renderByToolName(
    toolName,
    command,
    path,
    toTextCodeBlock(details),
    toolArgsRaw
  );
  return {
    toolName,
    command,
    path,
    body: rendered.body,
    promptKey: rendered.promptKey,
    labelKey: rendered.labelKey
  };
}
