import type { ConversationMessage, ModelConfig, ModelProviderOption } from "@/types/api";

export function buildModelKey(modelProvider: string, modelName: string) {
  return `${modelProvider}::${modelName}`;
}

export function splitModelKey(value: string) {
  const [modelProvider = "", modelName = ""] = value.split("::");
  return { modelProvider, modelName };
}

export function isProviderConfigured(provider: ModelConfig["providers"][number]) {
  if (!provider.requireApiKey) {
    return Boolean(provider.configured);
  }
  if (provider.local) {
    return Boolean(provider.baseUrl?.trim());
  }
  return Boolean(provider.apiKey?.trim());
}

export function isEmbeddingModel(model: ModelProviderOption) {
  const id = (model.id || "").toLowerCase();
  const name = (model.name || "").toLowerCase();
  return id.includes("embedding") || name.includes("embedding");
}

export function findAgentDefaultModel(
  selectedAgentUid: string,
  allAgents: Array<{ agentUid: string; modelProvider?: string; modelName?: string }>,
  configuredProviders: Array<{ id: string; models: Array<{ id: string }> }>
) {
  const agent = allAgents.find((item) => item.agentUid === selectedAgentUid);
  const matchedAgentProvider = configuredProviders.find((provider) => provider.id === agent?.modelProvider);
  if (agent?.modelProvider && agent?.modelName && matchedAgentProvider?.models.some((model) => model.id === agent.modelName)) {
    return { modelProvider: agent.modelProvider, modelName: agent.modelName };
  }
  const firstProvider = configuredProviders.find((provider) => provider.models.length);
  const firstModel = firstProvider?.models[0];
  return {
    modelProvider: firstProvider?.id || "",
    modelName: firstModel?.id || ""
  };
}

export function findLatestMessageModelSelection(
  sourceMessages: ConversationMessage[],
  configuredProviders: Array<{ id: string; models: Array<{ id: string }> }>
) {
  const latestUserMessage = [...sourceMessages]
    .reverse()
    .find((item) => item.role === "user" && item.provider && item.modelName);
  const latestProvider = latestUserMessage
    ? configuredProviders.find((provider) => provider.id === latestUserMessage.provider)
    : null;
  if (latestUserMessage?.provider && latestUserMessage?.modelName && latestProvider?.models.some((model) => model.id === latestUserMessage.modelName)) {
    return { modelProvider: latestUserMessage.provider, modelName: latestUserMessage.modelName };
  }
  return null;
}
