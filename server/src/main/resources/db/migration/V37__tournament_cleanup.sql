ALTER TABLE tournaments ADD COLUMN trashed_at TIMESTAMP WITH TIME ZONE;
-- Reversible garbage collection. Never delete match/replay/bot data.
CREATE TABLE tournament_cleanup (
 tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),
 candidate_at TIMESTAMP WITH TIME ZONE,
 candidate_revision BIGINT NOT NULL DEFAULT 0,
 reason VARCHAR(64),
 trashed_at TIMESTAMP WITH TIME ZONE,
 protected_until TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_tournament_cleanup_candidate ON tournament_cleanup(candidate_at);
