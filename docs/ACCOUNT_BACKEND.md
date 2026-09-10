# VOSTOK Account Backend

Status: backend v1 foundation.

The client-side `AccountCore` and this backend share one passwordless contract:

`provider/email OTP -> VOSTOK account session -> characters -> short-lived game ticket -> game server consumes ticket`

Security boundaries:

1. No VOSTOK game password exists.
2. Provider credentials are checked by the backend; plain provider IDs from the APK are never trusted.
3. Email OTP is hashed at rest, time-limited, attempt-limited and single-use.
4. Refresh tokens are opaque random values; only hashes are stored and tokens rotate on refresh.
5. Game tickets are random, short-lived, hashed at rest and single-use.
6. The game server consumes tickets through a protected internal endpoint and receives authoritative account/character identity.
7. OAuth/SMTP/server secrets live only in environment configuration.

Current persistence is SQLite for development and CI. PostgreSQL migration is required before public production.

Visual authorization, mobile provider SDK integration, character cards and Character Creator remain phase 2.
