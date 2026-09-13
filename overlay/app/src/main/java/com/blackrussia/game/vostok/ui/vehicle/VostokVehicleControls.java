package com.blackrussia.game.vostok.ui.vehicle;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Candidate 031E touch layer for the vehicle status row drawn by 031D.
 *
 * It deliberately draws no second speedometer. The 031D engine/light/belt/lock
 * glyphs stay the visual source of truth; this class only places small touch
 * targets over engine, lights and lock so the controls feel embedded in the
 * instrument cluster.
 */
public final class VostokVehicleControls {
    public interface ActionSink {
        void onVehicleAction(int action);
    }

    public static final int ACTION_ENGINE = 1;
    public static final int ACTION_LIGHTS = 2;
    public static final int ACTION_LOCK = 3;

    private static final long CLICK_DEBOUNCE_MS = 300L;

    private final Activity activity;
    private final ActionSink sink;
    private final FrameLayout root;
    private final FrameLayout row;
    private final float density;
    private final View engine;
    private final View lights;
    private final View lock;

    private boolean destroyed;
    private boolean vehicleMode;
    private long lastClickAt;

    public VostokVehicleControls(Activity activity, ActionSink sink) {
        this.activity = activity;
        this.sink = sink;
        density = Math.max(1.0f, activity.getResources().getDisplayMetrics().density);

        root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setClickable(false);
        root.setFocusable(false);
        root.setVisibility(View.GONE);

        row = new FrameLayout(activity);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(
                dp(112), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        rowParams.bottomMargin = dp(9);
        root.addView(row, rowParams);

        engine = addTouchTarget(0, ACTION_ENGINE);
        lights = addTouchTarget(1, ACTION_LIGHTS);
        // slot 2 is the seatbelt status. It is display-only until Seatbelt Core exists.
        lock = addTouchTarget(3, ACTION_LOCK);

        activity.addContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void setVehicleMode(boolean active) {
        if (destroyed || vehicleMode == active) return;
        vehicleMode = active;
        root.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    public boolean isVehicleMode() {
        return vehicleMode && !destroyed;
    }

    public void shutdown() {
        if (destroyed) return;
        destroyed = true;
        vehicleMode = false;
        root.setVisibility(View.GONE);
        if (root.getParent() instanceof ViewGroup) {
            ((ViewGroup) root.getParent()).removeView(root);
        }
    }

    private View addTouchTarget(int slot, int action) {
        final View target = new View(activity);
        target.setClickable(true);
        target.setFocusable(false);
        target.setBackground(hitDrawable(false));
        target.setContentDescription(contentDescription(action));

        int size = dp(28);
        int gap = dp(23);
        int center = dp(56);
        int targetCenter = center + Math.round((slot - 1.5f) * gap);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = targetCenter - size / 2;
        params.topMargin = dp(3);
        row.addView(target, params);

        target.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.setBackground(hitDrawable(true));
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.setBackground(hitDrawable(false));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.setBackground(hitDrawable(false));
            }
            return false;
        });
        target.setOnClickListener(view -> dispatch(action));
        return target;
    }

    private void dispatch(int action) {
        if (destroyed || !vehicleMode || sink == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastClickAt < CLICK_DEBOUNCE_MS) return;
        lastClickAt = now;
        sink.onVehicleAction(action);
    }

    private GradientDrawable hitDrawable(boolean pressed) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(pressed ? Color.argb(36, 255, 132, 38) : Color.TRANSPARENT);
        return drawable;
    }

    private String contentDescription(int action) {
        if (action == ACTION_ENGINE) return "Двигатель";
        if (action == ACTION_LIGHTS) return "Фары";
        return "Замок";
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
