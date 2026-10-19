CREATE TABLE remote_match_queue_guard (id INTEGER PRIMARY KEY CHECK (id=1));
INSERT INTO remote_match_queue_guard(id) VALUES(1);
CREATE TABLE match_execution_jobs (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL UNIQUE REFERENCES tournament_matches(id),
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','LEASED','DONE','CANCELLED')),
    spec_json TEXT NOT NULL,
    spec_hash VARCHAR(64) NOT NULL,
    language_a VARCHAR(8) NOT NULL,
    language_b VARCHAR(8) NOT NULL,
    game_core_version VARCHAR(64) NOT NULL,
    rule_set_version VARCHAR(64) NOT NULL,
    minimum_trust VARCHAR(16) NOT NULL CHECK (minimum_trust IN ('TRUSTED','COMMUNITY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(id,match_id)
);
CREATE TABLE match_execution_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES match_execution_jobs(id),
    match_id UUID NOT NULL REFERENCES tournament_matches(id),
    worker_id UUID NOT NULL REFERENCES workers(id),
    heartbeat_epoch BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CLAIMED','RUNNING','DECLINED','DONE','EXPIRED')),
    active_marker INTEGER CHECK (active_marker IS NULL OR active_marker=1),
    active_slot INTEGER CHECK (active_slot IS NULL OR active_slot BETWEEN 1 AND 256),
    assigned_slot INTEGER NOT NULL CHECK (assigned_slot BETWEEN 1 AND 256),
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE NOT NULL,
    declined_at TIMESTAMP WITH TIME ZONE,
    decline_reason VARCHAR(80),
    UNIQUE(match_id,active_marker),
    UNIQUE(worker_id,active_slot),
    FOREIGN KEY(job_id,match_id) REFERENCES match_execution_jobs(id,match_id),
    CHECK ((status IN ('CLAIMED','RUNNING') AND active_marker IS NOT NULL AND active_marker=1 AND active_slot IS NOT NULL)
        OR (status IN ('DECLINED','DONE','EXPIRED') AND active_marker IS NULL AND active_slot IS NULL))
);
CREATE TABLE worker_claim_receipts (
    worker_id UUID NOT NULL REFERENCES workers(id),
    request_id UUID NOT NULL,
    heartbeat_epoch BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(worker_id,request_id)
);
CREATE TABLE worker_claim_items (
    worker_id UUID NOT NULL,
    request_id UUID NOT NULL,
    execution_id UUID NOT NULL UNIQUE REFERENCES match_execution_attempts(id),
    position INTEGER NOT NULL,
    PRIMARY KEY(worker_id,request_id,position),
    FOREIGN KEY(worker_id,request_id) REFERENCES worker_claim_receipts(worker_id,request_id)
);
CREATE INDEX idx_remote_jobs_fifo ON match_execution_jobs(status,created_at,id);
CREATE INDEX idx_remote_worker_attempts ON match_execution_attempts(worker_id,status);
