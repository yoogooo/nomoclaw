UPDATE llm_provider_model
SET capabilities_json = '{"toolCalling":false,"imageRecognition":' ||
    CASE WHEN capabilities_json LIKE '%"image"%' THEN 'true' ELSE 'false' END ||
    ',"audioRecognition":' || CASE WHEN capabilities_json LIKE '%"audio"%' THEN 'true' ELSE 'false' END ||
    ',"videoRecognition":' || CASE WHEN capabilities_json LIKE '%"video"%' THEN 'true' ELSE 'false' END ||
    ',"reasoning":' || CASE WHEN reasoning = 1 THEN 'true' ELSE 'false' END || '}';

UPDATE llm_provider_model SET model_type = 'TEXT_GENERATION' WHERE model_type = 'CHAT';
UPDATE llm_provider_model SET model_type = 'IMAGE_GENERATION' WHERE model_type = 'IMAGE';
DELETE FROM llm_provider_model WHERE model_type IN ('SPEECH', 'MODERATION');

ALTER TABLE llm_provider_model DROP COLUMN reasoning;
ALTER TABLE llm_provider_model DROP COLUMN upload_policy_json;
