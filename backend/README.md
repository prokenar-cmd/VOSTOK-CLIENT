# VOSTOK Account Backend v1

Passwordless account service for the VOSTOK client.

Implemented: email six-digit single-use OTP with expiry/cooldown/limits; Google/Yandex/VK provider verification adapters; short-lived access tokens; rotating opaque refresh tokens stored only as hashes; account profile and character list; short-lived single-use game tickets; protected game-server ticket consumption; SQLite development persistence; SMTP transport; production secret checks.

No game password exists.

## Run locally

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q
uvicorn app.main:app --reload --port 8080
```

The development email sender prints OTP only to backend logs. The API response never contains the code.

## Production

SQLite is intentional for development/CI. Before public launch, migrate persistence to PostgreSQL and place the service behind HTTPS and reverse-proxy rate limiting. Real provider, SMTP and game-server secrets are environment variables and must never be committed.

Google credentials are server-verified ID tokens. Yandex identities are resolved through Yandex ID user-info. VK uses VK ID user-info with the configured app client ID; PKCE/device flow belongs to the later mobile SDK integration.

The internal `POST /v1/internal/game-ticket/consume` endpoint requires `X-VOSTOK-Server-Key`. A game ticket expires quickly and can be consumed once; its returned account/character identity is authoritative.
