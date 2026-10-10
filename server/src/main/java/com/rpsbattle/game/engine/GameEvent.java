package com.rpsbattle.game.engine;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.rule.CombatResult;

/** Ordered facts from one transition. All events share its resulting sequence. */
public sealed interface GameEvent {
    long sequence();

    default String type() {
        return switch (this) {
            case MatchStarted ignored -> "MATCH_STARTED";
            case TurnStarted ignored -> "TURN_STARTED";
            case PiecePlaced ignored -> "PIECE_PLACED";
            case PieceMoved ignored -> "PIECE_MOVED";
            case CombatOccurred ignored -> "COMBAT_OCCURRED";
            case PieceRemoved ignored -> "PIECE_REMOVED";
            case TurnChanged ignored -> "TURN_CHANGED";
            case TurnEnded ignored -> "TURN_ENDED";
            case GameFinished ignored -> "GAME_FINISHED";
            case MatchFinished ignored -> "MATCH_FINISHED";
        };
    }

    record MatchStarted(long sequence) implements GameEvent {}
    record TurnStarted(long sequence, PlayerId player) implements GameEvent {}
    record TurnEnded(long sequence, PlayerId currentPlayer, int turnNumber) implements GameEvent {}
    record MatchFinished(long sequence, GameResult result) implements GameEvent {}

    record PiecePlaced(long sequence, Piece piece) implements GameEvent {}
    record PieceMoved(long sequence, String pieceId, Position from, Position to) implements GameEvent {}
    record CombatOccurred(long sequence, Piece attacker, Piece defender, CombatResult result) implements GameEvent {}
    record PieceRemoved(long sequence, Piece piece) implements GameEvent {}
    record TurnChanged(long sequence, PlayerId previousPlayer, PlayerId currentPlayer,
                       int turnNumber) implements GameEvent {}
    record GameFinished(long sequence, GameResult result, int turnNumber) implements GameEvent {}
}
