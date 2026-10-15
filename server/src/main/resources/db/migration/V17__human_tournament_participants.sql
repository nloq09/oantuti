ALTER TABLE tournament_participants ALTER COLUMN bot_id DROP NOT NULL;
ALTER TABLE tournament_participants ADD COLUMN human_user_id UUID REFERENCES users(id);
ALTER TABLE tournament_participants ADD CONSTRAINT uq_tournament_human_participant UNIQUE(tournament_id,human_user_id);
ALTER TABLE tournament_participants ADD CONSTRAINT ck_participant_controller CHECK (
 (bot_id IS NOT NULL AND human_user_id IS NULL) OR
 (bot_id IS NULL AND human_user_id IS NOT NULL AND human_user_id=owner_user_id)
);
ALTER TABLE tournament_registrations ALTER COLUMN bot_id DROP NOT NULL;
ALTER TABLE tournament_registrations ALTER COLUMN bot_version_id DROP NOT NULL;
ALTER TABLE tournament_registrations ADD COLUMN human_user_id UUID REFERENCES users(id);
ALTER TABLE tournament_registrations ADD CONSTRAINT uq_tournament_human_registration UNIQUE(tournament_id,human_user_id);
ALTER TABLE tournament_registrations ADD CONSTRAINT ck_registration_controller CHECK (
 (bot_id IS NOT NULL AND bot_version_id IS NOT NULL AND bot_id=bot_version_id AND human_user_id IS NULL) OR
 (bot_id IS NULL AND bot_version_id IS NULL AND human_user_id IS NOT NULL AND human_user_id=user_id)
);
