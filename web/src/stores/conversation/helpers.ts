import type { ConversationMessage, ConversationSummary } from "@/types/api";

export function shouldReuseConversationSummary(current: ConversationSummary, next: ConversationSummary) {
  return current.conversationUid === next.conversationUid
    && current.agentGroupUid === next.agentGroupUid
    && current.agentUid === next.agentUid
    && current.title === next.title
    && current.pinned === next.pinned
    && Boolean(current.running) === Boolean(next.running)
    && current.waitingApproval === next.waitingApproval
    && current.unread === next.unread
    && (current.lastTaskTerminalTime || "") === (next.lastTaskTerminalTime || "")
    && current.lastUserMessageTime === next.lastUserMessageTime
    && current.createdTime === next.createdTime
    && current.updatedTime === next.updatedTime;
}

export function reuseStableConversationSummaries(current: ConversationSummary[], next: ConversationSummary[]) {
  if (!current.length) {
    return next;
  }
  let changed = current.length !== next.length;
  const currentByUid = new Map(current.map((item) => [item.conversationUid, item] as const));
  const merged = next.map((item, index) => {
    const existing = currentByUid.get(item.conversationUid);
    if (!existing) {
      changed = true;
      return item;
    }
    if (current[index]?.conversationUid !== item.conversationUid) {
      changed = true;
    }
    if (!shouldReuseConversationSummary(existing, item)) {
      changed = true;
      return item;
    }
    return existing;
  });
  return changed ? merged : current;
}

export function dedupeConversations(source: ConversationSummary[]) {
  const seen = new Set<string>();
  return source.filter((item) => {
    if (seen.has(item.conversationUid)) {
      return false;
    }
    seen.add(item.conversationUid);
    return true;
  });
}

export function dedupeMessages(source: ConversationMessage[]) {
  const seen = new Set<string>();
  const deduped: ConversationMessage[] = [];
  source.forEach((item) => {
    const key = item.messageUid
      ? `uid:${item.messageUid}`
      : `draft:${item.parentMessageUid || ""}:${item.createdTime}:${item.role}`;
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    deduped.push(item);
  });
  return deduped;
}

export function formatBytes(bytes: number) {
  const mb = Math.floor(bytes / (1024 * 1024));
  return mb > 0 ? `${mb}MB` : `${bytes}B`;
}

export function buildConversationPageRequestKey(params: {
  agentUid: string;
  limit: number;
  beforeSortKey?: string;
  asOf?: string;
}) {
  return JSON.stringify({
    agentUid: params.agentUid,
    limit: params.limit,
    beforeSortKey: params.beforeSortKey || "",
    asOf: params.asOf || ""
  });
}

export function buildConversationSearchRequestKey(params: {
  agentUid: string;
  keyword: string;
  limit: number;
  beforeSortKey?: string;
}) {
  return JSON.stringify({
    agentUid: params.agentUid,
    keyword: params.keyword,
    limit: params.limit,
    beforeSortKey: params.beforeSortKey || ""
  });
}
