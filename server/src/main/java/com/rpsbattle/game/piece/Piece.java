package com.rpsbattle.game.piece;

import com.rpsbattle.game.board.Position;
import com.rpsbattle.game.player.PlayerId;

import java.util.Objects;

/**
 * Immutable representation of a game piece.
 *
 * A Piece cannot mutate itself — any state change produces a new Piece instance.
 */
public final class Piece {

    private final String id;
    private final PlayerId ownerId;
    private final PieceType type;
    private final Position position;
    private final PieceStatus status;

    public Piece(String id, PlayerId ownerId, PieceType type, Position position, PieceStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Convenience factory — creates an ALIVE piece. */
    public static Piece alive(String id, PlayerId ownerId, PieceType type, Position position) {
        return new Piece(id, ownerId, type, position, PieceStatus.ALIVE);
    }

    // ---- Wither-style mutation (returns new instance) ----

    public Piece withPosition(Position newPosition) {
        return new Piece(id, ownerId, type, newPosition, status);
    }

    public Piece withStatus(PieceStatus newStatus) {
        return new Piece(id, ownerId, type, position, newStatus);
    }

    // ---- Accessors ----

    public String id() { return id; }
    public PlayerId ownerId() { return ownerId; }
    public PieceType type() { return type; }
    public Position position() { return position; }
    public PieceStatus status() { return status; }

    public boolean isAlive() { return status == PieceStatus.ALIVE; }
    public boolean isDead()  { return status == PieceStatus.DEAD; }

    public boolean ownedBy(PlayerId player) {
        return ownerId.equals(player);
    }

    // ---- Value equality (by id) ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Piece other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Piece{id='" + id + "', owner=" + ownerId + ", type=" + type
                + ", pos=" + position + ", status=" + status + "}";
    }
}
