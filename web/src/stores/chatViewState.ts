export type ChatLastViewState = {
  mode: "conversation" | "draft";
  conversationUid: string | null;
};

const CHAT_LAST_VIEW_STORAGE_KEY = "chat:last-view";

export function loadChatLastViewState(): ChatLastViewState | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(CHAT_LAST_VIEW_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<ChatLastViewState>;
    if (parsed.mode !== "conversation" && parsed.mode !== "draft") {
      return null;
    }
    return {
      mode: parsed.mode,
      conversationUid: typeof parsed.conversationUid === "string" ? parsed.conversationUid : null
    };
  } catch {
    return null;
  }
}

export function saveChatLastViewState(state: ChatLastViewState) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(CHAT_LAST_VIEW_STORAGE_KEY, JSON.stringify(state));
}
