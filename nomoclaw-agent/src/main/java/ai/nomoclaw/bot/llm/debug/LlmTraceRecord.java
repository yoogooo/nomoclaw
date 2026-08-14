package ai.nomoclaw.bot.llm.debug;

import java.time.LocalDateTime;

/**
 * One persisted provider request/response trace.
 */
public class LlmTraceRecord {
    private String traceUid;
    private String conversationUid;
    private String messageUid;
    private String requestUid;
    private String scene;
    private Integer roundIndex;
    private Integer attemptIndex;
    private String provider;
    private String modelName;
    private String status;
    private LocalDateTime requestStartedTime;
    private LocalDateTime responseFinishedTime;
    private Long latencyMs;
    private String systemPrompt;
    private String requestMessages;
    private String toolSpecifications;
    private String toolChoice;
    private String requestMetadata;
    private String protocolType;
    private String requestUrl;
    private String requestMethod;
    private String requestHeaders;
    private String rawRequestJson;
    private Integer responseStatus;
    private String rawResponseJson;
    private String rawStreamEvents;
    private String responseContent;
    private String responseThinking;
    private String responseToolCalls;
    private String finishReason;
    private Integer inputTokens;
    private Integer cachedInputTokens;
    private Integer outputTokens;
    private Integer reasoningTokens;
    private Integer totalTokens;
    private Boolean usageAvailable;
    private String errorType;
    private String errorMessage;

    public String getTraceUid() { return traceUid; }
    public void setTraceUid(String traceUid) { this.traceUid = traceUid; }
    public String getConversationUid() { return conversationUid; }
    public void setConversationUid(String conversationUid) { this.conversationUid = conversationUid; }
    public String getMessageUid() { return messageUid; }
    public void setMessageUid(String messageUid) { this.messageUid = messageUid; }
    public String getRequestUid() { return requestUid; }
    public void setRequestUid(String requestUid) { this.requestUid = requestUid; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Integer getRoundIndex() { return roundIndex; }
    public void setRoundIndex(Integer roundIndex) { this.roundIndex = roundIndex; }
    public Integer getAttemptIndex() { return attemptIndex; }
    public void setAttemptIndex(Integer attemptIndex) { this.attemptIndex = attemptIndex; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRequestStartedTime() { return requestStartedTime; }
    public void setRequestStartedTime(LocalDateTime requestStartedTime) { this.requestStartedTime = requestStartedTime; }
    public LocalDateTime getResponseFinishedTime() { return responseFinishedTime; }
    public void setResponseFinishedTime(LocalDateTime responseFinishedTime) { this.responseFinishedTime = responseFinishedTime; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getRequestMessages() { return requestMessages; }
    public void setRequestMessages(String requestMessages) { this.requestMessages = requestMessages; }
    public String getToolSpecifications() { return toolSpecifications; }
    public void setToolSpecifications(String toolSpecifications) { this.toolSpecifications = toolSpecifications; }
    public String getToolChoice() { return toolChoice; }
    public void setToolChoice(String toolChoice) { this.toolChoice = toolChoice; }
    public String getRequestMetadata() { return requestMetadata; }
    public void setRequestMetadata(String requestMetadata) { this.requestMetadata = requestMetadata; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public String getRawRequestJson() { return rawRequestJson; }
    public void setRawRequestJson(String rawRequestJson) { this.rawRequestJson = rawRequestJson; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getRawResponseJson() { return rawResponseJson; }
    public void setRawResponseJson(String rawResponseJson) { this.rawResponseJson = rawResponseJson; }
    public String getRawStreamEvents() { return rawStreamEvents; }
    public void setRawStreamEvents(String rawStreamEvents) { this.rawStreamEvents = rawStreamEvents; }
    public String getResponseContent() { return responseContent; }
    public void setResponseContent(String responseContent) { this.responseContent = responseContent; }
    public String getResponseThinking() { return responseThinking; }
    public void setResponseThinking(String responseThinking) { this.responseThinking = responseThinking; }
    public String getResponseToolCalls() { return responseToolCalls; }
    public void setResponseToolCalls(String responseToolCalls) { this.responseToolCalls = responseToolCalls; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getCachedInputTokens() { return cachedInputTokens; }
    public void setCachedInputTokens(Integer cachedInputTokens) { this.cachedInputTokens = cachedInputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getReasoningTokens() { return reasoningTokens; }
    public void setReasoningTokens(Integer reasoningTokens) { this.reasoningTokens = reasoningTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Boolean getUsageAvailable() { return usageAvailable; }
    public void setUsageAvailable(Boolean usageAvailable) { this.usageAvailable = usageAvailable; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
