CREATE TABLE tournament_timeline_registrations (
 id UUID PRIMARY KEY,
 tournament_id UUID NOT NULL REFERENCES tournaments(id),
 user_id UUID NOT NULL REFERENCES users(id),
 display_name VARCHAR(80) NOT NULL,
 intended_language VARCHAR(16) NOT NULL CHECK (intended_language IN ('CPP','PYTHON','JAVA')),
 affiliation VARCHAR(80),
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','WAITLISTED')),
 registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
 rules_accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(tournament_id,user_id)
);
CREATE INDEX timeline_registration_status ON tournament_timeline_registrations(tournament_id,status);
