package com.acme.cache.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.util.Random;

@Service
public class BedrockTitanEmbeddingService implements EmbeddingService {

    private final BedrockRuntimeClient client;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.bedrock.embeddingModelId}")
    private String modelId;

    @Value("${app.bedrock.embeddingDim}")
    private int dim;

    @Value("${app.bedrock.allowFallback:true}")
    private boolean allowFallback;

    public BedrockTitanEmbeddingService(@Value("${app.bedrock.region}") String region) {
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public float[] embed(String text) {

        try {
            String body = om.createObjectNode()
                    .put("inputText", text)
                    .toString();

            InvokeModelRequest req = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                    .build();

            InvokeModelResponse resp = client.invokeModel(req);
            String json = resp.body().asUtf8String();

            JsonNode root = om.readTree(json);
            JsonNode emb = root.get("embedding");
            if (emb == null || !emb.isArray()) {
                throw new IllegalStateException("Bedrock response missing 'embedding': " + json);
            }

            int n = emb.size();

            float[] out = new float[n];
            for (int i = 0; i < n; i++) out[i] = (float) emb.get(i).asDouble();
            return out;

        } catch (Exception e) {
            if (allowFallback) {
                return fallbackEmbedding(text, dim);
            }
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    private static float[] fallbackEmbedding(String text, int dim) {
        int size = Math.max(dim, 1);
        float[] out = new float[size];
        Random r = new Random(text.hashCode());
        for (int i = 0; i < size; i++) {
            out[i] = (float) r.nextGaussian();
        }
        return out;
    }
}
