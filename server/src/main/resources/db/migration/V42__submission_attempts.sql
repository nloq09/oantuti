ALTER TABLE tournament_submission_versions ADD COLUMN pipeline_submission BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE tournament_submission_attempts (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id),
 version_id UUID NOT NULL UNIQUE REFERENCES tournament_submission_versions(id),
 status VARCHAR(24) NOT NULL,
 error_code VARCHAR(64),
 error_detail TEXT,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 finished_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX submission_attempt_user ON tournament_submission_attempts(tournament_id,user_id,created_at);
ALTER TABLE bot_coding_jobs ADD COLUMN submission_attempt_id UUID REFERENCES tournament_submission_attempts(id);
CREATE UNIQUE INDEX coding_job_submission_attempt ON bot_coding_jobs(submission_attempt_id);
