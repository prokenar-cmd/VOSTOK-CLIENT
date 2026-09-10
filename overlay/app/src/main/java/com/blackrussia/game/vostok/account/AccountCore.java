package com.blackrussia.game.vostok.account;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.blackrussia.game.vostok.account.AccountModels.AccountProfile;
import static com.blackrussia.game.vostok.account.AccountModels.AccountSession;
import static com.blackrussia.game.vostok.account.AccountModels.CharacterProfile;
import static com.blackrussia.game.vostok.account.AccountModels.EmailOtpChallenge;
import static com.blackrussia.game.vostok.account.AccountModels.GameTicket;

public final class AccountCore {
    public interface Listener { void onAccountStateChanged(AccountSnapshot snapshot); }
    public interface GameTicketListener {
        void onGameTicketReady(GameTicket ticket);
        void onGameTicketError(String code, String message);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SecureSessionStore sessionStore;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private AccountGateway gateway;
    private AccountState state = AccountState.BACKEND_NOT_CONFIGURED;
    private AccountSession session;
    private AccountProfile profile;
    private List<CharacterProfile> characters = Collections.emptyList();
    private CharacterProfile selectedCharacter;
    private EmailOtpChallenge pendingEmailChallenge;
    private String lastErrorCode;
    private String lastErrorMessage;

    AccountCore(SecureSessionStore sessionStore) { this.sessionStore = sessionStore; }

    public synchronized void setGateway(AccountGateway gateway) {
        this.gateway = gateway;
        state = gateway == null ? AccountState.BACKEND_NOT_CONFIGURED : AccountState.SIGNED_OUT;
        clearErrorLocked();
        notifyListenersLocked();
    }

    public synchronized boolean isBackendConfigured() { return gateway != null; }
    public synchronized AccountSnapshot snapshot() { return buildSnapshotLocked(); }

    public void addListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
        listener.onAccountStateChanged(snapshot());
    }

    public void removeListener(Listener listener) { listeners.remove(listener); }

    public void restoreSession() {
        final AccountGateway activeGateway;
        final SecureSessionStore.RestoredSession restored;
        synchronized (this) {
            activeGateway = gateway;
            if (activeGateway == null) { setErrorLocked("backend_not_configured", "Account backend is not configured"); return; }
            restored = sessionStore.restoreSession();
            if (restored == null || !restored.canRestoreSilently()) {
                state = AccountState.SIGNED_OUT;
                notifyListenersLocked();
                return;
            }
            state = AccountState.RESTORING_SESSION;
            clearErrorLocked();
            notifyListenersLocked();
        }

        activeGateway.refreshSession(restored.getRefreshToken(), new AccountGateway.Callback<AccountSession>() {
            @Override public void onSuccess(final AccountSession value) { onMain(new Runnable() { @Override public void run() { acceptSessionAndLoad(value); } }); }
            @Override public void onError(final String code, final String message) {
                onMain(new Runnable() { @Override public void run() {
                    synchronized (AccountCore.this) {
                        sessionStore.clearSession();
                        session = null; profile = null; characters = Collections.emptyList(); selectedCharacter = null;
                        state = AccountState.SIGNED_OUT; lastErrorCode = code; lastErrorMessage = message;
                        notifyListenersLocked();
                    }
                }});
            }
        });
    }

    public void requestEmailCode(final String email) {
        final AccountGateway activeGateway;
        synchronized (this) {
            activeGateway = gateway;
            if (activeGateway == null) { setErrorLocked("backend_not_configured", "Account backend is not configured"); return; }
            if (email == null || !email.contains("@")) { setErrorLocked("invalid_email", "Email is invalid"); return; }
            state = AccountState.REQUESTING_EMAIL_CODE; clearErrorLocked(); notifyListenersLocked();
        }
        activeGateway.requestEmailCode(email.trim(), new AccountGateway.Callback<EmailOtpChallenge>() {
            @Override public void onSuccess(final EmailOtpChallenge challenge) { onMain(new Runnable() { @Override public void run() {
                synchronized (AccountCore.this) { pendingEmailChallenge = challenge; state = AccountState.WAITING_EMAIL_CODE; clearErrorLocked(); notifyListenersLocked(); }
            }}); }
            @Override public void onError(final String code, final String message) { onMain(new Runnable() { @Override public void run() { fail(code, message); } }); }
        });
    }

    public void verifyEmailCode(final String code) {
        final AccountGateway activeGateway;
        final EmailOtpChallenge challenge;
        synchronized (this) {
            activeGateway = gateway; challenge = pendingEmailChallenge;
            if (activeGateway == null) { setErrorLocked("backend_not_configured", "Account backend is not configured"); return; }
            if (challenge == null) { setErrorLocked("otp_not_requested", "Email code was not requested"); return; }
            if (code == null || code.trim().length() < 4) { setErrorLocked("invalid_code", "Email code is invalid"); return; }
            state = AccountState.AUTHORIZING_PROVIDER; clearErrorLocked(); notifyListenersLocked();
        }
        activeGateway.verifyEmailCode(challenge.getChallengeId(), code.trim(), new AccountGateway.Callback<AccountSession>() {
            @Override public void onSuccess(final AccountSession value) { onMain(new Runnable() { @Override public void run() { acceptSessionAndLoad(value); } }); }
            @Override public void onError(final String errorCode, final String message) { onMain(new Runnable() { @Override public void run() { fail(errorCode, message); } }); }
        });
    }

    public void authorizeProvider(final AuthProvider provider, final String providerCredential) {
        final AccountGateway activeGateway;
        synchronized (this) {
            activeGateway = gateway;
            if (activeGateway == null) { setErrorLocked("backend_not_configured", "Account backend is not configured"); return; }
            if (provider == null || provider == AuthProvider.EMAIL) { setErrorLocked("invalid_provider", "Use email OTP flow for email"); return; }
            if (providerCredential == null || providerCredential.trim().isEmpty()) { setErrorLocked("missing_provider_credential", "Provider credential is missing"); return; }
            state = AccountState.AUTHORIZING_PROVIDER; clearErrorLocked(); notifyListenersLocked();
        }
        activeGateway.exchangeProviderCredential(provider, providerCredential, new AccountGateway.Callback<AccountSession>() {
            @Override public void onSuccess(final AccountSession value) { onMain(new Runnable() { @Override public void run() { acceptSessionAndLoad(value); } }); }
            @Override public void onError(final String code, final String message) { onMain(new Runnable() { @Override public void run() { fail(code, message); } }); }
        });
    }

    public synchronized boolean selectCharacter(long characterId) {
        for (CharacterProfile character : characters) {
            if (character.getCharacterId() == characterId) {
                selectedCharacter = character;
                sessionStore.setSelectedCharacterId(characterId);
                state = AccountState.READY_TO_PLAY;
                clearErrorLocked(); notifyListenersLocked(); return true;
            }
        }
        setErrorLocked("character_not_found", "Character is not available for this account");
        return false;
    }

    public void requestGameTicket(final GameTicketListener listener) {
        final AccountGateway activeGateway;
        final AccountSession activeSession;
        final CharacterProfile character;
        synchronized (this) {
            activeGateway = gateway; activeSession = session; character = selectedCharacter;
            if (activeGateway == null) { if (listener != null) listener.onGameTicketError("backend_not_configured", "Account backend is not configured"); return; }
            if (activeSession == null || character == null || state != AccountState.READY_TO_PLAY) { if (listener != null) listener.onGameTicketError("not_ready", "Account or character is not ready"); return; }
            state = AccountState.REQUESTING_GAME_TICKET; clearErrorLocked(); notifyListenersLocked();
        }
        activeGateway.issueGameTicket(activeSession.getAccessToken(), character.getCharacterId(), new AccountGateway.Callback<GameTicket>() {
            @Override public void onSuccess(final GameTicket value) { onMain(new Runnable() { @Override public void run() {
                synchronized (AccountCore.this) { state = AccountState.READY_TO_PLAY; notifyListenersLocked(); }
                if (listener != null) listener.onGameTicketReady(value);
            }}); }
            @Override public void onError(final String code, final String message) { onMain(new Runnable() { @Override public void run() {
                fail(code, message); if (listener != null) listener.onGameTicketError(code, message);
            }}); }
        });
    }

    public synchronized void logout() {
        sessionStore.clearSession(); session = null; profile = null; characters = Collections.emptyList(); selectedCharacter = null; pendingEmailChallenge = null;
        state = gateway == null ? AccountState.BACKEND_NOT_CONFIGURED : AccountState.SIGNED_OUT;
        clearErrorLocked(); notifyListenersLocked();
    }

    private void acceptSessionAndLoad(AccountSession value) {
        synchronized (this) {
            session = value; pendingEmailChallenge = null; sessionStore.saveSession(value);
            state = AccountState.LOADING_ACCOUNT; clearErrorLocked(); notifyListenersLocked();
        }
        loadAccountThenCharacters();
    }

    private void loadAccountThenCharacters() {
        final AccountGateway activeGateway;
        final AccountSession activeSession;
        synchronized (this) {
            activeGateway = gateway; activeSession = session;
            if (activeGateway == null || activeSession == null) { fail("not_authenticated", "No active account session"); return; }
        }
        activeGateway.loadAccount(activeSession.getAccessToken(), new AccountGateway.Callback<AccountProfile>() {
            @Override public void onSuccess(final AccountProfile accountProfile) {
                activeGateway.loadCharacters(activeSession.getAccessToken(), new AccountGateway.Callback<List<CharacterProfile>>() {
                    @Override public void onSuccess(final List<CharacterProfile> loadedCharacters) { onMain(new Runnable() { @Override public void run() {
                        synchronized (AccountCore.this) { profile = accountProfile; characters = AccountModels.immutableCharacters(loadedCharacters); resolveCharacterStateLocked(); notifyListenersLocked(); }
                    }}); }
                    @Override public void onError(final String code, final String message) { onMain(new Runnable() { @Override public void run() { fail(code, message); } }); }
                });
            }
            @Override public void onError(final String code, final String message) { onMain(new Runnable() { @Override public void run() { fail(code, message); } }); }
        });
    }

    private void resolveCharacterStateLocked() {
        selectedCharacter = null;
        if (characters.isEmpty()) { sessionStore.clearSelectedCharacter(); state = AccountState.CHARACTER_CREATION_REQUIRED; clearErrorLocked(); return; }
        long remembered = sessionStore.getSelectedCharacterId();
        if (remembered > 0) {
            for (CharacterProfile character : characters) {
                if (character.getCharacterId() == remembered) { selectedCharacter = character; state = AccountState.READY_TO_PLAY; clearErrorLocked(); return; }
            }
            sessionStore.clearSelectedCharacter();
        }
        state = AccountState.CHARACTER_SELECTION_REQUIRED; clearErrorLocked();
    }

    private synchronized void fail(String code, String message) { setErrorLocked(code, message); }
    private void setErrorLocked(String code, String message) { lastErrorCode = code; lastErrorMessage = message; state = AccountState.ERROR; notifyListenersLocked(); }
    private void clearErrorLocked() { lastErrorCode = null; lastErrorMessage = null; }

    private void notifyListenersLocked() {
        final AccountSnapshot snapshot = buildSnapshotLocked();
        for (final Listener listener : listeners) onMain(new Runnable() { @Override public void run() { listener.onAccountStateChanged(snapshot); } });
    }

    private AccountSnapshot buildSnapshotLocked() {
        return new AccountSnapshot(state, profile, characters, selectedCharacter, pendingEmailChallenge, lastErrorCode, lastErrorMessage);
    }

    private void onMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run(); else mainHandler.post(runnable);
    }

    public static final class AccountSnapshot {
        private final AccountState state;
        private final AccountProfile profile;
        private final List<CharacterProfile> characters;
        private final CharacterProfile selectedCharacter;
        private final EmailOtpChallenge emailChallenge;
        private final String errorCode;
        private final String errorMessage;
        AccountSnapshot(AccountState state, AccountProfile profile, List<CharacterProfile> characters, CharacterProfile selectedCharacter,
                        EmailOtpChallenge emailChallenge, String errorCode, String errorMessage) {
            this.state = state; this.profile = profile; this.characters = characters; this.selectedCharacter = selectedCharacter;
            this.emailChallenge = emailChallenge; this.errorCode = errorCode; this.errorMessage = errorMessage;
        }
        public AccountState getState() { return state; }
        public AccountProfile getProfile() { return profile; }
        public List<CharacterProfile> getCharacters() { return characters; }
        public CharacterProfile getSelectedCharacter() { return selectedCharacter; }
        public EmailOtpChallenge getEmailChallenge() { return emailChallenge; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isReadyToPlay() { return state == AccountState.READY_TO_PLAY && selectedCharacter != null; }
    }
}
