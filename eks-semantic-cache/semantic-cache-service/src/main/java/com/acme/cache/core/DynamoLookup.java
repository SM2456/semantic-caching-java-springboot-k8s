package com.acme.cache.core;

public interface DynamoLookup {
    String fetchByQueryKey(String tenantId, String normalizedQuery);
}
