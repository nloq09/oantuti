package com.rpsbattle.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Separate JVM fixture: stdout is used only for JSON Lines. */
public final class TestBotProcessMain {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = input.readLine()) != null) {
            switch (args[0]) {
                case "hang" -> { Thread.sleep(30_000); return; }
                case "exit" -> { return; }
                case "partial" -> { output.print("{\"type\":"); output.flush(); return; }
                case "oversize" -> { output.println("x".repeat(70_000)); continue; }
                case "bad-json" -> { output.println("not JSON"); continue; }
                case "stderr" -> { System.err.print("log".repeat(100_000)); System.err.flush(); }
            }
            var request = mapper.readTree(line);
            var response = mapper.createObjectNode();
            response.put("type", "ACTION");
            response.put("sequence", request.get("sequence").asLong() + (args[0].equals("stale") ? 1 : 0));
            response.set("action", request.get("legalActions").get(0));
            if (args[0].equals("illegal")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) response.get("action").get("to")).put("row", 99);
            }
            output.println(mapper.writeValueAsString(response));
        }
    }
}
