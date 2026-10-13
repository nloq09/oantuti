ALTER TABLE replays ADD COLUMN sha256 VARCHAR(64);
-- Old rows have no verified file digest. Keep their metadata but do not serve them.
UPDATE replays SET valid = FALSE;
CREATE INDEX idx_matches_room_archive ON matches(room_id, created_at DESC, id);
