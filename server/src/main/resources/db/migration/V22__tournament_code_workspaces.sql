CREATE TABLE tournament_code_workspaces (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID NOT NULL REFERENCES users(id),
 language VARCHAR(16) NOT NULL CHECK (language IN ('PYTHON','CPP','JAVA')),
 source_code TEXT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 current_version_id UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(tournament_id,user_id,language)
);
CREATE INDEX workspace_user_tournament ON tournament_code_workspaces(user_id,tournament_id);
CREATE TABLE tournament_submission_versions (
 id UUID PRIMARY KEY,
 workspace_id UUID NOT NULL REFERENCES tournament_code_workspaces(id),
 version_number INTEGER NOT NULL,
 source_code TEXT NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 artifact_hash VARCHAR(64),
 artifact_id UUID,
 status VARCHAR(24) NOT NULL,
 build_log TEXT NOT NULL,
 test_result TEXT,
 sample_result TEXT,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(workspace_id,version_number)
);
CREATE INDEX workspace_version_status ON tournament_submission_versions(status,created_at);
ALTER TABLE tournament_code_workspaces ADD CONSTRAINT workspace_current_version_fk
 FOREIGN KEY(current_version_id) REFERENCES tournament_submission_versions(id);
