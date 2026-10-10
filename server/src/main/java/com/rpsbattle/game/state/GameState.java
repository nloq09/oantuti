package com.rpsbattle.game.state;

import com.rpsbattle.game.board.Board;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.player.Player;
import com.rpsbattle.game.player.PlayerId;

import java.util.*;

/**
 * Immutable snapshot of the entire game state at a point in time.
 *
 * GameState knows nothing about WebSocket, HTTP, database, or console.
 * It does NOT contain rule logic — that belongs to RuleSet.
 *
 * Design note: Board is intentionally mutable internally, but GameState
 * controls all mutations via its wither-style factory methods, returning
 * new snapshots rather than exposing setters.
 *
 * For the current stage we keep Board as a mutable field that is
 * defensively copied (deep copy) when producing new GameState instances,
 * ensuring each snapshot is independent.
 */
public final class GameState {

    private final String       matchId;
    private final Board        board;
    /** Ordered list of players — index 0 goes first. */
    private final List<Player> players;
    private final PlayerId     currentPlayerId;
    private final GamePhase    phase;
    private final int          turnNumber;
    /**
     * Monotonically increasing counter: incremented after every action
     * that successfully changes the game, including passing the turn.
     */
    private final long         sequence;
    /** Non-null only when phase == FINISHED. */
    private final GameResult   result;

    // ---------------------------------------------------------------
    // Constructor (private — use factory or wither methods)
    // ---------------------------------------------------------------

    private GameState(
            String matchId,
            Board board,
            List<Player> players,
            PlayerId currentPlayerId,
            GamePhase phase,
            int turnNumber,
            long sequence,
            GameResult result) {

        this.matchId         = Objects.requireNonNull(matchId,  "matchId");
        this.board           = Objects.requireNonNull(board,    "board").copy();
        this.players         = List.copyOf(Objects.requireNonNull(players, "players"));
        this.currentPlayerId = Objects.requireNonNull(currentPlayerId, "currentPlayerId");
        this.phase           = Objects.requireNonNull(phase,    "phase");
        this.turnNumber      = turnNumber;
        this.sequence        = sequence;
        this.result          = result; // nullable
    }

    // ---------------------------------------------------------------
    // Factory
    // ---------------------------------------------------------------

    /**
     * Creates the very first state for a match.
     *
     * @param matchId unique match identifier
     * @param board   fresh (empty) board
     * @param players ordered list of players; index 0 acts first
     */
    public static GameState initial(String matchId, Board board, List<Player> players) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players");
        }
        return new GameState(
                matchId,
                board,
                players,
                players.get(0).id(),
                GamePhase.CREATED,
                0,
                0L,
                null
        );
    }

    // ---------------------------------------------------------------
    // Wither-style mutation — each returns a new GameState instance
    // ---------------------------------------------------------------

    public GameState withBoard(Board newBoard) {
        return new GameState(matchId, newBoard, players, currentPlayerId, phase, turnNumber, sequence, result);
    }

    public GameState withPlayers(List<Player> newPlayers) {
        return new GameState(matchId, board, newPlayers, currentPlayerId, phase, turnNumber, sequence, result);
    }

    public GameState withUpdatedPlayer(Player updatedPlayer) {
        List<Player> updated = new ArrayList<>(players);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(updatedPlayer.id())) {
                updated.set(i, updatedPlayer);
                break;
            }
        }
        return withPlayers(updated);
    }

    public GameState withCurrentPlayer(PlayerId newCurrentPlayer) {
        return new GameState(matchId, board, players, newCurrentPlayer, phase, turnNumber, sequence, result);
    }

    public GameState withPhase(GamePhase newPhase) {
        return new GameState(matchId, board, players, currentPlayerId, newPhase, turnNumber, sequence, result);
    }

    public GameState withTurnNumber(int newTurn) {
        return new GameState(matchId, board, players, currentPlayerId, phase, newTurn, sequence, result);
    }

    public GameState withIncrementedTurn() {
        return withTurnNumber(turnNumber + 1);
    }

    /** Increments sequence — called after every valid action. */
    public GameState withIncrementedSequence() {
        return new GameState(matchId, board, players, currentPlayerId, phase, turnNumber, sequence + 1, result);
    }

    /** Restores a validated snapshot counter without depending on serialization. */
    public GameState withSequence(long newSequence) {
        if (newSequence < 0) throw new IllegalArgumentException("sequence must be nonnegative");
        return new GameState(matchId, board, players, currentPlayerId, phase, turnNumber, newSequence, result);
    }

    public GameState withResult(GameResult newResult) {
        return new GameState(matchId, board, players, currentPlayerId, phase, turnNumber, sequence, newResult);
    }

    // ---------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------

    public String matchId()            { return matchId; }
    public Board board()               { return board.copy(); }
    public List<Player> players()      { return players; }
    public PlayerId currentPlayerId()  { return currentPlayerId; }
    public GamePhase phase()           { return phase; }
    public int turnNumber()            { return turnNumber; }
    public long sequence()             { return sequence; }
    public Optional<GameResult> result() { return Optional.ofNullable(result); }

    public boolean isFinished() { return phase == GamePhase.FINISHED; }
    public boolean isPlaying()  { return phase == GamePhase.PLAYING; }

    /** Returns the Player object for the given id, or empty. */
    public Optional<Player> findPlayer(PlayerId id) {
        return players.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Returns the current Player object. */
    public Player currentPlayer() {
        return findPlayer(currentPlayerId)
                .orElseThrow(() -> new IllegalStateException("currentPlayerId not found: " + currentPlayerId));
    }

    /**
     * Returns the opponent(s) of the given player.
     * For a 2-player game this always returns exactly 1 player.
     */
    public List<Player> opponents(PlayerId ofPlayer) {
        return players.stream()
                .filter(p -> !p.id().equals(ofPlayer))
                .toList();
    }

    /**
     * Returns all alive pieces belonging to the given player.
     */
    public List<Piece> alivePiecesOf(PlayerId playerId) {
        return board.allPieces().stream()
                .filter(p -> p.ownedBy(playerId) && p.isAlive())
                .toList();
    }

    /** Total alive pieces for a player. */
    public int aliveCount(PlayerId playerId) {
        return alivePiecesOf(playerId).size();
    }

    @Override
    public String toString() {
        return "GameState{match=" + matchId
                + ", phase=" + phase
                + ", turn=" + turnNumber
                + ", seq=" + sequence
                + ", current=" + currentPlayerId + "}";
    }
}
