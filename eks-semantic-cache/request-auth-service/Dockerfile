FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY request-auth-service/pom.xml ./request-auth-service/pom.xml
COPY semantic-cache-service/pom.xml ./semantic-cache-service/pom.xml
COPY request-auth-service/src ./request-auth-service/src
RUN mvn -q -DskipTests -pl request-auth-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/request-auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]