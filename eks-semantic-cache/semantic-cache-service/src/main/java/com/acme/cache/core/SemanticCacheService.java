package com.acme.cache.core;

import com.acme.cache.api.SemanticQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SemanticCacheService {

    private final EmbeddingService embeddingService;
    private final RedisSemanticStore redisStore;
    private final DynamoLookup dynamo;
    private final ObjectMapper om = new ObjectMapper();

    public SemanticCacheService(
            EmbeddingService embeddingService,
            RedisSemanticStore redisStore,
            DynamoLookup dynamo
    ) {
        this.embeddingService = embeddingService;
        this.redisStore = redisStore;
        this.dynamo = dynamo;
    }

    public SemanticQueryResponse handle(String userId, String tenantId, String query) {
        String normalized = normalize(query);

        float[] embedding = embeddingService.embed(normalized);

        Optional<RedisSemanticStore.Hit> hit = redisStore.search(tenantId, embedding);

        if (hit.isPresent()) {
            RedisSemanticStore.Hit h = hit.get();
            return new SemanticQueryResponse("redis", h.score(), h.payloadJson());
        }


        String payloadJson = dynamo.fetchByQueryKey(tenantId, normalized);


        redisStore.upsert(
                tenantId,
                normalized,
                embedding,
                payloadJson,
                Instant.now().toEpochMilli()
        );

        return new SemanticQueryResponse("dynamodb", 0.0, payloadJson);
    }

    private String normalize(String q) {
        return q.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
