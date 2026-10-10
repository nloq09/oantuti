package com.rpsbattle.game.state;

/**
 * Lifecycle phases of a game match.
 */
public enum GamePhase {
    /** Match object created but setup not yet started. */
    CREATED,
    /** Players are placing their pieces on the board. */
    PLACEMENT,
    /** All pieces placed; ready to begin playing. */
    READY,
    /** The game is actively being played. */
    PLAYING,
    /** The game has ended. */
    FINISHED
}
