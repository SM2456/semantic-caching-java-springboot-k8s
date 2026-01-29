package com.acme.auth.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Value("${auth.apiKey}")
    private String apiKey;

    public AuthDecision verify(HttpServletRequest req) {
        // Minimal “industry standard” gateway auth pattern:
        // 1) Validate API key or JWT
        // 2) Validate method/path (optional)
        // 3) Return identity headers for downstream services

        String provided = req.getHeader("X-Api-Key");
        if (provided == null || !provided.equals(apiKey)) {
            return AuthDecision.deny(401);
        }

        // Example identity extraction; in real life map API key -> user/tenant
        String userId = "user-123";
        String tenantId = "tenant-001";
        return AuthDecision.allow(userId, tenantId);
    }
}
