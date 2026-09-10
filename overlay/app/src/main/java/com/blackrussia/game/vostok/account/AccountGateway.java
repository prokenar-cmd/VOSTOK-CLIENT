package com.blackrussia.game.vostok.account;

import java.util.List;

import static com.blackrussia.game.vostok.account.AccountModels.AccountProfile;
import static com.blackrussia.game.vostok.account.AccountModels.AccountSession;
import static com.blackrussia.game.vostok.account.AccountModels.CharacterProfile;
import static com.blackrussia.game.vostok.account.AccountModels.EmailOtpChallenge;
import static com.blackrussia.game.vostok.account.AccountModels.GameTicket;

public interface AccountGateway {
    interface Callback<T> {
        void onSuccess(T value);
        void onError(String code, String message);
    }

    void requestEmailCode(String email, Callback<EmailOtpChallenge> callback);
    void verifyEmailCode(String challengeId, String code, Callback<AccountSession> callback);
    void exchangeProviderCredential(AuthProvider provider, String providerCredential, Callback<AccountSession> callback);
    void refreshSession(String refreshToken, Callback<AccountSession> callback);
    void loadAccount(String accessToken, Callback<AccountProfile> callback);
    void loadCharacters(String accessToken, Callback<List<CharacterProfile>> callback);
    void issueGameTicket(String accessToken, long characterId, Callback<GameTicket> callback);
}
