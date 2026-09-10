package com.blackrussia.game.vostok.account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountModels {
    private AccountModels() {}

    public static final class EmailOtpChallenge {
        private final String challengeId;
        private final String email;
        private final long expiresAtEpochSeconds;
        private final int resendAfterSeconds;
        public EmailOtpChallenge(String challengeId, String email, long expiresAtEpochSeconds, int resendAfterSeconds) {
            this.challengeId = challengeId; this.email = email; this.expiresAtEpochSeconds = expiresAtEpochSeconds; this.resendAfterSeconds = resendAfterSeconds;
        }
        public String getChallengeId() { return challengeId; }
        public String getEmail() { return email; }
        public long getExpiresAtEpochSeconds() { return expiresAtEpochSeconds; }
        public int getResendAfterSeconds() { return resendAfterSeconds; }
    }

    public static final class AccountSession {
        private final String accountId;
        private final AuthProvider provider;
        private final String accessToken;
        private final String refreshToken;
        private final long accessExpiresAtEpochSeconds;
        public AccountSession(String accountId, AuthProvider provider, String accessToken, String refreshToken, long accessExpiresAtEpochSeconds) {
            this.accountId = accountId; this.provider = provider; this.accessToken = accessToken; this.refreshToken = refreshToken; this.accessExpiresAtEpochSeconds = accessExpiresAtEpochSeconds;
        }
        public String getAccountId() { return accountId; }
        public AuthProvider getProvider() { return provider; }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getAccessExpiresAtEpochSeconds() { return accessExpiresAtEpochSeconds; }
    }

    public static final class AccountProfile {
        private final String accountId;
        private final String displayName;
        private final String email;
        public AccountProfile(String accountId, String displayName, String email) {
            this.accountId = accountId; this.displayName = displayName; this.email = email;
        }
        public String getAccountId() { return accountId; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
    }

    public static final class CharacterProfile {
        private final long characterId;
        private final String name;
        private final int level;
        private final int skinId;
        private final String serverId;
        public CharacterProfile(long characterId, String name, int level, int skinId, String serverId) {
            this.characterId = characterId; this.name = name; this.level = level; this.skinId = skinId; this.serverId = serverId;
        }
        public long getCharacterId() { return characterId; }
        public String getName() { return name; }
        public int getLevel() { return level; }
        public int getSkinId() { return skinId; }
        public String getServerId() { return serverId; }
    }

    public static final class GameTicket {
        private final String ticket;
        private final long characterId;
        private final String serverHost;
        private final int serverPort;
        private final long expiresAtEpochSeconds;
        public GameTicket(String ticket, long characterId, String serverHost, int serverPort, long expiresAtEpochSeconds) {
            this.ticket = ticket; this.characterId = characterId; this.serverHost = serverHost; this.serverPort = serverPort; this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }
        public String getTicket() { return ticket; }
        public long getCharacterId() { return characterId; }
        public String getServerHost() { return serverHost; }
        public int getServerPort() { return serverPort; }
        public long getExpiresAtEpochSeconds() { return expiresAtEpochSeconds; }
    }

    static List<CharacterProfile> immutableCharacters(List<CharacterProfile> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(input));
    }
}
