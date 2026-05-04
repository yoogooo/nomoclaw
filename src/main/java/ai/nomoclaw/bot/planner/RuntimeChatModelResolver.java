package ai.nomoclaw.bot.planner;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;
import ai.nomoclaw.bot.llm.codex.CodexAuthFileTokenProvider;
import ai.nomoclaw.bot.llm.codex.CodexChatModel;
import ai.nomoclaw.bot.llm.codex.CodexStreamingChatModel;
import ai.nomoclaw.bot.llm.codex.CodexTokenProvider;
import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.orchestrator.ModelConfigAppService;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.store.entity.AgentConversationEntity;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentMessageEntity;
import ai.nomoclaw.bot.store.repository.AgentConversationRepository;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RuntimeChatModelResolver {

    private final LlmProperties llmProperties;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentConversationRepository agentConversationRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final ModelConfigAppService modelConfigAppService;
    private final HttpClient httpClient;

    public RuntimeChatModelResolver(LlmProperties llmProperties,
                                    AgentDefinitionRepository agentDefinitionRepository,
                                    AgentConversationRepository agentConversationRepository,
                                    AgentMessageRepository agentMessageRepository,
                                    ModelConfigAppService modelConfigAppService,
                                    HttpClient appHttpClient) {
        this.llmProperties = llmProperties;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentConversationRepository = agentConversationRepository;
        this.agentMessageRepository = agentMessageRepository;
        this.modelConfigAppService = modelConfigAppService;
        this.httpClient = appHttpClient;
    }

    public ResolvedModel resolve(PromptLoader.PromptContext promptContext) {
        ModelConfigDto config = modelConfigAppService.getModelConfig();
        Map<String, ModelConfigDto.Provider> providersById = config.providers().stream()
                .collect(Collectors.toMap(ModelConfigDto.Provider::id, Function.identity(), (left, right) -> left));

        String agentName = promptContext == null ? "" : trim(promptContext.agentName());
        AgentDefinitionEntity agent = agentName.isBlank() ? null : agentDefinitionRepository.findByName(agentName);
        AgentConversationEntity conversation = promptContext == null ? null : agentConversationRepository.findByConversationUid(trim(promptContext.sessionId()));
        AgentMessageEntity message = promptContext == null || trim(promptContext.messageUid()).isBlank()
                ? null
                : agentMessageRepository.findByMessageId(trim(promptContext.messageUid()));
        String selectedProviderId = message != null && message.getProvider() != null && !message.getProvider().isBlank()
                ? trim(message.getProvider())
                : agent == null ? "" : trim(agent.getModelProviderId());
        String selectedModelId = message != null && message.getModelName() != null && !message.getModelName().isBlank()
                ? trim(message.getModelName())
                : resolveAgentPrimaryModelId(agent);

        ModelConfigDto.Provider provider = providersById.get(selectedProviderId);
        if (provider == null) {
            String llmProvider = trim(llmProperties.getProvider()).toLowerCase(Locale.ROOT);
            provider = providersById.get(llmProvider);
        }
        if (provider == null) {
            provider = config.providers().stream().findFirst().orElse(null);
        }

        String runtimeModelId = selectedModelId;
        if (runtimeModelId.isBlank()) {
            runtimeModelId = trim(provider.defaultModel());
        }
        if (runtimeModelId.isBlank() && !provider.models().isEmpty()) {
            runtimeModelId = trim(provider.models().get(0).id());
        }
        if (runtimeModelId.isBlank() && "ollama".equals(provider.id())) {
            runtimeModelId = trim(llmProperties.getOllama().getModelName());
        }
        if (runtimeModelId.isBlank()) {
            throw new IllegalArgumentException("No model configured for provider=" + provider.id());
        }

        if ("codex".equals(provider.id())) {
            CodexTokenProvider tokenProvider = new CodexAuthFileTokenProvider(Path.of(System.getProperty("user.home"), ".codex"));
            CodexTokenProvider.AuthStatus authStatus = tokenProvider.authStatus();
            if (!authStatus.configured()) {
                throw new IllegalArgumentException(authStatus.message());
            }
            ChatModel model = new CodexChatModel(
                    httpClient,
                    tokenProvider,
                    provider.baseUrl(),
                    runtimeModelId,
                    llmProperties.getTimeout()
            );
            StreamingChatModel streamingModel = new CodexStreamingChatModel(
                    httpClient,
                    tokenProvider,
                    provider.baseUrl(),
                    runtimeModelId,
                    llmProperties.getTimeout()
            );
            return new ResolvedModel(provider.id(), runtimeModelId, model, streamingModel);
        }

        if (provider.local() || "ollama".equals(provider.id())) {
            String baseUrl = trim(provider.baseUrl()).isBlank() ? llmProperties.getOllama().getBaseUrl() : trim(provider.baseUrl());
            ChatModel model = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(runtimeModelId)
                    .timeout(llmProperties.getTimeout())
                    .maxRetries(llmProperties.getMaxRetries())
                    .build();
            StreamingChatModel streamingModel = OllamaStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(runtimeModelId)
                    .timeout(llmProperties.getTimeout())
                    .build();
            return new ResolvedModel(provider.id(), runtimeModelId, model, streamingModel);
        }

        String baseUrl = normalizeCompatibleBaseUrl(provider.id(), provider.baseUrl());
        String apiKey = trim(provider.apiKey());
        if (provider.requireApiKey() && apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required for provider=" + provider.id());
        }
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(Objects.requireNonNull(apiKey, "apiKey must not be null"))
                .baseUrl(baseUrl)
                .modelName(runtimeModelId)
                .timeout(llmProperties.getTimeout())
                .maxRetries(llmProperties.getMaxRetries())
                .build();
        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(Objects.requireNonNull(apiKey, "apiKey must not be null"))
                .baseUrl(baseUrl)
                .modelName(runtimeModelId)
                .timeout(llmProperties.getTimeout())
                .build();
        return new ResolvedModel(provider.id(), runtimeModelId, model, streamingModel);
    }

    private String normalizeCompatibleBaseUrl(String providerId, String rawBaseUrl) {
        String baseUrl = trim(rawBaseUrl);
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL is required for provider=" + providerId);
        }
        if ("gemini".equals(providerId) && !baseUrl.contains("/openai")) {
            if (baseUrl.endsWith("/v1beta/openai")) {
                return baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                return baseUrl + "v1beta/openai";
            }
            return baseUrl + "/v1beta/openai";
        }
        if ("minimax-cn".equals(providerId) && baseUrl.endsWith("/anthropic")) {
            return baseUrl.substring(0, baseUrl.length() - "/anthropic".length()) + "/v1";
        }
        return baseUrl;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveAgentPrimaryModelId(AgentDefinitionEntity agent) {
        if (agent == null) {
            return "";
        }
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        String extConfig = trim(agent.getExtConfig());
        if (!extConfig.isBlank()) {
            try {
                JsonNode node = JsonUtil.fromJson(extConfig, JsonNode.class);
                JsonNode modelIdsNode = node == null ? null : node.path("modelIds");
                if (modelIdsNode != null && modelIdsNode.isArray()) {
                    for (JsonNode item : modelIdsNode) {
                        String modelId = item == null ? "" : trim(item.asText(""));
                        if (!modelId.isBlank()) {
                            modelIds.add(modelId);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore malformed ext_config and fallback to model_id column.
            }
        }
        String fallback = trim(agent.getModelId());
        if (!fallback.isBlank()) {
            modelIds.add(fallback);
        }
        return modelIds.stream().findFirst().orElse("");
    }

    public record ResolvedModel(String providerId, String modelId, ChatModel model, StreamingChatModel streamingModel) {
        public boolean supportsStreaming() {
            return streamingModel != null;
        }
    }
}
