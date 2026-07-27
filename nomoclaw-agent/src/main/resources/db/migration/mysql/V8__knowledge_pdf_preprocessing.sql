ALTER TABLE knowledge_import_batch
    ADD COLUMN preprocessing_config TEXT NULL AFTER embedding_model_fingerprint;

ALTER TABLE knowledge_document_version
    ADD COLUMN preprocessing_config TEXT NULL AFTER embedding_dimension;
