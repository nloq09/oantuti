-- A missing scope denotes a platform worker; scopes are immutable after enrollment.
CREATE TABLE worker_token_scopes (
 token_id UUID PRIMARY KEY REFERENCES worker_registration_tokens(id),
 tournament_id UUID NOT NULL REFERENCES tournaments(id)
);
CREATE INDEX worker_token_scopes_tournament ON worker_token_scopes(tournament_id,token_id);
CREATE TABLE worker_tournament_scopes (
 worker_id UUID PRIMARY KEY REFERENCES workers(id),
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX worker_tournament_scopes_tournament ON worker_tournament_scopes(tournament_id,worker_id);
