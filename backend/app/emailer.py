from __future__ import annotations

import logging
import smtplib
from email.message import EmailMessage
from typing import Protocol

from .config import Settings


class EmailSender(Protocol):
    def send_otp(self, email: str, code: str, expires_minutes: int) -> None: ...


class ConsoleEmailSender:
    def send_otp(self, email: str, code: str, expires_minutes: int) -> None:
        logging.getLogger("vostok.email").warning("DEV EMAIL OTP for %s: %s (expires in %sm)", email, code, expires_minutes)


class SmtpEmailSender:
    def __init__(self, settings: Settings):
        self.settings = settings

    def send_otp(self, email: str, code: str, expires_minutes: int) -> None:
        msg = EmailMessage()
        msg["Subject"] = "Код входа VOSTOK"
        msg["From"] = self.settings.smtp_from
        msg["To"] = email
        msg.set_content(f"Ваш одноразовый код VOSTOK: {code}\nКод действует {expires_minutes} мин.\nЕсли вы не запрашивали вход, просто проигнорируйте письмо.")
        if self.settings.smtp_use_ssl:
            client = smtplib.SMTP_SSL(self.settings.smtp_host, self.settings.smtp_port, timeout=15)
        else:
            client = smtplib.SMTP(self.settings.smtp_host, self.settings.smtp_port, timeout=15)
            client.starttls()
        try:
            if self.settings.smtp_username:
                client.login(self.settings.smtp_username, self.settings.smtp_password)
            client.send_message(msg)
        finally:
            client.quit()


class MemoryEmailSender:
    def __init__(self):
        self.messages: list[tuple[str, str, int]] = []

    def send_otp(self, email: str, code: str, expires_minutes: int) -> None:
        self.messages.append((email, code, expires_minutes))


def build_email_sender(settings: Settings) -> EmailSender:
    return SmtpEmailSender(settings) if settings.email_mode == "smtp" else ConsoleEmailSender()
