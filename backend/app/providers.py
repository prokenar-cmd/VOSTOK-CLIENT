from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

import httpx
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from starlette.concurrency import run_in_threadpool

from .config import Settings


class ProviderError(ValueError):
    pass


@dataclass(frozen=True)
class ProviderIdentity:
    provider: str
    subject: str
    display_name: str
    email: str | None


class ProviderVerifier(Protocol):
    async def verify(self, provider: str, credential: str) -> ProviderIdentity: ...


class DefaultProviderVerifier:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def verify(self, provider: str, credential: str) -> ProviderIdentity:
        provider = provider.strip().lower()
        if provider == "google":
            return await self._google(credential)
        if provider == "yandex":
            return await self._yandex(credential)
        if provider == "vk":
            return await self._vk(credential)
        raise ProviderError("unsupported_provider")

    async def _google(self, credential: str) -> ProviderIdentity:
        if not self.settings.google_client_id:
            raise ProviderError("google_not_configured")
        def verify_sync():
            return google_id_token.verify_oauth2_token(credential, google_requests.Request(), self.settings.google_client_id)
        try:
            claims = await run_in_threadpool(verify_sync)
        except Exception as exc:
            raise ProviderError("invalid_google_credential") from exc
        subject = str(claims.get("sub") or "")
        if not subject:
            raise ProviderError("invalid_google_subject")
        name = str(claims.get("name") or claims.get("email") or "Google player")
        email = claims.get("email")
        return ProviderIdentity("google", subject, name, str(email) if email else None)

    async def _yandex(self, credential: str) -> ProviderIdentity:
        headers = {"Authorization": f"OAuth {credential}"}
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get("https://login.yandex.ru/info", params={"format": "json"}, headers=headers)
            response.raise_for_status()
            data = response.json()
        except Exception as exc:
            raise ProviderError("invalid_yandex_credential") from exc
        subject = str(data.get("id") or data.get("uid") or "")
        if not subject:
            raise ProviderError("invalid_yandex_subject")
        name = str(data.get("real_name") or data.get("display_name") or data.get("login") or "Yandex player")
        email = data.get("default_email")
        return ProviderIdentity("yandex", subject, name, str(email) if email else None)

    async def _vk(self, credential: str) -> ProviderIdentity:
        if not self.settings.vk_client_id:
            raise ProviderError("vk_not_configured")
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post("https://id.vk.com/oauth2/user_info", params={"client_id": self.settings.vk_client_id}, data={"access_token": credential})
            response.raise_for_status()
            data = response.json()
        except Exception as exc:
            raise ProviderError("invalid_vk_credential") from exc
        if data.get("error"):
            raise ProviderError("invalid_vk_credential")
        user = data.get("user") or data
        subject = str(user.get("user_id") or user.get("id") or "")
        if not subject:
            raise ProviderError("invalid_vk_subject")
        first = str(user.get("first_name") or "").strip()
        last = str(user.get("last_name") or "").strip()
        email = user.get("email")
        name = " ".join(x for x in (first, last) if x) or (str(email) if email else "VK player")
        return ProviderIdentity("vk", subject, name, str(email) if email else None)
