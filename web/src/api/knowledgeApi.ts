import { requestJson } from "@/utils/http";

export interface KnowledgeBase { knowledgeBaseUid: string; name: string; description: string; status: string; embeddingProviderId: string; embeddingModelId: string; embeddingDimension: number; vectorCollectionName: string; documentCount: number; chunkCount: number; updatedTime: string; }
export interface KnowledgeDocument { documentUid: string; importBatchUid: string; displayName: string; contentType: string; sizeBytes: number; status: string; failureCode: string; failureMessage: string; pageCount: number; chunkCount: number; jobUid: string; jobStatus: string; stage: string; progressPercent: number; attemptCount: number; maxAttempts: number; processedChunks: number; totalChunks: number; processedPages: number; totalPages: number; cacheHitChunks: number; cacheMissChunks: number; parseWarnings: string[]; nextRetryTime?: string | null; retryable: boolean; updatedTime: string; }
export type KnowledgeUploadOutcome = "ACCEPTED" | "DUPLICATE" | "REJECTED" | "FAILED";
export interface KnowledgeUploadItem { fileName: string; outcome: KnowledgeUploadOutcome; document?: KnowledgeDocument | null; errorCode: string; errorMessage: string; }
export interface KnowledgeUploadResult { batchUid: string; documents: KnowledgeDocument[]; items: KnowledgeUploadItem[]; }
export interface KnowledgeImportItem { itemUid: string; fileName: string; mode: "UPLOAD" | "REINDEX"; outcome: KnowledgeUploadOutcome; status: string; document?: KnowledgeDocument | null; errorCode: string; errorMessage: string; }
export interface KnowledgePreprocessingConfig { enabled: boolean; pdf: { removeHeader: boolean; removeFooter: boolean; removeWatermark: boolean; }; }
export interface KnowledgeImportBatch { batchUid: string; knowledgeBaseUid: string; status: "DRAFT" | "BUILDING" | "COMPLETED" | "CANCELLED"; parserMode: string; chunkSizeTokens: number; chunkOverlapTokens: number; embeddingProviderId: string; embeddingModelId: string; embeddingDimension: number; preprocessing: KnowledgePreprocessingConfig; items: KnowledgeImportItem[]; createdTime: string; updatedTime: string; }
export interface KnowledgeHit { citationId: string; documentName: string; pageFrom?: number; sectionPath: string; excerpt: string; score: number; rrfScore?: number | null; denseScore?: number | null; bm25Score?: number | null; rerankScore?: number | null; retrievalSources: string[]; }
export interface KnowledgeSearchDiagnostic { code: string; }
export interface KnowledgeSearchResponse { hits: KnowledgeHit[]; diagnostics: KnowledgeSearchDiagnostic[]; }
export interface KnowledgeChunk { chunkUid: string; chunkIndex: number; content: string; tokenCount: number; pageFrom?: number | null; pageTo?: number | null; sectionPath: string; charStart?: number | null; charEnd?: number | null; status: string; }

export const knowledgeApi = {
  list() { return requestJson<{ items: KnowledgeBase[]; total: number; vectorAvailable: boolean }>("/api/knowledge-bases/page"); },
  create(payload: { name: string; description: string; embeddingProviderId: string; embeddingModelId: string; embeddingDimension: number }) { return requestJson<KnowledgeBase>("/api/knowledge-bases", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); },
  documents(uid: string) { return requestJson<{ items: KnowledgeDocument[]; total: number }>(`/api/knowledge-bases/${uid}/documents/page`); },
  document(uid: string, documentUid: string) { return requestJson<KnowledgeDocument>(`/api/knowledge-bases/${uid}/documents/${documentUid}`); },
  chunks(uid: string, documentUid: string) { return requestJson<{ items: KnowledgeChunk[]; total: number }>(`/api/knowledge-bases/${uid}/documents/${documentUid}/chunks`); },
  upload(uid: string, files: File[]) { const body = new FormData(); files.forEach(file => body.append("files", file)); return requestJson<KnowledgeUploadResult>(`/api/knowledge-bases/${uid}/documents`, { method: "POST", body }); },
  createImport(uid: string) { return requestJson<KnowledgeImportBatch>(`/api/knowledge-bases/${uid}/imports`, { method: "POST" }); },
  importBatch(uid: string, batchUid: string) { return requestJson<KnowledgeImportBatch>(`/api/knowledge-bases/${uid}/imports/${batchUid}`); },
  addImportFiles(uid: string, batchUid: string, files: File[]) { const body = new FormData(); files.forEach(file => body.append("files", file)); return requestJson<KnowledgeUploadResult>(`/api/knowledge-bases/${uid}/imports/${batchUid}/files`, { method: "POST", body }); },
  buildImport(uid: string, batchUid: string, payload: { parserMode: string; chunkSizeTokens: number; chunkOverlapTokens: number; preprocessing: KnowledgePreprocessingConfig }) { return requestJson<KnowledgeImportBatch>(`/api/knowledge-bases/${uid}/imports/${batchUid}/build`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); },
  cancelImport(uid: string, batchUid: string) { return requestJson<{ status: string }>(`/api/knowledge-bases/${uid}/imports/${batchUid}`, { method: "DELETE" }); },
  deleteUploadedDocument(uid: string, documentUid: string) { return requestJson<{ status: string }>(`/api/knowledge-bases/${uid}/documents/${documentUid}`, { method: "DELETE" }); },
  reindexSession(uid: string, documentUid: string) { return requestJson<KnowledgeImportBatch>(`/api/knowledge-bases/${uid}/documents/${documentUid}/reindex-session`, { method: "POST" }); },
  retry(uid: string, documentUid: string) { return requestJson<{ status: string }>(`/api/knowledge-bases/${uid}/documents/${documentUid}/retry`, { method: "POST" }); },
  search(uid: string, query: string) { return requestJson<KnowledgeSearchResponse>(`/api/knowledge-bases/${uid}/search`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ query, topK: 8 }) }); }
};
