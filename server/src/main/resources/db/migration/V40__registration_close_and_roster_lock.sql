CREATE TABLE tournament_registration_controls (
 tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),
 closed_at TIMESTAMP WITH TIME ZONE,
 locked_at TIMESTAMP WITH TIME ZONE,
 locked_by UUID REFERENCES users(id),
 locked_by_name VARCHAR(80),
 locked_count INTEGER,
 CHECK ((locked_at IS NULL AND locked_by IS NULL AND locked_by_name IS NULL AND locked_count IS NULL)
  OR (locked_at IS NOT NULL AND closed_at IS NOT NULL AND locked_by IS NOT NULL AND locked_by_name IS NOT NULL AND locked_count IS NOT NULL AND locked_count>=2 AND closed_at<=locked_at))
);
