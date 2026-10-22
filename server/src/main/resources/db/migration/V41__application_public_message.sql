-- Internal review_reason remains organizer-only; never backfill it into public text.
ALTER TABLE tournament_timeline_registrations ADD COLUMN public_review_message VARCHAR(500);
