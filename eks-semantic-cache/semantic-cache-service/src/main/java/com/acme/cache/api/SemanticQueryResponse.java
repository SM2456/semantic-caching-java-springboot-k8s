package com.acme.cache.api;

public record SemanticQueryResponse(
        String source,
        double score,
        String payloadJson
) {}
