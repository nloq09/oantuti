package com.rpsbattle.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
@Configuration
@EnableScheduling
public class OnlineConfig {
    @Bean public Clock gameClock() { return Clock.systemUTC(); }
}
