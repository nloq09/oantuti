ALTER TABLE tournaments DROP CONSTRAINT ck_tournaments_capacity;
ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_capacity CHECK (
    max_participants BETWEEN 2 AND 2000
    AND (max_participants <= 32 OR format IN ('SINGLE_ELIMINATION','SWISS'))
);
CREATE INDEX idx_timeline_freeze_candidates ON tournament_timeline_registrations(tournament_id,status,user_id);
