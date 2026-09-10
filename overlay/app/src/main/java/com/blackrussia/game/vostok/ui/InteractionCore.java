package com.blackrussia.game.vostok.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VOSTOK world interaction router.
 *
 * This class deliberately contains no Android view code and no gameplay authority. Native/server
 * code supplies valid targets/actions; this core decides only direct-vs-radial presentation.
 * Runtime policy currently enables NPC targets only.
 */
public final class InteractionCore {
    public interface UiPort {
        void show(String label, float distanceMeters);

        void hide();

        void setHudContext(int flags, int reservedActionSlots);
    }

    public interface DirectActionPort {
        void execute(Target target, Action action);
    }

    public static final class Action {
        public final int id;
        public final String title;
        public final String subtitle;
        public final boolean enabled;

        public Action(int id, String title) {
            this(id, title, "", true);
        }

        public Action(int id, String title, String subtitle, boolean enabled) {
            this.id = id;
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.enabled = enabled;
        }
    }

    public static final class Target {
        public final int type;
        public final int id;
        public final String title;
        public final String subtitle;
        public final float distanceMeters;
        public final List<Action> actions;

        public Target(int type,
                      int id,
                      String title,
                      String subtitle,
                      float distanceMeters,
                      List<Action> actions) {
            this.type = type;
            this.id = id;
            this.title = safe(title);
            this.subtitle = safe(subtitle);
            this.distanceMeters = distanceMeters;
            this.actions = immutableActions(actions);
        }
    }

    private final UiPort uiPort;
    private final DirectActionPort directActionPort;
    private final RadialMenuContract.Host radialHost;
    private final Set<Integer> enabledTargetTypes = new HashSet<>();

    private List<Target> currentTargets = Collections.emptyList();
    private boolean destroyed;

    public InteractionCore(UiPort uiPort,
                           DirectActionPort directActionPort,
                           RadialMenuContract.Host radialHost) {
        this.uiPort = uiPort;
        this.directActionPort = directActionPort;
        this.radialHost = radialHost;

        // Current milestone rule: NPC interaction only.
        enabledTargetTypes.add(InteractionContract.TARGET_NPC);
    }

    /**
     * Compatibility entry point for the current native bridge. The server still resolves the
     * concrete NPC when the legacy /interact command is sent.
     */
    public void presentNpc(float distanceMeters) {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action(InteractionContract.ACTION_INTERACT, "Взаимодействие"));

        List<Target> targets = new ArrayList<>();
        targets.add(new Target(
                InteractionContract.TARGET_NPC,
                -1,
                "NPC",
                "",
                distanceMeters,
                actions
        ));
        present(targets);
    }

    /**
     * Future generic entry point for native/RPC-fed candidate sets.
     * Disabled target kinds are filtered here instead of being guessed by the UI.
     */
    public void present(List<Target> targets) {
        if (destroyed) {
            return;
        }

        List<Target> filtered = filterEnabledTargets(targets);
        currentTargets = filtered;

        if (filtered.isEmpty()) {
            uiPort.hide();
            if (radialHost != null) {
                radialHost.close();
            }
            return;
        }

        Target nearest = nearestTarget(filtered);
        String label = filtered.size() > 1
                ? "Взаимодействие"
                : InteractionContract.defaultLabelForTarget(nearest.type);
        uiPort.show(label, nearest.distanceMeters);
    }

    public void hide() {
        if (destroyed) {
            return;
        }
        currentTargets = Collections.emptyList();
        uiPort.hide();
        if (radialHost != null) {
            radialHost.close();
        }
    }

    /** Called by the hand button. */
    public void onPrimaryPressed() {
        if (destroyed || currentTargets.isEmpty()) {
            return;
        }

        if (currentTargets.size() > 1) {
            openTargetRadial(currentTargets);
            return;
        }

        resolveTarget(currentTargets.get(0));
    }

    /**
     * Target types are opt-in. Resource, vehicle, player and object interactions remain disabled
     * until their owning gameplay systems are implemented and explicitly enable them.
     */
    public void setTargetTypeEnabled(int targetType, boolean enabled) {
        if (destroyed || !InteractionContract.isKnownTargetType(targetType)) {
            return;
        }
        if (enabled) {
            enabledTargetTypes.add(targetType);
        } else {
            enabledTargetTypes.remove(targetType);
            currentTargets = filterEnabledTargets(currentTargets);
            if (currentTargets.isEmpty()) {
                uiPort.hide();
            }
        }
    }

    public boolean isTargetTypeEnabled(int targetType) {
        return !destroyed && enabledTargetTypes.contains(targetType);
    }

    public void setHudContext(int flags, int reservedActionSlots) {
        if (!destroyed) {
            uiPort.setHudContext(flags, reservedActionSlots);
        }
    }

    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        currentTargets = Collections.emptyList();
        enabledTargetTypes.clear();
        if (radialHost != null) {
            radialHost.close();
        }
        uiPort.hide();
    }

    private void resolveTarget(Target target) {
        List<Action> enabledActions = enabledActions(target.actions);
        if (enabledActions.isEmpty()) {
            return;
        }

        if (enabledActions.size() > 1) {
            openActionRadial(target, enabledActions);
            return;
        }

        executeDirect(target, enabledActions.get(0));
    }

    private void openTargetRadial(List<Target> targets) {
        if (radialHost == null) {
            return;
        }

        List<RadialMenuContract.Item> items = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            items.add(new RadialMenuContract.Item(
                    index,
                    target.type,
                    target.title.isEmpty() ? InteractionContract.defaultLabelForTarget(target.type) : target.title,
                    target.subtitle,
                    true
            ));
        }

        RadialMenuContract.Request request = new RadialMenuContract.Request(
                InteractionContract.RADIAL_TARGETS,
                InteractionContract.TARGET_NONE,
                -1,
                "Выберите объект",
                items,
                new RadialMenuContract.SelectionSink() {
                    @Override
                    public void onSelected(RadialMenuContract.Request request, RadialMenuContract.Item item) {
                        if (destroyed || item == null || item.id < 0 || item.id >= targets.size()) {
                            return;
                        }
                        resolveTarget(targets.get(item.id));
                    }

                    @Override
                    public void onCancelled(RadialMenuContract.Request request) {
                        // Keep the world interaction button available after closing the radial menu.
                    }
                }
        );
        radialHost.open(request);
    }

    private void openActionRadial(Target target, List<Action> actions) {
        if (radialHost == null) {
            return;
        }

        List<RadialMenuContract.Item> items = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            Action action = actions.get(index);
            items.add(new RadialMenuContract.Item(
                    index,
                    action.id,
                    action.title,
                    action.subtitle,
                    action.enabled
            ));
        }

        RadialMenuContract.Request request = new RadialMenuContract.Request(
                InteractionContract.RADIAL_ACTIONS,
                target.type,
                target.id,
                target.title,
                items,
                new RadialMenuContract.SelectionSink() {
                    @Override
                    public void onSelected(RadialMenuContract.Request request, RadialMenuContract.Item item) {
                        if (destroyed || item == null || item.id < 0 || item.id >= actions.size()) {
                            return;
                        }
                        Action action = actions.get(item.id);
                        if (action.enabled) {
                            executeDirect(target, action);
                        }
                    }

                    @Override
                    public void onCancelled(RadialMenuContract.Request request) {
                        // No gameplay state changes on cancel.
                    }
                }
        );
        radialHost.open(request);
    }

    private void executeDirect(Target target, Action action) {
        if (directActionPort != null && action.enabled) {
            directActionPort.execute(target, action);
        }
    }

    private List<Target> filterEnabledTargets(List<Target> targets) {
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Target> filtered = new ArrayList<>();
        for (Target target : targets) {
            if (target == null || !InteractionContract.isKnownTargetType(target.type)) {
                continue;
            }
            if (enabledTargetTypes.contains(target.type)) {
                filtered.add(target);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    private static Target nearestTarget(List<Target> targets) {
        Target nearest = targets.get(0);
        float nearestDistance = normalizedDistance(nearest.distanceMeters);
        for (int index = 1; index < targets.size(); index++) {
            Target candidate = targets.get(index);
            float candidateDistance = normalizedDistance(candidate.distanceMeters);
            if (candidateDistance < nearestDistance) {
                nearest = candidate;
                nearestDistance = candidateDistance;
            }
        }
        return nearest;
    }

    private static List<Action> enabledActions(List<Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Action> enabled = new ArrayList<>();
        for (Action action : actions) {
            if (action != null && action.enabled) {
                enabled.add(action);
            }
        }
        return enabled;
    }

    private static List<Action> immutableActions(List<Action> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static float normalizedDistance(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0.0f) {
            return Float.MAX_VALUE;
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}