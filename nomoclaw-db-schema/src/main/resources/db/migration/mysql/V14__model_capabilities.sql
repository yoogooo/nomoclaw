UPDATE llm_provider_model
SET capabilities_json = JSON_OBJECT(
        'toolCalling', FALSE,
        'imageRecognition', JSON_CONTAINS(capabilities_json, JSON_QUOTE('image')),
        'audioRecognition', JSON_CONTAINS(capabilities_json, JSON_QUOTE('audio')),
        'videoRecognition', JSON_CONTAINS(capabilities_json, JSON_QUOTE('video')),
        'reasoning', reasoning = 1
    );

UPDATE llm_provider_model SET model_type = 'TEXT_GENERATION' WHERE model_type = 'CHAT';
UPDATE llm_provider_model SET model_type = 'IMAGE_GENERATION' WHERE model_type = 'IMAGE';
DELETE FROM llm_provider_model WHERE model_type IN ('SPEECH', 'MODERATION');

ALTER TABLE llm_provider_model DROP COLUMN reasoning;
ALTER TABLE llm_provider_model DROP COLUMN upload_policy_json;
