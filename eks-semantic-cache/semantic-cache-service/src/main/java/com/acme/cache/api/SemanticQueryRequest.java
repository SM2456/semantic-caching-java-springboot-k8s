package com.acme.cache.api;

import jakarta.validation.constraints.NotBlank;

public record SemanticQueryRequest(
        @NotBlank String query
) {}
