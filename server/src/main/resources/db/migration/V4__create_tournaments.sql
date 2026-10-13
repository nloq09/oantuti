CREATE TABLE tournaments (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(80) NOT NULL,
    format VARCHAR(24) NOT NULL CHECK (format = 'ROUND_ROBIN'),
    status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT', 'READY', 'RUNNING', 'FINISHED', 'CANCELLED', 'RECOVERY_REQUIRED')),
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE tournament_participants (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    bot_id UUID NOT NULL REFERENCES bots(id),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    display_name VARCHAR(60) NOT NULL,
    seed BIGINT NOT NULL,
    CONSTRAINT uq_tournament_bot UNIQUE (tournament_id, bot_id),
    CONSTRAINT uq_participant_tournament UNIQUE (id, tournament_id)
);
CREATE TABLE tournament_matches (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    match_id VARCHAR(64) REFERENCES matches(id),
    participant_a_id UUID NOT NULL,
    participant_b_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'FINISHED', 'FAILED')),
    winner_participant_id UUID,
    outcome VARCHAR(16) CHECK (outcome IN ('WIN', 'DRAW')),
    failure VARCHAR(80),
    remaining_a INTEGER CHECK (remaining_a >= 0),
    remaining_b INTEGER CHECK (remaining_b >= 0),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (participant_a_id, tournament_id) REFERENCES tournament_participants(id, tournament_id),
    FOREIGN KEY (participant_b_id, tournament_id) REFERENCES tournament_participants(id, tournament_id),
    FOREIGN KEY (winner_participant_id, tournament_id) REFERENCES tournament_participants(id, tournament_id),
    CHECK (participant_a_id <> participant_b_id),
    CHECK (winner_participant_id IS NULL OR winner_participant_id IN (participant_a_id, participant_b_id)),
    CONSTRAINT uq_tournament_pair UNIQUE (tournament_id, participant_a_id, participant_b_id)
);
