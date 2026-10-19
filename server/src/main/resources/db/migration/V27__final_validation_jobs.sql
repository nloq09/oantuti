CREATE TABLE tournament_final_validations (
 id UUID PRIMARY KEY,
 freeze_id UUID NOT NULL UNIQUE REFERENCES tournament_coding_freezes(id),
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID NOT NULL REFERENCES users(id),
 version_id UUID NOT NULL REFERENCES tournament_submission_versions(id),
 artifact_hash VARCHAR(64) NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('QUEUED','RUNNING','PASSED','FAILED','INFRA_ERROR')),
 failure_policy VARCHAR(40) NOT NULL CHECK(failure_policy IN ('DISQUALIFY','USE_LAST_VALID_VERSION','ALLOW_EMERGENCY_RESUBMISSION')),
 steps_json TEXT NOT NULL,
 history_json TEXT NOT NULL,
 reason VARCHAR(80),
 job_id UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 finished_at TIMESTAMP WITH TIME ZONE,
 applied_at TIMESTAMP WITH TIME ZONE,
 emergency_open_at TIMESTAMP WITH TIME ZONE,
 emergency_deadline TIMESTAMP WITH TIME ZONE,
 emergency_actor UUID REFERENCES users(id),
 emergency_reason VARCHAR(400),
 CHECK((emergency_deadline IS NULL AND emergency_open_at IS NULL AND emergency_actor IS NULL AND emergency_reason IS NULL)
    OR (emergency_deadline IS NOT NULL AND emergency_open_at IS NOT NULL AND emergency_actor IS NOT NULL AND emergency_reason IS NOT NULL AND emergency_open_at<emergency_deadline))
);
CREATE INDEX final_validation_pending ON tournament_final_validations(tournament_id,status,applied_at);
ALTER TABLE bot_coding_jobs ADD COLUMN final_validation_id UUID REFERENCES tournament_final_validations(id);
ALTER TABLE bot_coding_jobs ADD CONSTRAINT final_job_reference CHECK(final_validation_id IS NULL OR kind='VALIDATE');
ALTER TABLE tournament_final_validations ADD CONSTRAINT final_validation_job_fk FOREIGN KEY(job_id) REFERENCES bot_coding_jobs(id);
