-- Archive UUIDs are distinct from short-lived public room codes.
ALTER TABLE matches ADD COLUMN room_id VARCHAR(64);
ALTER TABLE matches ADD COLUMN winner_seat VARCHAR(2) CHECK (winner_seat IN ('P1', 'P2'));
UPDATE matches SET room_id = id;
UPDATE matches SET winner_seat = CASE
    WHEN winner_user_id = player1_user_id THEN 'P1'
    WHEN winner_user_id = player2_user_id THEN 'P2'
    ELSE NULL END;
CREATE INDEX idx_matches_account1_history ON matches(player1_user_id, created_at DESC, id);
CREATE INDEX idx_matches_account2_history ON matches(player2_user_id, created_at DESC, id);
