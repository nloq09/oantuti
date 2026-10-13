package com.rpsbattle.bot.ownership;
import java.util.*;
public interface BotRepository {
    Optional<BotMetadata> find(UUID id);
    Optional<BotMetadata> lock(UUID id);
    List<BotMetadata> owned(UUID owner);
    BotMetadata create(BotMetadata bot);
    BotMetadata save(BotMetadata bot);
    long countActive();
}
