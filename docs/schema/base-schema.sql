-- Skema dasar saasno (struktur + data acuan) untuk instalasi BARU atau lingkungan uji (CI). Dibuat dari skema setelah migrasi terakhir
-- di docs/migrations (2026-11-06). Instalasi yang sudah ada tetap memakai migrasi bertahap di docs/migrations.
-- Berisi: semua tabel, daftar API dan menu, satu role SUPER dengan semua aksesnya, dan nilai bawaan Configuration.
-- TIDAK berisi pengguna: buat pengguna pertama sendiri (lihat docs/schema/README.md). Tidak ada kredensial di sini.
-- Jaga tetap mutakhir: setiap migrasi baru ikut diterapkan ke file ini (CI menjalankan backend dengan ddl-auto=validate terhadap file ini).
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11 (Debian 17.11-1.pgdg13+2)
-- Dumped by pg_dump version 17.11 (Debian 17.11-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


--
-- Name: stock_movement_immutable(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.stock_movement_immutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'stock_movement is append-only';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: announcement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.announcement (
    id character varying(64) NOT NULL,
    title character varying(120) NOT NULL,
    message character varying(1000) NOT NULL,
    level character varying(16) DEFAULT 'INFO'::character varying NOT NULL,
    starts_at timestamp without time zone,
    ends_at timestamp without time zone,
    active boolean DEFAULT true NOT NULL,
    created_by character varying(256),
    registered timestamp without time zone NOT NULL,
    modificated timestamp without time zone NOT NULL
);


--
-- Name: api; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api (
    id character varying(64) NOT NULL,
    name character varying(255) NOT NULL,
    path character varying(255) NOT NULL,
    method character varying(10) NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: api_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_token (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    name character varying(100) NOT NULL,
    token_hash character varying(64) NOT NULL,
    prefix character varying(16) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    last_used_at timestamp without time zone,
    revoked_at timestamp without time zone,
    registered timestamp without time zone NOT NULL
);


--
-- Name: change_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.change_log (
    id character varying(64) NOT NULL,
    entity_type character varying(32) NOT NULL,
    entity_id character varying(255) NOT NULL,
    entity_label character varying(256),
    action character varying(16) NOT NULL,
    field character varying(64),
    old_value text,
    new_value text,
    changed_by character varying(256),
    registered timestamp without time zone NOT NULL
);


--
-- Name: configuration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuration (
    id character varying(512) NOT NULL,
    value text NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: contact; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact (
    id character varying(64) NOT NULL,
    person_id character varying(64) NOT NULL,
    type character varying(16) NOT NULL,
    value character varying(256) NOT NULL,
    label character varying(16) DEFAULT 'PERSONAL'::character varying NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    verified_at timestamp without time zone,
    registered timestamp without time zone NOT NULL,
    modificated timestamp without time zone NOT NULL
);


--
-- Name: deletion_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deletion_request (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    username character varying(256) NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    requested_at timestamp without time zone NOT NULL,
    handled_at timestamp without time zone,
    handled_by character varying(256)
);


--
-- Name: download_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.download_job (
    id character varying(64) NOT NULL,
    type character varying(64) NOT NULL,
    format character varying(10) NOT NULL,
    status character varying(32) NOT NULL,
    file_name character varying(255),
    filter_json oid,
    error_message character varying(500),
    requested_by character varying(64) NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: email_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_template (
    id character varying(64) NOT NULL,
    subject character varying(200) NOT NULL,
    heading character varying(200) NOT NULL,
    intro character varying(1000) NOT NULL,
    button_label character varying(60) NOT NULL,
    note character varying(1000) DEFAULT ''::character varying NOT NULL,
    modificated timestamp without time zone NOT NULL
);


--
-- Name: login_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_history (
    id character varying(64) NOT NULL,
    user_id character varying(64),
    username character varying(256) NOT NULL,
    success boolean NOT NULL,
    reason character varying(32) NOT NULL,
    ip character varying(64),
    user_agent character varying(255),
    registered timestamp without time zone NOT NULL
);


--
-- Name: menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu (
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    icon character varying(255),
    url character varying(255) NOT NULL,
    order_index integer,
    parent_id character varying(255),
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    notification_message_id character varying(64) NOT NULL,
    status integer NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: notification_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_message (
    id character varying(64) NOT NULL,
    title text NOT NULL,
    message text NOT NULL,
    registered timestamp without time zone NOT NULL,
    type character varying(32) DEFAULT 'INFO'::character varying NOT NULL,
    link character varying(256)
);


--
-- Name: notification_unread; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_unread (
    user_id character varying(64) NOT NULL,
    total integer NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: person; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.person (
    id character varying(64) NOT NULL,
    full_name character varying(128) NOT NULL,
    nickname character varying(64),
    birth_place character varying(64),
    birth_date date,
    gender character varying(16),
    religion character varying(32),
    registered timestamp without time zone NOT NULL,
    modificated timestamp without time zone NOT NULL
);


--
-- Name: privacy_consent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.privacy_consent (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    version integer NOT NULL,
    accepted_at timestamp without time zone NOT NULL
);


--
-- Name: privacy_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.privacy_policy (
    version integer NOT NULL,
    content text,
    published_at timestamp without time zone NOT NULL,
    published_by character varying(256),
    content_id text
);


--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL
);


--
-- Name: role_api; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_api (
    role_id character varying(255) NOT NULL,
    api_id character varying(64) NOT NULL,
    registered timestamp without time zone
);


--
-- Name: role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_menu (
    role_id character varying(255) NOT NULL,
    menu_id character varying(255) NOT NULL,
    registered timestamp without time zone
);


--
-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."user" (
    id character varying(64) NOT NULL,
    username character varying(256) NOT NULL,
    password character varying(256) NOT NULL,
    status character varying(32) NOT NULL,
    secure boolean NOT NULL,
    two_factor_auth boolean NOT NULL,
    two_factor_auth_secret character varying(128),
    modificated timestamp without time zone NOT NULL,
    registered timestamp without time zone NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp without time zone,
    password_changed_at timestamp without time zone,
    password_must_change boolean DEFAULT false NOT NULL,
    two_factor_recovery_hashes text,
    person_id character varying(64) NOT NULL,
    deleted_at timestamp without time zone,
    deleted_by character varying(256)
);


--
-- Name: user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role (
    user_id character varying(64) NOT NULL,
    role_id character varying(255) NOT NULL
);


--
-- Name: announcement pk_announcement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.announcement
    ADD CONSTRAINT pk_announcement PRIMARY KEY (id);


--
-- Name: api pk_api; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api
    ADD CONSTRAINT pk_api PRIMARY KEY (id);


--
-- Name: api_token pk_api_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_token
    ADD CONSTRAINT pk_api_token PRIMARY KEY (id);


--
-- Name: change_log pk_change_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_log
    ADD CONSTRAINT pk_change_log PRIMARY KEY (id);


--
-- Name: configuration pk_configuration; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration
    ADD CONSTRAINT pk_configuration PRIMARY KEY (id);


--
-- Name: contact pk_contact; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact
    ADD CONSTRAINT pk_contact PRIMARY KEY (id);


--
-- Name: deletion_request pk_deletion_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deletion_request
    ADD CONSTRAINT pk_deletion_request PRIMARY KEY (id);


--
-- Name: download_job pk_download_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.download_job
    ADD CONSTRAINT pk_download_job PRIMARY KEY (id);


--
-- Name: email_template pk_email_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_template
    ADD CONSTRAINT pk_email_template PRIMARY KEY (id);


--
-- Name: login_history pk_login_history; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT pk_login_history PRIMARY KEY (id);


--
-- Name: menu pk_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu
    ADD CONSTRAINT pk_menu PRIMARY KEY (id);


--
-- Name: notification pk_notification; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT pk_notification PRIMARY KEY (id);


--
-- Name: notification_message pk_notification_message; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_message
    ADD CONSTRAINT pk_notification_message PRIMARY KEY (id);


--
-- Name: notification_unread pk_notification_unread; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_unread
    ADD CONSTRAINT pk_notification_unread PRIMARY KEY (user_id);


--
-- Name: person pk_person; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT pk_person PRIMARY KEY (id);


--
-- Name: privacy_consent pk_privacy_consent; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.privacy_consent
    ADD CONSTRAINT pk_privacy_consent PRIMARY KEY (id);


--
-- Name: privacy_policy pk_privacy_policy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.privacy_policy
    ADD CONSTRAINT pk_privacy_policy PRIMARY KEY (version);


--
-- Name: role pk_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT pk_role PRIMARY KEY (id);


--
-- Name: role_api pk_role_api; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_api
    ADD CONSTRAINT pk_role_api PRIMARY KEY (role_id, api_id);


--
-- Name: role_menu pk_role_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_menu
    ADD CONSTRAINT pk_role_menu PRIMARY KEY (role_id, menu_id);


--
-- Name: user pk_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT pk_user PRIMARY KEY (id);


--
-- Name: user_role pk_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id);


--
-- Name: role uk_role_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uk_role_name UNIQUE (name);


--
-- Name: api_token_hash_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX api_token_hash_uk ON public.api_token USING btree (token_hash);


--
-- Name: api_token_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_token_user_idx ON public.api_token USING btree (user_id);


--
-- Name: change_log_entity_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX change_log_entity_idx ON public.change_log USING btree (entity_type, entity_id, registered DESC);


--
-- Name: change_log_registered_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX change_log_registered_idx ON public.change_log USING btree (registered);


--
-- Name: contact_email_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX contact_email_uk ON public.contact USING btree (lower((value)::text)) WHERE ((type)::text = 'EMAIL'::text);


--
-- Name: contact_person_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX contact_person_idx ON public.contact USING btree (person_id);


--
-- Name: contact_person_value_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX contact_person_value_uk ON public.contact USING btree (person_id, type, lower((value)::text));


--
-- Name: contact_primary_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX contact_primary_uk ON public.contact USING btree (person_id, type) WHERE is_primary;


--
-- Name: deletion_request_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX deletion_request_status_idx ON public.deletion_request USING btree (status, requested_at);


--
-- Name: login_history_registered_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_history_registered_idx ON public.login_history USING btree (registered);


--
-- Name: login_history_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_history_user_idx ON public.login_history USING btree (user_id, registered DESC);


--
-- Name: notification_cleanup_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notification_cleanup_idx ON public.notification USING btree (status, registered);


--
-- Name: notification_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notification_user_idx ON public.notification USING btree (user_id, registered DESC);


--
-- Name: privacy_consent_user_version_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX privacy_consent_user_version_uk ON public.privacy_consent USING btree (user_id, version);


--
-- Name: user_person_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX user_person_uk ON public."user" USING btree (person_id);


--
-- Name: user_username_uk; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX user_username_uk ON public."user" USING btree (username);


--
-- Name: api_token fk_api_token_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_token
    ADD CONSTRAINT fk_api_token_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE;


--
-- Name: contact fk_contact_person; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact
    ADD CONSTRAINT fk_contact_person FOREIGN KEY (person_id) REFERENCES public.person(id) ON DELETE CASCADE;


--
-- Name: deletion_request fk_deletion_request_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deletion_request
    ADD CONSTRAINT fk_deletion_request_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE;


--
-- Name: menu fk_menu_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu
    ADD CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES public.menu(id);


--
-- Name: notification fk_notification_message; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_message FOREIGN KEY (notification_message_id) REFERENCES public.notification_message(id);


--
-- Name: privacy_consent fk_privacy_consent_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.privacy_consent
    ADD CONSTRAINT fk_privacy_consent_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE;


--
-- Name: role_api fk_role_api_api; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_api
    ADD CONSTRAINT fk_role_api_api FOREIGN KEY (api_id) REFERENCES public.api(id);


--
-- Name: role_api fk_role_api_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_api
    ADD CONSTRAINT fk_role_api_role FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: role_menu fk_role_menu_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_menu
    ADD CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES public.menu(id);


--
-- Name: role_menu fk_role_menu_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_menu
    ADD CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: user fk_user_person; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT fk_user_person FOREIGN KEY (person_id) REFERENCES public.person(id);


--
-- Name: user_role fk_user_role_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: user_role fk_user_role_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES public."user"(id);


--
-- Name: password_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_history (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    password_hash character varying(256) NOT NULL,
    registered timestamp without time zone NOT NULL,
    CONSTRAINT pk_password_history PRIMARY KEY (id),
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

CREATE INDEX password_history_user_idx ON public.password_history USING btree (user_id, registered DESC);


--
-- PostgreSQL database dump complete
--



-- ===== Data acuan =====
BEGIN;
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_OTP_RECOVERY', 'Membuat ulang kode cadangan 2FA', '/api/user/otp/recovery', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_USER_AVATAR', 'Melihat foto profil', '/api/user/avatar', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_AVATAR', 'Mengunggah foto profil', '/api/user/avatar', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_USER_AVATAR', 'Menghapus foto profil', '/api/user/avatar', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_DOWNLOAD_ALL', 'Menghapus semua unduhan milik sendiri', '/api/download/all', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_APP_SETTING', 'Melihat pengaturan aplikasi', '/api/app-setting', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_APP_SETTING', 'Mengubah pengaturan aplikasi', '/api/app-setting', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_APP_SETTING_LOGO', 'Mengunggah logo aplikasi', '/api/app-setting/logo', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_APP_SETTING_LOGO', 'Menghapus logo aplikasi', '/api/app-setting/logo', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_MAINTENANCE', 'Melihat ringkasan pemeliharaan', '/api/maintenance', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_MAINTENANCE_CLEANUP', 'Menjalankan pembersihan data lama', '/api/maintenance/cleanup', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_MAINTENANCE_DOWNLOAD', 'Menghapus semua unduhan semua pengguna', '/api/maintenance/download', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_USER_RESET_TWO_FACTOR', 'Reset autentikasi dua faktor pengguna', '/api/user/reset-two-factor', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_API', 'Membuat data API baru', '/api/api', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_API', 'Menampilkan daftar data API', '/api/api', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_API', 'Memperbarui data API', '/api/api', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_API', 'Menghapus data API', '/api/api', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_LOGOUT', 'Logout dari aplikasi', '/api/logout', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_CONFIGURATION', 'Membuat data konfigurasi baru', '/api/configuration', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_CONFIGURATION', 'Menampilkan daftar data konfigurasi', '/api/configuration', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_CONFIGURATION_{ID}', 'Menampilkan detail data konfigurasi', '/api/configuration/{id}', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_CONFIGURATION', 'Memperbarui data konfigurasi', '/api/configuration', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_CONFIGURATION_{ID}', 'Menghapus data konfigurasi', '/api/configuration/{id}', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_DOWNLOAD', 'Membuat job download baru', '/api/download', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_DOWNLOAD', 'Menampilkan daftar job download', '/api/download', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_DOWNLOAD_FILE', 'Mengunduh file hasil download', '/api/download/file', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_DOWNLOAD', 'Menghapus job download', '/api/download', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_MENU', 'Membuat data menu baru', '/api/menu', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_MENU', 'Menampilkan daftar data menu', '/api/menu', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_MENU', 'Memperbarui data menu', '/api/menu', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_MENU', 'Menghapus data menu', '/api/menu', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_NOTIFICATION_ME', 'Menampilkan notifikasi milik pengguna', '/api/notification/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_NOTIFICATION_ME', 'Memperbarui status notifikasi milik pengguna', '/api/notification/me', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_NOTIFICATION_UNREAD_ME', 'Menampilkan jumlah notifikasi belum dibaca', '/api/notification-unread/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_NOTIFICATION_UNREAD_ME', 'Memperbarui jumlah notifikasi belum dibaca', '/api/notification-unread/me', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_MAINTENANCE_MODE', 'Melihat pengaturan maintenance mode', '/api/maintenance/mode', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ROLE_API', 'Menampilkan daftar akses API role', '/api/role-api', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_MAINTENANCE_MODE', 'Mengubah pengaturan maintenance mode', '/api/maintenance/mode', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_ROLE', 'Membuat data role baru', '/api/role', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ROLE', 'Menampilkan daftar data role', '/api/role', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_ROLE', 'Memperbarui data role', '/api/role', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_ROLE', 'Menghapus data role', '/api/role', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ROLE_MENU', 'Menampilkan daftar akses menu role', '/api/role-menu', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ROLE_MENU_ACCESS', 'Mengecek akses menu untuk role', '/api/role-menu/access', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_OTP_SETUP', 'Memulai konfigurasi 2FA', '/api/user/otp/setup', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_OTP_ENABLE', 'Mengaktifkan 2FA', '/api/user/otp/enable', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_USER_OTP', 'Menonaktifkan 2FA', '/api/user/otp', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER', 'Membuat data pengguna baru', '/api/user', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_USER', 'Menampilkan daftar pengguna', '/api/user', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_USER', 'Memperbarui data pengguna', '/api/user', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_USER', 'Menghapus data pengguna', '/api/user', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_USER_ME', 'Menampilkan profil pengguna yang sedang login', '/api/user/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_USER_RESET_PASSWORD', 'Reset password pengguna', '/api/user/reset-password', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_USER_CHANGE_PASSWORD', 'Mengubah password pengguna sendiri', '/api/user/change-password', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_VERIFY_PASSWORD', 'Verify Password', '/api/user/verify-password', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_ROLE_API', 'Memperbarui akses API role', '/api/role-api', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_ROLE_MENU', 'Memperbarui akses menu role', '/api/role-menu', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_LOG', 'Menampilkan daftar audit log', '/api/log', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_NOTIFICATION_PREFERENCE', 'Menampilkan preferensi notifikasi', '/api/notification/preference', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_NOTIFICATION_PREFERENCE', 'Mengubah preferensi notifikasi', '/api/notification/preference', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_SESSION', 'Menampilkan perangkat yang sedang login (sendiri)', '/api/session', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_SESSION', 'Mengeluarkan perangkat login sendiri', '/api/session', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_SESSION_ALL', 'Menampilkan sesi aktif semua user dalam cakupan', '/api/session/all', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_SESSION_ALL', 'Mengeluarkan sesi user lain dalam cakupan', '/api/session/all', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_PERSON_ME', 'Melihat biodata dan kontak sendiri', '/api/person/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_PERSON_ME', 'Mengubah biodata dan kontak sendiri', '/api/person/me', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_PERSON_VERIFY_EMAIL', 'Mengirim link verifikasi email sendiri', '/api/person/me/verify-email', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_LOGIN_HISTORY', 'Melihat riwayat login semua pengguna', '/api/signin-history', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_LOGIN_HISTORY_ME', 'Melihat riwayat login sendiri', '/api/signin-history/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_CHANGE_LOG', 'Melihat riwayat perubahan data', '/api/change-log', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ANNOUNCEMENT', 'Melihat daftar pengumuman', '/api/announcement', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_ANNOUNCEMENT', 'Membuat pengumuman', '/api/announcement', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_ANNOUNCEMENT', 'Mengubah pengumuman', '/api/announcement', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_ANNOUNCEMENT', 'Menghapus pengumuman', '/api/announcement', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_ANNOUNCEMENT_ACTIVE', 'Melihat pengumuman yang tampil', '/api/announcement/active', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_EMAIL_TEMPLATE', 'Melihat template email', '/api/email-template', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_EMAIL_TEMPLATE', 'Mengubah template email', '/api/email-template', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_EMAIL_TEMPLATE', 'Mengembalikan template email ke bawaan', '/api/email-template', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_USER_TRASH', 'Melihat pengguna di tempat sampah', '/api/user/trash', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_USER_RESTORE', 'Memulihkan pengguna dari sampah', '/api/user/restore', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_USER_PURGE', 'Menghapus permanen pengguna', '/api/user/purge', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_BULK', 'Aksi massal pada pengguna', '/api/user/bulk', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_USER_IMPORT', 'Impor pengguna dari CSV', '/api/user/import', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_TOKEN', 'Melihat API token sendiri', '/api/token', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_TOKEN', 'Membuat API token', '/api/token', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('DELETE_API_TOKEN', 'Mencabut API token', '/api/token', 'DELETE', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_PRIVACY_POLICY', 'Melihat kebijakan privasi dan riwayat versinya', '/api/privacy/policy', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_PRIVACY_POLICY', 'Menerbitkan versi baru kebijakan privasi', '/api/privacy/policy', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_PRIVACY_ME', 'Melihat status persetujuan kebijakan privasi', '/api/privacy/me', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_PRIVACY_ACCEPT', 'Menyetujui kebijakan privasi', '/api/privacy/accept', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('POST_API_PRIVACY_DELETE_REQUEST', 'Meminta penghapusan akun sendiri', '/api/privacy/delete-request', 'POST', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_PRIVACY_DELETE_REQUESTS', 'Melihat permintaan hapus akun', '/api/privacy/delete-requests', 'GET', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('PUT_API_PRIVACY_DELETE_REQUESTS', 'Memproses permintaan hapus akun', '/api/privacy/delete-requests', 'PUT', now(), now());
INSERT INTO public.api (id, name, path, method, modificated, registered) VALUES ('GET_API_PERSON_EXPORT', 'Mengunduh data pribadi sendiri', '/api/person/me/export', 'GET', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('DASHBOARD', 'Dashboard', 'dashboard', '/dashboard', 1, NULL, now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('SECURITY', 'Security', 'shield', '/security', 2, NULL, now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('SYSTEM', 'System', 'settings', '#', 3, NULL, now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('USER', 'User', 'users', '/system/user', 1, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('ROLE', 'Role', 'clipboard', '/system/role', 2, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('MENU', 'Menu', 'layout', '/system/menu', 3, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('API', 'Api', 'api', '/system/api', 4, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('CONFIGURATION', 'Configuration', 'papper', '/system/configuration', 5, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('ROLE_MENU', 'Role Menu', 'calendar', '/system/role-menu', 6, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('ROLE_API', 'Role Api', 'clipboard', '/system/role-api', 7, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('AUDIT_LOG', 'Audit Log', 'history', '/system/audit-log', 8, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('SESSION', 'Active Sessions', 'shield', '/system/session', 9, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('APPLICATION', 'Application', 'palette', '/system/application', 10, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('MAINTENANCE', 'Maintenance', 'wrench', '/system/maintenance', 11, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('LOGIN_HISTORY', 'Login History', 'history', '/system/login-history', 12, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('CHANGE_HISTORY', 'Change History', 'history', '/system/change-history', 13, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('ANNOUNCEMENT', 'Announcement', 'bell', '/system/announcement', 14, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('EMAIL_TEMPLATE', 'Email Template', 'papper', '/system/email-template', 15, 'SYSTEM', now(), now());
INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES ('PRIVACY_POLICY', 'Privacy Policy', 'shield', '/system/privacy', 16, 'SYSTEM', now(), now());
INSERT INTO public.role (id, name, modificated, registered) VALUES ('SUPER', 'Super User', now(), now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_USER_RESET_TWO_FACTOR', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_LOGOUT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_CONFIGURATION', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_CONFIGURATION', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_CONFIGURATION_{ID}', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_CONFIGURATION', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_CONFIGURATION_{ID}', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_DOWNLOAD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_DOWNLOAD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_DOWNLOAD_FILE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_DOWNLOAD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_NOTIFICATION_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_NOTIFICATION_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_NOTIFICATION_UNREAD_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_NOTIFICATION_UNREAD_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ROLE_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_ROLE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ROLE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_ROLE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_ROLE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ROLE_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ROLE_MENU_ACCESS', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_OTP_SETUP', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_OTP_ENABLE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_USER_OTP', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_USER', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_USER', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_USER', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_USER_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_USER_RESET_PASSWORD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_USER_CHANGE_PASSWORD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_ROLE_API', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_ROLE_MENU', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_APP_SETTING_LOGO', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_VERIFY_PASSWORD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_LOG', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_DOWNLOAD_ALL', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_MAINTENANCE_DOWNLOAD', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_USER_AVATAR', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_APP_SETTING', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_MAINTENANCE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_USER_AVATAR', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_APP_SETTING_LOGO', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_MAINTENANCE_CLEANUP', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_AVATAR', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_OTP_RECOVERY', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_APP_SETTING', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_MAINTENANCE_MODE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_MAINTENANCE_MODE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_NOTIFICATION_PREFERENCE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_NOTIFICATION_PREFERENCE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_SESSION', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_SESSION', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_SESSION_ALL', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_SESSION_ALL', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_PERSON_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_PERSON_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_PERSON_VERIFY_EMAIL', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_LOGIN_HISTORY', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_LOGIN_HISTORY_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_CHANGE_LOG', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ANNOUNCEMENT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_ANNOUNCEMENT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_ANNOUNCEMENT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_ANNOUNCEMENT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_ANNOUNCEMENT_ACTIVE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_EMAIL_TEMPLATE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_EMAIL_TEMPLATE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_EMAIL_TEMPLATE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_USER_TRASH', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_USER_RESTORE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_USER_PURGE', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_BULK', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_USER_IMPORT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_TOKEN', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_TOKEN', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'DELETE_API_TOKEN', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_PRIVACY_POLICY', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_PRIVACY_POLICY', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_PRIVACY_ME', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_PRIVACY_ACCEPT', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'POST_API_PRIVACY_DELETE_REQUEST', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_PRIVACY_DELETE_REQUESTS', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'PUT_API_PRIVACY_DELETE_REQUESTS', now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_PERSON_EXPORT', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'APPLICATION', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'MAINTENANCE', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'API', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'AUDIT_LOG', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'CONFIGURATION', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'DASHBOARD', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'MENU', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'ROLE', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'ROLE_API', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'ROLE_MENU', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'SYSTEM', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'USER', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'SECURITY', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'SESSION', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'LOGIN_HISTORY', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'CHANGE_HISTORY', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'ANNOUNCEMENT', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'EMAIL_TEMPLATE', now());
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'PRIVACY_POLICY', now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('ALLOWED_ORIGINS', 'http://localhost:3000', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('APP_NAME', 'Saasno', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('AUDIT_LOG_RETENTION_DAYS', '365', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('BRAND_CHROMA', '1', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('BRAND_HUE', '293', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('DOWNLOAD_RETENTION_DAYS', '7', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('FRONTEND_URL', 'http://localhost:3000', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('LOGIN_IP_ALLOWLIST', '', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('LOGIN_LOCK_MINUTES', '15', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_AUTH', 'false', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_FROM', 'no-reply@saasno.local', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_FROM_NAME', 'Saasno', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_HOST', '', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_PASSWORD', '', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_PORT', '587', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_STARTTLS', 'false', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAIL_USERNAME', '', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAINTENANCE_BYPASS_ROLES', 'SUPER', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAINTENANCE_MESSAGE', 'System under maintenance!', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAINTENANCE_MODE', 'false', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAINTENANCE_UNTIL', '', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('MAX_LOGIN_ATTEMPTS', '5', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('NOTIFICATION_RETENTION_DAYS', '30', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_EXPIRY_DAYS', '0', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_HISTORY_COUNT', '5', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_MIN_LENGTH', '8', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_REQUIRE_COMPLEXITY', 'true', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_RESET_MAX_PER_HOUR', '3', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('PASSWORD_RESET_MINUTES', '30', now(), now());
INSERT INTO public.configuration (id, value, modificated, registered) VALUES ('TWO_FACTOR_REQUIRED', 'false', now(), now());
COMMIT;
