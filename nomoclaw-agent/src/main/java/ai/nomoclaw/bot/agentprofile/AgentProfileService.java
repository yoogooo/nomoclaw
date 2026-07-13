package ai.nomoclaw.bot.agentprofile;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.agentprofile.model.CreateAgentParam;
import ai.nomoclaw.bot.agentprofile.model.UpdateAgentBasicInfoParam;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogAgentDto;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogGroupDto;
import ai.nomoclaw.bot.agentprofile.model.AgentDocDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.mcp.McpApplicationService;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.PermissionAppService;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupMemberRepository;
import ai.nomoclaw.bot.store.repository.AgentMcpToolRelationRepository;
import ai.nomoclaw.bot.store.repository.AgentSkillRelationRepository;
import ai.nomoclaw.bot.tip.AgentTipService;
import ai.nomoclaw.bot.tool.AgentToolService;
import ai.nomoclaw.bot.util.JsonUtil;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 基础档案与配置管理应用服务。
 *
 * <p>负责 Agent 的基本资料、文档管理，以及删除清理流程。
 */
@Service
@Slf4j
public class AgentProfileService {

    private static final String DEFAULT_AGENT_PROMPT_RESOURCE_ROOT = "prompts/agents/default";
    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";
    private static final String DEFAULT_AGENT_TYPE = "chat";
    private static final String AGENT_TYPE_CODEX = "codex";
    private static final String VIRTUAL_AGENT_GROUP_UID = "group_short_drama";
    private static final String VIRTUAL_AGENT_GROUP_NAME = "all_agents";
    private static final String VIRTUAL_AGENT_GROUP_DISPLAY_NAME = "All Agent";
    private static final Map<String, String> AGENT_DOC_FILES = new LinkedHashMap<>();
    private static final LinkedHashSet<String> SUPPORTED_AGENT_TYPES = new LinkedHashSet<>();

    static {
        SUPPORTED_AGENT_TYPES.add("coding");
        SUPPORTED_AGENT_TYPES.add(AGENT_TYPE_CODEX);
        SUPPORTED_AGENT_TYPES.add("research");
        SUPPORTED_AGENT_TYPES.add("ops");
        SUPPORTED_AGENT_TYPES.add(DEFAULT_AGENT_TYPE);
        AGENT_DOC_FILES.put("soul", "SOUL.md");
        AGENT_DOC_FILES.put("agent", "AGENT.md");
        AGENT_DOC_FILES.put("memory", "MEMORY.md");
        AGENT_DOC_FILES.put("tools", "TOOLS.md");
        AGENT_DOC_FILES.put("identity", "IDENTITY.md");
        AGENT_DOC_FILES.put("user", "USER.md");
    }

    private final AgentStore store;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentSkillRelationRepository agentSkillRelationRepository;
    private final AgentMcpToolRelationRepository agentMcpToolRelationRepository;
    private final AgentTipService agentTipService;
    private final AgentToolService agentToolService;
    private final McpApplicationService mcpApplicationService;
    private final PermissionAppService permissionAppService;
    private final ExecutionScopeResolver executionScopeResolver;
    private final ConversationAttachmentService conversationAttachmentAppService;
    private final MessageCancellationRegistry cancellationRegistry;
    private final MessageExecutionOrchestrator messageExecutionOrchestrator;

    public AgentProfileService(AgentStore store,
                                  AgentDefinitionRepository agentDefinitionRepository,
                                  AgentGroupMemberRepository agentGroupMemberRepository,
                                  AgentSkillRelationRepository agentSkillRelationRepository,
                                  AgentMcpToolRelationRepository agentMcpToolRelationRepository,
                                  AgentTipService agentTipService,
                                  AgentToolService agentToolService,
                                  McpApplicationService mcpApplicationService,
                                  PermissionAppService permissionAppService,
                                  ExecutionScopeResolver executionScopeResolver,
                                  ConversationAttachmentService conversationAttachmentAppService,
                                  MessageCancellationRegistry cancellationRegistry,
                                  MessageExecutionOrchestrator messageExecutionOrchestrator) {
        this.store = store;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentGroupMemberRepository = agentGroupMemberRepository;
        this.agentSkillRelationRepository = agentSkillRelationRepository;
        this.agentMcpToolRelationRepository = agentMcpToolRelationRepository;
        this.agentTipService = agentTipService;
        this.agentToolService = agentToolService;
        this.mcpApplicationService = mcpApplicationService;
        this.permissionAppService = permissionAppService;
        this.executionScopeResolver = executionScopeResolver;
        this.conversationAttachmentAppService = conversationAttachmentAppService;
        this.cancellationRegistry = cancellationRegistry;
        this.messageExecutionOrchestrator = messageExecutionOrchestrator;
    }

    public List<AgentCatalogGroupDto> listAgentGroups() {
        List<AgentDefinitionEntity> allAgents = agentDefinitionRepository.listAllActive();
        if (allAgents == null || allAgents.isEmpty()) {
            return List.of();
        }
        allAgents.forEach(this::ensureWorkspaceDocsForExistingAgent);

        List<AgentCatalogAgentDto> agentItems = new ArrayList<>();
        for (AgentDefinitionEntity agent : allAgents) {
            String agentUid = agent.getAgentUid();
            AgentGroupMemberEntity member = withDefaultMember(null, VIRTUAL_AGENT_GROUP_UID, agentUid);
            member.setMemberRole(DEFAULT_AGENT_UID.equals(agentUid) ? "owner" : "member");
            member.setResponsibility(DEFAULT_AGENT_UID.equals(agentUid) ? "默认主 Agent" : "");
            member.setIsPrimary(DEFAULT_AGENT_UID.equals(agentUid) ? 1 : 0);
            AgentCatalogAgentDto item = toAgentCatalogItem(member, agent);
            if (item == null) {
                continue;
            }
            agentItems.add(item);
        }

        return List.of(new AgentCatalogGroupDto(
                VIRTUAL_AGENT_GROUP_UID,
                VIRTUAL_AGENT_GROUP_NAME,
                VIRTUAL_AGENT_GROUP_DISPLAY_NAME,
                "🧭",
                "基于 agent_definition 自动聚合",
                List.of("general"),
                "single",
                agentItems
        ));
    }

    public AgentCatalogAgentDto createAgent(CreateAgentParam request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String agentName = request.agentName() == null ? "" : request.agentName().trim();
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (agentDefinitionRepository.findByName(agentName) != null) {
            throw new IllegalArgumentException("agentName already exists: " + agentName);
        }
        List<String> modelIds = sanitizeModelIds(request.modelName(), request.modelNames());
        executionScopeResolver.validateModelSelection(request.modelProvider(), modelIds);

        String avatar = request.avatar() == null ? "" : request.avatar().trim();
        if (avatar.isBlank()) {
            avatar = "bot";
        }
        String avatarColor = request.avatarColor() == null ? "" : request.avatarColor().trim();
        if (avatarColor.isBlank()) {
            avatarColor = "#2F6FED";
        }
        String agentType = normalizeAgentType(request.agentType());

        LocalDateTime now = LocalDateTime.now();
        String agentUid = "agent_" + UuidUtil.newUuid().replace("-", "");
        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid(agentUid);
        agent.setAgentName(agentName);
        agent.setDisplayName(displayName);
        agent.setAvatar(avatar);
        agent.setDescription(request.description() == null ? "" : request.description().trim());
        agent.setCapabilityTags("[]");
        agent.setPromptProfile("");
        agent.setAgentType(agentType);
        agent.setModelProviderId(request.modelProvider().trim());
        agent.setModelId(modelIds.get(0));
        agent.setSortIndex(nextAgentSortIndex());
        agent.setIsGroupEntry(0);
        agent.setStatus("ACTIVE");
        ObjectNode extConfig = JsonNodeFactory.instance.objectNode();
        extConfig.put("avatarColor", avatarColor);
        extConfig.set("modelIds", JsonUtil.fromJson(JsonUtil.toJson(modelIds), JsonNode.class));
        writeCodexWorkdir(extConfig, agentType, request.codexWorkdir());
        AgentWorkspaceConfig workspaceConfig = resolveWorkspaceConfigForMutation(
                agentName,
                "",
                request.workspace()
        ).ensureDirectories();
        agent.setWorkspace(workspaceConfig.workspaceDir().toString());
        agent.setExtConfig(JsonUtil.toJson(extConfig));
        agent.setCreatedTime(now);
        agent.setUpdatedTime(now);
        agentDefinitionRepository.save(agent);
        permissionAppService.syncAgentManagedWorkspaceAllowRule(
                agentUid,
                agentName,
                workspaceConfig.workspaceDir()
        );

        AgentGroupMemberEntity member = new AgentGroupMemberEntity();
        member.setAgentUid(agentUid);
        member.setMemberRole("成员");
        member.setResponsibility("");
        member.setIsPrimary(0);
        agentToolService.initializeAgentToolRelations(agentUid, now);
        mcpApplicationService.initializeAgentToolRelations(agentUid, now);
        initializeAgentWorkspaceDocs(agentName, displayName);
        return toAgentCatalogItem(member, agent);
    }

    public AgentCatalogAgentDto updateAgentBasicInfo(String agentUid, UpdateAgentBasicInfoParam request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalizedAgentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + normalizedAgentUid);
        }

        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }

        String avatar = request.avatar() == null ? "" : request.avatar().trim();
        if (avatar.isBlank()) {
            avatar = "bot";
        }
        String avatarColor = request.avatarColor() == null ? "" : request.avatarColor().trim();
        if (avatarColor.isBlank()) {
            avatarColor = "#2F6FED";
        }
        String agentType = normalizeAgentType(request.agentType());
        List<String> modelIds = sanitizeModelIds(request.modelName(), request.modelNames());
        executionScopeResolver.validateModelSelection(request.modelProvider(), modelIds);

        agent.setDisplayName(displayName);
        agent.setDescription(request.description() == null ? "" : request.description().trim());
        agent.setAvatar(avatar);
        agent.setAgentType(agentType);
        agent.setModelProviderId(request.modelProvider().trim());
        agent.setModelId(modelIds.get(0));
        agent.setUpdatedTime(LocalDateTime.now());
        ObjectNode extConfig = readExtConfigObject(agent.getExtConfig());
        extConfig.put("avatarColor", avatarColor);
        extConfig.set("modelIds", JsonUtil.fromJson(JsonUtil.toJson(modelIds), JsonNode.class));
        writeCodexWorkdir(extConfig, agentType, request.codexWorkdir());
        AgentWorkspaceConfig workspaceConfig = resolveWorkspaceConfigForMutation(
                agent.getAgentName(),
                agent.getWorkspace(),
                request.workspace()
        ).ensureDirectories();
        agent.setWorkspace(workspaceConfig.workspaceDir().toString());
        agent.setExtConfig(JsonUtil.toJson(extConfig));
        agentDefinitionRepository.updateById(agent);
        permissionAppService.syncAgentManagedWorkspaceAllowRule(
                agent.getAgentUid(),
                agent.getAgentName(),
                workspaceConfig.workspaceDir()
        );

        AgentGroupMemberEntity member = agentGroupMemberRepository.findPrimaryByAgentUid(agent.getAgentUid());
        if (member == null) {
            member = new AgentGroupMemberEntity();
            member.setMemberRole("成员");
            member.setResponsibility("");
            member.setIsPrimary(0);
        }
        return toAgentCatalogItem(member, agent);
    }

    public List<AgentDocDto> listAgentDocs(String agentUid) {
        AgentDefinitionEntity agent = requireAgentByUid(agentUid);
        ensureWorkspaceDocsForExistingAgent(agent);
        Path workspace = NomoClawPaths.ensureAgentHome(agent.getAgentName());
        List<AgentDocDto> docs = new ArrayList<>();
        for (Map.Entry<String, String> entry : AGENT_DOC_FILES.entrySet()) {
            docs.add(readAgentDoc(workspace, entry.getKey(), entry.getValue()));
        }
        return docs;
    }

    public AgentDocDto updateAgentDoc(String agentUid, String docKey, String content) {
        AgentDefinitionEntity agent = requireAgentByUid(agentUid);
        ensureWorkspaceDocsForExistingAgent(agent);
        String normalizedDocKey = docKey == null ? "" : docKey.trim().toLowerCase();
        String fileName = AGENT_DOC_FILES.get(normalizedDocKey);
        if (fileName == null) {
            throw new IllegalArgumentException("unsupported doc key: " + normalizedDocKey);
        }
        Path workspace = NomoClawPaths.ensureAgentHome(agent.getAgentName());
        Path file = workspace.resolve(fileName).toAbsolutePath().normalize();
        try {
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save doc file: " + file, ex);
        }
        return readAgentDoc(workspace, normalizedDocKey, fileName);
    }

    public void deleteAgent(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        if (DEFAULT_AGENT_UID.equals(normalizedAgentUid)) {
            throw new IllegalArgumentException("default agent cannot be deleted");
        }
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalizedAgentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + normalizedAgentUid);
        }

        List<String> conversationUids = store.listConversations().stream()
                .filter(conversation -> normalizedAgentUid.equals(conversation.agentUid()))
                .map(AgentConversation::conversationUid)
                .toList();
        for (String conversationUid : conversationUids) {
            deleteConversation(conversationUid);
        }

        agentTipService.purgeAgentTips(normalizedAgentUid);
        agentSkillRelationRepository.deleteByAgentUid(normalizedAgentUid);
        agentToolService.purgeAgentTools(normalizedAgentUid);
        agentMcpToolRelationRepository.deleteByAgentUid(normalizedAgentUid);
        agentGroupMemberRepository.deleteByAgentUid(normalizedAgentUid);
        agentDefinitionRepository.deleteByAgentUid(normalizedAgentUid);
        permissionAppService.removeAgentManagedWorkspaceRules(agent.getAgentUid(), agent.getAgentName());
        log.info("[Agent] deleted agentUid={} agentName={} conversations={} workspaceRetained=true",
                normalizedAgentUid,
                agent.getAgentName(),
                conversationUids.size());
    }

    private void deleteConversation(String conversationUid) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        messages.forEach(message -> {
            cancellationRegistry.cancel(message.messageUid());
            messageExecutionOrchestrator.clearRuntime(message.messageUid());
        });
        conversationAttachmentAppService.purgeConversationAttachments(conversationUid);
        store.deleteConversation(conversationUid);
        log.info("[Agent] conversation deleted conversationUid={} agentGroupUid={} agentUid={}",
                conversationUid, conversation.agentGroupUid(), conversation.agentUid());
    }

    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }

    private AgentCatalogAgentDto toAgentCatalogItem(AgentGroupMemberEntity member, AgentDefinitionEntity agent) {
        if (agent == null) {
            return null;
        }
        AgentWorkspaceConfig workspaceConfig = resolveWorkspaceConfig(agent);
        String avatarColor = readAvatarColor(agent.getExtConfig());
        List<String> modelIds = readModelIds(agent.getExtConfig(), agent.getModelId());
        return new AgentCatalogAgentDto(
                agent.getAgentUid(),
                agent.getAgentName(),
                agent.getDisplayName(),
                readAgentType(agent),
                agent.getAvatar(),
                avatarColor,
                agent.getDescription(),
                nullToEmpty(agent.getModelProviderId()),
                nullToEmpty(agent.getModelId()),
                modelIds,
                workspaceConfig.workspaceDir().toString(),
                readCodexWorkdir(agent.getExtConfig()),
                workspaceConfig.reportDir().toString(),
                workspaceConfig.tmpDir().toString(),
                agent.getSortIndex() == null ? 0 : agent.getSortIndex(),
                readStringArray(agent.getCapabilityTags()),
                member.getMemberRole(),
                member.getResponsibility(),
                member.getIsPrimary() != null && member.getIsPrimary() == 1
        );
    }

    private AgentGroupMemberEntity withDefaultMember(AgentGroupMemberEntity member, String groupUid, String agentUid) {
        if (member != null) {
            return member;
        }
        AgentGroupMemberEntity fallback = new AgentGroupMemberEntity();
        fallback.setAgentGroupUid(groupUid == null ? "" : groupUid);
        fallback.setAgentUid(agentUid == null ? "" : agentUid);
        fallback.setMemberRole("成员");
        fallback.setResponsibility("");
        fallback.setIsPrimary(0);
        return fallback;
    }

    private ObjectNode readExtConfigObject(String extConfigRaw) {
        if (extConfigRaw == null || extConfigRaw.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode node = JsonUtil.fromJson(extConfigRaw, JsonNode.class);
            if (node != null && node.isObject()) {
                return (ObjectNode) node.deepCopy();
            }
        } catch (Exception ex) {
            log.warn("[Agent] failed to parse ext config json={}", summarize(extConfigRaw), ex);
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private String readAvatarColor(String extConfigRaw) {
        ObjectNode node = readExtConfigObject(extConfigRaw);
        String color = node.path("avatarColor").asString("");
        return color == null || color.isBlank() ? "#2F6FED" : color;
    }

    private String readAgentType(AgentDefinitionEntity agent) {
        if (agent == null) {
            return DEFAULT_AGENT_TYPE;
        }
        return normalizeAgentType(agent.getAgentType());
    }

    private String normalizeAgentType(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase();
        if (normalized.isBlank()) {
            return DEFAULT_AGENT_TYPE;
        }
        if (!SUPPORTED_AGENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported agentType: " + rawValue);
        }
        return normalized;
    }

    private void writeCodexWorkdir(ObjectNode extConfig, String agentType, String codexWorkdirRaw) {
        if (AGENT_TYPE_CODEX.equals(agentType)) {
            Path codexWorkdir = parseAbsolutePathOrDefault(codexWorkdirRaw, null, "codexWorkdir");
            if (codexWorkdir != null) {
                extConfig.put("codexWorkdir", codexWorkdir.toString());
                return;
            }
        }
        extConfig.remove("codexWorkdir");
    }

    private String readCodexWorkdir(String extConfigRaw) {
        ObjectNode node = readExtConfigObject(extConfigRaw);
        String workdir = node.path("codexWorkdir").asString("");
        return workdir == null ? "" : workdir.trim();
    }

    private List<String> sanitizeModelIds(String modelName, List<String> modelNames) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String primary = modelName == null ? "" : modelName.trim();
        if (!primary.isBlank()) {
            values.add(primary);
        }
        if (modelNames != null) {
            for (String modelId : modelNames) {
                String normalized = modelId == null ? "" : modelId.trim();
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least one model must be selected");
        }
        return List.copyOf(values);
    }

    private List<String> readModelIds(String extConfigRaw, String fallbackModelId) {
        ObjectNode ext = readExtConfigObject(extConfigRaw);
        JsonNode modelIdsNode = ext.path("modelIds");
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        if (modelIdsNode.isArray()) {
            for (JsonNode node : modelIdsNode) {
                String modelId = node == null ? "" : node.asString("");
                if (modelId != null && !modelId.isBlank()) {
                    modelIds.add(modelId.trim());
                }
            }
        }
        String fallback = fallbackModelId == null ? "" : fallbackModelId.trim();
        if (!fallback.isBlank()) {
            modelIds.add(fallback);
        }
        return List.copyOf(modelIds);
    }

    private AgentWorkspaceConfig resolveWorkspaceConfig(AgentDefinitionEntity agent) {
        if (agent == null) {
            return AgentWorkspaceConfig.defaults(NomoClawPaths.DEFAULT_AGENT_NAME).ensureDirectories();
        }
        return AgentWorkspaceConfig.resolve(agent.getAgentName(), agent.getWorkspace()).ensureDirectories();
    }

    private AgentWorkspaceConfig resolveWorkspaceConfigForMutation(String agentName,
                                                                   String currentWorkspaceRaw,
                                                                   String workspaceRaw) {
        AgentWorkspaceConfig current = AgentWorkspaceConfig.resolve(agentName, currentWorkspaceRaw);
        Path workspace = parseAbsolutePathOrDefault(workspaceRaw, current.workspaceDir(), "workspace");
        return AgentWorkspaceConfig.fromWorkspacePath(workspace);
    }

    private Path parseAbsolutePathOrDefault(String rawValue, Path fallback, String fieldName) {
        if (rawValue == null || rawValue.trim().isBlank()) {
            return fallback == null ? null : fallback.toAbsolutePath().normalize();
        }
        String trimmed = rawValue.trim();
        try {
            Path parsed = Path.of(trimmed);
            if (!parsed.isAbsolute()) {
                throw new IllegalArgumentException(fieldName + " must be an absolute path: " + trimmed);
            }
            return parsed.toAbsolutePath().normalize();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid " + fieldName + ": " + trimmed, ex);
        }
    }

    private int nextAgentSortIndex() {
        return agentDefinitionRepository.listAllActive().stream()
                .map(AgentDefinitionEntity::getSortIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    private void initializeAgentWorkspaceDocs(String agentName, String displayName) {
        Path workspace = NomoClawPaths.ensureAgentHome(agentName);
        Map<String, String> defaults = loadDefaultAgentPromptTemplates();

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            Path file = workspace.resolve(entry.getKey());
            if (Files.exists(file)) {
                continue;
            }
            try {
                Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to initialize agent doc: " + file, ex);
            }
        }
        Path legacyAgentsDoc = workspace.resolve("AGENTS.md");
        try {
            Files.deleteIfExists(legacyAgentsDoc);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to cleanup legacy agent doc: " + legacyAgentsDoc, ex);
        }
    }

    private Map<String, String> loadDefaultAgentPromptTemplates() {
        LocaleContextHolder.getLocale();
        String language = normalizeLanguage(LocaleContextHolder.getLocale().getLanguage());
        List<String> candidates = "en".equals(language)
                ? List.of("en", "zh")
                : List.of("zh", "en");
        for (String candidate : candidates) {
            Map<String, String> loaded = loadDefaultAgentPromptTemplates(candidate);
            if (!loaded.isEmpty()) {
                return loaded;
            }
        }
        throw new IllegalStateException("default agent prompt templates not found under classpath: " + DEFAULT_AGENT_PROMPT_RESOURCE_ROOT);
    }

    private Map<String, String> loadDefaultAgentPromptTemplates(String language) {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (String fileName : AGENT_DOC_FILES.values()) {
            String content = readClasspathPrompt(language, fileName);
            if (content == null) {
                return Map.of();
            }
            defaults.put(fileName, content);
        }
        return defaults;
    }

    private String readClasspathPrompt(String language, String fileName) {
        String resourcePath = DEFAULT_AGENT_PROMPT_RESOURCE_ROOT + "/" + language + "/" + fileName;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load prompt template from classpath: " + resourcePath, ex);
        }
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        return "en".equalsIgnoreCase(language.trim()) ? "en" : "zh";
    }

    private AgentDefinitionEntity requireAgentByUid(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        AgentDefinitionEntity agent = agentDefinitionRepository.findByUid(normalizedAgentUid);
        if (agent == null) {
            throw new IllegalArgumentException("agent not found: " + normalizedAgentUid);
        }
        return agent;
    }

    private AgentDocDto readAgentDoc(Path workspace, String key, String fileName) {
        Path file = workspace.resolve(fileName).toAbsolutePath().normalize();
        String content;
        LocalDateTime updatedTime;
        try {
            content = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            updatedTime = Files.exists(file)
                    ? LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault())
                    : LocalDateTime.now();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read doc file: " + file, ex);
        }
        return new AgentDocDto(key, fileName, content, updatedTime);
    }

    private void ensureWorkspaceDocsForExistingAgent(AgentDefinitionEntity agent) {
        if (agent == null) {
            return;
        }
        resolveWorkspaceConfig(agent);
        initializeAgentWorkspaceDocs(agent.getAgentName(), agent.getDisplayName());
    }

    private List<String> readStringArray(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = JsonUtil.fromJson(rawJson, JsonNode.class);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (item != null && item.isTextual()) {
                    values.add(item.asString());
                }
            });
            return values;
        } catch (Exception ex) {
            log.warn("[Agent] failed to parse string array json={}", summarize(rawJson), ex);
            return List.of();
        }
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        int limit = 2000;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
