ALTER TABLE tournaments ADD COLUMN check_in_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tournaments ADD COLUMN max_participants INTEGER NOT NULL DEFAULT 10 CHECK (max_participants BETWEEN 2 AND 10);

ALTER TABLE bots ADD CONSTRAINT uq_bot_registration_owner UNIQUE (id,owner_user_id);
CREATE TABLE tournament_registrations (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    user_id UUID NOT NULL REFERENCES users(id),
    bot_id UUID NOT NULL REFERENCES bots(id),
    bot_version_id UUID NOT NULL REFERENCES bots(id),
    display_name VARCHAR(60) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','CHECKED_IN','NOT_CHECKED_IN')),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (tournament_id,bot_id),
    FOREIGN KEY (bot_id,user_id) REFERENCES bots(id,owner_user_id),
    CHECK (bot_version_id=bot_id)
);
CREATE INDEX ix_tournament_registrations_user ON tournament_registrations(user_id,tournament_id);
INSERT INTO tournament_registrations(id,tournament_id,user_id,bot_id,bot_version_id,display_name,status,registered_at,approved_at)
SELECT p.id,p.tournament_id,p.owner_user_id,p.bot_id,p.bot_id,p.display_name,'APPROVED',t.created_at,t.created_at
FROM tournament_participants p JOIN tournaments t ON t.id=p.tournament_id;
