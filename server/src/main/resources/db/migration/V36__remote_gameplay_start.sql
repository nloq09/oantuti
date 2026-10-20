-- /start is reservation/startup acknowledgement; gameplay begins only after worker preparation.
ALTER TABLE match_execution_attempts ADD COLUMN gameplay_started_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX remote_attempt_job_order ON match_execution_attempts(job_id,claimed_at,id);
