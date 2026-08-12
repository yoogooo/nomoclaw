ALTER TABLE llm_trace ADD protocol_type VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE llm_trace ADD request_url VARCHAR(2048) NOT NULL DEFAULT '';
ALTER TABLE llm_trace ADD request_method VARCHAR(16) NOT NULL DEFAULT '';
ALTER TABLE llm_trace ADD request_headers CLOB NULL;
ALTER TABLE llm_trace ADD raw_request_json CLOB NULL;
ALTER TABLE llm_trace ADD response_status INT NULL;
ALTER TABLE llm_trace ADD raw_response_json CLOB NULL;
ALTER TABLE llm_trace ADD raw_stream_events CLOB NULL;
