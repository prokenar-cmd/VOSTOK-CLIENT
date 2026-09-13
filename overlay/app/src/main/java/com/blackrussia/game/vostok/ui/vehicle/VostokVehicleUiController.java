package com.blackrussia.game.vostok.ui.vehicle;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Candidate 031E vehicle-control surface.
 *
 * 031D already owns the visual speedometer. 031E adds only the small interactive
 * engine hit target and vehicle-mode safeguards without replacing native driving
 * controls or inventing a second vehicle protocol.
 */
public final class VostokVehicleUiController {
    private static final Map<Activity, VostokVehicleUiController> INSTANCES = new WeakHashMap<>();
    private static final int ORANGE = Color.rgb(255, 132, 38);

    private final Activity activity;
    private final FrameLayout host;
    private final EngineHitTarget engineTarget;
    private final float density;

    private boolean visible;
    private boolean engineOn;
    private boolean lightsOn;
    private boolean beltOn;
    private boolean locked;

    public static synchronized VostokVehicleUiController getOrCreate(Activity activity) {
        VostokVehicleUiController value = INSTANCES.get(activity);
        if (value == null) {
            value = new VostokVehicleUiController(activity);
            INSTANCES.put(activity, value);
        }
        return value;
    }

    private VostokVehicleUiController(Activity activity) {
        this.activity = activity;
        density = Math.max(1.0f, activity.getResources().getDisplayMetrics().density);

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            throw new IllegalStateException("VOSTOK Vehicle UI requires android.R.id.content ViewGroup");
        }

        host = new FrameLayout(activity);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        host.setClickable(false);
        host.setFocusable(false);
        ((ViewGroup) content).addView(host, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        engineTarget = new EngineHitTarget(activity);
        engineTarget.setContentDescription("Двигатель");
        engineTarget.setOnClickListener(v -> toggleEngine());
        host.addView(engineTarget);
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutEngineTarget());
        host.setVisibility(View.GONE);
        layoutEngineTarget();
    }

    public void show() {
        visible = true;
        suppressLegacyVehicleVisuals();
        invoke("setVostokVehicleMode", new Class<?>[]{boolean.class}, true);
        engineTarget.setVisibility(View.VISIBLE);
        host.setVisibility(View.VISIBLE);
    }

    public void hide() {
        visible = false;
        engineTarget.setVisibility(View.GONE);
        host.setVisibility(View.GONE);
        invoke("setVostokVehicleMode", new Class<?>[]{boolean.class}, false);
    }

    public void updateState(int engine, int lights, int belt, int lock) {
        engineOn = engine != 0;
        lightsOn = lights != 0;
        beltOn = belt != 0;
        locked = lock != 0;
        engineTarget.invalidate();
        if (visible) suppressLegacyVehicleVisuals();
    }

    public boolean isEngineOn() {
        return engineOn;
    }

    public boolean isLightsOn() {
        return lightsOn;
    }

    public boolean isBeltOn() {
        return beltOn;
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * Keep native pedals/steering/horn/action controls intact. Hide only known
     * donor speedometer/turn-indicator visuals that 031D/031E replace.
     */
    public void suppressLegacyVehicleVisuals() {
        String[] ids = {
                "speedometer", "speed_text", "speed_line", "speed_fuel_text",
                "speed_car_hp_text", "speed_engine_ico", "speed_lock_ico",
                "povoro_left", "povoro_right"
        };
        for (String name : ids) {
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            if (id == 0) continue;
            View view = activity.findViewById(id);
            if (view != null && view != host) view.setVisibility(View.GONE);
        }
    }

    public void shutdown() {
        hide();
        synchronized (VostokVehicleUiController.class) {
            INSTANCES.remove(activity);
        }
        ViewGroup parent = (ViewGroup) host.getParent();
        if (parent != null) parent.removeView(host);
    }

    private void toggleEngine() {
        if (!visible) return;
        // Reuse the donor/native vehicle transport. Radial action id 3 is engine.
        invoke("sendRadialClick", new Class<?>[]{int.class}, 3);
    }

    private void layoutEngineTarget() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(44), dp(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        lp.bottomMargin = dp(7);
        engineTarget.setLayoutParams(lp);
        // 031D engine icon is the first status icon left of speedometer centre.
        engineTarget.setTranslationX(-dp(35));
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = activity.getClass().getMethod(name, types);
            method.invoke(activity, args);
        } catch (Throwable ignored) {
            // Source candidate: missing optional transport must not crash gameplay.
        }
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private final class EngineHitTarget extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EngineHitTarget(Activity activity) {
            super(activity);
            setClickable(true);
            setFocusable(false);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!isPressed()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.3f));
            paint.setColor(Color.argb(210, Color.red(ORANGE), Color.green(ORANGE), Color.blue(ORANGE)));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(12), paint);
        }
    }
}
