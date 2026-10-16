ALTER TABLE tournaments DROP CONSTRAINT ck_tournaments_format;
ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_format
    CHECK (format IN ('ROUND_ROBIN','SINGLE_ELIMINATION','GROUP_KNOCKOUT'));
-- A pairing may meet again in the final. Physical games remain unique within each series.
ALTER TABLE tournament_matches DROP CONSTRAINT uq_tournament_pair_game;
