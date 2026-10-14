CREATE TABLE login_sessions (
 id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 user_id uuid NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 token_version bigint NOT NULL,
 token_hash varchar(64) NOT NULL,
 previous_hash varchar(64), rotated_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL, idle_expires_at timestamptz NOT NULL,
 revoked boolean NOT NULL DEFAULT false
);
CREATE INDEX login_sessions_user ON login_sessions(user_id);
CREATE TABLE used_session_tokens (
 token_hash varchar(64) PRIMARY KEY,
 session_id uuid NOT NULL REFERENCES login_sessions(id) ON DELETE CASCADE
);
