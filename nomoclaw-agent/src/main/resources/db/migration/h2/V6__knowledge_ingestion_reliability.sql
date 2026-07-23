ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS stage VARCHAR(32) DEFAULT 'QUEUED' NOT NULL;
ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS worker_id VARCHAR(128);
ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64);
ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS last_heartbeat_time TIMESTAMP(3);
ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS next_retry_time TIMESTAMP(3);
ALTER TABLE knowledge_ingestion_job ADD COLUMN IF NOT EXISTS retryable INT DEFAULT 0 NOT NULL;

UPDATE knowledge_ingestion_job
SET stage = CASE
                WHEN status = 'COMPLETED' THEN 'PUBLISHING'
                ELSE 'QUEUED'
            END,
    status = CASE WHEN status IN ('COMPLETED', 'FAILED') THEN status ELSE 'PENDING' END,
    worker_id = NULL,
    lease_token = NULL,
    lease_until = NULL,
    last_heartbeat_time = NULL,
    next_retry_time = NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_job_claim
    ON knowledge_ingestion_job(status, next_retry_time, lease_until, id);
