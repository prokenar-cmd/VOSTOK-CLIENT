package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.os.Looper;

import com.blackrussia.game.gui.HudManager;
import com.blackrussia.game.gui.Notification;

/**
 * Single Java-side UI bridge for VOSTOK gameplay overlays.
 *
 * Native/JNI-facing methods remain on NvEventQueueActivity for binary compatibility;
 * they delegate here. Future Interaction, objective, courier and other gameplay UI
 * modules should be owned/routed through this bridge instead of creating parallel JNI paths.
 */
public final class VostokUiBridge {
    private final Activity activity;
    private final HudManager hudManager;
    private final Notification notificationManager;
    private volatile boolean destroyed;

    public VostokUiBridge(Activity activity) {
        this.activity = activity;
        hudManager = new HudManager(activity);
        notificationManager = new Notification(activity);
    }

    public void updateHudInfo(int health, int armour, int hunger, int weaponId, int ammo,
                              int playerId, int money, int wanted) {
        runOnUiThread(() -> hudManager.UpdateHudInfo(
                health, armour, hunger, weaponId, ammo, playerId, money, wanted
        ));
    }

    public void showHud() {
        runOnUiThread(hudManager::ShowHud);
    }

    public void hideHud() {
        runOnUiThread(hudManager::HideHud);
    }

    public void showNotification(int type, String text, int duration,
                                 String actionForButton, String buttonText) {
        runOnUiThread(() -> notificationManager.ShowNotification(
                type, text, duration, actionForButton, buttonText
        ));
    }

    public void showInfo(String text, int duration) {
        runOnUiThread(() -> notificationManager.ShowInfo(text, duration));
    }

    public void showSuccess(String text, int duration) {
        runOnUiThread(() -> notificationManager.ShowSuccess(text, duration));
    }

    public void showWarning(String text, int duration) {
        runOnUiThread(() -> notificationManager.ShowWarning(text, duration));
    }

    public void showError(String text, int duration) {
        runOnUiThread(() -> notificationManager.ShowError(text, duration));
    }

    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        Runnable cleanup = notificationManager::Shutdown;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
        } else {
            activity.runOnUiThread(cleanup);
        }
    }

    private void runOnUiThread(Runnable action) {
        if (destroyed) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            activity.runOnUiThread(() -> {
                if (!destroyed) {
                    action.run();
                }
            });
        }
    }
}
