CREATE TABLE worker_result_receipts (
 execution_id UUID PRIMARY KEY REFERENCES match_execution_attempts(id),
 job_id UUID NOT NULL UNIQUE REFERENCES match_execution_jobs(id),
 match_id UUID NOT NULL UNIQUE REFERENCES tournament_matches(id),
 worker_id UUID NOT NULL REFERENCES workers(id),
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 result_hash VARCHAR(64) NOT NULL CHECK(char_length(result_hash)=64),
 result_json TEXT NOT NULL,
 replay_json TEXT NOT NULL,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL,
 projection_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(projection_status IN ('PENDING','APPLIED','IGNORED')),
 projected_at TIMESTAMP WITH TIME ZONE,
 projection_error VARCHAR(80),
 FOREIGN KEY(job_id,match_id) REFERENCES match_execution_jobs(id,match_id)
);
CREATE INDEX remote_result_projection ON worker_result_receipts(projection_status,received_at);
