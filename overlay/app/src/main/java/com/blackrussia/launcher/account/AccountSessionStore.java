package com.blackrussia.launcher.account;

import android.content.Context;
import android.content.SharedPreferences;

public final class AccountSessionStore {
    private static final String PREFS = "vostok_account_session";
    private static final String TOKEN = "token";
    private static final String ACCOUNT_ID = "account_id";
    private static final String CHARACTER_ID = "character_id";
    private static final String CHARACTER_NAME = "character_name";

    private final SharedPreferences preferences;

    public AccountSessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getToken() {
        return preferences.getString(TOKEN, "");
    }

    public long getAccountId() {
        return preferences.getLong(ACCOUNT_ID, 0L);
    }

    public long getCharacterId() {
        return preferences.getLong(CHARACTER_ID, 0L);
    }

    public String getCharacterName() {
        return preferences.getString(CHARACTER_NAME, "");
    }

    public void save(AccountApi.AuthResult result) {
        AccountApi.CharacterInfo character = result.getPrimaryCharacter();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(TOKEN, result.sessionToken)
                .putLong(ACCOUNT_ID, result.accountId);
        if (character != null) {
            editor.putLong(CHARACTER_ID, character.id)
                    .putString(CHARACTER_NAME, character.name);
        } else {
            editor.remove(CHARACTER_ID).remove(CHARACTER_NAME);
        }
        editor.apply();
    }

    public void saveCharacter(AccountApi.CharacterInfo character) {
        if (character == null) return;
        preferences.edit()
                .putLong(CHARACTER_ID, character.id)
                .putString(CHARACTER_NAME, character.name)
                .apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }
}
