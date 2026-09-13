package com.blackrussia.game.vostok.ui.hud;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Candidate 031D persistent gameplay HUD.
 *
 * The view is deliberately self-contained and uses no donor drawables. It keeps
 * chat/radar/native controls alive while replacing donor status/quick-access and
 * speedometer visuals with the VOSTOK graphite/orange telemetry language.
 */
public final class VostokHudController {
    private static final Map<Activity, VostokHudController> INSTANCES = new WeakHashMap<>();

    private static final int ORANGE = Color.rgb(255, 132, 38);
    private static final int WHITE = Color.rgb(245, 247, 249);
    private static final int STEEL = Color.rgb(160, 169, 178);
    private static final int GRAPHITE = Color.rgb(18, 21, 24);

    private final Activity activity;
    private final FrameLayout host;
    private final HudCanvas canvas;
    private final QuickButton[] quickButtons = new QuickButton[4];
    private final float density;

    private boolean hudVisible;
    private boolean vehicleVisible;

    public static synchronized VostokHudController getOrCreate(Activity activity) {
        VostokHudController value = INSTANCES.get(activity);
        if (value == null) {
            value = new VostokHudController(activity);
            INSTANCES.put(activity, value);
        }
        return value;
    }

    private VostokHudController(Activity activity) {
        this.activity = activity;
        density = Math.max(1.0f, activity.getResources().getDisplayMetrics().density);

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            throw new IllegalStateException("VOSTOK HUD requires android.R.id.content ViewGroup");
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

        canvas = new HudCanvas(activity);
        canvas.setClickable(false);
        canvas.setFocusable(false);
        host.addView(canvas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        for (int i = 0; i < quickButtons.length; i++) {
            final int action = i;
            QuickButton button = new QuickButton(activity, i);
            button.setOnClickListener(v -> onQuickAction(action));
            quickButtons[i] = button;
            host.addView(button);
        }

        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutQuickButtons());
        host.setVisibility(View.GONE);
        layoutQuickButtons();
    }

    public void updatePlayerState(int health, int armour, int hunger, int money) {
        canvas.health = clamp(health, 0, 100);
        canvas.armour = clamp(armour, 0, 100);
        canvas.hunger = clamp(hunger, 0, 100);
        canvas.money = money;
        canvas.invalidate();
    }

    public void updateVehicleState(int speed, int fuel, int condition, int mileage,
                                   int engine, int light, int belt, int lock) {
        canvas.speed = Math.max(0, speed);
        canvas.fuel = Math.max(0, fuel);
        canvas.condition = clamp(condition, 0, 100);
        canvas.mileage = Math.max(0, mileage);
        canvas.engine = engine != 0;
        canvas.light = light != 0;
        canvas.belt = belt != 0;
        canvas.lock = lock != 0;
        canvas.invalidate();
    }

    public void showHud() {
        hudVisible = true;
        hideLegacyHudElements();
        refreshVisibility();
    }

    public void hideHud() {
        hudVisible = false;
        refreshVisibility();
    }

    public void showVehicleHud() {
        vehicleVisible = true;
        canvas.invalidate();
        refreshVisibility();
    }

    public void hideVehicleHud() {
        vehicleVisible = false;
        canvas.invalidate();
        refreshVisibility();
    }

    public void showQuest(String title, String objective, int current, int total) {
        canvas.questTitle = title == null ? "" : title.trim();
        canvas.questObjective = objective == null ? "" : objective.trim();
        canvas.questCurrent = Math.max(0, current);
        canvas.questTotal = Math.max(0, total);
        canvas.questVisible = !canvas.questTitle.isEmpty() || !canvas.questObjective.isEmpty();
        canvas.invalidate();
    }

    public void hideQuest() {
        canvas.questVisible = false;
        canvas.invalidate();
    }

    /** Keep donor radar/chat/weapon/wanted; hide only visuals replaced by 031D. */
    public void hideLegacyHudElements() {
        String[] ids = {
                "hud_money", "ruble_png", "progress_hp", "progress_armor",
                "imageView", "imageView2", "imageView3", "imageView4", "imageView6", "imageView7",
                "hud_menu", "hud_BP", "hud_maga", "hud_yvedomlenia",
                "hud_camera", "camera_button", "button_camera", "btn_camera"
        };
        for (String name : ids) {
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            if (id == 0) continue;
            View view = activity.findViewById(id);
            if (view != null && view != host) view.setVisibility(View.GONE);
        }
    }

    public void shutdown() {
        synchronized (VostokHudController.class) {
            INSTANCES.remove(activity);
        }
        ViewGroup parent = (ViewGroup) host.getParent();
        if (parent != null) parent.removeView(host);
    }

    private void refreshVisibility() {
        host.setVisibility(hudVisible || vehicleVisible ? View.VISIBLE : View.GONE);
        for (QuickButton button : quickButtons) {
            button.setVisibility(hudVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void layoutQuickButtons() {
        int size = dp(42);
        int gap = dp(5);
        int top = dp(14);
        int right = dp(18);
        for (int i = 0; i < quickButtons.length; i++) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.RIGHT);
            lp.topMargin = top;
            lp.rightMargin = right + (quickButtons.length - 1 - i) * (size + gap);
            quickButtons[i].setLayoutParams(lp);
        }
        canvas.quickRailRight = right;
        canvas.quickRailTop = top;
        canvas.quickCell = size;
        canvas.quickGap = gap;
        canvas.invalidate();
    }

    private void onQuickAction(int action) {
        if (action == 0) {
            invoke("showMenuu", new Class<?>[0]);
            invoke("togglePlayer", new Class<?>[]{int.class}, 1);
        } else if (action == 1) {
            // Existing donor slot 1 is the shop path. Reuse transport, replace visual only.
            invoke("sendHuddeClick", new Class<?>[]{int.class}, 1);
        } else if (action == 2) {
            // Inventory controller is introduced in 031G. UI Core safely ignores it until registered.
            invoke("showVostokUiScreen", new Class<?>[]{int.class}, 11);
        } else if (action == 3) {
            // Tablet controller is introduced in 031H. UI Core safely ignores it until registered.
            invoke("showVostokUiScreen", new Class<?>[]{int.class}, 13);
        }
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = activity.getClass().getMethod(name, types);
            method.invoke(activity, args);
        } catch (Throwable ignored) {
            // Candidate keeps donor/native compatibility; unavailable future actions stay inert.
        }
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class HudCanvas extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF arc = new RectF();
        private final DecimalFormat moneyFormat;

        int health = 100;
        int armour;
        int hunger = 100;
        int money;

        int speed;
        int fuel;
        int condition = 100;
        int mileage;
        boolean engine;
        boolean light;
        boolean belt;
        boolean lock;

        boolean questVisible;
        String questTitle = "";
        String questObjective = "";
        int questCurrent;
        int questTotal;

        int quickRailRight;
        int quickRailTop;
        int quickCell;
        int quickGap;

        HudCanvas(Activity context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.SQUARE);
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("ru", "RU"));
            symbols.setGroupingSeparator(' ');
            moneyFormat = new DecimalFormat("#,###", symbols);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (hudVisible) {
                drawPlayerTelemetry(c);
                drawQuickRail(c);
                if (questVisible) drawQuest(c);
            }
            if (vehicleVisible) drawVehicleHud(c);
        }

        private void drawPlayerTelemetry(Canvas c) {
            float x = dp(148);
            float y = dp(18);
            float width = dp(118);

            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(170, 15, 18, 21));
            drawCutPanel(c, x, y, width, dp(33), dp(7), fill);

            stroke.setColor(ORANGE);
            stroke.setStrokeWidth(dp(1.1f));
            c.drawLine(x + dp(8), y + dp(32), x + dp(44), y + dp(32), stroke);

            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(dp(12));
            text.setColor(WHITE);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(moneyFormat.format(money), x + width - dp(20), y + dp(21), text);
            text.setTextSize(dp(10));
            text.setColor(ORANGE);
            c.drawText("₽", x + width - dp(7), y + dp(21), text);

            drawStat(c, x, y + dp(42), width, health, 0);
            drawStat(c, x, y + dp(61), width, armour, 1);
            drawStat(c, x, y + dp(80), width, hunger, 2);

            // V-notch joins telemetry to the donor circular minimap without boxing the map in.
            stroke.setColor(Color.argb(190, 156, 166, 175));
            stroke.setStrokeWidth(dp(1));
            path.reset();
            path.moveTo(x - dp(7), y + dp(92));
            path.lineTo(x - dp(14), y + dp(99));
            path.lineTo(x - dp(21), y + dp(92));
            c.drawPath(path, stroke);
        }

        private void drawStat(Canvas c, float x, float y, float width, int value, int icon) {
            drawStatusIcon(c, x + dp(7), y + dp(5), icon);
            float sx = x + dp(19);
            float ex = x + width;
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(Color.argb(110, 130, 140, 148));
            c.drawLine(sx, y + dp(5), ex, y + dp(5), stroke);
            stroke.setColor(ORANGE);
            c.drawLine(sx, y + dp(5), sx + (ex - sx) * (value / 100f), y + dp(5), stroke);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(dp(8));
            text.setColor(Color.argb(220, 230, 234, 237));
            c.drawText(String.valueOf(value), ex, y - dp(1), text);
        }

        private void drawStatusIcon(Canvas c, float cx, float cy, int icon) {
            stroke.setColor(icon == 0 ? ORANGE : Color.argb(220, 200, 207, 212));
            stroke.setStrokeWidth(dp(1.4f));
            if (icon == 0) {
                path.reset();
                path.moveTo(cx - dp(4), cy - dp(1));
                path.cubicTo(cx - dp(5), cy - dp(5), cx, cy - dp(5), cx, cy - dp(2));
                path.cubicTo(cx, cy - dp(5), cx + dp(5), cy - dp(5), cx + dp(4), cy - dp(1));
                path.lineTo(cx, cy + dp(4));
                path.close();
                c.drawPath(path, stroke);
            } else if (icon == 1) {
                path.reset();
                path.moveTo(cx, cy - dp(5));
                path.lineTo(cx + dp(5), cy - dp(3));
                path.lineTo(cx + dp(4), cy + dp(2));
                path.lineTo(cx, cy + dp(5));
                path.lineTo(cx - dp(4), cy + dp(2));
                path.lineTo(cx - dp(5), cy - dp(3));
                path.close();
                c.drawPath(path, stroke);
            } else {
                c.drawLine(cx - dp(5), cy - dp(3), cx + dp(5), cy - dp(3), stroke);
                c.drawLine(cx - dp(4), cy, cx + dp(4), cy, stroke);
                c.drawLine(cx - dp(3), cy + dp(3), cx + dp(3), cy + dp(3), stroke);
            }
        }

        private void drawQuickRail(Canvas c) {
            float width = quickCell * 4f + quickGap * 3f + dp(10);
            float left = getWidth() - quickRailRight - width;
            float top = quickRailTop - dp(5);
            fill.setColor(Color.argb(155, 14, 17, 20));
            drawCutPanel(c, left, top, width, quickCell + dp(10), dp(7), fill);
            stroke.setColor(ORANGE);
            stroke.setStrokeWidth(dp(1));
            c.drawLine(left + dp(9), top + quickCell + dp(9), left + dp(39), top + quickCell + dp(9), stroke);
            for (int i = 1; i < 4; i++) {
                float x = left + dp(5) + i * (quickCell + quickGap) - quickGap / 2f;
                stroke.setColor(Color.argb(70, 190, 198, 204));
                c.drawLine(x, top + dp(10), x, top + quickCell, stroke);
            }
        }

        private void drawQuest(Canvas c) {
            float width = dp(235);
            float right = getWidth() - dp(18);
            float top = quickRailTop + quickCell + dp(20);
            float left = right - width;
            float height = questObjective.isEmpty() ? dp(44) : dp(58);
            fill.setColor(Color.argb(165, 15, 18, 21));
            drawCutPanel(c, left, top, width, height, dp(8), fill);
            fill.setColor(ORANGE);
            c.drawRect(left, top + dp(8), left + dp(3), top + height - dp(7), fill);

            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(dp(10));
            text.setColor(WHITE);
            c.drawText(ellipsize(questTitle, 25), left + dp(13), top + dp(18), text);

            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(dp(9));
            text.setColor(ORANGE);
            String progress = questTotal > 0 ? questCurrent + "/" + questTotal : "";
            c.drawText(progress, right - dp(9), top + dp(18), text);

            if (!questObjective.isEmpty()) {
                text.setTextAlign(Paint.Align.LEFT);
                text.setTypeface(android.graphics.Typeface.DEFAULT);
                text.setTextSize(dp(8.5f));
                text.setColor(Color.argb(225, 190, 198, 205));
                c.drawText(ellipsize(questObjective, 39), left + dp(13), top + dp(36), text);
            }

            if (questTotal > 0) {
                float sx = left + dp(13);
                float ex = right - dp(10);
                float py = top + height - dp(8);
                stroke.setStrokeWidth(dp(1.5f));
                stroke.setColor(Color.argb(100, 115, 125, 132));
                c.drawLine(sx, py, ex, py, stroke);
                stroke.setColor(ORANGE);
                float fraction = Math.max(0f, Math.min(1f, questCurrent / (float) questTotal));
                c.drawLine(sx, py, sx + (ex - sx) * fraction, py, stroke);
            }
        }

        private void drawVehicleHud(Canvas c) {
            float cx = getWidth() / 2f;
            float cy = getHeight() - dp(71);
            float radius = dp(55);

            fill.setColor(Color.argb(165, 13, 16, 19));
            path.reset();
            path.moveTo(cx - dp(160), cy + dp(37));
            path.lineTo(cx - dp(126), cy - dp(5));
            path.lineTo(cx - dp(68), cy - dp(5));
            path.lineTo(cx - dp(55), cy - dp(44));
            path.lineTo(cx + dp(55), cy - dp(44));
            path.lineTo(cx + dp(68), cy - dp(5));
            path.lineTo(cx + dp(126), cy - dp(5));
            path.lineTo(cx + dp(160), cy + dp(37));
            path.lineTo(cx + dp(93), cy + dp(37));
            path.lineTo(cx + dp(73), cy + dp(20));
            path.lineTo(cx - dp(73), cy + dp(20));
            path.lineTo(cx - dp(93), cy + dp(37));
            path.close();
            c.drawPath(path, fill);

            arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
            stroke.setColor(Color.argb(90, 154, 164, 171));
            c.drawArc(arc, 205, 130, false, stroke);
            stroke.setColor(ORANGE);
            float speedFraction = Math.max(0f, Math.min(1f, speed / 240f));
            c.drawArc(arc, 205, 130 * speedFraction, false, stroke);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setColor(WHITE);
            text.setTextSize(dp(27));
            c.drawText(String.valueOf(speed), cx, cy + dp(3), text);
            text.setTextSize(dp(7));
            text.setColor(STEEL);
            c.drawText("КМ/Ч", cx, cy + dp(15), text);
            text.setTextSize(dp(7.5f));
            c.drawText(String.format(Locale.US, "%06d КМ", mileage), cx, cy + dp(29), text);

            drawVehicleWing(c, cx - dp(137), cy + dp(3), true);
            drawVehicleWing(c, cx + dp(137), cy + dp(3), false);
            drawVehicleStatus(c, cx, cy + dp(45));
        }

        private void drawVehicleWing(Canvas c, float cx, float cy, boolean left) {
            stroke.setStrokeWidth(dp(1.7f));
            stroke.setColor(Color.argb(115, 150, 160, 168));
            float start = cx - dp(39);
            float end = cx + dp(39);
            c.drawLine(start, cy + dp(13), end, cy + dp(13), stroke);
            stroke.setColor(ORANGE);
            float fraction = left ? Math.min(1f, fuel / 150f) : condition / 100f;
            c.drawLine(start, cy + dp(13), start + (end - start) * fraction, cy + dp(13), stroke);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(dp(12));
            text.setColor(WHITE);
            c.drawText(left ? fuel + " L" : condition + "%", cx, cy + dp(4), text);
            text.setTextSize(dp(6.5f));
            text.setColor(STEEL);
            c.drawText(left ? "ТОПЛИВО" : "СОСТОЯНИЕ", cx, cy - dp(9), text);
        }

        private void drawVehicleStatus(Canvas c, float cx, float cy) {
            float gap = dp(23);
            drawTinyStatus(c, cx - gap * 1.5f, cy, engine, 0);
            drawTinyStatus(c, cx - gap * 0.5f, cy, light, 1);
            drawTinyStatus(c, cx + gap * 0.5f, cy, belt, 2);
            drawTinyStatus(c, cx + gap * 1.5f, cy, lock, 3);
        }

        private void drawTinyStatus(Canvas c, float cx, float cy, boolean active, int type) {
            stroke.setStrokeWidth(dp(1.2f));
            stroke.setColor(active ? ORANGE : Color.argb(120, 150, 158, 164));
            if (type == 0) {
                c.drawRect(cx - dp(4), cy - dp(3), cx + dp(4), cy + dp(3), stroke);
                c.drawLine(cx + dp(4), cy - dp(1), cx + dp(7), cy - dp(1), stroke);
            } else if (type == 1) {
                c.drawArc(new RectF(cx - dp(5), cy - dp(4), cx + dp(2), cy + dp(4)), -80, 160, false, stroke);
                c.drawLine(cx + dp(3), cy - dp(3), cx + dp(7), cy - dp(5), stroke);
                c.drawLine(cx + dp(3), cy, cx + dp(8), cy, stroke);
                c.drawLine(cx + dp(3), cy + dp(3), cx + dp(7), cy + dp(5), stroke);
            } else if (type == 2) {
                c.drawLine(cx - dp(5), cy - dp(5), cx + dp(5), cy + dp(5), stroke);
                c.drawCircle(cx - dp(5), cy - dp(5), dp(1.5f), stroke);
            } else {
                c.drawRect(cx - dp(5), cy - dp(1), cx + dp(5), cy + dp(5), stroke);
                c.drawArc(new RectF(cx - dp(4), cy - dp(7), cx + dp(4), cy + dp(2)), 190, 160, false, stroke);
            }
        }

        private void drawCutPanel(Canvas c, float x, float y, float w, float h, float cut, Paint paint) {
            path.reset();
            path.moveTo(x, y);
            path.lineTo(x + w - cut, y);
            path.lineTo(x + w, y + cut);
            path.lineTo(x + w, y + h);
            path.lineTo(x + cut, y + h);
            path.lineTo(x, y + h - cut);
            path.close();
            c.drawPath(path, paint);
        }

        private String ellipsize(String value, int max) {
            if (value == null || value.length() <= max) return value == null ? "" : value;
            return value.substring(0, Math.max(0, max - 1)) + "…";
        }
    }

    private final class QuickButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int kind;

        QuickButton(Activity context, int kind) {
            super(context);
            this.kind = kind;
            setClickable(true);
            setFocusable(false);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setColor(isPressed() ? ORANGE : WHITE);

            if (kind == 0) {
                c.drawLine(cx - dp(7), cy - dp(6), cx + dp(7), cy - dp(6), paint);
                c.drawLine(cx - dp(7), cy, cx + dp(4), cy, paint);
                c.drawLine(cx - dp(7), cy + dp(6), cx + dp(7), cy + dp(6), paint);
            } else if (kind == 1) {
                c.drawRect(cx - dp(7), cy - dp(3), cx + dp(7), cy + dp(8), paint);
                c.drawArc(new RectF(cx - dp(4), cy - dp(8), cx + dp(4), cy), 180, 180, false, paint);
            } else if (kind == 2) {
                path.reset();
                path.moveTo(cx, cy - dp(8));
                path.lineTo(cx + dp(8), cy - dp(4));
                path.lineTo(cx + dp(8), cy + dp(5));
                path.lineTo(cx, cy + dp(9));
                path.lineTo(cx - dp(8), cy + dp(5));
                path.lineTo(cx - dp(8), cy - dp(4));
                path.close();
                c.drawPath(path, paint);
                c.drawLine(cx, cy, cx, cy + dp(9), paint);
                c.drawLine(cx - dp(8), cy - dp(4), cx, cy, paint);
                c.drawLine(cx + dp(8), cy - dp(4), cx, cy, paint);
            } else {
                c.drawRect(cx - dp(7), cy - dp(9), cx + dp(7), cy + dp(9), paint);
                c.drawCircle(cx, cy + dp(6), dp(1), paint);
            }
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            invalidate();
        }
    }
}
