package com.rpsbattle.api.dto;

import com.rpsbattle.game.action.*;
import com.rpsbattle.game.player.PlayerId;
import java.util.Objects;

public record ActionDto(String type, String pieceId, PositionDto from, PositionDto to) {
    public Action toDomain(PlayerId player) {
        if (type == null) throw new IllegalArgumentException("Missing action type");
        return switch (type) {
            case "MOVE" -> MoveAction.of(player, Objects.requireNonNull(pieceId),
                    Objects.requireNonNull(from).toDomain(), Objects.requireNonNull(to).toDomain());
            case "PASS" -> PassAction.of(player);
            case "SURRENDER" -> SurrenderAction.of(player);
            default -> throw new IllegalArgumentException("Unsupported action type");
        };
    }

    public static ActionDto from(Action action) {
        return switch (action) {
            case MoveAction move -> new ActionDto("MOVE", move.pieceId(), PositionDto.from(move.from()), PositionDto.from(move.to()));
            case PassAction ignored -> new ActionDto("PASS", null, null, null);
            case SurrenderAction ignored -> new ActionDto("SURRENDER", null, null, null);
            default -> throw new IllegalArgumentException("Not a demo gameplay action");
        };
    }
}
