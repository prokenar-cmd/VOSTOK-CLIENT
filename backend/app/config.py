from __future__ import annotations

import os
from dataclasses import dataclass


DEV_JWT_SECRET = "dev-only-change-me-jwt-secret-32-bytes"
DEV_OTP_PEPPER = "dev-only-change-me-otp-pepper-32-bytes"
DEV_SERVER_KEY = "dev-only-change-me-server-key-32-bytes"


@dataclass(frozen=True)
class Settings:
    environment: str = "development"
    db_path: str = "data/vostok_auth.db"
    jwt_secret: str = DEV_JWT_SECRET
    otp_pepper: str = DEV_OTP_PEPPER
    server_shared_key: str = DEV_SERVER_KEY
    access_ttl_seconds: int = 15 * 60
    refresh_ttl_seconds: int = 30 * 24 * 60 * 60
    otp_ttl_seconds: int = 10 * 60
    otp_resend_seconds: int = 60
    otp_max_attempts: int = 5
    otp_window_seconds: int = 15 * 60
    otp_max_requests_per_window: int = 5
    game_ticket_ttl_seconds: int = 45
    game_server_id: str = "vostok-dev-1"
    game_server_host: str = "192.168.0.22"
    game_server_port: int = 7777
    email_mode: str = "console"
    smtp_host: str = ""
    smtp_port: int = 465
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_from: str = ""
    smtp_use_ssl: bool = True
    google_client_id: str = ""
    vk_client_id: str = ""
    yandex_client_id: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        b = lambda name, default: os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}
        i = lambda name, default: int(os.getenv(name, str(default)))
        return cls(
            environment=os.getenv("VOSTOK_ENV", "development"),
            db_path=os.getenv("VOSTOK_DB_PATH", "data/vostok_auth.db"),
            jwt_secret=os.getenv("VOSTOK_JWT_SECRET", DEV_JWT_SECRET),
            otp_pepper=os.getenv("VOSTOK_OTP_PEPPER", DEV_OTP_PEPPER),
            server_shared_key=os.getenv("VOSTOK_SERVER_SHARED_KEY", DEV_SERVER_KEY),
            access_ttl_seconds=i("VOSTOK_ACCESS_TTL_SECONDS", 15 * 60),
            refresh_ttl_seconds=i("VOSTOK_REFRESH_TTL_SECONDS", 30 * 24 * 60 * 60),
            otp_ttl_seconds=i("VOSTOK_OTP_TTL_SECONDS", 10 * 60),
            otp_resend_seconds=i("VOSTOK_OTP_RESEND_SECONDS", 60),
            otp_max_attempts=i("VOSTOK_OTP_MAX_ATTEMPTS", 5),
            otp_window_seconds=i("VOSTOK_OTP_WINDOW_SECONDS", 15 * 60),
            otp_max_requests_per_window=i("VOSTOK_OTP_MAX_REQUESTS_PER_WINDOW", 5),
            game_ticket_ttl_seconds=i("VOSTOK_GAME_TICKET_TTL_SECONDS", 45),
            game_server_id=os.getenv("VOSTOK_GAME_SERVER_ID", "vostok-dev-1"),
            game_server_host=os.getenv("VOSTOK_GAME_SERVER_HOST", "192.168.0.22"),
            game_server_port=i("VOSTOK_GAME_SERVER_PORT", 7777),
            email_mode=os.getenv("VOSTOK_EMAIL_MODE", "console"),
            smtp_host=os.getenv("VOSTOK_SMTP_HOST", ""),
            smtp_port=i("VOSTOK_SMTP_PORT", 465),
            smtp_username=os.getenv("VOSTOK_SMTP_USERNAME", ""),
            smtp_password=os.getenv("VOSTOK_SMTP_PASSWORD", ""),
            smtp_from=os.getenv("VOSTOK_SMTP_FROM", ""),
            smtp_use_ssl=b("VOSTOK_SMTP_USE_SSL", True),
            google_client_id=os.getenv("VOSTOK_GOOGLE_CLIENT_ID", ""),
            vk_client_id=os.getenv("VOSTOK_VK_CLIENT_ID", ""),
            yandex_client_id=os.getenv("VOSTOK_YANDEX_CLIENT_ID", ""),
        )

    def validate(self) -> None:
        if self.environment.strip().lower() == "production":
            defaults = {
                "VOSTOK_JWT_SECRET": (self.jwt_secret, DEV_JWT_SECRET),
                "VOSTOK_OTP_PEPPER": (self.otp_pepper, DEV_OTP_PEPPER),
                "VOSTOK_SERVER_SHARED_KEY": (self.server_shared_key, DEV_SERVER_KEY),
            }
            for name, (value, default) in defaults.items():
                if value == default or len(value) < 32:
                    raise RuntimeError(f"{name} must be a unique secret of at least 32 characters in production")
            if self.email_mode != "smtp":
                raise RuntimeError("VOSTOK_EMAIL_MODE must be smtp in production")
            if not self.smtp_host or not self.smtp_from:
                raise RuntimeError("SMTP host/from must be configured in production")
