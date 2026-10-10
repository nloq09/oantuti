package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Standalone seeded bot: selects a supplied action, without importing the engine.
 */
public final class DemoBotApplication {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide a seed");
        Random random = new Random(Long.parseLong(args[0]));
        ObjectMapper mapper = new ObjectMapper();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = input.readLine()) != null) {
            var turn = mapper.readTree(line);
            if (!turn.path("type").asText().equals("YOUR_TURN"))
                throw new IllegalArgumentException("Expected YOUR_TURN");
            var actions = turn.get("legalActions");
            if (actions == null || !actions.isArray() || actions.isEmpty())
                throw new IllegalArgumentException("No legal actions");
            var response = mapper.createObjectNode();
            response.put("type", "ACTION");
            response.set("sequence", turn.get("sequence"));
            response.set("action", actions.get(random.nextInt(actions.size())));
            output.println(mapper.writeValueAsString(response));
        }
    }
}
