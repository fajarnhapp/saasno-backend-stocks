package com.saasno.utility.constant;

public enum ConfigurationKey {

	FRONTEND_URL,

	// Days a read notification is kept before it is deleted automatically (integer 1-30).
	NOTIFICATION_RETENTION_DAYS,

	// Number of consecutive wrong logins (or OTP codes) before the account is locked temporarily (integer >= 1).
	MAX_LOGIN_ATTEMPTS,

	// Minutes an account stays locked after exceeding MAX_LOGIN_ATTEMPTS (integer >= 1).
	LOGIN_LOCK_MINUTES,

	// Minimum password length, enforced when a password is chosen (integer >= 1, default 8).
	PASSWORD_MIN_LENGTH,

	// Whether a new password must mix upper case, lower case and digits (true/false, default true).
	PASSWORD_REQUIRE_COMPLEXITY,

	// Days before a password must be changed; 0 turns expiry off (integer >= 0, default 0).
	PASSWORD_EXPIRY_DAYS,

	// How many of the latest passwords of an account cannot be chosen again; 0 turns this off (integer 0-24, default 5).
	PASSWORD_HISTORY_COUNT,

	// Minutes a forgot-password link stays valid (integer >= 1, default 30).
	PASSWORD_RESET_MINUTES,

	// Forgot-password emails sent per account per hour (integer >= 1, default 3).
	PASSWORD_RESET_MAX_PER_HOUR,

	// Name shown in the app (login page, sidebar, emails), 1-60 characters (default "Saasno").
	APP_NAME,

	// Brand colour: hue 0-360 (default 293) and saturation multiplier 0.3-1.2 (default 1); see globals.css in the frontend.
	BRAND_HUE,
	BRAND_CHROMA,

	// Days a finished download is kept before the daily cleanup removes it (integer >= 1, default 7).
	DOWNLOAD_RETENTION_DAYS,

	// Days an audit log entry is kept; 0 keeps them forever (integer >= 0, default 90).
	AUDIT_LOG_RETENTION_DAYS,

	// Networks allowed to log in: comma separated IPs or CIDR ranges (e.g. 203.0.113.7, 10.0.0.0/8). Empty = anywhere.
	LOGIN_IP_ALLOWLIST,

	// Whether every account must set up two-factor authentication at its first login (true/false, default true).
	TWO_FACTOR_REQUIRED,

	// Browser origins the frontend accepts cross-origin calls from (CORS), comma separated, e.g. https://app.example.com, https://uat.example.com.
	// http://localhost:3000 is always allowed by the frontend, whatever the list says.
	ALLOWED_ORIGINS,

	// Outgoing mail (SMTP). Email is off while MAIL_HOST is empty: the forgot-password form still answers, but no email is sent.
	// MAIL_PORT is a port number (1-65535, default 587). MAIL_USERNAME may be empty for a server without login.
	// MAIL_PASSWORD is write-only: the API and the Configuration page show it as ******** and never return the real value.
	MAIL_HOST,
	MAIL_PORT,
	MAIL_USERNAME,
	MAIL_PASSWORD,

	// How to connect: log in with MAIL_USERNAME/MAIL_PASSWORD (only when a user name is set) and use STARTTLS (true/false, default true).
	MAIL_AUTH,
	MAIL_STARTTLS,

	// Sender address of every email (default no-reply@saasno.local), and the name shown beside it. An empty MAIL_FROM_NAME means the
	// name of the app (APP_NAME).
	MAIL_FROM,
	MAIL_FROM_NAME,

	// Maintenance mode: while on, only the roles in MAINTENANCE_BYPASS_ROLES can sign in or use the app (true/false, default false).
	MAINTENANCE_MODE,

	// Text shown to everyone else (up to 500 characters; empty = a default text).
	MAINTENANCE_MESSAGE,

	// When maintenance ends by itself, as yyyy-MM-ddTHH:mm in the server's time zone; empty = only when switched off.
	MAINTENANCE_UNTIL,

	// Role ids (comma separated) that keep working during maintenance. SUPER is always included.
	MAINTENANCE_BYPASS_ROLES;

}
