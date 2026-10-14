ALTER TABLE tournaments ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE tournament_audit_events (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    sequence_number BIGINT NOT NULL CHECK (sequence_number > 0),
    event_type VARCHAR(64) NOT NULL,
    actor_user_id UUID REFERENCES users(id),
    target_id VARCHAR(160),
    reason VARCHAR(500),
    before_value TEXT,
    after_value TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tournament_id,sequence_number)
);
CREATE INDEX ix_tournament_audit_order ON tournament_audit_events(tournament_id,sequence_number);
