package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.os.Looper;

import com.blackrussia.game.gui.HudManager;
import com.blackrussia.game.gui.Notification;
import com.blackrussia.game.vostok.ui.hud.VostokHudController;

import java.util.List;

/**
 * Single Java-side UI bridge for VOSTOK gameplay overlays.
 *
 * Native/JNI-facing methods remain on NvEventQueueActivity for binary compatibility.
 * 031D keeps donor chat/radar/native controls alive while routing player telemetry
 * to the new VOSTOK HUD surface.
 */
public final class VostokUiBridge {
    private final Activity activity;
    private final VostokUiManager uiManager;
    private final HudManager hudManager;
    private final VostokHudController vostokHud;
    private final Notification notificationManager;
    private final InteractionUiManager interactionManager;
    private final InteractionCore interactionCore;
    private final Runnable legacyNpcInteractionAction;
    private volatile boolean destroyed;

    public VostokUiBridge(Activity activity, Runnable interactionAction) {
        this.activity = activity;
        this.legacyNpcInteractionAction = interactionAction;
        uiManager = new VostokUiManager(activity);

        hudManager = new HudManager(activity);
        vostokHud = VostokHudController.getOrCreate(activity);
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
                    if (target.type == InteractionContract.TARGET_NPC
                            && action.id == InteractionContract.ACTION_INTERACT
                            && legacyNpcInteractionAction != null) {
                        legacyNpcInteractionAction.run();
                    }
                },
                null
        );
    }

    public VostokUiManager getUiManager() {
        return uiManager;
    }

    public boolean onBackPressed() {
        return !destroyed && uiManager.handleBack();
    }

    public boolean shouldBlockGameInput() {
        return !destroyed && uiManager.shouldBlockGameInput();
    }

    public void showUiScreen(int wireId) {
        runOnUiThread(() -> uiManager.openScreen(wireId));
    }

    public void hideUiScreen(int wireId) {
        runOnUiThread(() -> uiManager.closeScreen(wireId));
    }

    public void closeAllTransientUi() {
        runOnUiThread(uiManager::closeAllTransient);
    }

    public void updateHudInfo(int health, int armour, int hunger, int weaponId, int ammo,
                              int playerId, int money, int wanted) {
        runOnUiThread(() -> {
            // Keep donor logic alive for radar/chat/weapon/wanted/native compatibility.
            hudManager.UpdateHudInfo(
                    health, armour, hunger, weaponId, ammo, playerId, money, wanted
            );
            // 031D consumes the same authoritative values, including hunger/satiety.
            vostokHud.updatePlayerState(health, armour, hunger, money);
            vostokHud.hideLegacyHudElements();
            interactionManager.setWeaponActive(usesCombatControls(weaponId));
        });
    }

    public void showHud() {
        runOnUiThread(() -> {
            hudManager.ShowHud();
            vostokHud.showHud();
        });
    }

    public void hideHud() {
        runOnUiThread(() -> {
            hudManager.HideHud();
            vostokHud.hideHud();
        });
    }

    public void showQuest(String title, String objective, int current, int total) {
        runOnUiThread(() -> vostokHud.showQuest(title, objective, current, total));
    }

    public void hideQuest() {
        runOnUiThread(vostokHud::hideQuest);
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

    public void showInteraction(float distanceMeters) {
        showNpcInteraction(distanceMeters);
    }

    public void showNpcInteraction(float distanceMeters) {
        runOnUiThread(() -> interactionCore.presentNpc(distanceMeters));
    }

    public void hideInteraction() {
        runOnUiThread(interactionCore::hide);
    }

    public void presentInteractionTargets(List<InteractionCore.Target> targets) {
        runOnUiThread(() -> interactionCore.present(targets));
    }

    public void setInteractionTargetTypeEnabled(int targetType, boolean enabled) {
        runOnUiThread(() -> interactionCore.setTargetTypeEnabled(targetType, enabled));
    }

    public void setInteractionContext(int flags, int reservedActionSlots) {
        runOnUiThread(() -> interactionCore.setHudContext(flags, reservedActionSlots));
    }

    public void shutdown() {
        if (destroyed) return;
        destroyed = true;
        Runnable cleanup = () -> {
            notificationManager.Shutdown();
            interactionCore.shutdown();
            interactionManager.shutdown();
            vostokHud.shutdown();
            uiManager.shutdown();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run();
        else activity.runOnUiThread(cleanup);
    }

    private void onInteractionPressed() {
        if (!destroyed) interactionCore.onPrimaryPressed();
    }

    private void runOnUiThread(Runnable action) {
        if (destroyed) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            activity.runOnUiThread(() -> {
                if (!destroyed) action.run();
            });
        }
    }

    private static boolean usesCombatControls(int weaponId) {
        return (weaponId >= 16 && weaponId <= 18)
                || (weaponId >= 22 && weaponId <= 39);
    }
}
