ALTER TABLE tournament_matches ADD COLUMN series_id UUID;
ALTER TABLE tournament_matches ADD COLUMN game_number INTEGER NOT NULL DEFAULT 1 CHECK (game_number BETWEEN 1 AND 5);
UPDATE tournament_matches SET series_id=id;
ALTER TABLE tournament_matches DROP CONSTRAINT uq_tournament_pair;
ALTER TABLE tournament_matches ADD CONSTRAINT uq_tournament_pair_game UNIQUE(tournament_id,participant_a_id,participant_b_id,game_number);
ALTER TABLE tournament_matches ADD CONSTRAINT uq_tournament_series_game UNIQUE(tournament_id,series_id,game_number);
ALTER TABLE tournament_matches ADD CONSTRAINT ck_tournament_series_root CHECK (
    (game_number=1 AND (series_id IS NULL OR series_id=id)) OR
    (game_number>1 AND series_id IS NOT NULL AND series_id<>id)
);
