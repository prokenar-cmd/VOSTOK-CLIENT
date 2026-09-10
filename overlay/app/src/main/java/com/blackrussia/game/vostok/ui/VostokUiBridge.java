package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.os.Looper;

import com.blackrussia.game.gui.HudManager;
import com.blackrussia.game.gui.Notification;

import java.util.List;

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
    private final InteractionCore interactionCore;
    private final Runnable legacyNpcInteractionAction;
    private volatile boolean destroyed;

    public VostokUiBridge(Activity activity, Runnable interactionAction) {
        this.activity = activity;
        this.legacyNpcInteractionAction = interactionAction;
        hudManager = new HudManager(activity);
        notificationManager = new Notification(activity);
        interactionManager = new InteractionUiManager(activity, this::onInteractionPressed);

        interactionCore = new InteractionCore(
                new InteractionCore.UiPort() {
                    @Override
                    public void show(String label, float distanceMeters) {
                        interactionManager.show(label, distanceMeters);
                    }

                    @Override
                    public void hide() {
                        interactionManager.hide();
                    }

                    @Override
                    public void setHudContext(int flags, int reservedActionSlots) {
                        interactionManager.setContext(flags, reservedActionSlots);
                    }
                },
                (target, action) -> {
                    // Runtime policy currently enables NPC only. Keep the legacy command strictly
                    // scoped to NPC interaction so future resource/vehicle/player actions cannot
                    // accidentally reuse the wrong transport.
                    if (target.type == InteractionContract.TARGET_NPC
                            && action.id == InteractionContract.ACTION_INTERACT
                            && legacyNpcInteractionAction != null) {
                        legacyNpcInteractionAction.run();
                    }
                },
                null // Visual radial menu will plug in here later without replacing InteractionCore.
        );
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

    /**
     * Current native compatibility entry point. It is intentionally NPC-only for this milestone.
     */
    public void showInteraction(float distanceMeters) {
        showNpcInteraction(distanceMeters);
    }

    public void showNpcInteraction(float distanceMeters) {
        runOnUiThread(() -> interactionCore.presentNpc(distanceMeters));
    }

    public void hideInteraction() {
        runOnUiThread(interactionCore::hide);
    }

    /**
     * Future Java/native adapter point for authoritative candidate sets. Target kinds other than
     * NPC remain filtered until their owning gameplay system explicitly enables them.
     */
    public void presentInteractionTargets(List<InteractionCore.Target> targets) {
        runOnUiThread(() -> interactionCore.present(targets));
    }

    public void setInteractionTargetTypeEnabled(int targetType, boolean enabled) {
        runOnUiThread(() -> interactionCore.setTargetTypeEnabled(targetType, enabled));
    }

    /**
     * Allows combat/special UI to reserve action-button slots without hard-coding coordinates.
     * In-vehicle engine/driving controls remain owned by the speedometer UI, not InteractionCore.
     */
    public void setInteractionContext(int flags, int reservedActionSlots) {
        runOnUiThread(() -> interactionCore.setHudContext(flags, reservedActionSlots));
    }

    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        Runnable cleanup = () -> {
            notificationManager.Shutdown();
            interactionCore.shutdown();
            interactionManager.shutdown();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
        } else {
            activity.runOnUiThread(cleanup);
        }
    }

    private void onInteractionPressed() {
        if (!destroyed) {
            interactionCore.onPrimaryPressed();
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
