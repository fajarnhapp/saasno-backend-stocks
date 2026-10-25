# Skema dasar

`base-schema.sql` adalah skema lengkap (semua tabel) beserta data acuan (daftar API dan menu, role `SUPER` dengan semua aksesnya, nilai bawaan Configuration)
sesudah migrasi terakhir di `docs/migrations`. Tidak ada pengguna dan tidak ada kredensial di dalamnya.

Dipakai untuk:
- **instalasi baru**: buat database kosong lalu jalankan file ini sebagai pemilik database (`psql -U database -d saasno -f docs/schema/base-schema.sql`), kemudian buat pengguna pertama
  (lihat `frontend/e2e/seed.sql` untuk contoh baris `person`, `contact`, `user`, `user_role`);
- **lingkungan uji / CI**: workflow end-to-end di repo frontend memuat file ini lalu menjalankan backend dengan `ddl-auto=validate`, jadi bila skema dan entity tidak cocok, CI gagal.

Instalasi yang sudah ada tetap memakai migrasi bertahap di `docs/migrations` (dijalankan berurutan sesuai tanggal).

**Saat menambah migrasi baru, terapkan juga ke `base-schema.sql`** (tabel/kolom, dan baris `api`/`menu`/`role_api`/`role_menu` untuk SUPER). Cara membuatnya ulang dari database yang sudah dimigrasi:
`pg_dump -s --no-owner --no-privileges` untuk struktur, ditambah INSERT untuk tabel `api`, `menu`, `role` (hanya SUPER), `role_api` dan `role_menu` (hanya SUPER).
Jangan memasukkan isi `configuration` yang asli: nilai di dalamnya bisa berupa kredensial (MAIL_USERNAME, MAIL_PASSWORD).
