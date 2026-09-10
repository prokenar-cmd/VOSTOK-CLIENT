package com.blackrussia.game.vostok;

import android.app.Application;

import com.blackrussia.game.vostok.account.AccountCoreBootstrap;

public final class VostokApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AccountCoreBootstrap.initialize(this);
    }
}
