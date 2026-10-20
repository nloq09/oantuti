ALTER TABLE match_execution_attempts ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
UPDATE match_execution_attempts SET started_at=claimed_at WHERE status IN ('RUNNING','DONE');
CREATE TABLE worker_execution_recoveries (
 execution_id UUID PRIMARY KEY REFERENCES match_execution_attempts(id),
 job_id UUID NOT NULL REFERENCES match_execution_jobs(id),
 match_id UUID NOT NULL REFERENCES tournament_matches(id),
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 reason VARCHAR(40) NOT NULL CHECK(reason IN ('LEASE_EXPIRED','WORKER_RESTART','MATCH_NOT_PENDING')),
 attempt_count INTEGER NOT NULL CHECK(attempt_count>0),
 terminal BOOLEAN NOT NULL,
 recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
 projected_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX remote_lease_expiry ON match_execution_attempts(status,lease_until);
CREATE INDEX remote_recovery_projection ON worker_execution_recoveries(terminal,projected_at);
