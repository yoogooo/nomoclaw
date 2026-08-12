ALTER TABLE llm_trace
    ADD COLUMN protocol_type VARCHAR(32) NOT NULL DEFAULT '' AFTER request_metadata,
    ADD COLUMN request_url VARCHAR(2048) NOT NULL DEFAULT '' AFTER protocol_type,
    ADD COLUMN request_method VARCHAR(16) NOT NULL DEFAULT '' AFTER request_url,
    ADD COLUMN request_headers LONGTEXT NULL AFTER request_method,
    ADD COLUMN raw_request_json LONGTEXT NULL AFTER request_headers,
    ADD COLUMN response_status INT NULL AFTER raw_request_json,
    ADD COLUMN raw_response_json LONGTEXT NULL AFTER response_status,
    ADD COLUMN raw_stream_events LONGTEXT NULL AFTER raw_response_json;
