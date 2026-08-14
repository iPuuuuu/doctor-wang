create extension if not exists pgcrypto;

alter table app_user add column password_hash varchar(255);

-- V8 has not been released. Hash every existing password in-place during this
-- transaction, then remove the plaintext source column. `crypt` produces a
-- BCrypt hash with the requested cost and no plaintext leaves this statement.
update app_user
set password_hash = crypt(password, gen_salt('bf', 12))
where password is not null;

alter table app_user alter column password_hash set not null;
alter table app_user drop column password;

-- Opaque pre-V8 bearer tokens cannot be recovered as SHA-256 hashes without
-- retaining plaintext. Invalidate them before introducing the hash contract;
-- clients must log in again and receive a JWT-backed session.
delete from user_session;
alter table user_session rename column token to token_hash;
alter table user_session alter column token_hash type varchar(64);
alter table user_session add column jti varchar(64);
update user_session set jti = 'legacy-' || id where jti is null;
alter table user_session alter column jti set not null;
alter table user_session add constraint uk_user_session_jti unique (jti);
alter table user_session add column revoked_at timestamptz;
create index idx_user_session_jti on user_session(jti);
