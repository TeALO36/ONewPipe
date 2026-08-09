# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# git is required because app/build.gradle.kts runs `git rev-parse`
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
COPY . .
RUN ./gradlew :server:fatJar --no-daemon --console=plain

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/server/build/libs/onewpipe-server-all.jar /app/onewpipe-server.jar
ENV PORT=8080 DATA_DIR=/app/data
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/onewpipe-server.jar"]
