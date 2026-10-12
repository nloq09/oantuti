package com.rpsbattle.agent;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.PlayerId;
import com.rpsbattle.game.state.GameState;
import com.rpsbattle.rules.classic.ClassicVictoryRule;
import com.rpsbattle.rule.*;

import java.util.*;

/**
 * Scores supplied legal moves using public information and injected variant rules.
 */
public final class TacticalBotAgent implements PlayerAgent {
    private final RuleSet rules;
    private final Random random;
    private final Deque<String> recentBoards = new ArrayDeque<>();

    public TacticalBotAgent(RuleSet rules, long seed) {
        this.rules = Objects.requireNonNull(rules);
        this.random = new Random(seed);
    }

    @Override
    public Action chooseAction(PlayerView view, List<Action> legalActions) {
        if (legalActions.isEmpty()) throw new IllegalArgumentException("No legal actions supplied");
        Board current = view.state().board();
        double best = -Double.MAX_VALUE;
        var candidates = new ArrayList<Action>();
        for (Action action : legalActions) {
            double score = -Double.MAX_VALUE;
            if (action instanceof MoveAction move) {
                Board next = predict(current, move);
                score = evaluate(next, view.playerId(), view.state());
                if (current.getPiece(move.to()).filter(target -> rules.combatRule().resolve(
                        current.findById(move.pieceId()).orElseThrow(), target) == CombatResult.DRAW).isPresent())
                    score -= 25; // Same-type attacks consume a turn without changing the board.
                String fingerprint = fingerprint(next);
                score -= 45 * recentBoards.stream().filter(fingerprint::equals).count();
            } else if (action instanceof PassAction) score = evaluate(current, view.playerId(), view.state()) - 100;
            if (score > best) {
                best = score;
                candidates.clear();
            }
            if (score == best) candidates.add(action);
        }
        candidates.sort(Comparator.comparing(action -> action instanceof MoveAction move
                ? move.pieceId() + ":" + move.to().row() + ":" + move.to().col() : action.getClass().getSimpleName()));
        Action chosen = candidates.get(random.nextInt(candidates.size()));
        if (chosen instanceof MoveAction move) {
            recentBoards.addLast(fingerprint(predict(current, move)));
            if (recentBoards.size() > 16) recentBoards.removeFirst();
        }
        return chosen;
    }

    private Board predict(Board current, MoveAction move) {
        Board next = current.copy();
        Piece attacker = next.findById(move.pieceId()).orElseThrow();
        var target = next.getPiece(move.to());
        if (target.isEmpty()) next.movePiece(move.from(), move.to());
        else switch (rules.combatRule().resolve(attacker, target.get())) {
            case ATTACKER_WIN -> {
                next.removePiece(move.to());
                next.movePiece(move.from(), move.to());
            }
            case DEFENDER_WIN -> next.removePiece(move.from());
            case BOTH_DIE -> {
                next.removePiece(move.from());
                next.removePiece(move.to());
            }
            case DRAW -> {
            }
        }
        return next;
    }

    private double evaluate(Board board, PlayerId player, GameState context) {
        var projected = context.withBoard(board);
        var result = rules.victoryRule().checkResult(projected);
        if (result.isPresent()) return result.get().isDraw() ? 0
                : result.get().winner().filter(player::equals).isPresent() ? 1_000_000 : -1_000_000;
        var own = board.allPieces().stream().filter(p -> p.isAlive() && p.ownedBy(player)).toList();
        var enemy = board.allPieces().stream().filter(p -> p.isAlive() && !p.ownedBy(player)).toList();
        if (own.isEmpty() && enemy.isEmpty()) return 0;
        if (enemy.isEmpty()) return 1_000_000;
        if (own.isEmpty()) return -1_000_000;
        double score = (own.size() - enemy.size()) * 1000;
        double worstReply = 0;
        if (rules.victoryRule() instanceof ClassicVictoryRule classic) {
            var home = classic.stronghold(projected, player);
            var opponent = context.opponents(player).get(0).id();
            var goal = classic.stronghold(projected, opponent);
            int closest = own.stream().mapToInt(p -> Math.max(Math.abs(p.position().row() - goal.row()),
                    Math.abs(p.position().col() - goal.col()))).min().orElse(0);
            score -= 12 * closest;
            for (Piece attacker : enemy) {
                if (rules.movementRule().canMove(attacker, attacker.position(), home, board)) {
                    var defender = board.getPiece(home);
                    if (defender.isEmpty() || rules.combatRule().resolve(attacker, defender.get()) == CombatResult.ATTACKER_WIN)
                        worstReply = 1_000_000;
                }
            }
        }
        for (Piece defender : own) {
            int preyDistance = Integer.MAX_VALUE;
            int anyDistance = Integer.MAX_VALUE;
            for (Piece attacker : enemy) {
                int distance = Math.max(Math.abs(defender.position().row() - attacker.position().row()),
                        Math.abs(defender.position().col() - attacker.position().col()));
                anyDistance = Math.min(anyDistance, distance);
                if (rules.combatRule().resolve(defender, attacker) == CombatResult.ATTACKER_WIN)
                    preyDistance = Math.min(preyDistance, distance);
                if (rules.movementRule().canMove(attacker, attacker.position(), defender.position(), board)) {
                    var outcome = rules.combatRule().resolve(attacker, defender);
                    if (outcome == CombatResult.ATTACKER_WIN)
                        worstReply = Math.max(worstReply, own.size() == 1 ? 1_000_000 : 1100);
                    else if (outcome == CombatResult.BOTH_DIE)
                        worstReply = Math.max(worstReply, 120);
                }
            }
            // Pursue favorable matchups; when no prey exists, keep moving towards opponents.
            score -= 3 * (preyDistance == Integer.MAX_VALUE ? anyDistance : preyDistance);
        }
        return score - worstReply;
    }

    private String fingerprint(Board board) {
        return board.allPieces().stream().sorted(Comparator.comparing(Piece::id))
                .map(p -> p.id() + "@" + p.position()).reduce("", (a, b) -> a + ";" + b);
    }
}
