package com.acme.auth;

import com.acme.auth.core.AuthDecision;
import com.acme.auth.core.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Called by NGINX auth_request (can be GET or POST)
    @RequestMapping(path = "/verify", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> verify(HttpServletRequest request) {
        AuthDecision decision = authService.verify(request);

        if (!decision.allowed()) {
            return ResponseEntity.status(decision.statusCode()).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", decision.userId());
        headers.add("X-Tenant-Id", decision.tenantId());
        return ResponseEntity.ok().headers(headers).build();
    }
}
