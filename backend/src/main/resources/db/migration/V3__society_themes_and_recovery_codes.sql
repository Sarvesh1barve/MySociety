ALTER TABLE societies ADD COLUMN theme text NOT NULL DEFAULT 'FOREST';
ALTER TABLE societies ADD CONSTRAINT society_theme_valid CHECK (theme IN ('FOREST','OCEAN','INDIGO','ROSE','AMBER','SLATE'));
CREATE TABLE recovery_codes (
  id uuid PRIMARY KEY,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL,
  user_id uuid NOT NULL REFERENCES accounts(id),
  code_hash text NOT NULL,
  used_at timestamptz,
  UNIQUE(user_id, code_hash)
);
CREATE INDEX recovery_codes_user_idx ON recovery_codes(user_id);
