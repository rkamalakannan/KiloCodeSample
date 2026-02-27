# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Uses Eclipse Temurin (formerly AdoptOpenJDK) — free, production-grade JDK
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper + pom first (layer caching: only re-download deps when pom changes)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# JRE-only image: ~100MB smaller than JDK
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

WORKDIR /app

# Copy only the fat JAR from builder
COPY --from=builder /build/target/ta4j-trading-bot-*.jar app.jar

# ─── JVM Optimization Flags ───────────────────────────────────────────────────
# ZGC: low-latency GC ideal for trading (sub-millisecond pauses)
# -Xms128m / -Xmx512m: fits in Railway/Fly.io free tier (512MB RAM)
# -XX:+UseStringDeduplication: reduces heap for repeated strings
# -Djava.security.egd: faster SecureRandom (important for Spring startup)
ENV JAVA_OPTS="\
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms128m \
  -Xmx512m \
  -XX:+OptimizeStringConcat \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.jmx.enabled=false \
  -Dspring.main.lazy-initialization=true"

# Health check for container orchestrators
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-8080}/api/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
