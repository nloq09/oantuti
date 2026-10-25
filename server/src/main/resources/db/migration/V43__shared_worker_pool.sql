-- Add shared-pool metadata without deleting historical group or route records.
CREATE TABLE worker_pool_policies (
 tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),
 mode VARCHAR(24) NOT NULL CHECK(mode IN ('AUTO','LOCAL','REMOTE_REQUIRED')),
 minimum_trust VARCHAR(16) NOT NULL DEFAULT 'TRUSTED' CHECK(minimum_trust IN ('TRUSTED','COMMUNITY')),
 max_matches INTEGER NOT NULL CHECK(max_matches BETWEEN 1 AND 256),
 revision BIGINT NOT NULL DEFAULT 0
);
INSERT INTO worker_pool_policies(tournament_id,mode,minimum_trust,max_matches,revision)
 SELECT tournament_id,mode,minimum_trust,max_matches,revision FROM tournament_worker_policies;
CREATE TABLE worker_pool_routes (
 job_id UUID PRIMARY KEY REFERENCES match_execution_jobs(id),
 match_attempt INTEGER NOT NULL DEFAULT 1 CHECK(match_attempt>0),
 retry_floor INTEGER NOT NULL DEFAULT 0 CHECK(retry_floor>=0)
);
INSERT INTO worker_pool_routes(job_id,match_attempt,retry_floor)
 SELECT job_id,match_attempt,retry_floor FROM remote_job_routes;
