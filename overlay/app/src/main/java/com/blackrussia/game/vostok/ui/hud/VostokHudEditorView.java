package com.blackrussia.game.vostok.ui.hud;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** DEV-only on-device HUD calibrator for VOSTOK RP. */
public final class VostokHudEditorView extends View {
    public static final int TARGET_LEFT = 0;
    public static final int TARGET_RADAR = 1;
    public static final int TARGET_TOP = 2;
    public static final int TARGET_QUEST = 3;
    public static final int TARGET_FIST = 4;

    private static final int ORANGE = Color.rgb(255, 132, 38);
    private static final int WHITE = Color.rgb(245, 247, 249);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final VostokHudController controller;
    private int target = TARGET_LEFT;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public VostokHudEditorView(Activity activity, VostokHudController controller) {
        super(activity);
        this.controller = controller;
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(Color.TRANSPARENT);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.argb(32, 0, 0, 0));
        c.drawRect(0, 0, w, h, fill);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f);
        stroke.setColor(Color.argb(65, 255, 255, 255));
        for (int x = 0; x < w; x += Math.max(40, w / 20)) c.drawLine(x, 0, x, h, stroke);
        for (int y = 0; y < h; y += Math.max(40, h / 12)) c.drawLine(0, y, w, y, stroke);

        fill.setColor(Color.argb(220, 14, 17, 20));
        c.drawRect(12, 12, Math.min(w - 12, 540), 76, fill);
        text.setTextSize(24f);
        text.setColor(ORANGE);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("VOSTOK HUD EDITOR / DEV", 26, 40, text);
        text.setTextSize(16f);
        text.setColor(WHITE);
        c.drawText(controller.editorDescribe(target), 26, 65, text);

        final float pad = 12f;
        final float tabTop = h - 132f;
        final float tabH = 42f;
        final String[] names = {"ЛЕВЫЙ HUD", "РАДАР", "ВЕРХ", "ЗАДАНИЕ", "КУЛАК"};
        float tabW = (w - pad * 2f) / names.length;
        for (int i = 0; i < names.length; i++) {
            float l = pad + i * tabW;
            fill.setColor(i == target ? Color.argb(235, 52, 36, 22) : Color.argb(220, 14, 17, 20));
            c.drawRect(l, tabTop, l + tabW - 2f, tabTop + tabH, fill);
            stroke.setColor(i == target ? ORANGE : Color.argb(100, 180, 188, 194));
            c.drawRect(l, tabTop, l + tabW - 2f, tabTop + tabH, stroke);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(14f);
            text.setColor(i == target ? ORANGE : WHITE);
            c.drawText(names[i], l + (tabW - 2f) / 2f, tabTop + 27f, text);
        }

        final String[] actions = {"−", "+", "СБРОС", "СОХРАНИТЬ", "ЗАКРЫТЬ"};
        final float actionTop = h - 78f;
        float actionW = (w - pad * 2f) / actions.length;
        for (int i = 0; i < actions.length; i++) {
            float l = pad + i * actionW;
            fill.setColor(Color.argb(225, 14, 17, 20));
            c.drawRect(l, actionTop, l + actionW - 2f, h - 18f, fill);
            stroke.setColor(i == 3 ? ORANGE : Color.argb(110, 180, 188, 194));
            c.drawRect(l, actionTop, l + actionW - 2f, h - 18f, stroke);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(16f);
            text.setColor(i == 3 ? ORANGE : WHITE);
            c.drawText(actions[i], l + (actionW - 2f) / 2f, actionTop + 37f, text);
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(17f);
        text.setColor(Color.argb(230, 245, 247, 249));
        c.drawText("Выберите элемент и тяните пальцем по экрану.  − / + меняют размер.", w / 2f, h - 150f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int w = getWidth();
        int h = getHeight();
        float pad = 12f;
        float tabTop = h - 132f;
        float tabW = (w - pad * 2f) / 5f;
        float actionTop = h - 78f;
        float actionW = (w - pad * 2f) / 5f;

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = lastX = x;
            downY = lastY = y;
            dragging = y < tabTop;
            if (y >= tabTop && y <= tabTop + 42f) {
                int next = (int)((x - pad) / tabW);
                if (next >= 0 && next < 5) {
                    target = next;
                    invalidate();
                }
                return true;
            }
            if (y >= actionTop && y <= h - 18f) {
                int action = (int)((x - pad) / actionW);
                if (action == 0) controller.editorScale(target, -1);
                else if (action == 1) controller.editorScale(target, 1);
                else if (action == 2) controller.editorReset(target);
                else if (action == 3) controller.editorSave();
                else if (action == 4) controller.closeHudEditor();
                invalidate();
                return true;
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging) {
            float dx = x - lastX;
            float dy = y - lastY;
            lastX = x;
            lastY = y;
            controller.editorMove(target, dx, dy);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            return true;
        }
        return true;
    }
}
