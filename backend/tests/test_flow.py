from __future__ import annotations

import tempfile
from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings
from app.emailer import MemoryEmailSender
from app.main import create_app
from app.providers import ProviderIdentity, ProviderError


class FakeProviderVerifier:
    async def verify(self, provider: str, credential: str) -> ProviderIdentity:
        if credential != "valid-provider-credential":
            raise ProviderError("bad_provider_credential")
        return ProviderIdentity(provider, "provider-user-1", "Provider Player", "p@example.com")


def make_client():
    temp = tempfile.TemporaryDirectory()
    settings = Settings(environment="test", db_path=str(Path(temp.name) / "auth.db"), jwt_secret="test-jwt-secret-" + "x" * 32, otp_pepper="test-otp-pepper-" + "x" * 32, server_shared_key="test-server-key-" + "x" * 32, game_server_id="vostok-dev-1", game_server_host="127.0.0.1", game_server_port=7777, email_mode="console")
    sender = MemoryEmailSender()
    app = create_app(settings, sender, FakeProviderVerifier())
    return temp, settings, sender, app, TestClient(app)


def test_email_otp_session_character_ticket_flow():
    temp, settings, sender, app, client = make_client()
    try:
        r = client.post("/v1/auth/email/request-code", json={"email": "Player@Example.com"})
        assert r.status_code == 200, r.text
        challenge = r.json()
        assert "code" not in challenge
        assert sender.messages[0][0] == "player@example.com"
        code = sender.messages[0][1]
        wrong_code = "111111" if code != "111111" else "222222"
        wrong = client.post("/v1/auth/email/verify-code", json={"challenge_id": challenge["challenge_id"], "code": wrong_code})
        assert wrong.status_code == 400
        assert wrong.json()["error"]["code"] == "otp_invalid"
        verified = client.post("/v1/auth/email/verify-code", json={"challenge_id": challenge["challenge_id"], "code": code})
        assert verified.status_code == 200, verified.text
        session = verified.json()
        headers = {"Authorization": f"Bearer {session['access_token']}"}
        me = client.get("/v1/account/me", headers=headers)
        assert me.status_code == 200
        account_id = me.json()["account_id"]
        assert me.json()["email"] == "player@example.com"
        assert client.get("/v1/characters", headers=headers).json() == {"characters": []}
        character_id = app.state.auth_service.db.create_character_for_test(account_id, "Test_Player", settings.game_server_id, 1_700_000_000, skin_id=42)
        issued = client.post("/v1/game-ticket", headers=headers, json={"character_id": character_id})
        assert issued.status_code == 200, issued.text
        ticket = issued.json()["ticket"]
        consumed = client.post("/v1/internal/game-ticket/consume", headers={"X-VOSTOK-Server-Key": settings.server_shared_key}, json={"ticket": ticket})
        assert consumed.status_code == 200, consumed.text
        assert consumed.json()["account_id"] == account_id
        assert consumed.json()["character_id"] == character_id
        assert consumed.json()["skin_id"] == 42
        reused = client.post("/v1/internal/game-ticket/consume", headers={"X-VOSTOK-Server-Key": settings.server_shared_key}, json={"ticket": ticket})
        assert reused.status_code == 401
        assert reused.json()["error"]["code"] == "game_ticket_already_used"
    finally:
        temp.cleanup()


def test_refresh_token_rotates():
    temp, settings, sender, app, client = make_client()
    try:
        c = client.post("/v1/auth/email/request-code", json={"email": "a@example.com"}).json()
        code = sender.messages[-1][1]
        session = client.post("/v1/auth/email/verify-code", json={"challenge_id": c["challenge_id"], "code": code}).json()
        first = client.post("/v1/auth/session/refresh", json={"refresh_token": session["refresh_token"]})
        assert first.status_code == 200
        assert first.json()["refresh_token"] != session["refresh_token"]
        replay = client.post("/v1/auth/session/refresh", json={"refresh_token": session["refresh_token"]})
        assert replay.status_code == 401
        assert replay.json()["error"]["code"] == "invalid_refresh_token"
    finally:
        temp.cleanup()


def test_provider_exchange_uses_verified_subject():
    temp, settings, sender, app, client = make_client()
    try:
        r = client.post("/v1/auth/provider/exchange", json={"provider": "google", "credential": "valid-provider-credential"})
        assert r.status_code == 200, r.text
        headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
        me = client.get("/v1/account/me", headers=headers)
        assert me.status_code == 200
        assert me.json()["display_name"] == "Provider Player"
    finally:
        temp.cleanup()
