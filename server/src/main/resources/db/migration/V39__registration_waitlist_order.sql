ALTER TABLE tournament_timeline_registrations ADD COLUMN waitlist_order BIGINT;

-- Legacy waitlisted entries retain deterministic registered_at/id order.
UPDATE tournament_timeline_registrations r
SET waitlist_order = (
 SELECT COUNT(*) FROM tournament_timeline_registrations earlier
 WHERE earlier.tournament_id=r.tournament_id AND earlier.status='WAITLISTED'
 AND (earlier.registered_at<r.registered_at OR (earlier.registered_at=r.registered_at AND earlier.id<=r.id))
)
WHERE r.status='WAITLISTED';

ALTER TABLE tournament_timeline_registrations ADD CONSTRAINT application_waitlist_order_positive
 CHECK (waitlist_order IS NULL OR waitlist_order>0);
ALTER TABLE tournament_timeline_registrations ADD CONSTRAINT application_waitlist_order_unique
 UNIQUE (tournament_id,waitlist_order);
CREATE INDEX application_waitlist_position ON tournament_timeline_registrations(tournament_id,status,waitlist_order);
