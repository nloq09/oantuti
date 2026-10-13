CREATE INDEX idx_bots_owner_user ON bots(owner_user_id);
CREATE INDEX idx_matches_player1_user ON matches(player1_user_id);
CREATE INDEX idx_matches_player2_user ON matches(player2_user_id);
CREATE INDEX idx_matches_finished_at ON matches(finished_at);
CREATE INDEX idx_match_participants_user ON match_participants(seat_user_id);
CREATE INDEX idx_tournaments_owner_user ON tournaments(owner_user_id);
CREATE INDEX idx_tournament_participants_owner ON tournament_participants(owner_user_id);
CREATE INDEX idx_tournament_matches_tournament ON tournament_matches(tournament_id);
