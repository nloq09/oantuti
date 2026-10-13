CREATE TABLE matches (
    id VARCHAR(64) PRIMARY KEY,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    player1_user_id UUID REFERENCES users(id),
    player2_user_id UUID REFERENCES users(id),
    winner_user_id UUID REFERENCES users(id),
    outcome VARCHAR(16) CHECK (outcome IN ('WIN', 'DRAW')),
    turn_count INTEGER NOT NULL DEFAULT 0 CHECK (turn_count >= 0),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT CHECK (duration_ms >= 0),
    rule_set VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE match_participants (
    match_id VARCHAR(64) NOT NULL REFERENCES matches(id),
    seat VARCHAR(2) NOT NULL CHECK (seat IN ('P1', 'P2')),
    seat_user_id UUID REFERENCES users(id),
    seat_bot_id UUID REFERENCES bots(id),
    controller_type VARCHAR(24) NOT NULL CHECK (controller_type IN ('HUMAN', 'BUILTIN_BOT', 'UPLOADED_BOT')),
    PRIMARY KEY (match_id, seat)
);
