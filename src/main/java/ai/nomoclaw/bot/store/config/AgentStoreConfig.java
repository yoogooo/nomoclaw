package ai.nomoclaw.bot.store.config;

import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.InMemoryAgentStore;
import ai.nomoclaw.bot.store.MybatisPlusAgentStore;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageRepository;
import ai.nomoclaw.bot.store.repository.AgentConversationRepository;
import ai.nomoclaw.bot.store.repository.AgentStepRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class AgentStoreConfig {

    @Bean
    public AgentStore agentStore(ObjectProvider<AgentConversationRepository> sessionRepositoryProvider,
                                 ObjectProvider<AgentMessageRepository> messageRepositoryProvider,
                                 ObjectProvider<AgentStepRepository> stepRepositoryProvider,
                                 ObjectProvider<AgentEventRepository> eventRepositoryProvider) {
        AgentConversationRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
        AgentMessageRepository messageRepository = messageRepositoryProvider.getIfAvailable();
        AgentStepRepository stepRepository = stepRepositoryProvider.getIfAvailable();
        AgentEventRepository eventRepository = eventRepositoryProvider.getIfAvailable();
        if (sessionRepository != null && messageRepository != null && stepRepository != null && eventRepository != null) {
            return new MybatisPlusAgentStore(sessionRepository, messageRepository, stepRepository, eventRepository);
        }
        return new InMemoryAgentStore();
    }
}
