import type { AgentEvent } from "@/types/api";

const EVENT_NAMES = [
  "message_delta",
  "plan_created",
  "step_started",
  "step_waiting_approval",
  "step_rejected",
  "step_finished",
  "step_failed",
  "round_token_usage",
  "loop_limit_reached",
  "message_completed",
  "message_canceled"
];

export function subscribeConversationEvents(conversationUid: string, onEvent: (event: AgentEvent) => void) {
  const eventSource = new EventSource(`/api/conversations/${conversationUid}/events`);

  EVENT_NAMES.forEach((eventName) => {
    eventSource.addEventListener(eventName, (event) => {
      onEvent(JSON.parse((event as MessageEvent<string>).data) as AgentEvent);
    });
  });

  return eventSource;
}
