CREATE INDEX idx_agent_conversation_agent_time_id
    ON agent_conversation (agent_uid, last_user_message_time, id);

CREATE INDEX idx_agent_message_conversation_id
    ON agent_message (conversation_uid, id);
