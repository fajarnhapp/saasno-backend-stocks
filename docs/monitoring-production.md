# Monitoring di produksi (EC2)

Semua komponen berjalan sebagai container terpisah di `/home/application/data/apps/fajarnhapp/<nama>` dan **tidak ada port yang dibuka ke publik**:
semua `ports` terikat ke `127.0.0.1`, dan cAdvisor, node-exporter, exporter database, serta Loki tidak punya port ke host sama sekali. Buka dari luar lewat
SSH tunnel atau reverse proxy ber-HTTPS dan login. Jangan buka port 3030, 9090, 3001, 12345, 12347 di security group.

## 1. Komponen

| Folder | Fungsi |
|---|---|
| `loki` | Penyimpanan log (1 tahun) |
| `alloy` | Membaca log backend dan frontend dari file; menerima telemetri browser (Faro) di port 12347 |
| `prometheus` | Metrik: backend, frontend (Next.js), cAdvisor, node-exporter, PostgreSQL, Redis, MongoDB, RabbitMQ |
| `cadvisor` | CPU, memori, jaringan, disk I/O per container |
| `node-exporter` | CPU, RAM, disk server (host) |
| `exporters` | Exporter PostgreSQL, Redis, MongoDB (RabbitMQ memakai plugin bawaannya) |
| `grafana` | Dashboard, aturan alert (7 + 3 + 1), peringatan Telegram |
| `uptime-kuma` | Cek hidup/mati dan notifikasi |
| `monitoring-backup` | Backup harian terjadwal volume monitoring, berjalan sebagai container dan bisa dipantau (metrik, `/health`, alert) |

## 2. Urutan menjalankan

Setiap folder punya `startup.sh` (membuat jaringan Docker bila belum ada, lalu `docker compose up --build -d`).

| # | Folder | Perintah | Catatan |
|---|---|---|---|
| 1 | `loki` | `./startup.sh` | |
| 2 | `alloy` | `FRONTEND_LOG_DIR=/data/logs/com/saasno/frontend ./startup.sh` | Tanpa variabel itu Alloy membaca folder dev dan log frontend produksi tidak terbaca |
| 3 | `prometheus` | `BACKEND_TARGET=saasno-backend:8081 FRONTEND_TARGET=saasno-frontend:9464 ./startup.sh` | Tanpa variabel itu target-nya `host.docker.internal` (khusus dev) |
| 4 | `cadvisor` | `./startup.sh` | Lihat bagian 4 |
| 5 | `node-exporter` | `./startup.sh` | |
| 6 | `exporters` | `./startup.sh` | Membuat akun `monitoring` (hanya-baca) di PostgreSQL dan MongoDB; butuh database berjalan |
| 7 | `grafana` | isi Telegram dulu (bagian 5), lalu `./startup.sh` | |
| 8 | `uptime-kuma` | `./startup.sh` | Bergabung ke jaringan `fajarnhapp-prometheus` dan `fajarnhapp-loki` |
| 9 | `monitoring-backup` | `BACKUP_DIR=/backup/monitoring ./startup.sh` | Setelah project lain berjalan (yang dibackup adalah volumenya); backup pertama langsung dibuat |

Aplikasi: backend (`saasno-backend/startup.sh`) harus berjalan sebagai container **`saasno-backend`** dan frontend sebagai **`saasno-frontend`**. Keduanya bergabung ke
`fajarnhapp-prometheus` (metrik di port 8081 dan 9464, tidak dipublikasikan) dan frontend juga ke `fajarnhapp-loki` (telemetri browser ke `fajarnhapp-alloy:12347`).
Jaringan itu dibuat oleh `startup.sh` dan oleh langkah deploy di workflow GitHub, jadi aplikasi tetap bisa dideploy tanpa monitoring terpasang.

Path log harus sama di tiga tempat: `LOG_PATH` di `logback.xml`, volume di `saasno-backend/docker-compose.yml`, dan mount `/logs` di `alloy/docker-compose.yml`
(`/home/application/data/logs/com/saasno`). Log frontend: volume di `saasno-frontend/docker-compose.yml` (`/data/logs/com/saasno/frontend`) sama dengan `FRONTEND_LOG_DIR`.

## 3. Yang sudah diuji, dan yang belum

Diuji di mesin dev (WSL2): seluruh jalur backend sebagai container `saasno-backend` dari `docker-compose.yml` asli (sehat, tanpa port ke host), Prometheus yang dibangun dengan
`BACKEND_TARGET=saasno-backend:8081` (target UP, metrik JVM dan Hikari terbaca), cAdvisor melihat container itu, `/actuator/prometheus` di port aplikasi tertutup (401),
di port 8081 hanya endpoint metrik yang terbuka tanpa sesi (`/actuator/env` 401). Exporter, Faro (error browser, token tersaring), metrik Next.js (build standalone), backup dan
pemulihannya, serta pengiriman alert ke Telegram juga diuji di dev.

**Belum teruji, periksa di EC2:** cAdvisor dan node-exporter di OS EC2 (bagian 4), frontend sebagai container yang bergabung ke jaringan `fajarnhapp-loki` dan
`fajarnhapp-prometheus` (target `saasno-frontend:9464`, dan `/api/faro` ke Alloy), serta jalur log frontend produksi.

## 4. Perbedaan EC2 yang perlu diperiksa

**cAdvisor** (`cadvisor/docker-compose.yml`) memakai dua socket read-only: `/var/run/docker.sock` dan `/run/containerd/containerd.sock`, dengan
`--containerd-namespace=moby`. Itu perlu di Docker yang menyimpan container lewat containerd (Docker 29 di WSL2 begitu). Jika di EC2
`/run/containerd/containerd.sock` tidak ada, **hapus baris mount itu dan flag `--containerd-namespace=moby`**: Docker membuat *direktori* di tempat file
socket yang hilang, dan itu merusak container. Mount `docker.sock` tetap diperlukan.

Pemeriksaan setelah jalan (harus muncul nama container, bukan hanya `id="/"`):

```bash
docker run --rm --network fajarnhapp-prometheus curlimages/curl:8.11.1 -s http://fajarnhapp-cadvisor:8080/metrics \
  | grep '^container_memory_working_set_bytes' | grep -o 'name="[^"]*"' | sort -u
```

Grup metrik `disk` dimatikan karena menjalankan `du` berat (±240 MiB RAM) dan usage filesystem per container tidak tersedia di host uji.

**node-exporter** memakai `/:/host:ro` tanpa `rslave` (ditolak Docker bila root host bukan mount shared). Bandingkan dengan `free -b` dan `df /`:

```bash
docker run --rm --network fajarnhapp-prometheus curlimages/curl:8.11.1 -s http://fajarnhapp-node-exporter:9100/metrics \
  | grep -E '^node_memory_MemTotal_bytes|^node_filesystem_size_bytes\{.*mountpoint="/"'
```

Pemakaian disk di dashboard memakai `size - free` (sama dengan `df`); alert memakai `avail`, yang lebih konservatif karena blok cadangan ext4 (±5%) ikut dihitung terpakai.

**Exporter database** (`exporters/startup.sh`): membuat role `monitoring` (pg_monitor) di PostgreSQL dan user `monitoring` (clusterMonitor) di MongoDB dengan kata sandi acak di
`exporters/secrets/` (diabaikan git). Kata sandi root Mongo dibaca dari `../mongo-db/secrets/mongo_root_password.txt` (ubah dengan `MONGO_ROOT_PASSWORD_FILE`);
password Redis bawaan `admin` (ubah dengan `REDIS_PASSWORD`). Pemeriksaan: `pg_up`, `redis_up`, `mongodb_up` bernilai 1 di Prometheus.

## 5. Peringatan Telegram

Tiga belas aturan ada di `grafana/provisioning/alerting/alerts.yml`: memori container ≥90% limit, backend 5xx, log ERROR melonjak, pool DB ≥90%, disk ≥85%,
RAM server ≥90%, target mati, database tidak terjangkau, koneksi PostgreSQL ≥80%, antrean RabbitMQ >1000, error JavaScript browser >10 per 5 menit, backup gagal, backup lebih dari 26 jam.
Kebijakan: dikelompokkan per nama aturan, diulang tiap 4 jam bila belum selesai.

Kredensial dibaca dari file (diabaikan git):

```bash
cd grafana
printf '%s' '<token dari BotFather>' > secrets/telegram_bot_token.txt
printf '%s' '<chat id>'              > secrets/telegram_chat_id.txt
./startup.sh          # menulis secrets/telegram-contact.yml, lalu me-restart Grafana bila berubah
./test-telegram.sh    # kirim pesan uji (contact point dari provisioning hanya-baca di UI, jadi tombol Test tidak ada)
```

Chat id baru tersedia setelah Anda menekan **Start** pada bot di Telegram (bot tidak bisa memulai percakapan). Selama file masih kosong, `startup.sh` menampilkan WARNING dan alert
tetap dievaluasi tetapi tidak terkirim. Di dev, alert "Target monitoring mati" ikut menyala bila backend (8081) atau frontend (9464) sengaja dimatikan lebih dari 5 menit.

## 6. Monitor Uptime Kuma (dibuat lewat UI)

Kuma bergabung ke jaringan monitoring, jadi alamatnya memakai nama container. Tambahkan lewat **Add New Monitor** (tipe HTTP(s), interval 60 dtk, notifikasi Telegram):

| Nama | URL | Catatan |
|---|---|---|
| Prometheus | `http://fajarnhapp-prometheus:9090/-/healthy` | |
| Grafana | `http://fajarnhapp-grafana:3000/api/health` | |
| Loki | `http://fajarnhapp-loki:3100/loki/api/v1/status/buildinfo` | bukan `/ready` (kadang 503) |
| Alloy | `http://fajarnhapp-alloy:12345/-/ready` | |
| cAdvisor | `http://fajarnhapp-cadvisor:8080/healthz` | |
| node-exporter | `http://fajarnhapp-node-exporter:9100/` | |
| postgres / redis / mongodb exporter | `http://fajarnhapp-postgres-exporter:9187/`, `...redis-exporter:9121/`, `...mongodb-exporter:9216/` | |
| Frontend | `https://<domain>/api/health` dan `https://<domain>/login` | `/api/health` mencakup backend dan database |
| Backup monitoring | `http://fajarnhapp-monitoring-backup:9101/health` | 503 bila belum ada backup berhasil atau yang terakhir lebih dari 26 jam |

Semua alamat internal di atas sudah diuji dari dalam container Kuma (HTTP 200).

## 7. Backup

`monitoring-backup` berjalan sebagai container `fajarnhapp-monitoring-backup`: mengarsipkan volume Prometheus, Loki, Alloy, Grafana, Uptime Kuma, plus `grafana/secrets` (izin 600),
setiap hari pukul 03:00 (zona waktu `TZ`, bawaan Asia/Jakarta) dan menyimpan 14 hari (arsip terbaru tiap sumber tidak pernah dihapus). Saat container pertama kali jalan dan
belum ada backup berhasil dalam 24 jam terakhir, backup langsung dibuat. Volume SQLite (Grafana, Kuma) diambil lewat API backup SQLite dan diperiksa `PRAGMA integrity_check`.
Tanpa `docker.sock`: volume di-mount langsung, dan container hanya punya hak CHOWN dan DAC_OVERRIDE (`cap_drop: ALL`, root filesystem read-only).

```bash
cd monitoring-backup
BACKUP_DIR=/backup/monitoring BACKUP_AT=02:30 RETENTION_DAYS=30 ./startup.sh   # semua opsional; bawaan: ~/data/backup/monitoring, 03:00, 14 hari
./run-now.sh                                                                   # satu backup sekarang, di luar jadwal (kode keluar 0 bila semua berhasil)
docker logs fajarnhapp-monitoring-backup                                       # riwayat run
```

Pemantauan: `/metrics` (job Prometheus `backup`) dan `/health` (200 bila backup berhasil terakhir kurang dari 26 jam, 503 bila tidak) di port 9101 (tidak dipublikasikan); panel
"Backup monitoring" di dashboard Host Server; alert Grafana "Backup monitoring gagal" dan "Backup monitoring tidak berjalan". Perintah pemulihan ada di kepala `backup.py`
(sudah diuji: integritas SQLite ok dan isinya sama). Folder `BACKUP_DIR` berada di disk yang sama dengan server; salin hasilnya ke luar server (S3 atau lainnya) agar aman dari disk rusak.

## 8. Jangan lakukan

- Jangan menjalankan `docker compose up` di folder `grafana` tanpa `startup.sh`: mount `secrets/telegram-contact.yml` tidak ada, dan Docker membuat direktori di tempatnya.
- Jangan mengganti UID datasource Grafana (`prometheus`, `loki`) di UI: aturan alert merujuknya.
- Jangan membuka port monitoring di security group.
- `/api/faro` di frontend bersifat publik (error di halaman login juga penting). Ia hanya meneruskan JSON ≤64 KB ke Alloy dan dibatasi rate limit per IP; query string disaring di browser (token di URL).

## 9. Rollback

Hanya memengaruhi monitoring, tidak menyentuh aplikasi, volume, atau jaringan bersama.

```bash
cd cadvisor      && docker compose down      # hanya container cAdvisor
cd monitoring-backup && docker compose down   # arsip di BACKUP_DIR tidak dihapus
cd node-exporter && docker compose down
cd exporters     && docker compose down      # akun monitoring di database tidak ikut terhapus (DROP ROLE monitoring / db.dropUser("monitoring") bila perlu)
cd prometheus    && git checkout <commit-sebelumnya> -- prometheus.yml && ./startup.sh
cd grafana       && git checkout <commit-sebelumnya> -- provisioning && ./startup.sh
```

Setiap folder monitoring adalah repo git; lihat `git log`. Backup volume Grafana sebelum perubahan UID datasource: volume `grafana-backup-20261023`.
