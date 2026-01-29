# Semantic Caching on EKS (Spring Boot + NGINX + Redis + DynamoDB)

A reference implementation of a semantic cache gateway stack deployed on Amazon EKS. The system uses:
- **NGINX** as the edge gateway (auth_request pattern)
- **request-auth-service** for API key verification and identity headers
- **semantic-cache-service** for embedding generation, cache lookup, and DynamoDB fallback
- **Redis Stack** for vector similarity search
- **DynamoDB** as a durable source of truth
- **ECR** to store container images consumed by EKS

This repo is focused on the **project flow and architecture** for an EKS deployment. Local Docker Compose is included for fast iteration.

## Architecture (EKS + ECR + DynamoDB)
```
  .-----------------------.        .------------------------.        .--------------------.
  :       Client / App    : -----> : Load Balancer / Ingress: -----> :   NGINX Gateway    :
  '-----------------------'        '------------------------'        '--------------------'
                                                                          |         \
                                                                          | auth_request /auth/verify
                                                                          v           \
                                                             .-------------------------.  \
                                                             :  request-auth-service  :   \
                                                             '-------------------------'    \
                                                                          | X-User-Id, X-Tenant-Id
                                                                          v
                                                              .------------------------.
                                                              : semantic-cache-service :
                                                              '------------------------'
                                                                 | embed(query)   | vector search
                                                                 v                v
                                                     .-----------------.   .----------------------.
                                                     : AWS Bedrock     :   : Redis Stack (Vector) :
                                                     '-----------------'   '----------------------'
                                                                 | cache miss        ^
                                                                 v                   |
                                                           .-----------.             |
                                                           : DynamoDB  : --- upsert --'
                                                           '-----------'

  .------------------------------.                    .---------------------------.
  :             ECR              : -- image pull -->  :        EKS Cluster         :
  : - nginx-gateway image        :                    : NGINX | Auth | Cache | Redis:
  : - request-auth-service image :                    '---------------------------'
  : - semantic-cache-service img :
  '------------------------------'
```

## Sequence Diagram (Semantic Query)
```
  .-----------------------.      .--------------------.      .------------------------.
  :       Client / App    : ---> :   NGINX Gateway    : ---> :  request-auth-service  :
  '-----------------------'      '--------------------'      '------------------------'
            |                              |                         |
            | POST /api/semantic-query     | auth_request /auth/verify|
            v                              |<---- X-User-Id/X-Tenant-Id
  .-----------------------.                v
  : semantic-cache-service: <--------------'
  '-----------------------'
            |
            | embed(normalized query)
            v
  .-----------------.     vector search (topK)     .----------------------.
  : AWS Bedrock     : ---------------------------> : Redis Stack (Vector) :
  '-----------------'                              '----------------------'
            |                                                  |
            | cache miss                                       | cache hit
            v                                                  v
  .-----------.                                      .----------------------.
  : DynamoDB  : ---- payload ----------------------> : semantic-cache-service :
  '-----------'                                      '----------------------'
            |                                                  |
            | upsert vector + payload                          | Response (source=redis)
            v                                                  v
  .----------------------.                           .-----------------------.
  : Redis Stack (Vector) : <-----------------------  :       Client / App     :
  '----------------------'                           '-----------------------'
```

## Request Flow (Runtime)
1. **Client** calls `POST /api/semantic-query` on the gateway.
2. **NGINX** runs `auth_request` against `request-auth-service` (`/auth/verify`).
3. On success, auth service returns `X-User-Id` and `X-Tenant-Id` headers.
4. **semantic-cache-service** normalizes the query and requests an **embedding** from **Bedrock**.
5. It performs a **vector similarity search** in **Redis**.
6. If **hit**, return cached payload (source = `redis`).
7. If **miss**, fetch from **DynamoDB** (source of truth), then **upsert** into Redis and return (source = `dynamodb`).

## Repository Layout
- `eks-semantic-cache/`
  - `nginx/` NGINX gateway container and config
  - `request-auth-service/` Spring Boot auth service
  - `semantic-cache-service/` Spring Boot semantic cache service
  - `k8s/` Kubernetes manifests (namespace, deployments, services, configmaps)
  - `docker-compose.yml` Local dev stack

## Local Development (Docker Compose)
From `eks-semantic-cache/`:
```bash
./mvnw -DskipTests package

docker compose up --build
```

The gateway runs on `http://localhost:8080` and proxies to:
- Auth service: `http://localhost:8081`
- Semantic cache service: `http://localhost:8082`

## EKS + ECR Deployment Flow
1. **Build images** for the three services (`nginx-gateway`, `request-auth-service`, `semantic-cache-service`).
2. **Push to ECR** and tag them (for example: `123456789012.dkr.ecr.us-east-1.amazonaws.com/semantic-cache-service:1.0.0`).
3. **Update the Kubernetes manifests** in `eks-semantic-cache/k8s/` to reference ECR image URIs.
4. **Deploy to EKS** using `kubectl apply -f k8s/`.
5. Expose NGINX via **LoadBalancer** or **Ingress** (the sample manifest uses NodePort for simplicity).

## Configuration (Key Environment Variables)
**semantic-cache-service** (`application.yml`):
- `APP_CACHE_REDISHOST`, `APP_CACHE_REDISPORT`
- `APP_BEDROCK_REGION`, `APP_BEDROCK_EMBEDDINGMODELID`, `APP_BEDROCK_EMBEDDINGDIM`
- `APP_DYNAMODB_TABLENAME`, `APP_DYNAMODB_PKNAME`

**request-auth-service**:
- `AUTH_APIKEY` (simple API key auth)

## DynamoDB Table Expectations
The service reads by partition key:
- **Partition key**: `pk` (format: `{tenantId}:{normalizedQuery}`)
- **Payload**: string attribute named `payload` (JSON response to cache)

Example item:
```json
{
  "pk": "tenant-001:how do i reset my password",
  "payload": "{\"answer\":\"...\"}"
}
```

