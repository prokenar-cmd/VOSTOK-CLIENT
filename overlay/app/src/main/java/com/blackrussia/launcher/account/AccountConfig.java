package com.blackrussia.launcher.account;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AccountConfig {
    private static final String DEFAULT_BASE_URL = "http://192.168.0.22:8787";

    private AccountConfig() {
    }

    public static String getBaseUrl(Context context) {
        try (InputStream in = context.getAssets().open("vostok_account_config.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            JSONObject json = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            String value = json.optString("baseUrl", DEFAULT_BASE_URL).trim();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return value.isEmpty() ? DEFAULT_BASE_URL : value;
        } catch (Exception ignored) {
            return DEFAULT_BASE_URL;
        }
    }
}
