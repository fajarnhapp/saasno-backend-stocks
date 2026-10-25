# ==========================================
# STAGE 1: Build Stage (Maven + JDK 25, sesuai java.version di pom.xml)
# ==========================================
FROM maven:3.9-eclipse-temurin-26 AS builder

WORKDIR /app

# Salin pom.xml lebih dulu supaya layer dependency bisa di-cache selama pom.xml tidak berubah
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Salin source code project dan build (menghasilkan target/saasno-backend-<versi>.jar)
COPY src ./src
RUN mvn -B -q clean package -DskipTests


# ==========================================
# STAGE 2: Runtime Stage (hanya JRE 25)
# ==========================================
FROM eclipse-temurin:25-jre AS runner

ENV TZ=Asia/Jakarta
ENV DEBIAN_FRONTEND=noninteractive

# Timezone + curl (dipakai HEALTHCHECK di docker-compose.yml)
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone && \
    apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Set direktori kerja sesuai standard Anda
WORKDIR /home/application/data/apps/saasno-backend

# Ambil hasil .jar yang sudah jadi dari Stage 1 (builder); nama di dalam container dibuat tetap saasno-backend.jar karena dipakai docker-entrypoint.sh
COPY --from=builder /app/target/saasno-backend-*.jar ./saasno-backend.jar

# Salin file konfigurasi logback & skrip runner internal
COPY src/main/resources/logback.xml ./logback.xml
# Entrypoint bernama docker-entrypoint.sh seperti image resmi lain (Postgres, Redis, ...)
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8000

ENTRYPOINT ["docker-entrypoint.sh"]
