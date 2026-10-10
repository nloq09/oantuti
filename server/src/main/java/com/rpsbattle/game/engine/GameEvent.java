package com.rpsbattle.game.engine;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameResult;
import com.rpsbattle.rule.CombatResult;

/** Ordered facts from one transition. All events share its resulting sequence. */
public sealed interface GameEvent {
    long sequence();

    record PiecePlaced(long sequence, Piece piece) implements GameEvent {}
    record PieceMoved(long sequence, String pieceId, Position from, Position to) implements GameEvent {}
    record CombatOccurred(long sequence, Piece attacker, Piece defender, CombatResult result) implements GameEvent {}
    record PieceRemoved(long sequence, Piece piece) implements GameEvent {}
    record TurnChanged(long sequence, PlayerId previousPlayer, PlayerId currentPlayer,
                       int turnNumber) implements GameEvent {}
    record GameFinished(long sequence, GameResult result, int turnNumber) implements GameEvent {}
}
