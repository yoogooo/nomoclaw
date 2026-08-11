import type { ConversationAttachment, ModelCapabilities } from "@/types/api";

const MAX_FILES_PER_MESSAGE = 10;

export function normalizeCapabilities(capabilities?: ModelCapabilities | null): ModelCapabilities {
  return {
    toolCalling: Boolean(capabilities?.toolCalling),
    imageRecognition: Boolean(capabilities?.imageRecognition),
    audioRecognition: Boolean(capabilities?.audioRecognition),
    videoRecognition: Boolean(capabilities?.videoRecognition),
    reasoning: Boolean(capabilities?.reasoning)
  };
}

export function resolveMimeGroup(file: File) {
  const contentType = (file.type || "").toLowerCase();
  if (contentType.startsWith("image/")) return "image";
  if (contentType.startsWith("audio/")) return "audio";
  if (contentType.startsWith("video/")) return "video";
  if (contentType === "application/pdf" || file.name.toLowerCase().endsWith(".pdf")) return "pdf";
  if (contentType.startsWith("text/") || ["application/json", "application/xml", "application/yaml", "application/x-yaml", "application/csv"].includes(contentType)
    || [".txt", ".md", ".json", ".csv", ".yaml", ".yml", ".xml"].some((suffix) => file.name.toLowerCase().endsWith(suffix))) return "text";
  return "application";
}

export function isAttachmentSupported(capabilities: ModelCapabilities, group: string) {
  return group === "text" || group === "pdf"
    || (group === "image" && capabilities.imageRecognition)
    || (group === "audio" && capabilities.audioRecognition)
    || (group === "video" && capabilities.videoRecognition);
}

export function validateFilesAgainstCapabilities(capabilities: ModelCapabilities, existing: ConversationAttachment[], files: File[],
                                                  translate: (key: string, params?: Record<string, any>) => string) {
  if (existing.length + files.length > MAX_FILES_PER_MESSAGE) {
    throw new Error(translate("chat.composer.maxFiles", { count: MAX_FILES_PER_MESSAGE }));
  }
  const unsupported = [...existing.map((item) => item.mimeGroup), ...files.map(resolveMimeGroup)]
    .find((group) => !isAttachmentSupported(capabilities, group));
  if (unsupported) throw new Error(translate("chat.composer.unsupportedType"));
}
