package com.acme.cache.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Component
public class RedisSemanticStoreImpl implements RedisSemanticStore {

    private final JedisPooled jedis;

    @Value("${app.cache.indexName}") private String indexName;
    @Value("${app.cache.keyPrefix}") private String keyPrefix;
    @Value("${app.cache.ttlSeconds}") private int ttlSeconds;
    @Value("${app.cache.topK}") private int topK;
    @Value("${app.cache.minScore}") private double minScore;
    @Value("${app.bedrock.embeddingDim}") private int embeddingDim;

    public RedisSemanticStoreImpl(
            @Value("${app.cache.redisHost}") String host,
            @Value("${app.cache.redisPort}") int port
    ) {
        this.jedis = new JedisPooled(host, port);
    }

    @Override
    public void ensureIndex() {

        try {

            jedis.sendCommand(cmd("FT.INFO"), indexName);
            return;
        } catch (Exception ignored) { }

        List<String> args = new ArrayList<>();
        args.add(indexName);
        args.add("ON"); args.add("HASH");
        args.add("PREFIX"); args.add("1"); args.add(keyPrefix);
        args.add("SCHEMA");
        args.add("tenant"); args.add("TAG");
        args.add("query"); args.add("TEXT");
        args.add("payload"); args.add("TEXT");
        args.add("ts"); args.add("NUMERIC");
        args.add("vec"); args.add("VECTOR"); args.add("HNSW"); args.add("6");
        args.add("TYPE"); args.add("FLOAT32");
        args.add("DIM"); args.add(String.valueOf(embeddingDim));
        args.add("DISTANCE_METRIC"); args.add("COSINE");

        jedis.sendCommand(cmd("FT.CREATE"), args.toArray(new String[0]));
    }

    @Override
    public Optional<Hit> search(String tenantId, float[] embedding) {

        byte[] blob = toFloat32LE(embedding);

        String query = String.format("(@tenant:{%s})=>[KNN %d @vec $BLOB AS score]", escapeTag(tenantId), topK);

        List<byte[]> args = new ArrayList<>();
        args.add(SafeEncoder.encode(indexName));
        args.add(SafeEncoder.encode(query));
        args.add(SafeEncoder.encode("PARAMS")); args.add(SafeEncoder.encode("2"));
        args.add(SafeEncoder.encode("BLOB")); args.add(blob);
        args.add(SafeEncoder.encode("SORTBY")); args.add(SafeEncoder.encode("score"));
        args.add(SafeEncoder.encode("DIALECT")); args.add(SafeEncoder.encode("2"));
        args.add(SafeEncoder.encode("RETURN")); args.add(SafeEncoder.encode("2"));
        args.add(SafeEncoder.encode("payload")); args.add(SafeEncoder.encode("score"));

        Object raw = jedis.sendCommand(cmd("FT.SEARCH"), args.toArray(new byte[0][]));

        if (!(raw instanceof List<?> list) || list.size() < 2) return Optional.empty();
        if (Long.parseLong(list.get(0).toString()) == 0) return Optional.empty();

        String key = list.get(1).toString();
        @SuppressWarnings("unchecked")
        List<Object> fields = (List<Object>) list.get(2);

        String payload = null;
        Double score = null;
        for (int i = 0; i < fields.size(); i += 2) {
            String f = fields.get(i).toString();
            Object v = fields.get(i + 1);
            if ("payload".equals(f)) payload = v.toString();
            if ("score".equals(f)) {
                double dist = Double.parseDouble(v.toString());
                score = 1.0 - dist;
            }
        }

        if (payload == null || score == null) return Optional.empty();
        if (score < minScore) return Optional.empty();

        return Optional.of(new Hit(key, score, payload));
    }

    @Override
    public void upsert(String tenantId, String normalizedQuery, float[] embedding, String payloadJson, long tsMillis) {
        String key = keyPrefix + tenantId + ":" + stableKey(normalizedQuery);

        Map<String, String> hash = new HashMap<>();
        hash.put("tenant", tenantId);
        hash.put("query", normalizedQuery);
        hash.put("payload", payloadJson);
        hash.put("ts", String.valueOf(tsMillis));

        jedis.hset(key, hash);
        jedis.hset(key.getBytes(), "vec".getBytes(), toFloat32LE(embedding));
        jedis.expire(key, ttlSeconds);
    }

    private static byte[] toFloat32LE(float[] vec) {
        ByteBuffer bb = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) bb.putFloat(v);
        return bb.array();
    }

    private static String stableKey(String normalizedQuery) {

        return Integer.toHexString(normalizedQuery.hashCode());
    }

    private static String escapeTag(String s) {

        return s.replace("{", "\\{").replace("}", "\\}").replace(":", "\\:");
    }

    private static ProtocolCommand cmd(String name) {
        return () -> SafeEncoder.encode(name);
    }
}
