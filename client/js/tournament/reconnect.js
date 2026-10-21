export function canResumeTournamentSeat(seat,tournamentId,onlineId){return Boolean(seat?.token&&seat?.playerId&&seat.tournamentId===tournamentId&&seat.matchId===onlineId);}
