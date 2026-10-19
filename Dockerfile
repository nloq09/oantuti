FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY server/build.gradle server/settings.gradle server/
COPY worker/build.gradle worker/
COPY worker/src worker/src
COPY server/src server/src
COPY client client
WORKDIR /workspace/server
RUN gradle bootJar --no-daemon --console=plain

FROM docker:29-cli AS docker-cli
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 rps && useradd --uid 10001 --gid rps --no-create-home rps \
    && mkdir -p /app/data/bots /app/data/replays \
    && chown -R 10001:10001 /app
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=build /workspace/server/build/libs/rps-battle-1.0-SNAPSHOT.jar /app/server.jar
WORKDIR /app
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/server.jar"]
