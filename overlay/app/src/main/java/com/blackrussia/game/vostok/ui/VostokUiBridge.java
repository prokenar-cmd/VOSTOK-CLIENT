package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.os.Looper;

import com.blackrussia.game.gui.HudManager;
import com.blackrussia.game.gui.Notification;

/**
 * Single Java-side UI bridge for VOSTOK gameplay overlays.
 *
 * Native/JNI-facing methods remain on NvEventQueueActivity for binary compatibility;
 * gameplay overlays are owned here so HUD, notifications, interaction, objectives and
 * future job UI do not grow separate parallel bridges.
 */
public final class VostokUiBridge {
    private final Activity activity;
    private final HudManager hudManager;
    private final Notification notificationManager;
    private final InteractionUiManager interactionManager;
    private volatile boolean destroyed;

    public VostokUiBridge(Activity activity, Runnable interactionAction) {
        this.activity = activity;
        hudManager = new HudManager(activity);
        notificationManager = new Notification(activity);
        interactionManager = new InteractionUiManager(activity, () -> {
            if (interactionAction != null) {
                interactionAction.run();
            }
        });
    }

    public void updateHudInfo(int health, int armour, int hunger, int weaponId, int ammo,
                              int playerId, int money, int wanted) {
        runOnUiThread(() -> {
            hudManager.UpdateHudInfo(
                    health, armour, hunger, weaponId, ammo, playerId, money, wanted
            );
            interactionManager.setWeaponActive(usesCombatControls(weaponId));
        });
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

    /** Server/native-authoritative visibility entry point. */
    public void showInteraction(float distanceMeters) {
        runOnUiThread(() -> interactionManager.show(distanceMeters));
    }

    public void hideInteraction() {
        runOnUiThread(interactionManager::hide);
    }

    /**
     * Allows combat/vehicle/special systems to reserve action-button slots without
     * changing InteractionUiManager coordinates directly.
     */
    public void setInteractionContext(int flags, int reservedActionSlots) {
        runOnUiThread(() -> interactionManager.setContext(flags, reservedActionSlots));
    }

    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        Runnable cleanup = () -> {
            notificationManager.Shutdown();
            interactionManager.shutdown();
        };
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

    private static boolean usesCombatControls(int weaponId) {
        return (weaponId >= 16 && weaponId <= 18)
                || (weaponId >= 22 && weaponId <= 39);
    }
}
