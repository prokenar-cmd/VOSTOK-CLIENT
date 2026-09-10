package com.blackrussia.game.vostok.account;

import android.content.Context;

public final class AccountCoreBootstrap {
    private static volatile AccountCore instance;

    private AccountCoreBootstrap() {}

    public static AccountCore initialize(Context context) {
        if (instance == null) {
            synchronized (AccountCoreBootstrap.class) {
                if (instance == null) {
                    instance = new AccountCore(new SecureSessionStore(context.getApplicationContext()));
                }
            }
        }
        return instance;
    }

    public static AccountCore get() {
        if (instance == null) throw new IllegalStateException("VOSTOK Account Core is not initialized");
        return instance;
    }

    public static AccountCore configureHttpBackend(Context context, String baseUrl) {
        AccountCore core = initialize(context);
        core.setGateway(new HttpAccountGateway(baseUrl));
        return core;
    }
}
