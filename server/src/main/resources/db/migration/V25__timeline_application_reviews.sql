ALTER TABLE tournament_timeline_registrations ADD COLUMN review_reason VARCHAR(500);
ALTER TABLE tournament_timeline_registrations ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tournament_timeline_registrations ADD COLUMN reviewed_by UUID REFERENCES users(id);
ALTER TABLE tournament_timeline_registrations ADD CONSTRAINT application_review_metadata CHECK (
 (review_reason IS NULL AND reviewed_at IS NULL AND reviewed_by IS NULL) OR
 (review_reason IS NOT NULL AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
);
