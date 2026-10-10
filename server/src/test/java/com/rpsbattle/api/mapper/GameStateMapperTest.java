package com.rpsbattle.api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.api.dto.*;
import com.rpsbattle.online.DemoMatchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GameStateMapperTest {
    private final DemoMatchService service = new DemoMatchService();
    private final GameStateMapper mapper = new GameStateMapper();

    @Test
    void jsonContainsOnlyDtoFieldsAndRecipientLegalActions() throws Exception {
        var dto = mapper.toDto(service.state(), service.legalActions(DemoMatchService.P1), List.of());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));
        assertThat(json.path("boardWidth").asInt()).isEqualTo(9);
        assertThat(json.path("pieces").size()).isEqualTo(4);
        assertThat(json.path("currentPlayer").asText()).isEqualTo("P1");
        assertThat(json.path("pieces").get(0).path("owner").isTextual()).isTrue();
        assertThat(json.path("legalActions").size()).isGreaterThan(0);
        assertThat(mapper.toDto(service.state(), service.legalActions(DemoMatchService.P2), List.of()).legalActions()).isEmpty();
        assertThatThrownBy(() -> dto.pieces().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void moveDtoRoundTripsAndRejectsUnsupportedCommands() {
        var dto = new ActionDto("MOVE", "P1-R1", new PositionDto(6, 2), new PositionDto(5, 2));
        assertThat(ActionDto.from(dto.toDomain(DemoMatchService.P1))).isEqualTo(dto);
        assertThatThrownBy(() -> new ActionDto("PLACE", null, null, null).toDomain(DemoMatchService.P1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
