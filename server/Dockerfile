# ---- Web interface ----
FROM node:22-slim AS webapp
WORKDIR /webapp
COPY webapp/package.json webapp/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY webapp/ ./
RUN npm run build

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# git is required because app/build.gradle.kts runs `git rev-parse`
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
COPY . .
COPY --from=webapp /webapp/dist webapp/dist
# The interface was built above; Gradle only packs webapp/dist into the jar.
RUN ./gradlew :server:fatJar -PskipWebapp --no-daemon --console=plain

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/server/build/libs/onewpipe-server-all.jar /app/onewpipe-server.jar
ENV PORT=8080 DATA_DIR=/app/data
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/onewpipe-server.jar"]
