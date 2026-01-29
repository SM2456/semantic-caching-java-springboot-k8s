package com.acme.auth.core;

public record AuthDecision(boolean allowed, int statusCode, String userId, String tenantId) {
    public static AuthDecision allow(String userId, String tenantId) {
        return new AuthDecision(true, 200, userId, tenantId);
    }
    public static AuthDecision deny(int statusCode) {
        return new AuthDecision(false, statusCode, null, null);
    }
}
