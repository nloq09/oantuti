package com.rpsbattle.online;

/**
 * Stable demo error code; no Java exception details are sent to clients.
 */
public class DemoRequestException extends RuntimeException {
    public DemoRequestException(String code) {
        super(code);
    }

    public String code() {
        return getMessage();
    }
}
