# eks-semantic-cache

Multi-module Spring Boot setup for a simple auth gateway and semantic cache service behind an Nginx gateway.

## Modules
- request-auth-service (port 8081)
- semantic-cache-service (port 8082)

## Build
```
./mvnw -DskipTests package
```

## Run (Docker Compose)
```
docker compose up --build
```