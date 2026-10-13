package com.rpsbattle.auth;

public class AuthException extends RuntimeException {
    private final String code;
    private final int status;
    public AuthException(String code, int status) { super(code);this.code=code;this.status=status; }
    public String code() { return code; }
    public int status() { return status; }
}
