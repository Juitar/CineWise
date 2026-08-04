FROM maven:3.9.11-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,id=cinewise-backend-m2,target=/root/.m2/repository,sharing=locked \
    mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
COPY config ./config
RUN --mount=type=cache,id=cinewise-backend-m2,target=/root/.m2/repository,sharing=locked \
    mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S cinewise && adduser -S cinewise -G cinewise
COPY --from=builder --chown=cinewise:cinewise /workspace/target/cinewise-backend-*.jar app.jar
USER cinewise
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
