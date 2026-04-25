package ai.nomoclaw.bot.store.config;

import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.MybatisPlusAgentStore;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageRepository;
import ai.nomoclaw.bot.store.repository.AgentConversationRepository;
import ai.nomoclaw.bot.store.repository.AgentStepRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentStoreConfig {

    @Bean
    public AgentStore agentStore(AgentConversationRepository sessionRepository,
                                 AgentMessageRepository messageRepository,
                                 AgentStepRepository stepRepository,
                                 AgentEventRepository eventRepository) {
        return new MybatisPlusAgentStore(
                sessionRepository,
                messageRepository,
                stepRepository,
                eventRepository
        );
    }
}
