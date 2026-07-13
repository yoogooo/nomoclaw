RENAME TABLE conversation_knowledge_base_relation TO agent_conversation_knowledge_base_relation,
             message_knowledge_citation TO agent_message_knowledge_citation;

ALTER TABLE agent_conversation_knowledge_base_relation COMMENT = 'Agent会话与知识库覆盖绑定表';
ALTER TABLE agent_message_knowledge_citation COMMENT = 'Agent消息知识库引用表';
