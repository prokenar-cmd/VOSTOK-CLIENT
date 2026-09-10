package com.blackrussia.game.vostok.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.blackrussia.game.R;

/**
 * VOSTOK Interaction UI Core v1.
 *
 * Presentation only: the server/native side decides when interaction is available.
 * The button is intentionally kept in a movable action slot so future combat,
 * vehicle and special controls can reserve the same right-side HUD area.
 */
public final class InteractionUiManager {
    public interface ActionSink {
        void onInteractionPressed();
    }

    public static final int CONTEXT_WEAPON = 1;
    public static final int CONTEXT_VEHICLE = 1 << 1;
    public static final int CONTEXT_SPECIAL = 1 << 2;

    private static final String DEFAULT_LABEL = "Взаимодействие";
    private static final int GREEN = 0xFF35E84A;
    private static final int BASE_RIGHT_MARGIN_DP = 54;
    private static final int BASE_BOTTOM_MARGIN_DP = 20;
    private static final int ACTION_SLOT_SPACING_DP = 86;
    private static final int MAX_RESERVED_SLOTS = 4;
    private static final long CLICK_DEBOUNCE_MS = 250L;
    private static final long ANIMATION_MS = 120L;

    private final Activity activity;
    private final ActionSink actionSink;
    private final FrameLayout root;
    private final LinearLayout panel;
    private final TextView distanceView;
    private final TextView directionView;
    private final TextView labelView;
    private final FrameLayout actionButton;

    private boolean destroyed;
    private boolean visible;
    private boolean weaponActive;
    private int contextFlags;
    private int reservedActionSlots;
    private long lastClickAtMs;

    public InteractionUiManager(Activity activity, ActionSink actionSink) {
        this.activity = activity;
        this.actionSink = actionSink;

        root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setVisibility(View.GONE);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        distanceView = new TextView(activity);
        distanceView.setTextColor(Color.WHITE);
        distanceView.setTextSize(15.0f);
        distanceView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        distanceView.setGravity(Gravity.CENTER);
        distanceView.setShadowLayer(dp(2), 0.0f, dp(1), Color.BLACK);
        panel.addView(distanceView, linearParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23), 0, 0));

        directionView = new TextView(activity);
        directionView.setText("▼");
        directionView.setTextColor(GREEN);
        directionView.setTextSize(17.0f);
        directionView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        directionView.setGravity(Gravity.CENTER);
        panel.addView(directionView, linearParams(dp(30), dp(22), 0, dp(2)));

        FrameLayout halo = new FrameLayout(activity);
        halo.setBackground(circleDrawable(0x4435E84A, 0, Color.TRANSPARENT));
        halo.setClickable(true);
        halo.setFocusable(true);

        actionButton = new FrameLayout(activity);
        actionButton.setBackground(circleDrawable(0xE6141716, dp(2), GREEN));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER);
        halo.addView(actionButton, actionParams);

        ImageView hand = new ImageView(activity);
        hand.setImageResource(R.drawable.vostok_interaction_hand);
        hand.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams handParams = new FrameLayout.LayoutParams(dp(43), dp(43), Gravity.CENTER);
        actionButton.addView(hand, handParams);

        halo.setOnClickListener(view -> dispatchClick());
        panel.addView(halo, linearParams(dp(86), dp(86), 0, dp(6)));

        labelView = new TextView(activity);
        labelView.setText(DEFAULT_LABEL);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(13.0f);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        labelView.setPadding(dp(11), 0, dp(11), 0);
        labelView.setBackground(pillDrawable());
        panel.addView(labelView, linearParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(31), 0, 0));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(148), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM
        );
        panelParams.rightMargin = dp(BASE_RIGHT_MARGIN_DP);
        panelParams.bottomMargin = dp(BASE_BOTTOM_MARGIN_DP);
        root.addView(panel, panelParams);

        activity.addContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void show(float distanceMeters) {
        show(DEFAULT_LABEL, distanceMeters);
    }

    public void show(String label, float distanceMeters) {
        if (destroyed) {
            return;
        }

        labelView.setText(label == null || label.trim().isEmpty() ? DEFAULT_LABEL : label.trim());
        updateDistance(distanceMeters);
        updatePlacement();

        if (!visible) {
            visible = true;
            root.animate().cancel();
            root.setAlpha(0.0f);
            root.setVisibility(View.VISIBLE);
            root.animate().alpha(1.0f).setDuration(ANIMATION_MS).start();
        }
    }

    public void hide() {
        if (destroyed || !visible) {
            return;
        }
        visible = false;
        root.animate().cancel();
        root.animate()
                .alpha(0.0f)
                .setDuration(ANIMATION_MS)
                .withEndAction(() -> {
                    if (!destroyed && !visible) {
                        root.setVisibility(View.GONE);
                        root.setAlpha(1.0f);
                    }
                })
                .start();
    }

    public void setWeaponActive(boolean active) {
        if (destroyed || weaponActive == active) {
            return;
        }
        weaponActive = active;
        updatePlacement();
    }

    /**
     * Reserves right-side HUD slots for future controls such as fire/aim/reload.
     * reservedSlots is intentionally generic so new systems do not hard-code positions here.
     */
    public void setContext(int flags, int reservedSlots) {
        if (destroyed) {
            return;
        }
        contextFlags = flags;
        reservedActionSlots = clamp(reservedSlots, 0, MAX_RESERVED_SLOTS);
        updatePlacement();
    }

    public boolean isVisible() {
        return visible && !destroyed;
    }

    public void shutdown() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        visible = false;
        root.animate().cancel();
        actionButton.animate().cancel();
        root.setVisibility(View.GONE);
        ViewParentCompat.removeFromParent(root);
    }

    private void dispatchClick() {
        if (destroyed || !visible) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastClickAtMs < CLICK_DEBOUNCE_MS) {
            return;
        }
        lastClickAtMs = now;

        actionButton.animate().cancel();
        actionButton.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(55L)
                .withEndAction(() -> actionButton.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(75L)
                        .start())
                .start();

        if (actionSink != null) {
            actionSink.onInteractionPressed();
        }
    }

    private void updateDistance(float distanceMeters) {
        if (Float.isNaN(distanceMeters) || Float.isInfinite(distanceMeters) || distanceMeters < 0.0f) {
            distanceView.setVisibility(View.GONE);
            directionView.setVisibility(View.GONE);
            return;
        }
        distanceView.setVisibility(View.VISIBLE);
        directionView.setVisibility(View.VISIBLE);
        distanceView.setText(Math.max(0, Math.round(distanceMeters)) + " м");
    }

    private void updatePlacement() {
        ViewGroup.LayoutParams raw = panel.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) {
            return;
        }

        boolean contextNeedsSpace = weaponActive
                || (contextFlags & CONTEXT_WEAPON) != 0
                || (contextFlags & CONTEXT_VEHICLE) != 0
                || (contextFlags & CONTEXT_SPECIAL) != 0;

        int slots = reservedActionSlots;
        if (contextNeedsSpace) {
            slots = Math.max(slots, 1);
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        int targetRightMargin = dp(BASE_RIGHT_MARGIN_DP + ACTION_SLOT_SPACING_DP * slots);
        int targetBottomMargin = dp(BASE_BOTTOM_MARGIN_DP);
        if (params.rightMargin != targetRightMargin || params.bottomMargin != targetBottomMargin) {
            params.rightMargin = targetRightMargin;
            params.bottomMargin = targetBottomMargin;
            panel.setLayoutParams(params);
        }
    }

    private GradientDrawable circleDrawable(int fillColor, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable pillDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(0xE6161817);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), GREEN);
        return drawable;
    }

    private LinearLayout.LayoutParams linearParams(int width, int height, int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = topMargin;
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ViewParentCompat {
        private ViewParentCompat() {
        }

        static void removeFromParent(View view) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }
    }
}
