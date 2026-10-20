CREATE TABLE worker_groups (
 id UUID PRIMARY KEY,
 name VARCHAR(64) NOT NULL UNIQUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE worker_group_members (
 group_id UUID NOT NULL REFERENCES worker_groups(id),
 worker_id UUID NOT NULL REFERENCES workers(id),
 PRIMARY KEY(group_id,worker_id)
);
CREATE TABLE worker_capacity_limits (
 worker_id UUID PRIMARY KEY REFERENCES workers(id),
 max_matches INTEGER NOT NULL CHECK(max_matches BETWEEN 1 AND 256)
);
CREATE TABLE tournament_worker_policies (
 tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),
 mode VARCHAR(24) NOT NULL CHECK(mode IN ('LOCAL','REMOTE_REQUIRED')),
 group_id UUID REFERENCES worker_groups(id),
 minimum_trust VARCHAR(16) NOT NULL DEFAULT 'TRUSTED' CHECK(minimum_trust IN ('TRUSTED','COMMUNITY')),
 max_matches INTEGER NOT NULL CHECK(max_matches BETWEEN 1 AND 256),
 revision BIGINT NOT NULL DEFAULT 0,
 CHECK(mode='LOCAL' OR group_id IS NOT NULL)
);
CREATE TABLE remote_job_routes (
 job_id UUID PRIMARY KEY REFERENCES match_execution_jobs(id),
 group_id UUID NOT NULL REFERENCES worker_groups(id),
 match_attempt INTEGER NOT NULL DEFAULT 1 CHECK(match_attempt>0),
 retry_floor INTEGER NOT NULL DEFAULT 0 CHECK(retry_floor>=0)
);
CREATE INDEX worker_group_members_worker ON worker_group_members(worker_id,group_id);
CREATE INDEX remote_jobs_tournament_status ON match_execution_jobs(tournament_id,status);
