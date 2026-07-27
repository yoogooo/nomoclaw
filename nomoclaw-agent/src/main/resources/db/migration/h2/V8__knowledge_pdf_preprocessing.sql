ALTER TABLE knowledge_import_batch ADD COLUMN IF NOT EXISTS preprocessing_config CLOB;
ALTER TABLE knowledge_document_version ADD COLUMN IF NOT EXISTS preprocessing_config CLOB;
