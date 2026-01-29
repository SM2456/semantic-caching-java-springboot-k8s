package com.acme.cache.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

@Component
public class DynamoLookupImpl implements DynamoLookup {

    private final DynamoDbClient dynamo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.dynamodb.tableName}") private String tableName;
    @Value("${app.dynamodb.pkName}") private String pkName;

    public DynamoLookupImpl(@Value("${app.bedrock.region}") String region) {
        this.dynamo = DynamoDbClient.builder().region(Region.of(region)).build();
    }

    @Override
    public String fetchByQueryKey(String tenantId, String normalizedQuery) {
        String pk = tenantId + ":" + normalizedQuery;

        try {
            GetItemRequest req = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(pkName, AttributeValue.fromS(pk)))
                    .consistentRead(false)
                    .build();

            GetItemResponse resp = dynamo.getItem(req);
            if (resp.item() == null || resp.item().isEmpty()) {
                return "{\"message\":\"not found\"}";
            }

            AttributeValue payload = resp.item().get("payload");
            if (payload == null || payload.s() == null) {
                // fallback to returning full item
                try { return om.writeValueAsString(resp.item()); }
                catch (Exception e) { return resp.item().toString(); }
            }
            return payload.s();
        } catch (SdkClientException | DynamoDbException e) {
            return "{\"message\":\"DynamoDB unavailable\",\"pk\":\"" + pk + "\"}";
        }
    }
}
