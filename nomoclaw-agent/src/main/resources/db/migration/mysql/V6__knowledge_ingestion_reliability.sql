ALTER TABLE knowledge_ingestion_job
    ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED' AFTER status,
    ADD COLUMN worker_id VARCHAR(128) NULL AFTER failure_message,
    ADD COLUMN lease_token VARCHAR(64) NULL AFTER worker_id,
    ADD COLUMN last_heartbeat_time DATETIME(3) NULL AFTER lease_until,
    ADD COLUMN next_retry_time DATETIME(3) NULL AFTER last_heartbeat_time,
    ADD COLUMN retryable TINYINT(1) NOT NULL DEFAULT 0 AFTER next_retry_time;

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

CREATE INDEX idx_knowledge_job_claim
    ON knowledge_ingestion_job (status, next_retry_time, lease_until, id);
