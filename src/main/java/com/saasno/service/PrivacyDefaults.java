package com.saasno.service;

// The built-in privacy policy (used until an admin publishes their own). It lists what this base application really stores, and has
// [placeholders] the organisation must fill in. Not legal advice: have it reviewed before real people use the application.
// Format: "# " title, "## " heading, "- " list item, a blank line between paragraphs.
final class PrivacyDefaults {

	private PrivacyDefaults() {
	}

	static String text(String language) {
		return "id".equals(language) ? INDONESIAN : ENGLISH;
	}

	static final String INDONESIAN = """
			# Kebijakan Privasi

			Terakhir diperbarui: [tanggal]

			## Siapa kami

			[Nama organisasi] ("kami") mengelola aplikasi ini. Pertanyaan tentang data pribadi Anda dapat dikirim ke [alamat email kontak].

			## Data apa yang kami simpan

			- Akun: nama pengguna, status akun, peran, waktu pendaftaran, dan kata sandi (yang sekarang dan beberapa yang sebelumnya, hanya disimpan sebagai hash yang tidak dapat dibaca siapa pun; yang sebelumnya dipakai untuk mencegah pemakaian ulang).
			- Data diri: nama lengkap, nama panggilan, tempat dan tanggal lahir, jenis kelamin, dan agama. Hanya yang diisi oleh Anda atau administrator.
			- Kontak: alamat email dan nomor telepon.
			- Keamanan: pengaturan autentikasi dua faktor, kode cadangan (disimpan sebagai hash), dan daftar perangkat yang sedang masuk.
			- Riwayat login: waktu, alamat IP, jenis peramban atau perangkat, dan hasil setiap percobaan masuk (berhasil atau gagal).
			- Catatan aktivitas: permintaan yang dikirim ke aplikasi (waktu, alamat, alamat IP, nama pengguna, dan nama lengkap Anda) untuk keperluan audit, serta riwayat perubahan data.
			- Foto profil, bila Anda mengunggahnya.

			## Untuk apa data dipakai

			- Mengenali Anda dan mengamankan akun (masuk, autentikasi dua faktor, pemulihan kata sandi, peringatan perangkat baru).
			- Menjalankan fitur aplikasi sesuai peran Anda.
			- Mengirim email yang berkaitan dengan akun, seperti aktivasi, pengaturan ulang kata sandi, dan konfirmasi email.
			- Audit dan penelusuran bila terjadi masalah keamanan atau kesalahan.

			Data tidak kami jual dan tidak dipakai untuk iklan.

			## Dasar pemrosesan

			[Isi dasar hukum yang berlaku, misalnya perjanjian kerja, persetujuan Anda, atau kepentingan yang sah. Sesuaikan dengan penggunaan aplikasi.]

			## Dengan siapa data dibagikan

			- Penyedia layanan yang membantu kami menjalankan aplikasi, misalnya penyedia pengiriman email ([nama penyedia]) dan penyedia hosting ([nama penyedia]).
			- Pihak berwenang bila diwajibkan oleh hukum.

			## Berapa lama data disimpan

			- Data akun dan data diri: selama akun ada. Akun yang dihapus lebih dulu masuk tempat sampah, lalu dihapus permanen oleh administrator.
			- Riwayat login, catatan aktivitas, dan riwayat perubahan: [365] hari sejak dibuat, lalu dihapus otomatis (dapat diatur administrator). Catatan ini tetap ada sampai masa simpannya berakhir, juga setelah akun dihapus permanen.
			- Notifikasi: [30] hari. Berkas unduhan: [7] hari.

			## Keamanan

			Kata sandi disimpan sebagai hash. Sambungan ke aplikasi dienkripsi (HTTPS). Akses ke data dibatasi menurut peran, dan perubahan penting dicatat.

			## Hak Anda

			- Melihat dan memperbaiki data diri Anda di halaman Profil.
			- Mengunduh salinan data Anda lewat tombol "Unduh data saya" di halaman Profil.
			- Meminta penghapusan akun lewat halaman Profil. Administrator akan memprosesnya.
			- Mengajukan keberatan atau menarik persetujuan dengan menghubungi kami di [alamat email kontak].

			## Perubahan kebijakan

			Bila kebijakan ini berubah, Anda akan diminta membacanya kembali dan menyatakan sudah membacanya.
			""";

	static final String ENGLISH = """
			# Privacy Policy

			Last updated: [date]

			## Who we are

			[Organisation name] ("we") runs this application. Questions about your personal data can be sent to [contact email address].

			## What data we keep

			- Account: username, account status, role, registration time, and passwords (the current one and a few earlier ones, kept only as hashes that nobody can read; the earlier ones stop a password from being reused).
			- Personal details: full name, nickname, place and date of birth, gender, and religion. Only what you or an administrator fill in.
			- Contacts: email addresses and phone numbers.
			- Security: two-factor authentication settings, recovery codes (kept as hashes), and the list of devices that are signed in.
			- Sign-in history: time, IP address, type of browser or device, and the result of every sign-in attempt (successful or not).
			- Activity records: requests sent to the application (time, address, IP address, username, and your full name) for audit, and the history of changes to data.
			- Profile picture, if you upload one.

			## What the data is used for

			- To recognise you and keep the account safe (sign-in, two-factor authentication, password recovery, new-device alerts).
			- To run the features of the application that fit your role.
			- To send emails about the account, such as activation, password reset and email confirmation.
			- For audit and investigation when something goes wrong or a security problem occurs.

			We do not sell the data and do not use it for advertising.

			## Basis for processing

			[State the legal basis that applies, for example an employment contract, your consent, or a legitimate interest. Adjust it to how the application is used.]

			## Who the data is shared with

			- Service providers that help us run the application, for example the email delivery provider ([provider name]) and the hosting provider ([provider name]).
			- Authorities, when the law requires it.

			## How long the data is kept

			- Account and personal details: as long as the account exists. A deleted account goes to the trash first, and an administrator deletes it for good later.
			- Sign-in history, activity records and change history: [365] days from when they are created, then deleted automatically (an administrator can change this). These records stay until that period ends, also after an account is deleted for good.
			- Notifications: [30] days. Download files: [7] days.

			## Security

			Passwords are kept as hashes. The connection to the application is encrypted (HTTPS). Access to data is limited by role, and important changes are recorded.

			## Your rights

			- See and correct your personal details on the Profile page.
			- Download a copy of your data with the "Download my data" button on the Profile page.
			- Ask for your account to be deleted on the Profile page. An administrator will handle the request.
			- Object, or withdraw your consent, by contacting us at [contact email address].

			## Changes to this policy

			When this policy changes, you will be asked to read it again and to confirm that you have.
			""";
}
