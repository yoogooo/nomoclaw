import type { ConversationAttachment, UploadPolicy } from "@/types/api";
import { formatBytes } from "./helpers";

const EMPTY_UPLOAD_POLICY: UploadPolicy = {
  enabled: false,
  allowedMimeGroups: [],
  maxFilesPerMessage: 0,
  maxImagesPerMessage: 0,
  maxFileBytes: 0,
  maxTotalBytes: 0,
  singleMimeGroupOnly: false,
  allowMixedImageAndFile: false
};

export function normalizeUploadPolicy(policy?: UploadPolicy | null): UploadPolicy {
  if (!policy) {
    return { ...EMPTY_UPLOAD_POLICY };
  }
  return {
    enabled: Boolean(policy.enabled),
    allowedMimeGroups: Array.isArray(policy.allowedMimeGroups) ? policy.allowedMimeGroups.filter(Boolean) : [],
    maxFilesPerMessage: Math.max(0, Number(policy.maxFilesPerMessage || 0)),
    maxImagesPerMessage: Math.max(0, Number(policy.maxImagesPerMessage || 0)),
    maxFileBytes: Math.max(0, Number(policy.maxFileBytes || 0)),
    maxTotalBytes: Math.max(0, Number(policy.maxTotalBytes || 0)),
    singleMimeGroupOnly: Boolean(policy.singleMimeGroupOnly),
    allowMixedImageAndFile: Boolean(policy.allowMixedImageAndFile)
  };
}

export function normalizeMimeGroupFromName(fileName: string) {
  const normalized = fileName.toLowerCase();
  if (normalized.endsWith(".pdf")) {
    return "pdf";
  }
  if ([".txt", ".md", ".json", ".csv", ".yaml", ".yml", ".xml"].some((suffix) => normalized.endsWith(suffix))) {
    return "text";
  }
  return "application";
}

export function resolveMimeGroup(file: File) {
  const contentType = (file.type || "").toLowerCase();
  if (contentType.startsWith("image/")) {
    return "image";
  }
  if (contentType.startsWith("audio/")) {
    return "audio";
  }
  if (contentType.startsWith("video/")) {
    return "video";
  }
  if (contentType === "application/pdf") {
    return "pdf";
  }
  if (contentType.startsWith("text/") || ["application/json", "application/xml", "application/yaml", "application/x-yaml", "application/csv"].includes(contentType)) {
    return "text";
  }
  return normalizeMimeGroupFromName(file.name || "");
}

export function validateFilesAgainstPolicy(
  policy: UploadPolicy,
  existing: ConversationAttachment[],
  files: File[],
  translate: (key: string, params?: Record<string, any>) => string
) {
  const combinedGroups = new Set<string>([
    ...existing.map((item) => item.mimeGroup),
    ...files.map((file) => resolveMimeGroup(file))
  ]);
  if (!combinedGroups.size) {
    return;
  }
  const modelGroups = [...combinedGroups].filter((group) => group !== "text");
  const textOnly = modelGroups.length === 0;
  if (!policy.enabled && !textOnly) {
    throw new Error(translate("chat.composer.uploadDisabled"));
  }

  const maxFileBytes = policy.maxFileBytes || 0;
  if (!textOnly && maxFileBytes > 0 && files.some((file) => file.size > maxFileBytes)) {
    throw new Error(translate("chat.composer.maxFileSize", { size: formatBytes(maxFileBytes) }));
  }
  const totalBytes = existing.reduce((sum, item) => sum + item.sizeBytes, 0) + files.reduce((sum, file) => sum + file.size, 0);
  const maxTotalBytes = policy.maxTotalBytes || 0;
  if (!textOnly && maxTotalBytes > 0 && totalBytes > maxTotalBytes) {
    throw new Error(translate("chat.composer.maxTotalSize", { size: formatBytes(maxTotalBytes) }));
  }
  if (textOnly) {
    return;
  }
  if (policy.singleMimeGroupOnly && modelGroups.length > 1) {
    throw new Error(translate("chat.composer.singleTypeOnly"));
  }
  const unsupportedGroup = modelGroups.find((group) =>
    policy.allowedMimeGroups.length && !policy.allowedMimeGroups.includes(group)
  );
  if (unsupportedGroup) {
    throw new Error(translate("chat.composer.unsupportedType"));
  }

  const existingImageCount = existing.filter((item) => item.mimeGroup === "image").length;
  const incomingImageCount = files.filter((file) => resolveMimeGroup(file) === "image").length;
  const imageCount = existingImageCount + incomingImageCount;
  const existingNonTextCount = existing.filter((item) => item.mimeGroup !== "text").length;
  const incomingNonTextCount = files.filter((file) => resolveMimeGroup(file) !== "text").length;
  const nonImageCount = existingNonTextCount + incomingNonTextCount - imageCount;
  if (imageCount > 0 && nonImageCount > 0 && !policy.allowMixedImageAndFile) {
    throw new Error(translate("chat.composer.mixedTypeNotAllowed"));
  }
  if (imageCount > 0 && imageCount > policy.maxImagesPerMessage) {
    throw new Error(translate("chat.composer.maxImages", { count: policy.maxImagesPerMessage }));
  }
  if (nonImageCount > 0 && nonImageCount > policy.maxFilesPerMessage) {
    throw new Error(translate("chat.composer.maxFiles", { count: policy.maxFilesPerMessage }));
  }
}
