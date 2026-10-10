package com.rpsbattle.api.mapper;

import com.rpsbattle.api.dto.*;
import com.rpsbattle.game.action.Action;
import com.rpsbattle.game.engine.GameEvent;
import com.rpsbattle.game.piece.Piece;
import com.rpsbattle.game.state.GameState;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class GameStateMapper {
    public GameStateDto toDto(GameState state, List<Action> legalActions, List<GameEvent> events) {
        var board = state.board();
        return new GameStateDto(state.matchId(), board.cols(), board.rows(),
                board.allPieces().stream().sorted(Comparator.comparing(Piece::id)).map(this::piece).toList(),
                state.currentPlayerId().value(), state.turnNumber(), state.sequence(), state.phase().name(),
                state.result().flatMap(result -> result.winner()).map(player -> player.value()).orElse(null),
                state.result().map(result -> result.outcome().name()).orElse(null),
                legalActions.stream().map(ActionDto::from).toList(), events.stream().map(this::event).toList());
    }

    private PieceDto piece(Piece value) {
        return new PieceDto(value.id(), value.owner().value(), value.type().name(),
                PositionDto.from(value.position()), value.status().name());
    }

    private GameEventDto event(GameEvent value) {
        Map<String, Object> payload = switch (value) {
            case GameEvent.PieceMoved move -> Map.of("pieceId", move.pieceId(),
                    "from", PositionDto.from(move.from()), "to", PositionDto.from(move.to()));
            case GameEvent.CombatOccurred combat -> Map.of("attacker", piece(combat.attacker()),
                    "defender", piece(combat.defender()), "result", combat.result().name());
            case GameEvent.PieceRemoved removed -> Map.of("piece", piece(removed.piece()));
            case GameEvent.PiecePlaced placed -> Map.of("piece", piece(placed.piece()));
            case GameEvent.TurnChanged turn -> Map.of("currentPlayer", turn.currentPlayer().value(), "turn", turn.turnNumber());
            case GameEvent.TurnStarted turn -> Map.of("player", turn.player().value());
            case GameEvent.TurnEnded turn -> Map.of("currentPlayer", turn.currentPlayer().value(), "turn", turn.turnNumber());
            case GameEvent.GameFinished finished -> Map.of("outcome", finished.result().outcome().name(),
                    "winner", finished.result().winner().map(player -> player.value()).orElse("DRAW"));
            case GameEvent.MatchFinished finished -> Map.of("outcome", finished.result().outcome().name(),
                    "winner", finished.result().winner().map(player -> player.value()).orElse("DRAW"));
            case GameEvent.MatchStarted ignored -> Map.of();
        };
        return new GameEventDto(value.sequence(), value.type(), payload);
    }
}
