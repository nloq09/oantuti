ALTER TABLE tournament_submission_versions ADD COLUMN hidden_result TEXT;
ALTER TABLE tournament_submission_versions ADD COLUMN execution_bot_id UUID REFERENCES bots(id);
ALTER TABLE tournament_submission_versions ADD COLUMN imported_bot_id UUID REFERENCES bots(id);
ALTER TABLE tournament_submission_versions ADD COLUMN runtime_language VARCHAR(16);
ALTER TABLE tournament_submission_versions ADD COLUMN hidden_passed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tournament_submission_versions ADD COLUMN public_passed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tournament_submission_versions ADD COLUMN sample_passed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE tournament_bot_submissions (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID NOT NULL REFERENCES users(id),
 registration_id UUID NOT NULL,
 version_id UUID NOT NULL REFERENCES tournament_submission_versions(id),
 execution_bot_id UUID NOT NULL REFERENCES bots(id),
 artifact_hash VARCHAR(64) NOT NULL,
 source_type VARCHAR(24) NOT NULL CHECK(source_type IN ('PRE_UPLOADED','PLATFORM_CODED')),
 status VARCHAR(24) NOT NULL CHECK(status IN ('SUBMITTED','LOCKED','REJECTED','DISQUALIFIED')),
 submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
 locked_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(tournament_id,user_id),
 CHECK(status<>'LOCKED' OR locked_at IS NOT NULL)
);
CREATE INDEX submission_tournament_status ON tournament_bot_submissions(tournament_id,status);
CREATE TABLE bot_coding_jobs (
 id UUID PRIMARY KEY,
 workspace_id UUID NOT NULL REFERENCES tournament_code_workspaces(id),
 version_id UUID NOT NULL REFERENCES tournament_submission_versions(id),
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('BUILD','TEST','SAMPLE','VALIDATE')),
 status VARCHAR(16) NOT NULL CHECK(status IN ('QUEUED','RUNNING','DONE','FAILED')),
 lease_token UUID,
 lease_until TIMESTAMP WITH TIME ZONE,
 attempts INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 finished_at TIMESTAMP WITH TIME ZONE,
 hidden_required BOOLEAN NOT NULL
);
CREATE INDEX coding_job_dispatch ON bot_coding_jobs(status,created_at);
CREATE TABLE coding_queue_guard(id INTEGER PRIMARY KEY);
INSERT INTO coding_queue_guard(id) VALUES(1);
CREATE TABLE tournament_coding_enrollment(tournament_id UUID PRIMARY KEY REFERENCES tournaments(id));
INSERT INTO tournament_coding_enrollment(tournament_id)
 SELECT tournament_id FROM tournament_bot_policies WHERE submission_mode IN ('CODE_IN_PLATFORM_ONLY','BOTH');
CREATE TABLE bot_submission_audit (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID,
 action VARCHAR(40) NOT NULL,
 version_id UUID,
 reason VARCHAR(400),
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX submission_audit_tournament ON bot_submission_audit(tournament_id,occurred_at);
CREATE TABLE tournament_submission_deadlines(tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),processed_at TIMESTAMP WITH TIME ZONE NOT NULL);
