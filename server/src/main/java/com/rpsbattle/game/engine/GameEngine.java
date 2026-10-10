package com.rpsbattle.game.engine;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GamePhase;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rule.CombatResult;
import com.rpsbattle.rule.RuleSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies actions to independent snapshots, delegating variant decisions to rules. */
public final class GameEngine {
    private final RuleSet rules;

    public GameEngine(RuleSet rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public GameTransition applyAction(GameState state, PlayerId player, Action action) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        if (state.isFinished()) reject("Game is already finished");
        if (state.findPlayer(player).isEmpty()) reject("Unknown player: " + player);
        if (!player.equals(action.playerId())) reject("Action player does not match caller");
        if (!rules.turnRule().canAct(state, player, action)) {
            reject("Action is not allowed in this phase or turn");
        }

        Board board = state.board();
        long sequence = Math.addExact(state.sequence(), 1);
        List<GameEvent> events = new ArrayList<>();
        GameState next = state;
        switch (action) {
            case MoveAction move -> applyMove(board, player, move, sequence, events);
            case PlaceAction place -> {
                if (place.pieceId().isBlank()) reject("Piece id must not be blank");
                if (board.findById(place.pieceId()).isPresent()
                        || state.players().stream().anyMatch(p -> p.pieceIds().contains(place.pieceId()))) {
                    reject("Piece id already exists: " + place.pieceId());
                }
                if (!rules.placementRule().canPlace(player, place.pieceType(), place.position(), board)) {
                    reject("Illegal placement: " + place.position());
                }
                Piece piece = Piece.alive(place.pieceId(), player, place.pieceType(), place.position());
                board.placePiece(piece);
                next = next.withUpdatedPlayer(state.findPlayer(player).orElseThrow().withAddedPiece(piece.id()));
                events.add(new GameEvent.PiecePlaced(sequence, piece));
            }
            case PassAction ignored -> { /* TurnRule decides whether passing is allowed. */ }
            case SurrenderAction ignored -> { /* VictoryRule resolves surrender. */ }
        }
        next = next.withBoard(board).withIncrementedSequence();
        // Count the completed turn before victory evaluation, so limit N ends at N.
        if (rules.turnRule().advancesTurn(state, action)) next = next.withIncrementedTurn();
        var result = rules.victoryRule().checkResult(next, action);
        if (result.isPresent()) {
            next = next.withResult(result.get()).withPhase(GamePhase.FINISHED);
            events.add(new GameEvent.GameFinished(sequence, result.get(), next.turnNumber()));
        } else {
            PlayerId nextPlayer = rules.turnRule().nextPlayer(next);
            if (next.findPlayer(nextPlayer).isEmpty()) {
                throw new IllegalStateException("TurnRule returned an unknown player: " + nextPlayer);
            }
            events.add(new GameEvent.TurnChanged(sequence, next.currentPlayerId(), nextPlayer, next.turnNumber()));
            next = next.withCurrentPlayer(nextPlayer);
        }
        return new GameTransition(next, events);
    }

    private void applyMove(Board board, PlayerId player, MoveAction move,
                           long sequence, List<GameEvent> events) {
        Piece attacker = board.findById(move.pieceId())
                .orElseThrow(() -> new InvalidActionException("Unknown piece: " + move.pieceId()));
        if (!attacker.ownedBy(player)) reject("Cannot move an opponent's piece");
        if (!attacker.isAlive()) reject("Cannot move a dead piece");
        if (!attacker.position().equals(move.from())) reject("Source does not match piece position");
        if (!rules.movementRule().canMove(attacker, move.from(), move.to(), board)) {
            reject("Illegal movement: " + move.from() + " -> " + move.to());
        }
        var target = board.getPiece(move.to());
        if (target.isEmpty()) {
            board.movePiece(move.from(), move.to());
            events.add(new GameEvent.PieceMoved(sequence, attacker.id(), move.from(), move.to()));
            return;
        }
        Piece defender = target.get();
        if (defender.ownedBy(player)) reject("Cannot attack a friendly piece");
        CombatResult combat = rules.combatRule().resolve(attacker, defender);
        events.add(new GameEvent.CombatOccurred(sequence, attacker, defender, combat));
        switch (combat) {
            case ATTACKER_WIN -> {
                remove(board, defender, sequence, events);
                board.movePiece(move.from(), move.to());
                events.add(new GameEvent.PieceMoved(sequence, attacker.id(), move.from(), move.to()));
            }
            case DEFENDER_WIN -> remove(board, attacker, sequence, events);
            case BOTH_DIE -> {
                remove(board, attacker, sequence, events);
                remove(board, defender, sequence, events);
            }
            case DRAW -> { /* Neither piece changes position or status. */ }
        }
    }

    private void remove(Board board, Piece piece, long sequence, List<GameEvent> events) {
        board.removePiece(piece.position());
        events.add(new GameEvent.PieceRemoved(sequence, piece));
    }

    private static void reject(String message) {
        throw new InvalidActionException(message);
    }
}
