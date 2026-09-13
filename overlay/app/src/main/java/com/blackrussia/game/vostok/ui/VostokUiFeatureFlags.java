package com.blackrussia.game.vostok.ui;

/**
 * Central migration switches for the VOSTOK client UI.
 *
 * 031B deliberately keeps donor/legacy surfaces alive until their VOSTOK
 * replacements pass a device smoke test. Do not flip these flags casually:
 * each replacement has its own promotion gate.
 */
public final class VostokUiFeatureFlags {
    public static final boolean UI_CORE_ENABLED = true;

    // Confirmed working VOSTOK surface.
    public static final boolean VOSTOK_INTERACTION_ENABLED = true;

    // Deferred visual replacements. Legacy implementations remain active.
    public static final boolean VOSTOK_HUD_ENABLED = false;
    public static final boolean VOSTOK_SPEEDOMETER_ENABLED = false;
    public static final boolean VOSTOK_RADIAL_ENABLED = false;
    public static final boolean VOSTOK_INVENTORY_ENABLED = false;
    public static final boolean VOSTOK_PHONE_ENABLED = false;
    public static final boolean VOSTOK_TABLET_ENABLED = false;
    public static final boolean VOSTOK_CHARACTER_CREATOR_ENABLED = false;
    public static final boolean VOSTOK_SPAWN_SELECTOR_ENABLED = false;

    private VostokUiFeatureFlags() {
    }
}
