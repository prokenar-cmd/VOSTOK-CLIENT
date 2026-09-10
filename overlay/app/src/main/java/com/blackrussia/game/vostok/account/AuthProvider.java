package com.blackrussia.game.vostok.account;

public enum AuthProvider {
    GOOGLE("google"),
    VK("vk"),
    YANDEX("yandex"),
    EMAIL("email");

    private final String wireName;

    AuthProvider(String wireName) {
        this.wireName = wireName;
    }

    public String getWireName() {
        return wireName;
    }

    public static AuthProvider fromWireName(String value) {
        if (value == null) return null;
        for (AuthProvider provider : values()) {
            if (provider.wireName.equalsIgnoreCase(value)) return provider;
        }
        return null;
    }
}
