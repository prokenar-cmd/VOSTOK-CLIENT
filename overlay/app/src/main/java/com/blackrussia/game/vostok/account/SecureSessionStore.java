package com.blackrussia.game.vostok.account;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.Charset;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static com.blackrussia.game.vostok.account.AccountModels.AccountSession;

public final class SecureSessionStore {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String PREFS = "vostok_account_core";
    private static final String KEY_ALIAS = "vostok_account_refresh_v1";
    private static final String K_ACCOUNT_ID = "account_id";
    private static final String K_PROVIDER = "provider";
    private static final String K_REFRESH_CIPHER = "refresh_cipher";
    private static final String K_REFRESH_IV = "refresh_iv";
    private static final String K_SELECTED_CHARACTER = "selected_character";

    private final SharedPreferences preferences;

    public SecureSessionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(AccountSession session) {
        if (session == null) {
            clearSession();
            return;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(K_ACCOUNT_ID, session.getAccountId())
                .putString(K_PROVIDER, session.getProvider() == null ? null : session.getProvider().getWireName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && session.getRefreshToken() != null) {
            try {
                EncryptedValue encrypted = encrypt(session.getRefreshToken());
                editor.putString(K_REFRESH_CIPHER, encrypted.cipherText);
                editor.putString(K_REFRESH_IV, encrypted.iv);
            } catch (Exception ignored) {
                editor.remove(K_REFRESH_CIPHER).remove(K_REFRESH_IV);
            }
        } else {
            editor.remove(K_REFRESH_CIPHER).remove(K_REFRESH_IV);
        }
        editor.apply();
    }

    public RestoredSession restoreSession() {
        String accountId = preferences.getString(K_ACCOUNT_ID, null);
        AuthProvider provider = AuthProvider.fromWireName(preferences.getString(K_PROVIDER, null));
        if (accountId == null || provider == null) return null;

        String refreshToken = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String cipherText = preferences.getString(K_REFRESH_CIPHER, null);
            String iv = preferences.getString(K_REFRESH_IV, null);
            if (cipherText != null && iv != null) {
                try {
                    refreshToken = decrypt(cipherText, iv);
                } catch (Exception ignored) {
                    clearSession();
                    return null;
                }
            }
        }
        return new RestoredSession(accountId, provider, refreshToken, getSelectedCharacterId());
    }

    public void setSelectedCharacterId(long characterId) {
        preferences.edit().putLong(K_SELECTED_CHARACTER, characterId).apply();
    }

    public long getSelectedCharacterId() {
        return preferences.getLong(K_SELECTED_CHARACTER, -1L);
    }

    public void clearSelectedCharacter() {
        preferences.edit().remove(K_SELECTED_CHARACTER).apply();
    }

    public void clearSession() {
        preferences.edit().remove(K_ACCOUNT_ID).remove(K_PROVIDER).remove(K_REFRESH_CIPHER)
                .remove(K_REFRESH_IV).remove(K_SELECTED_CHARACTER).apply();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private EncryptedValue encrypt(String clearText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(clearText.getBytes(UTF8));
        return new EncryptedValue(Base64.encodeToString(encrypted, Base64.NO_WRAP),
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String decrypt(String cipherText, String iv) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key == null) throw new IllegalStateException("VOSTOK account key is missing");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
        return new String(plain, UTF8);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return keyGenerator.generateKey();
    }

    private static final class EncryptedValue {
        private final String cipherText;
        private final String iv;
        private EncryptedValue(String cipherText, String iv) { this.cipherText = cipherText; this.iv = iv; }
    }

    public static final class RestoredSession {
        private final String accountId;
        private final AuthProvider provider;
        private final String refreshToken;
        private final long selectedCharacterId;
        RestoredSession(String accountId, AuthProvider provider, String refreshToken, long selectedCharacterId) {
            this.accountId = accountId; this.provider = provider; this.refreshToken = refreshToken; this.selectedCharacterId = selectedCharacterId;
        }
        public String getAccountId() { return accountId; }
        public AuthProvider getProvider() { return provider; }
        public String getRefreshToken() { return refreshToken; }
        public long getSelectedCharacterId() { return selectedCharacterId; }
        public boolean canRestoreSilently() { return refreshToken != null && !refreshToken.isEmpty(); }
    }
}
