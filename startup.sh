#!/bin/bash
set -e

# Cleanup function for error handling
cleanup() {
    local exit_code=$?
    if [ $exit_code -ne 0 ]; then
        echo "❌ Error occurred (exit code: $exit_code). Cleaning up..."
        rm -f .env
    fi
    exit $exit_code
}
trap cleanup EXIT

# Pindah ke direktori project
cd "$(dirname "$(realpath "$0")")"

# Buat file .env temporer untuk dibaca oleh docker-compose
cat << EOF > .env
# ── Database ──────────────────────────────────────────────────────────────────
SAASNO_DATABASE_URL=jdbc:postgresql://saasno-database:5432/saasno
SAASNO_DATABASE_USERNAME=database
SAASNO_DATABASE_PASSWORD=database

# ── RabbitMQ ──────────────────────────────────────────────────────────────────
SAASNO_RABBITMQ_HOST=saasno-rabbitmq
SAASNO_RABBITMQ_PORT=5672
SAASNO_RABBITMQ_USERNAME=admin
SAASNO_RABBITMQ_PASSWORD=admin123

# ── Redis ─────────────────────────────────────────────────────────────────────
SAASNO_REDIS_HOST=saasno-redis
SAASNO_REDIS_PORT=6379
SAASNO_REDIS_PASSWORD=admin

# ── MongoDB (audit log) ───────────────────────────────────────────────────────
SAASNO_MONGODB_URI=mongodb://database:database@saasno-mongodb:27017/saasno?authSource=admin

# ── Email SMTP (lupa password) ────────────────────────────────────────────────
# Server SMTP dan pengirim email (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_AUTH, MAIL_STARTTLS, MAIL_FROM,
# MAIL_FROM_NAME) diatur di menu Konfigurasi, bukan di .env; selama MAIL_HOST kosong, tidak ada email terkirim.

# ── Dokumentasi API (Swagger UI di /swagger-ui.html) ──────────────────────────
# Aktifkan (true) hanya di dev/UAT; produksi sebaiknya false.
SAASNO_API_DOCS_ENABLED=false
EOF

# Pastikan network docker tersedia
docker network create saasno-backend || true
# Jaringan bersama Prometheus (monitoring); compose memakainya sebagai external, jadi harus ada sebelum 'up'
docker network create fajarnhapp-prometheus || true

# Jalankan docker compose dengan flag --build untuk memicu kompilasi Maven di dalam Docker
docker compose up -d --build --remove-orphans

# Hapus file .env setelah up demi keamanan
rm -f .env

# Opsional: Bersihkan cache image lama agar disk VPS tidak penuh
docker image prune -f

echo "✅ Deployment completed successfully!"