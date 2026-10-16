ALTER TABLE tournaments DROP CONSTRAINT ck_tournaments_format;
ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_format
    CHECK (format IN ('ROUND_ROBIN','SINGLE_ELIMINATION','GROUP_KNOCKOUT','SWISS','TEAM_ROUND_ROBIN'));
