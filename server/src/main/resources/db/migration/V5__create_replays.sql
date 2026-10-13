CREATE TABLE replays (
    id UUID PRIMARY KEY,
    match_id VARCHAR(64) NOT NULL REFERENCES matches(id),
    file_path VARCHAR(1024) NOT NULL,
    event_count INTEGER NOT NULL CHECK (event_count >= 0),
    valid BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_replay_match UNIQUE (match_id)
);
