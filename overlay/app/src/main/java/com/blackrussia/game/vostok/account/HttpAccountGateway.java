package com.blackrussia.game.vostok.account;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.blackrussia.game.vostok.account.AccountModels.AccountProfile;
import static com.blackrussia.game.vostok.account.AccountModels.AccountSession;
import static com.blackrussia.game.vostok.account.AccountModels.CharacterProfile;
import static com.blackrussia.game.vostok.account.AccountModels.EmailOtpChallenge;
import static com.blackrussia.game.vostok.account.AccountModels.GameTicket;

public final class HttpAccountGateway implements AccountGateway {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public HttpAccountGateway(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new IllegalArgumentException("baseUrl is required");
        String normalized = baseUrl.trim();
        this.baseUrl = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    @Override
    public void requestEmailCode(final String email, final Callback<EmailOtpChallenge> callback) {
        execute("POST", "/v1/auth/email/request-code", null, json("email", email), new JsonCallback() {
            @Override public void onSuccess(JSONObject json) throws JSONException {
                callback.onSuccess(new EmailOtpChallenge(json.getString("challenge_id"), email,
                        json.optLong("expires_at", 0L), json.optInt("resend_after_seconds", 0)));
            }
            @Override public void onError(String code, String message) { callback.onError(code, message); }
        });
    }

    @Override
    public void verifyEmailCode(String challengeId, String code, final Callback<AccountSession> callback) {
        JSONObject body = new JSONObject();
        try { body.put("challenge_id", challengeId); body.put("code", code); }
        catch (JSONException e) { callback.onError("client_json", e.getMessage()); return; }
        execute("POST", "/v1/auth/email/verify-code", null, body, sessionCallback(callback));
    }

    @Override
    public void exchangeProviderCredential(AuthProvider provider, String providerCredential, final Callback<AccountSession> callback) {
        JSONObject body = new JSONObject();
        try { body.put("provider", provider.getWireName()); body.put("credential", providerCredential); }
        catch (JSONException e) { callback.onError("client_json", e.getMessage()); return; }
        execute("POST", "/v1/auth/provider/exchange", null, body, sessionCallback(callback));
    }

    @Override
    public void refreshSession(String refreshToken, final Callback<AccountSession> callback) {
        execute("POST", "/v1/auth/session/refresh", null, json("refresh_token", refreshToken), sessionCallback(callback));
    }

    @Override
    public void loadAccount(String accessToken, final Callback<AccountProfile> callback) {
        execute("GET", "/v1/account/me", accessToken, null, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) throws JSONException {
                callback.onSuccess(new AccountProfile(json.getString("account_id"),
                        json.optString("display_name", ""), json.optString("email", "")));
            }
            @Override public void onError(String code, String message) { callback.onError(code, message); }
        });
    }

    @Override
    public void loadCharacters(String accessToken, final Callback<List<CharacterProfile>> callback) {
        execute("GET", "/v1/characters", accessToken, null, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) throws JSONException {
                JSONArray array = json.optJSONArray("characters");
                List<CharacterProfile> result = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.getJSONObject(i);
                        result.add(new CharacterProfile(item.getLong("character_id"), item.optString("name", ""),
                                item.optInt("level", 1), item.optInt("skin_id", 0), item.optString("server_id", "main")));
                    }
                }
                callback.onSuccess(result);
            }
            @Override public void onError(String code, String message) { callback.onError(code, message); }
        });
    }

    @Override
    public void issueGameTicket(String accessToken, long characterId, final Callback<GameTicket> callback) {
        JSONObject body = new JSONObject();
        try { body.put("character_id", characterId); }
        catch (JSONException e) { callback.onError("client_json", e.getMessage()); return; }
        execute("POST", "/v1/game-ticket", accessToken, body, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) throws JSONException {
                callback.onSuccess(new GameTicket(json.getString("ticket"), json.getLong("character_id"),
                        json.getString("server_host"), json.getInt("server_port"), json.optLong("expires_at", 0L)));
            }
            @Override public void onError(String code, String message) { callback.onError(code, message); }
        });
    }

    private JsonCallback sessionCallback(final Callback<AccountSession> callback) {
        return new JsonCallback() {
            @Override public void onSuccess(JSONObject json) throws JSONException {
                callback.onSuccess(new AccountSession(json.getString("account_id"),
                        AuthProvider.fromWireName(json.getString("provider")), json.getString("access_token"),
                        json.getString("refresh_token"), json.optLong("access_expires_at", 0L)));
            }
            @Override public void onError(String code, String message) { callback.onError(code, message); }
        };
    }

    private void execute(final String method, final String path, final String bearerToken,
                         final JSONObject body, final JsonCallback callback) {
        executor.execute(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.setRequestMethod(method);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    if (bearerToken != null && !bearerToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
                    if (body != null) {
                        connection.setDoOutput(true);
                        byte[] payload = body.toString().getBytes(UTF8);
                        connection.setFixedLengthStreamingMode(payload.length);
                        OutputStream output = connection.getOutputStream();
                        output.write(payload);
                        output.close();
                    }
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                    String response = readAll(stream);
                    JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
                    if (status >= 200 && status < 300) callback.onSuccess(json);
                    else callback.onError(json.optString("code", "http_" + status), json.optString("message", "VOSTOK account request failed"));
                } catch (Exception e) {
                    callback.onError("network_error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static JSONObject json(String key, String value) {
        JSONObject body = new JSONObject();
        try { body.put(key, value); } catch (JSONException ignored) {}
        return body;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();
        return result.toString();
    }

    private interface JsonCallback {
        void onSuccess(JSONObject json) throws JSONException;
        void onError(String code, String message);
    }
}
