ALTER TABLE IF EXISTS agent_definition ADD COLUMN IF NOT EXISTS workspace VARCHAR(1024) NOT NULL DEFAULT '';
UPDATE agent_definition SET workspace = '' WHERE workspace IS NULL;
