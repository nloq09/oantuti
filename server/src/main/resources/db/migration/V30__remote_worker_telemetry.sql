ALTER TABLE workers ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workers ADD COLUMN heartbeat_epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workers ADD COLUMN last_heartbeat_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE workers ADD COLUMN remote_address VARCHAR(64);
CREATE TABLE worker_capabilities (
    worker_id UUID PRIMARY KEY REFERENCES workers(id),
    cpu_cores INTEGER NOT NULL CHECK (cpu_cores BETWEEN 1 AND 1024),
    memory_mb BIGINT NOT NULL CHECK (memory_mb BETWEEN 1 AND 1073741824),
    os VARCHAR(80) NOT NULL,
    docker_enabled BOOLEAN NOT NULL,
    supported_languages VARCHAR(32) NOT NULL,
    max_concurrent_matches INTEGER NOT NULL CHECK (max_concurrent_matches BETWEEN 1 AND 256),
    max_bot_processes INTEGER NOT NULL CHECK (max_bot_processes BETWEEN 2 AND 512),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (max_bot_processes >= max_concurrent_matches * 2)
);
-- One latest heartbeat per machine, not an unbounded five-second event history.
CREATE TABLE worker_heartbeats (
    worker_id UUID PRIMARY KEY REFERENCES workers(id),
    heartbeat_epoch BIGINT NOT NULL,
    sequence_number BIGINT NOT NULL CHECK (sequence_number > 0),
    active_matches INTEGER NOT NULL CHECK (active_matches BETWEEN 0 AND 256),
    offered_slots INTEGER NOT NULL CHECK (offered_slots BETWEEN 0 AND 256),
    active_bot_processes INTEGER NOT NULL CHECK (active_bot_processes BETWEEN 0 AND 512),
    cpu_usage DOUBLE PRECISION CHECK (cpu_usage BETWEEN 0 AND 100),
    memory_usage DOUBLE PRECISION CHECK (memory_usage BETWEEN 0 AND 100),
    disk_free_mb BIGINT NOT NULL CHECK (disk_free_mb >= 0),
    cache_size_mb BIGINT NOT NULL CHECK (cache_size_mb >= 0),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE worker_identity_events ADD COLUMN reason VARCHAR(500);
ALTER TABLE worker_identity_events ADD COLUMN before_value TEXT;
ALTER TABLE worker_identity_events ADD COLUMN after_value TEXT;
CREATE INDEX idx_worker_heartbeat_expiry ON workers(last_heartbeat_at,status);
