package com.blackrussia.game.vostok.ui;

/**
 * Central migration switches for the VOSTOK client UI.
 *
 * Candidate flags may be enabled for source/device testing before promotion.
 * MASTER promotion still requires the candidate gate; do not treat a true flag
 * here as proof of runtime acceptance.
 */
public final class VostokUiFeatureFlags {
    public static final boolean UI_CORE_ENABLED = true;
    public static final boolean VOSTOK_INTERACTION_ENABLED = true;

    // 031D device-test candidate surfaces.
    public static final boolean VOSTOK_HUD_ENABLED = true;
    public static final boolean VOSTOK_SPEEDOMETER_ENABLED = true;

    // Later candidates.
    public static final boolean VOSTOK_RADIAL_ENABLED = false;
    public static final boolean VOSTOK_INVENTORY_ENABLED = false;
    public static final boolean VOSTOK_PHONE_ENABLED = false;
    public static final boolean VOSTOK_TABLET_ENABLED = false;
    public static final boolean VOSTOK_CHARACTER_CREATOR_ENABLED = false;
    public static final boolean VOSTOK_SPAWN_SELECTOR_ENABLED = false;

    private VostokUiFeatureFlags() {
    }
}
