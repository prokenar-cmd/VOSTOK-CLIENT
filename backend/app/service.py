from __future__ import annotations

import hmac
import secrets
from dataclasses import dataclass

from .config import Settings
from .db import Database
from .emailer import EmailSender
from .providers import ProviderIdentity, ProviderVerifier, ProviderError
from .security import hash_token, issue_access_token, normalize_email, now_ts, otp_digest, random_token


class ApiError(RuntimeError):
    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


@dataclass(frozen=True)
class SessionBundle:
    access_token: str
    refresh_token: str
    expires_at: int


class AccountService:
    def __init__(self, settings: Settings, db: Database, email_sender: EmailSender, provider_verifier: ProviderVerifier):
        self.settings = settings
        self.db = db
        self.email_sender = email_sender
        self.provider_verifier = provider_verifier

    def request_email_code(self, email: str) -> dict:
        email = normalize_email(email)
        now = now_ts()
        with self.db.transaction() as conn:
            recent = conn.execute("SELECT challenge_id, resend_after FROM email_challenges WHERE email=? ORDER BY created_at DESC LIMIT 1", (email,)).fetchone()
            if recent and int(recent["resend_after"]) > now:
                raise ApiError(429, "otp_resend_too_soon", "Подождите перед повторной отправкой кода")
            count = conn.execute("SELECT COUNT(*) AS c FROM email_challenges WHERE email=? AND created_at>=?", (email, now - self.settings.otp_window_seconds)).fetchone()["c"]
            if int(count) >= self.settings.otp_max_requests_per_window:
                raise ApiError(429, "otp_rate_limited", "Слишком много запросов кода. Попробуйте позже")
            challenge_id = random_token(18)
            code = f"{secrets.randbelow(1_000_000):06d}"
            expires_at = now + self.settings.otp_ttl_seconds
            resend_after = now + self.settings.otp_resend_seconds
            digest = otp_digest(self.settings, challenge_id, email, code)
            conn.execute("INSERT INTO email_challenges(challenge_id,email,code_hash,created_at,expires_at,resend_after,attempts,consumed_at) VALUES(?,?,?,?,?,?,0,NULL)", (challenge_id, email, digest, now, expires_at, resend_after))
        try:
            self.email_sender.send_otp(email, code, max(1, self.settings.otp_ttl_seconds // 60))
        except Exception as exc:
            with self.db.transaction() as conn:
                conn.execute("DELETE FROM email_challenges WHERE challenge_id=?", (challenge_id,))
            raise ApiError(503, "email_delivery_failed", "Не удалось отправить код") from exc
        return {"challenge_id": challenge_id, "expires_at": expires_at, "resend_after_seconds": self.settings.otp_resend_seconds}

    def verify_email_code(self, challenge_id: str, code: str) -> SessionBundle:
        now = now_ts()
        with self.db.transaction() as conn:
            row = conn.execute("SELECT * FROM email_challenges WHERE challenge_id=?", (challenge_id,)).fetchone()
            if not row:
                raise ApiError(400, "otp_not_found", "Код не найден или устарел")
            if row["consumed_at"] is not None:
                raise ApiError(400, "otp_already_used", "Этот код уже использован")
            if int(row["expires_at"]) <= now:
                raise ApiError(400, "otp_expired", "Срок действия кода истёк")
            if int(row["attempts"]) >= self.settings.otp_max_attempts:
                raise ApiError(429, "otp_attempts_exceeded", "Превышено число попыток")
            expected = str(row["code_hash"])
            actual = otp_digest(self.settings, challenge_id, str(row["email"]), code.strip())
            if not hmac.compare_digest(expected, actual):
                conn.execute("UPDATE email_challenges SET attempts=attempts+1 WHERE challenge_id=?", (challenge_id,))
                raise ApiError(400, "otp_invalid", "Неверный одноразовый код")
            conn.execute("UPDATE email_challenges SET consumed_at=? WHERE challenge_id=?", (now, challenge_id))
            identity = conn.execute("SELECT account_id FROM identities WHERE provider='email' AND subject=?", (str(row["email"]),)).fetchone()
            if identity:
                account_id = int(identity["account_id"])
            else:
                local = str(row["email"]).split("@", 1)[0][:40] or "Player"
                cur = conn.execute("INSERT INTO accounts(display_name,email,created_at) VALUES(?,?,?)", (local, str(row["email"]), now))
                account_id = int(cur.lastrowid)
                conn.execute("INSERT INTO identities(account_id,provider,subject,email,created_at) VALUES(?,?,?,?,?)", (account_id, "email", str(row["email"]), str(row["email"]), now))
        return self._issue_session(account_id)

    async def exchange_provider(self, provider: str, credential: str) -> SessionBundle:
        try:
            identity: ProviderIdentity = await self.provider_verifier.verify(provider, credential)
        except ProviderError as exc:
            raise ApiError(401, str(exc), "Не удалось подтвердить аккаунт провайдера") from exc
        now = now_ts()
        existing = self.db.find_identity(identity.provider, identity.subject)
        if existing:
            account_id = int(existing["account_id"])
        else:
            account_id = self.db.create_account_with_identity(identity.provider, identity.subject, identity.email, identity.display_name, now)
        return self._issue_session(account_id)

    def refresh_session(self, refresh_token: str) -> SessionBundle:
        now = now_ts()
        digest = hash_token(refresh_token)
        with self.db.transaction() as conn:
            row = conn.execute("SELECT * FROM refresh_sessions WHERE token_hash=?", (digest,)).fetchone()
            if not row or row["revoked_at"] is not None or int(row["expires_at"]) <= now:
                raise ApiError(401, "invalid_refresh_token", "Сессия истекла")
            account_id = int(row["account_id"])
            conn.execute("UPDATE refresh_sessions SET revoked_at=? WHERE id=?", (now, int(row["id"])))
        return self._issue_session(account_id)

    def get_account(self, account_id: int) -> dict:
        row = self.db.get_account(account_id)
        if not row:
            raise ApiError(404, "account_not_found", "Аккаунт не найден")
        return {"account_id": int(row["id"]), "display_name": str(row["display_name"]), "email": row["email"]}

    def list_characters(self, account_id: int) -> list[dict]:
        return [{"character_id": int(row["id"]), "name": str(row["name"]), "level": int(row["level"]), "skin_id": int(row["skin_id"]), "server_id": str(row["server_id"])} for row in self.db.list_characters(account_id)]

    def issue_game_ticket(self, account_id: int, character_id: int) -> dict:
        character = self.db.get_character_for_account(account_id, character_id)
        if not character:
            raise ApiError(404, "character_not_found", "Персонаж не принадлежит этому аккаунту")
        if str(character["server_id"]) != self.settings.game_server_id:
            raise ApiError(409, "character_server_mismatch", "Персонаж находится на другом сервере")
        now = now_ts()
        expires_at = now + self.settings.game_ticket_ttl_seconds
        ticket = random_token(32)
        with self.db.transaction() as conn:
            conn.execute("INSERT INTO game_tickets(token_hash,account_id,character_id,server_id,created_at,expires_at,consumed_at) VALUES(?,?,?,?,?,?,NULL)", (hash_token(ticket), account_id, character_id, self.settings.game_server_id, now, expires_at))
        return {"ticket": ticket, "character_id": character_id, "server_host": self.settings.game_server_host, "server_port": self.settings.game_server_port, "expires_at": expires_at}

    def consume_game_ticket(self, server_key: str, ticket: str) -> dict:
        if not hmac.compare_digest(server_key or "", self.settings.server_shared_key):
            raise ApiError(401, "invalid_server_key", "Server authentication failed")
        now = now_ts()
        with self.db.transaction() as conn:
            row = conn.execute("SELECT gt.*, c.name, c.skin_id, c.level FROM game_tickets gt JOIN characters c ON c.id=gt.character_id WHERE gt.token_hash=?", (hash_token(ticket),)).fetchone()
            if not row:
                raise ApiError(401, "invalid_game_ticket", "Game ticket is invalid")
            if row["consumed_at"] is not None:
                raise ApiError(401, "game_ticket_already_used", "Game ticket was already used")
            if int(row["expires_at"]) <= now:
                raise ApiError(401, "game_ticket_expired", "Game ticket expired")
            conn.execute("UPDATE game_tickets SET consumed_at=? WHERE id=? AND consumed_at IS NULL", (now, int(row["id"])))
            return {"account_id": int(row["account_id"]), "character_id": int(row["character_id"]), "character_name": str(row["name"]), "server_id": str(row["server_id"]), "skin_id": int(row["skin_id"]), "level": int(row["level"])}

    def _issue_session(self, account_id: int) -> SessionBundle:
        now = now_ts()
        access_token, access_expires_at = issue_access_token(self.settings, account_id)
        refresh_token = random_token(40)
        with self.db.transaction() as conn:
            conn.execute("INSERT INTO refresh_sessions(account_id,token_hash,created_at,expires_at,revoked_at) VALUES(?,?,?,?,NULL)", (account_id, hash_token(refresh_token), now, now + self.settings.refresh_ttl_seconds))
        return SessionBundle(access_token, refresh_token, access_expires_at)
