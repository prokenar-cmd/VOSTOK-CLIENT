# VOSTOK Account Core v1

Status: foundation only. Visual authorization/registration UI is intentionally deferred.

## Product flow

VOSTOK does not use a game password.

Supported account entry methods:
- Google
- VK
- Yandex
- email + one-time code

The account and the game character are separate entities.

Expected player flow:

`app start -> restore VOSTOK account session -> launcher -> selected character -> Play -> one-time game ticket -> game server`

If no character exists, Account Core enters `CHARACTER_CREATION_REQUIRED`.
If characters exist but none is selected on this device, it enters `CHARACTER_SELECTION_REQUIRED`.
If the previously selected character still exists, it enters `READY_TO_PLAY` without another login prompt.

## Implemented client foundation

Package: `com.blackrussia.game.vostok.account`

- `AuthProvider` — Google / VK / Yandex / Email.
- `AccountState` — explicit login/OTP/character/ticket state machine.
- `AccountModels` — session, profile, character, OTP challenge, game ticket.
- `AccountGateway` — backend contract, independent from launcher UI.
- `HttpAccountGateway` — REST transport implementation.
- `SecureSessionStore` — encrypted refresh-token persistence through Android Keystore on API 23+.
- `AccountCore` — state machine and orchestration.
- `AccountCoreBootstrap` — process-wide access point.
- `VostokApplication` — initializes the core without changing the current launcher flow.

Provider SDKs are deliberately NOT wired yet. The future Google/VK/Yandex UI obtains a provider credential and passes it to `AccountCore.authorizeProvider(...)`. The VOSTOK backend, not the APK, must verify that credential with the provider.

The current Play button is deliberately not gated by Account Core yet. That switch belongs to the visual/integration phase after backend endpoints exist, so this foundation cannot lock us out of the current test client.

## REST contract v1

Production transport must use HTTPS.

`POST /v1/auth/email/request-code`
- request: `{"email":"player@example.com"}`
- response: `challenge_id`, `expires_at`, `resend_after_seconds`

`POST /v1/auth/email/verify-code`
- request: `challenge_id`, `code`
- response: account session

`POST /v1/auth/provider/exchange`
- request: `provider`, `credential`
- response: account session

`POST /v1/auth/session/refresh`
- request: `refresh_token`
- response: renewed account session

`GET /v1/account/me`
- Bearer access token
- response: `account_id`, `display_name`, `email`

`GET /v1/characters`
- Bearer access token
- response: `characters[]` with `character_id`, `name`, `level`, `skin_id`, `server_id`

`POST /v1/game-ticket`
- Bearer access token
- request: `character_id`
- response: `ticket`, `character_id`, `server_host`, `server_port`, `expires_at`

## Security rules

- No VOSTOK password exists in the game client or game server.
- Email OTP must be short-lived, rate-limited and single-use.
- Provider credentials must be verified server-side.
- Access tokens should be short-lived.
- Refresh tokens are encrypted at rest on supported Android versions.
- On Android below API 23 the foundation intentionally does not persist the refresh token, so silent login is not promised there.
- A game ticket is short-lived and single-use. The game server validates it and derives `account_id` and `character_id`; the client must never be trusted to assert identity by itself.
- Selected character ID is only a preference. The backend/game server remains authoritative.

## Deferred to phase 2

- visual login screen;
- Google Identity integration;
- VK ID integration;
- Yandex ID integration;
- email/OTP screen;
- character cards in the lower-left launcher area;
- Character Creator;
- Play-button gating;
- JNI/game connection ticket handoff;
- account linking/recovery UI.

This document is the contract those later systems should target rather than introducing a second authentication path.
