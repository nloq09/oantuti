CREATE TABLE tournament_coding_freezes (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID NOT NULL REFERENCES users(id),
 coding_close_at TIMESTAMP WITH TIME ZONE NOT NULL,
 decision VARCHAR(16) NOT NULL CHECK (decision IN ('AUTO_SUBMITTED','EXPLICIT_SUBMIT','MISSING')),
 reason VARCHAR(80),
 version_id UUID REFERENCES tournament_submission_versions(id),
 version_number INTEGER,
 language VARCHAR(16),
 source_hash VARCHAR(64),
 artifact_hash VARCHAR(64),
 submitted_at TIMESTAMP WITH TIME ZONE,
 frozen_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(tournament_id,user_id,coding_close_at),
 CHECK ((decision='MISSING' AND version_id IS NULL AND source_hash IS NULL AND artifact_hash IS NULL AND submitted_at IS NULL)
     OR (decision<>'MISSING' AND version_id IS NOT NULL AND version_number IS NOT NULL AND language IS NOT NULL AND source_hash IS NOT NULL AND artifact_hash IS NOT NULL AND submitted_at IS NOT NULL))
);
CREATE INDEX coding_freeze_deadline ON tournament_coding_freezes(tournament_id,coding_close_at);
