package com.rpsbattle.bot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * One persistent process per bot, UTF-8 JSON Lines; stderr is separate from protocol.
 */
public final class LocalProcessRunner implements BotProcess {
    private static final int MAX_LINE = 65_536;
    private final List<String> command;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private Process process;
    private Writer stdin;
    private Reader stdout;
    private boolean closed;

    public LocalProcessRunner(List<String> command) {
        this.command = List.copyOf(command);
        if (command.isEmpty() || command.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Bot command must contain nonblank arguments");
        }
    }

    @Override
    public synchronized void start() {
        if (closed) throw new BotProtocolException("Bot process is closed");
        if (process != null) {
            if (!process.isAlive()) throw new BotProtocolException("Bot process has exited");
            return;
        }
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            close();
            throw new BotProtocolException("Cannot start bot process", e);
        }
    }

    @Override
    public synchronized String exchange(String request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Timeout must be positive");
        if (request.contains("\n") || request.contains("\r"))
            throw new IllegalArgumentException("Request must be one JSON line");
        start();
        Future<String> response = io.submit(() -> {
            stdin.write(request);
            stdin.write('\n');
            stdin.flush();
            StringBuilder line = new StringBuilder();
            int character;
            while ((character = stdout.read()) != -1) {
                if (character == '\n') return line.toString();
                if (line.length() >= MAX_LINE) throw new IOException("Bot response exceeds maximum line length");
                line.append((char) character);
            }
            throw new EOFException("Bot stdout closed before a complete JSON line");
        });
        try {
            return response.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            close();
            throw new BotProtocolException("Bot response timed out", e);
        } catch (InterruptedException e) {
            response.cancel(true);
            close();
            Thread.currentThread().interrupt();
            throw new BotProtocolException("Interrupted while waiting for bot", e);
        } catch (ExecutionException e) {
            close();
            throw new BotProtocolException("Bot communication failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
            }
            try {
                process.getErrorStream().close();
            } catch (IOException ignored) {
            }
        }
        io.shutdownNow();
    }
}
