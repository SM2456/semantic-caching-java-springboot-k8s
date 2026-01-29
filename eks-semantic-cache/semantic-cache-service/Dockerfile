FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY request-auth-service/pom.xml ./request-auth-service/pom.xml
COPY semantic-cache-service/pom.xml ./semantic-cache-service/pom.xml
COPY semantic-cache-service/src ./semantic-cache-service/src
RUN mvn -q -DskipTests -pl semantic-cache-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/semantic-cache-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]