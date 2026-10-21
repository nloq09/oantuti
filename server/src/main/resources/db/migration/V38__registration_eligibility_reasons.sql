-- Keep PENDING as the legacy storage value; API projects it as NEEDS_REVIEW.
ALTER TABLE tournament_timeline_registrations ADD COLUMN eligibility_reasons VARCHAR(500) NOT NULL DEFAULT '';
