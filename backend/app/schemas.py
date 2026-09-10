from __future__ import annotations

from pydantic import BaseModel, EmailStr, Field


class EmailRequest(BaseModel):
    email: EmailStr

class EmailChallengeResponse(BaseModel):
    challenge_id: str
    expires_at: int
    resend_after_seconds: int

class EmailVerifyRequest(BaseModel):
    challenge_id: str = Field(min_length=8, max_length=256)
    code: str = Field(min_length=4, max_length=12)

class ProviderExchangeRequest(BaseModel):
    provider: str = Field(min_length=2, max_length=32)
    credential: str = Field(min_length=8, max_length=16384)

class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=20, max_length=4096)

class SessionResponse(BaseModel):
    access_token: str
    refresh_token: str
    expires_at: int

class AccountResponse(BaseModel):
    account_id: int
    display_name: str
    email: str | None = None

class CharacterResponse(BaseModel):
    character_id: int
    name: str
    level: int
    skin_id: int
    server_id: str

class CharactersResponse(BaseModel):
    characters: list[CharacterResponse]

class GameTicketRequest(BaseModel):
    character_id: int = Field(gt=0)

class GameTicketResponse(BaseModel):
    ticket: str
    character_id: int
    server_host: str
    server_port: int
    expires_at: int

class ConsumeGameTicketRequest(BaseModel):
    ticket: str = Field(min_length=20, max_length=4096)

class ConsumedTicketResponse(BaseModel):
    account_id: int
    character_id: int
    character_name: str
    server_id: str
    skin_id: int
    level: int
