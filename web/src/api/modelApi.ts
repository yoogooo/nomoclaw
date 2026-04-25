import type { ModelCatalogStatus, ModelConfig, ModelProviderTestResult, TestModelProviderRequest } from "@/types/api";
import { requestJson } from "@/utils/http";

export const modelApi = {
  getModelConfig() {
    return requestJson<ModelConfig>("/api/system/models");
  },
  getAvailableModelConfig() {
    return requestJson<ModelConfig>("/api/system/models/available");
  },
  updateModelConfig(payload: ModelConfig) {
    return requestJson<ModelConfig>("/api/system/models", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  getModelCatalogStatus() {
    return requestJson<ModelCatalogStatus>("/api/system/models/catalog/status");
  },
  refreshModelCatalog() {
    return requestJson<ModelCatalogStatus>("/api/system/models/catalog/refresh", {
      method: "POST"
    });
  },
  loadLocalModels(providerId: string) {
    return requestJson<ModelConfig>(`/api/system/models/providers/${encodeURIComponent(providerId)}/load-local`, {
      method: "POST"
    });
  },
  testProviderConnection(payload: TestModelProviderRequest) {
    return requestJson<ModelProviderTestResult>("/api/system/models/providers/test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  }
};
