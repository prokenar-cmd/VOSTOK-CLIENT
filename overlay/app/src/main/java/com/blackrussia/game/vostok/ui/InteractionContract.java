package com.blackrussia.game.vostok.ui;

/**
 * Stable integer contract shared by the VOSTOK interaction layers.
 *
 * Values are intentionally primitive/JNI-friendly. Gameplay systems may be added later
 * without changing InteractionUiManager or inventing another interaction path.
 */
public final class InteractionContract {
    private InteractionContract() {
    }

    // Target kinds. Only TARGET_NPC is enabled by the runtime policy today.
    public static final int TARGET_NONE = 0;
    public static final int TARGET_NPC = 1;
    public static final int TARGET_RESOURCE = 2;
    public static final int TARGET_VEHICLE = 3;
    public static final int TARGET_PLAYER = 4;
    public static final int TARGET_OBJECT = 5;
    public static final int TARGET_PICKUP = 6;
    public static final int TARGET_DOOR = 7;
    public static final int TARGET_CHECKPOINT = 8;
    public static final int TARGET_CUSTOM = 100;

    // Generic actions. Server/native code remains authoritative for availability.
    public static final int ACTION_NONE = 0;
    public static final int ACTION_INTERACT = 1;
    public static final int ACTION_TALK = 2;
    public static final int ACTION_COLLECT = 3;
    public static final int ACTION_USE = 4;
    public static final int ACTION_OPEN = 5;
    public static final int ACTION_ENTER = 6;
    public static final int ACTION_CUSTOM = 100;

    // How a press should resolve after candidates/actions are known.
    public static final int FLOW_DIRECT = 0;
    public static final int FLOW_TARGET_RADIAL = 1;
    public static final int FLOW_ACTION_RADIAL = 2;
    public static final int FLOW_TARGET_THEN_ACTION = 3;

    // Radial menu purpose. Kept primitive so JNI/native code can pass it later.
    public static final int RADIAL_TARGETS = 1;
    public static final int RADIAL_ACTIONS = 2;
    public static final int RADIAL_CONTEXT = 3;

    /**
     * Default presentation rule once authoritative candidates/actions are already known.
     */
    public static int resolveFlow(int targetCount, int actionCount) {
        int safeTargets = Math.max(0, targetCount);
        int safeActions = Math.max(0, actionCount);

        if (safeTargets > 1 && safeActions > 1) {
            return FLOW_TARGET_THEN_ACTION;
        }
        if (safeTargets > 1) {
            return FLOW_TARGET_RADIAL;
        }
        if (safeActions > 1) {
            return FLOW_ACTION_RADIAL;
        }
        return FLOW_DIRECT;
    }

    public static String defaultLabelForTarget(int targetType) {
        if (targetType == TARGET_RESOURCE) {
            return "Собрать";
        }
        return "Взаимодействие";
    }

    public static boolean isKnownTargetType(int targetType) {
        return targetType == TARGET_NPC
                || targetType == TARGET_RESOURCE
                || targetType == TARGET_VEHICLE
                || targetType == TARGET_PLAYER
                || targetType == TARGET_OBJECT
                || targetType == TARGET_PICKUP
                || targetType == TARGET_DOOR
                || targetType == TARGET_CHECKPOINT
                || targetType == TARGET_CUSTOM;
    }
}