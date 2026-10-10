package com.rpsbattle.game.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a player in the game.
 * <p>
 * A Player holds identity and a list of piece IDs.
 * The Player does NOT contain business logic.
 */
public final class Player {

    private final PlayerId id;
    private final String name;
    /**
     * IDs of pieces belonging to this player (alive and dead).
     */
    private final List<String> pieceIds;

    public Player(PlayerId id, String name, List<String> pieceIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.pieceIds = List.copyOf(Objects.requireNonNull(pieceIds, "pieceIds"));
    }

    /**
     * Factory with empty piece list.
     */
    public static Player of(PlayerId id, String name) {
        return new Player(id, name, List.of());
    }

    /**
     * Returns a new Player with an additional piece id.
     */
    public Player withAddedPiece(String pieceId) {
        List<String> updated = new ArrayList<>(pieceIds);
        updated.add(pieceId);
        return new Player(id, name, updated);
    }

    /**
     * Returns a new Player without the given piece id.
     */
    public Player withRemovedPiece(String pieceId) {
        List<String> updated = new ArrayList<>(pieceIds);
        updated.remove(pieceId);
        return new Player(id, name, updated);
    }

    public PlayerId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<String> pieceIds() {
        return pieceIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Player{id=" + id + ", name='" + name + "', pieces=" + pieceIds.size() + "}";
    }
}
