package com.acme.cache.api;

import com.acme.cache.core.SemanticCacheService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class SemanticCacheController {

    private final SemanticCacheService service;

    public SemanticCacheController(SemanticCacheService service) {
        this.service = service;
    }


    @PostMapping("/api/semantic-query")
    public SemanticQueryResponse query(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody SemanticQueryRequest req
    ) {
        String resolvedUserId = (userId == null || userId.isBlank()) ? "user-123" : userId;
        String resolvedTenantId = (tenantId == null || tenantId.isBlank()) ? "tenant-001" : tenantId;
        return service.handle(resolvedUserId, resolvedTenantId, req.query());
    }
}
