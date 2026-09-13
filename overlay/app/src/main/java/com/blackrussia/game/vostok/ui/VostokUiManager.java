package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Central VOSTOK UI Core.
 *
 * 031B contains lifecycle/state infrastructure only. It intentionally does
 * not draw the future HUD, speedometer, radial menu, inventory, phone,
 * tablet, character creator or spawn selector.
 */
public final class VostokUiManager {
    public enum Screen {
        HUD(1, false, false, false),
        SPEEDOMETER(2, false, false, false),
        INTERACTION(3, false, false, false),
        RADIAL_MENU(10, true, true, true),
        INVENTORY(11, true, true, true),
        PHONE(12, true, true, true),
        TABLET(13, true, true, true),
        CHARACTER_CREATION(20, true, true, true),
        SPAWN_SELECTION(21, true, true, true),
        SETTINGS(30, true, true, true);

        public final int wireId;
        final boolean inBackStack;
        final boolean blocksGameInput;
        final boolean closeOnBack;

        Screen(int wireId, boolean inBackStack, boolean blocksGameInput, boolean closeOnBack) {
            this.wireId = wireId;
            this.inBackStack = inBackStack;
            this.blocksGameInput = blocksGameInput;
            this.closeOnBack = closeOnBack;
        }

        public static Screen fromWireId(int wireId) {
            for (Screen screen : values()) {
                if (screen.wireId == wireId) return screen;
            }
            return null;
        }
    }

    public interface ScreenController {
        void show(FrameLayout host, VostokUiMetrics metrics);
        void hide();
    }

    public interface StateListener {
        void onUiStateChanged(Screen topScreen, boolean gameInputBlocked);
    }

    private final Activity activity;
    private final FrameLayout host;
    private final VostokUiMetrics metrics;
    private final EnumMap<Screen, ScreenController> controllers = new EnumMap<>(Screen.class);
    private final EnumSet<Screen> persistentVisible = EnumSet.noneOf(Screen.class);
    private final ArrayDeque<Screen> screenStack = new ArrayDeque<>();
    private final List<StateListener> stateListeners = new ArrayList<>();

    private volatile boolean gameInputBlocked;
    private boolean destroyed;

    public VostokUiManager(Activity activity) {
        this.activity = activity;
        host = new FrameLayout(activity);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        host.setClickable(false);
        host.setFocusable(false);
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        activity.addContentView(host, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        metrics = new VostokUiMetrics(activity, host);
    }

    public FrameLayout getHost() {
        return host;
    }

    public VostokUiMetrics getMetrics() {
        return metrics;
    }

    public void registerScreen(Screen screen, ScreenController controller) {
        if (destroyed || screen == null || controller == null) return;
        ScreenController old = controllers.put(screen, controller);
        if (old != null && old != controller) old.hide();
    }

    public void unregisterScreen(Screen screen) {
        if (destroyed || screen == null) return;
        closeScreen(screen);
        ScreenController controller = controllers.remove(screen);
        if (controller != null) controller.hide();
    }

    public boolean openScreen(Screen screen) {
        if (destroyed || screen == null) return false;
        ScreenController controller = controllers.get(screen);
        if (controller == null) return false;

        if (!screen.inBackStack) {
            if (persistentVisible.add(screen)) controller.show(host, metrics);
            publishState();
            return true;
        }

        Screen current = screenStack.peekLast();
        if (current == screen) return true;

        if (current != null) {
            ScreenController currentController = controllers.get(current);
            if (currentController != null) currentController.hide();
        }

        screenStack.remove(screen);
        screenStack.addLast(screen);
        controller.show(host, metrics);
        publishState();
        return true;
    }

    public boolean openScreen(int wireId) {
        return openScreen(Screen.fromWireId(wireId));
    }

    public boolean closeScreen(Screen screen) {
        if (destroyed || screen == null) return false;
        ScreenController controller = controllers.get(screen);

        if (!screen.inBackStack) {
            if (!persistentVisible.remove(screen)) return false;
            if (controller != null) controller.hide();
            publishState();
            return true;
        }

        boolean wasTop = screen == screenStack.peekLast();
        boolean removed = screenStack.remove(screen);
        if (!removed) return false;
        if (controller != null) controller.hide();

        if (wasTop) {
            Screen next = screenStack.peekLast();
            ScreenController nextController = next == null ? null : controllers.get(next);
            if (nextController != null) nextController.show(host, metrics);
        }
        publishState();
        return true;
    }

    public boolean closeScreen(int wireId) {
        return closeScreen(Screen.fromWireId(wireId));
    }

    public boolean handleBack() {
        if (destroyed) return false;
        Screen top = screenStack.peekLast();
        if (top == null || !top.closeOnBack) return false;
        return closeScreen(top);
    }

    public void closeAllTransient() {
        if (destroyed) return;
        while (!screenStack.isEmpty()) {
            Screen screen = screenStack.removeLast();
            ScreenController controller = controllers.get(screen);
            if (controller != null) controller.hide();
        }
        publishState();
    }

    public boolean isOpen(Screen screen) {
        if (screen == null) return false;
        return screen.inBackStack ? screenStack.contains(screen) : persistentVisible.contains(screen);
    }

    public Screen getTopScreen() {
        return screenStack.peekLast();
    }

    public boolean shouldBlockGameInput() {
        return gameInputBlocked;
    }

    public void addStateListener(StateListener listener, boolean notifyImmediately) {
        if (destroyed || listener == null) return;
        if (!stateListeners.contains(listener)) stateListeners.add(listener);
        if (notifyImmediately) listener.onUiStateChanged(getTopScreen(), gameInputBlocked);
    }

    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public void shutdown() {
        if (destroyed) return;
        closeAllTransient();

        for (Screen screen : EnumSet.copyOf(persistentVisible)) {
            ScreenController controller = controllers.get(screen);
            if (controller != null) controller.hide();
        }
        persistentVisible.clear();
        controllers.clear();
        stateListeners.clear();
        gameInputBlocked = false;
        metrics.shutdown();
        destroyed = true;

        if (host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
    }

    private void publishState() {
        Screen top = screenStack.peekLast();
        boolean nextBlocked = top != null && top.blocksGameInput;
        gameInputBlocked = nextBlocked;
        List<StateListener> copy = new ArrayList<>(stateListeners);
        for (StateListener listener : copy) {
            listener.onUiStateChanged(top, nextBlocked);
        }
    }
}
