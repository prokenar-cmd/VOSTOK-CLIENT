package com.blackrussia.launcher.account;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountApi {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static final class CharacterInfo {
        public final long id;
        public final int slot;
        public final String name;
        public final int gender;
        public final int skin;
        public final int level;
        public final int exp;
        public final int money;

        private CharacterInfo(JSONObject json) {
            id = json.optLong("id", 0L);
            slot = json.optInt("slot", 0);
            name = json.optString("name", "");
            gender = json.optInt("gender", 0);
            skin = json.optInt("skin", 0);
            level = json.optInt("level", 1);
            exp = json.optInt("exp", 0);
            money = json.optInt("money", 0);
        }
    }

    public static final class AuthResult {
        public final String sessionToken;
        public final long accountId;
        public final List<CharacterInfo> characters;

        private AuthResult(JSONObject json, String existingToken) {
            sessionToken = json.optString("session_token", existingToken == null ? "" : existingToken);
            JSONObject account = json.optJSONObject("account");
            accountId = account == null ? 0L : account.optLong("id", 0L);
            characters = parseCharacters(json.optJSONArray("characters"));
        }

        public CharacterInfo getPrimaryCharacter() {
            if (characters.isEmpty()) return null;
            for (CharacterInfo character : characters) {
                if (character.slot == 0) return character;
            }
            return characters.get(0);
        }
    }

    public static final class CodeRequest {
        public final String delivery;
        public final String devCode;
        public final int expiresIn;

        private CodeRequest(JSONObject json) {
            delivery = json.optString("delivery", "");
            devCode = json.optString("dev_code", "");
            expiresIn = json.optInt("expires_in", 0);
        }
    }

    public static final class GameTicket {
        public final String connectName;
        public final int expiresIn;
        public final CharacterInfo character;

        private GameTicket(JSONObject json) {
            connectName = json.optString("connect_name", "");
            expiresIn = json.optInt("expires_in", 0);
            JSONObject characterJson = json.optJSONObject("character");
            character = characterJson == null ? null : new CharacterInfo(characterJson);
        }
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AccountApi(Context context) {
        baseUrl = AccountConfig.getBaseUrl(context.getApplicationContext());
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void devLogin(Callback<AuthResult> callback) {
        request("POST", "/v1/auth/dev", null, new JSONObject(), new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new AuthResult(value, null)); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void restoreSession(String token, Callback<AuthResult> callback) {
        request("GET", "/v1/session", token, null, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new AuthResult(value, token)); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void requestEmailCode(String email, Callback<CodeRequest> callback) {
        JSONObject body = new JSONObject();
        try { body.put("email", email); } catch (Exception ignored) { }
        request("POST", "/v1/auth/email/request-code", null, body, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new CodeRequest(value)); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void verifyEmailCode(String email, String code, Callback<AuthResult> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("code", code);
        } catch (Exception ignored) { }
        request("POST", "/v1/auth/email/verify", null, body, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new AuthResult(value, null)); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void createCharacter(String token, String name, Callback<CharacterInfo> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("gender", 0);
            body.put("skin", 0);
        } catch (Exception ignored) { }
        request("POST", "/v1/characters", token, body, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) {
                JSONObject character = value.optJSONObject("character");
                if (character == null) callback.onError("Backend не вернул персонажа");
                else callback.onSuccess(new CharacterInfo(character));
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void issueGameTicket(String token, long characterId, Callback<GameTicket> callback) {
        JSONObject body = new JSONObject();
        try { body.put("character_id", characterId); } catch (Exception ignored) { }
        request("POST", "/v1/game-ticket", token, body, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new GameTicket(value)); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void logout(String token, Callback<Boolean> callback) {
        request("POST", "/v1/logout", token, new JSONObject(), new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject value) { callback.onSuccess(Boolean.TRUE); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    private void request(String method, String path, String token, JSONObject body, Callback<JSONObject> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (body != null && !"GET".equals(method)) {
                    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setFixedLengthStreamingMode(data.length);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(data);
                    }
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                String text = readAll(stream);
                JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                if (status < 200 || status >= 300 || !json.optBoolean("ok", false)) {
                    String message = json.optString("message", "Account Backend: HTTP " + status);
                    postError(callback, message);
                    return;
                }
                postSuccess(callback, json);
            } catch (Exception e) {
                postError(callback, "Account Backend недоступен: " + e.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private <T> void postSuccess(Callback<T> callback, T value) {
        mainHandler.post(() -> callback.onSuccess(value));
    }

    private <T> void postError(Callback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private static List<CharacterInfo> parseCharacters(JSONArray array) {
        List<CharacterInfo> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.optJSONObject(i);
            if (value != null) result.add(new CharacterInfo(value));
        }
        return result;
    }
}
