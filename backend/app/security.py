from __future__ import annotations

import hashlib
import hmac
import secrets
import time
from typing import Any

import jwt

from .config import Settings


class TokenError(ValueError):
    pass


def now_ts() -> int:
    return int(time.time())


def random_token(bytes_count: int = 32) -> str:
    return secrets.token_urlsafe(bytes_count)


def hash_token(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def normalize_email(email: str) -> str:
    return email.strip().casefold()


def otp_digest(settings: Settings, challenge_id: str, email: str, code: str) -> str:
    material = f"{challenge_id}\n{normalize_email(email)}\n{code}".encode("utf-8")
    return hmac.new(settings.otp_pepper.encode("utf-8"), material, hashlib.sha256).hexdigest()


def issue_access_token(settings: Settings, account_id: int) -> tuple[str, int]:
    issued_at = now_ts()
    expires_at = issued_at + settings.access_ttl_seconds
    payload: dict[str, Any] = {
        "iss": "vostok-account",
        "sub": str(account_id),
        "typ": "access",
        "iat": issued_at,
        "exp": expires_at,
        "jti": random_token(12),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256"), expires_at


def verify_access_token(settings: Settings, token: str) -> int:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=["HS256"],
            issuer="vostok-account",
            options={"require": ["sub", "typ", "exp", "iat"]},
        )
    except jwt.PyJWTError as exc:
        raise TokenError("invalid_access_token") from exc
    if payload.get("typ") != "access":
        raise TokenError("invalid_access_token_type")
    try:
        return int(payload["sub"])
    except (TypeError, ValueError, KeyError) as exc:
        raise TokenError("invalid_access_token_subject") from exc
