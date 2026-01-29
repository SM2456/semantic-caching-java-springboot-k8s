package com.acme.cache.core;

import java.util.Optional;

public interface RedisSemanticStore {

    record Hit(String key, double score, String payloadJson) {}

    void ensureIndex();
    Optional<Hit> search(String tenantId, float[] embedding);
    void upsert(String tenantId, String normalizedQuery, float[] embedding, String payloadJson, long tsMillis);
}
