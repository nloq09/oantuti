package com.rpsbattle.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.websocket.handler.*;
import com.rpsbattle.online.DemoMatchService;
import com.rpsbattle.api.mapper.GameStateMapper;
import com.rpsbattle.spectator.SpectatorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.time.Clock;
import java.net.InetSocketAddress;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class WebSocketLimitTest {
    @Test void pingLimitSurvivesReconnectAndDoesNotDispatchRejectedMessages() throws Exception {
        var online=mock(OnlineGameGateway.class);var meters=new SimpleMeterRegistry();var metrics=new RuntimeMetrics(meters);
        var handler=new GameWebSocketHandler(new ObjectMapper(),mock(DemoMatchService.class),mock(GameStateMapper.class),online,mock(SpectatorService.class));
        handler.operations(new RequestLimits(Clock.fixed(java.time.Instant.EPOCH,java.time.ZoneOffset.UTC),100,1),metrics);
        var first=socket("first");handler.afterConnectionEstablished(first);
        for(int i=0;i<3;i++)handler.handleMessage(first,new TextMessage("{\"type\":\"PING\"}"));
        verify(online,times(2)).handle(eq(first),anyString());
        verify(first).sendMessage(argThat(message->message.getPayload().toString().contains("RATE_LIMITED")));
        assertThat(meters.get("websocket_sessions").gauge().value()).isEqualTo(1);
        handler.afterConnectionClosed(first,CloseStatus.NORMAL);
        var second=socket("second");handler.afterConnectionEstablished(second);handler.handleMessage(second,new TextMessage("{\"type\":\"PING\"}"));
        verify(online,never()).handle(eq(second),anyString());
        handler.afterConnectionClosed(second,CloseStatus.NORMAL);assertThat(meters.get("websocket_sessions").gauge().value()).isZero();
    }
    @Test void onlyTimeoutCausesIncrementTimeoutMetric() {
        var meters=new SimpleMeterRegistry();var metrics=new RuntimeMetrics(meters);
        metrics.botFailure(new IllegalStateException("invalid response"));metrics.botFailure(new IllegalStateException("timeout",new java.util.concurrent.TimeoutException()));
        assertThat(meters.get("bot_timeout_total").counter().count()).isEqualTo(1);
    }
    private WebSocketSession socket(String id) {
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn(id);when(socket.isOpen()).thenReturn(true);
        when(socket.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.42",12345));return socket;
    }
}
