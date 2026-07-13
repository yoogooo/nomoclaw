import { requestJson } from "@/utils/http";

export interface KnowledgeBase { knowledgeBaseUid: string; name: string; description: string; status: string; embeddingProviderId: string; embeddingModelId: string; embeddingDimension: number; vectorCollectionName: string; documentCount: number; chunkCount: number; updatedTime: string; }
export interface KnowledgeDocument { documentUid: string; displayName: string; contentType: string; sizeBytes: number; status: string; failureCode: string; failureMessage: string; pageCount: number; chunkCount: number; jobUid: string; progressPercent: number; updatedTime: string; }
export interface KnowledgeHit { citationId: string; documentName: string; pageFrom?: number; sectionPath: string; excerpt: string; score: number; }

export const knowledgeApi = {
  list() { return requestJson<{ items: KnowledgeBase[]; total: number; vectorAvailable: boolean }>("/api/knowledge-bases/page"); },
  create(payload: { name: string; description: string; embeddingProviderId: string; embeddingModelId: string; embeddingDimension: number }) { return requestJson<KnowledgeBase>("/api/knowledge-bases", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); },
  documents(uid: string) { return requestJson<{ items: KnowledgeDocument[]; total: number }>(`/api/knowledge-bases/${uid}/documents/page`); },
  upload(uid: string, files: File[]) { const body = new FormData(); files.forEach(file => body.append("files", file)); return requestJson<{ documents: KnowledgeDocument[] }>(`/api/knowledge-bases/${uid}/documents`, { method: "POST", body }); },
  retry(uid: string, documentUid: string) { return requestJson<{ status: string }>(`/api/knowledge-bases/${uid}/documents/${documentUid}/retry`, { method: "POST" }); },
  search(uid: string, query: string) { return requestJson<KnowledgeHit[]>(`/api/knowledge-bases/${uid}/search`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ query, topK: 8 }) }); }
};
