from __future__ import annotations

from fastapi import Depends, FastAPI, Header, Request
from fastapi.responses import JSONResponse

from .config import Settings
from .db import Database
from .emailer import EmailSender, build_email_sender
from .providers import DefaultProviderVerifier, ProviderVerifier
from .schemas import AccountResponse, CharactersResponse, ConsumeGameTicketRequest, ConsumedTicketResponse, EmailChallengeResponse, EmailRequest, EmailVerifyRequest, GameTicketRequest, GameTicketResponse, ProviderExchangeRequest, RefreshRequest, SessionResponse
from .security import TokenError, verify_access_token
from .service import AccountService, ApiError, SessionBundle


def create_app(settings: Settings | None = None, email_sender: EmailSender | None = None, provider_verifier: ProviderVerifier | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    settings.validate()
    db = Database(settings.db_path)
    db.initialize()
    service = AccountService(settings, db, email_sender or build_email_sender(settings), provider_verifier or DefaultProviderVerifier(settings))
    app = FastAPI(title="VOSTOK Account Backend", version="1.0.0")
    app.state.auth_service = service
    app.state.settings = settings

    @app.exception_handler(ApiError)
    async def api_error_handler(_request: Request, exc: ApiError):
        return JSONResponse(status_code=exc.status_code, content={"error": {"code": exc.code, "message": exc.message}})

    def current_account_id(authorization: str = Header(default="")) -> int:
        if not authorization.startswith("Bearer "):
            raise ApiError(401, "missing_access_token", "Authorization token is required")
        try:
            return verify_access_token(settings, authorization[7:].strip())
        except TokenError as exc:
            raise ApiError(401, "invalid_access_token", "Authorization token is invalid") from exc

    @app.get("/health")
    def health():
        return {"status": "ok", "service": "vostok-account"}

    @app.post("/v1/auth/email/request-code", response_model=EmailChallengeResponse)
    def request_email_code(body: EmailRequest):
        return service.request_email_code(str(body.email))

    @app.post("/v1/auth/email/verify-code", response_model=SessionResponse)
    def verify_email_code(body: EmailVerifyRequest):
        return _session(service.verify_email_code(body.challenge_id, body.code))

    @app.post("/v1/auth/provider/exchange", response_model=SessionResponse)
    async def exchange_provider(body: ProviderExchangeRequest):
        return _session(await service.exchange_provider(body.provider, body.credential))

    @app.post("/v1/auth/session/refresh", response_model=SessionResponse)
    def refresh_session(body: RefreshRequest):
        return _session(service.refresh_session(body.refresh_token))

    @app.get("/v1/account/me", response_model=AccountResponse)
    def account_me(account_id: int = Depends(current_account_id)):
        return service.get_account(account_id)

    @app.get("/v1/characters", response_model=CharactersResponse)
    def characters(account_id: int = Depends(current_account_id)):
        return {"characters": service.list_characters(account_id)}

    @app.post("/v1/game-ticket", response_model=GameTicketResponse)
    def game_ticket(body: GameTicketRequest, account_id: int = Depends(current_account_id)):
        return service.issue_game_ticket(account_id, body.character_id)

    @app.post("/v1/internal/game-ticket/consume", response_model=ConsumedTicketResponse)
    def consume_game_ticket(body: ConsumeGameTicketRequest, x_vostok_server_key: str = Header(default="")):
        return service.consume_game_ticket(x_vostok_server_key, body.ticket)

    return app


def _session(value: SessionBundle) -> dict:
    return {"access_token": value.access_token, "refresh_token": value.refresh_token, "expires_at": value.expires_at}


app = create_app()
