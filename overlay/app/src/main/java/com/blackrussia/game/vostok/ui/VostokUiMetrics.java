package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared screen metrics for all future VOSTOK UI surfaces.
 *
 * Complex layouts can use 1920x1080 design units while simple controls can
 * continue to use dp. Safe insets are kept separately so a surface can avoid
 * notches/cutouts without baking device-specific coordinates into its layout.
 */
public final class VostokUiMetrics {
    public static final float DESIGN_WIDTH = 1920.0f;
    public static final float DESIGN_HEIGHT = 1080.0f;

    public interface Listener {
        void onMetricsChanged(Snapshot snapshot);
    }

    public static final class Insets {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        Insets(int left, int top, int right, int bottom) {
            this.left = Math.max(0, left);
            this.top = Math.max(0, top);
            this.right = Math.max(0, right);
            this.bottom = Math.max(0, bottom);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Insets)) return false;
            Insets value = (Insets) other;
            return left == value.left && top == value.top
                    && right == value.right && bottom == value.bottom;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            result = 31 * result + right;
            result = 31 * result + bottom;
            return result;
        }
    }

    public static final class Snapshot {
        public final int widthPx;
        public final int heightPx;
        public final float density;
        public final float designScale;
        public final Insets safeInsets;

        Snapshot(int widthPx, int heightPx, float density, float designScale, Insets safeInsets) {
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.density = density;
            this.designScale = designScale;
            this.safeInsets = safeInsets;
        }
    }

    private final Activity activity;
    private final View rootView;
    private final List<Listener> listeners = new ArrayList<>();
    private final View.OnLayoutChangeListener layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> refreshSize();

    private Insets safeInsets = new Insets(0, 0, 0, 0);
    private Snapshot snapshot;
    private boolean destroyed;

    public VostokUiMetrics(Activity activity, View rootView) {
        this.activity = activity;
        this.rootView = rootView;
        rootView.addOnLayoutChangeListener(layoutListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rootView.setOnApplyWindowInsetsListener((view, insets) -> {
                updateInsets(insets);
                return insets;
            });
            rootView.requestApplyInsets();
        }
        refreshSize();
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void addListener(Listener listener, boolean notifyImmediately) {
        if (listener == null || destroyed) return;
        if (!listeners.contains(listener)) listeners.add(listener);
        if (notifyImmediately && snapshot != null) listener.onMetricsChanged(snapshot);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int dp(float value) {
        float density = snapshot == null ? activity.getResources().getDisplayMetrics().density : snapshot.density;
        return Math.round(value * density);
    }

    public int designPx(float value) {
        float scale = snapshot == null ? 1.0f : snapshot.designScale;
        return Math.round(value * scale);
    }

    public Rect getSafeContentRect() {
        Snapshot current = snapshot;
        if (current == null) return new Rect();
        return new Rect(
                current.safeInsets.left,
                current.safeInsets.top,
                Math.max(current.safeInsets.left, current.widthPx - current.safeInsets.right),
                Math.max(current.safeInsets.top, current.heightPx - current.safeInsets.bottom)
        );
    }

    public void shutdown() {
        if (destroyed) return;
        destroyed = true;
        listeners.clear();
        rootView.removeOnLayoutChangeListener(layoutListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rootView.setOnApplyWindowInsetsListener(null);
        }
    }

    private void updateInsets(WindowInsets insets) {
        if (destroyed || insets == null) return;

        int left = insets.getSystemWindowInsetLeft();
        int top = insets.getSystemWindowInsetTop();
        int right = insets.getSystemWindowInsetRight();
        int bottom = insets.getSystemWindowInsetBottom();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                left = Math.max(left, cutout.getSafeInsetLeft());
                top = Math.max(top, cutout.getSafeInsetTop());
                right = Math.max(right, cutout.getSafeInsetRight());
                bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            }
        }

        Insets next = new Insets(left, top, right, bottom);
        if (!next.equals(safeInsets)) {
            safeInsets = next;
            refreshSize();
        }
    }

    private void refreshSize() {
        if (destroyed) return;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = rootView.getWidth() > 0 ? rootView.getWidth() : metrics.widthPixels;
        int height = rootView.getHeight() > 0 ? rootView.getHeight() : metrics.heightPixels;
        float density = metrics.density <= 0.0f ? 1.0f : metrics.density;
        float designScale = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
        if (designScale <= 0.0f || Float.isNaN(designScale) || Float.isInfinite(designScale)) {
            designScale = 1.0f;
        }

        Snapshot next = new Snapshot(width, height, density, designScale, safeInsets);
        if (sameSnapshot(snapshot, next)) return;
        snapshot = next;

        List<Listener> copy = new ArrayList<>(listeners);
        for (Listener listener : copy) {
            listener.onMetricsChanged(next);
        }
    }

    private static boolean sameSnapshot(Snapshot a, Snapshot b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.widthPx == b.widthPx
                && a.heightPx == b.heightPx
                && Float.compare(a.density, b.density) == 0
                && Float.compare(a.designScale, b.designScale) == 0
                && a.safeInsets.equals(b.safeInsets);
    }
}
