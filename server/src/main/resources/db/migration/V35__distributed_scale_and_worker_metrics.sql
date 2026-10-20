ALTER TABLE tournaments DROP CONSTRAINT ck_tournaments_capacity;
ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_capacity CHECK (
 max_participants BETWEEN 2 AND 4000
 AND (max_participants<=32 OR format IN ('SINGLE_ELIMINATION','SWISS'))
);
ALTER TABLE worker_heartbeats ADD COLUMN cache_hits BIGINT NOT NULL DEFAULT 0 CHECK(cache_hits>=0);
ALTER TABLE worker_heartbeats ADD COLUMN cache_misses BIGINT NOT NULL DEFAULT 0 CHECK(cache_misses>=0);
CREATE INDEX remote_attempts_claimed ON match_execution_attempts(claimed_at,id);
CREATE INDEX remote_result_received ON worker_result_receipts(received_at);
