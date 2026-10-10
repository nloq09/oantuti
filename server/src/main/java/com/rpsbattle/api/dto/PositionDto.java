package com.rpsbattle.api.dto;

import com.rpsbattle.game.board.Position;

public record PositionDto(int row, int col) {
    public static PositionDto from(Position value) { return new PositionDto(value.row(), value.col()); }
    public Position toDomain() { return new Position(row, col); }
}
