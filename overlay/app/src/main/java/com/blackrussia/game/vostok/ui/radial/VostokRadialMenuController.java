package com.blackrussia.game.vostok.ui.radial;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.blackrussia.game.vostok.ui.RadialMenuContract;
import com.blackrussia.game.vostok.ui.VostokUiManager;
import com.blackrussia.game.vostok.ui.VostokUiMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Candidate 031F radial menu.
 *
 * Design contract:
 * - one centered wheel only; no branches, helper cards or side panels;
 * - root contains exactly four sectors;
 * - a child level replaces the previous wheel completely;
 * - root center closes, child center goes back;
 * - nearby-player data is empty until an authoritative feed supplies it.
 */
public final class VostokRadialMenuController implements
        VostokUiManager.ScreenController, RadialMenuContract.Host {

    private static final int ORANGE = Color.rgb(255, 125, 32);
    private static final int ORANGE_HOT = Color.rgb(255, 93, 30);
    private static final int WHITE = Color.rgb(245, 247, 249);
    private static final int STEEL = Color.rgb(166, 175, 183);
    private static final int GRAPHITE = Color.rgb(17, 20, 23);

    private static final int ROOT_PERSON = 101;
    private static final int ROOT_DOCUMENTS = 102;
    private static final int ROOT_ANIMATIONS = 103;
    private static final int ROOT_QUICK = 104;

    private static final int DOC_PASSPORT = 201;
    private static final int DOC_LICENSES = 202;
    private static final int DOC_MEDICAL = 203;

    private static final int ANIM_EMOTIONS = 301;
    private static final int ANIM_GESTURES = 302;
    private static final int ANIM_POSES = 303;

    private static final int PLAYER_BASE = 10000;

    private enum LevelType {
        ROOT,
        DOCUMENTS,
        ANIMATIONS,
        PLAYERS_FOR_DOCUMENT,
        PLAYERS_FOR_ACTION,
        ACTIONS_FOR_PLAYER,
        EXTERNAL
    }

    public static final class NearbyPlayer {
        public final int playerId;
        public final String name;

        public NearbyPlayer(int playerId, String name) {
            this.playerId = playerId;
            this.name = name == null ? "" : name.trim();
        }
    }

    private static final class Level {
        final LevelType type;
        final String title;
        final List<RadialMenuContract.Item> items;
        final RadialMenuContract.Request externalRequest;
        final int documentId;
        final int playerId;

        Level(LevelType type, String title, List<RadialMenuContract.Item> items,
              RadialMenuContract.Request externalRequest, int documentId, int playerId) {
            this.type = type;
            this.title = title == null ? "" : title;
            this.items = items == null ? Collections.emptyList() : items;
            this.externalRequest = externalRequest;
            this.documentId = documentId;
            this.playerId = playerId;
        }
    }

    private final Activity activity;
    private final VostokUiManager uiManager;
    private final ArrayDeque<Level> levels = new ArrayDeque<>();
    private final List<NearbyPlayer> nearbyPlayers = new ArrayList<>();

    private FrameLayout host;
    private RadialView radialView;
    private boolean visible;

    public VostokRadialMenuController(Activity activity, VostokUiManager uiManager) {
        this.activity = activity;
        this.uiManager = uiManager;
    }

    @Override
    public void show(FrameLayout host, VostokUiMetrics metrics) {
        this.host = host;
        if (radialView == null) {
            radialView = new RadialView(activity);
            radialView.setClickable(true);
            radialView.setFocusable(true);
        }
        if (radialView.getParent() instanceof ViewGroup) {
            ((ViewGroup) radialView.getParent()).removeView(radialView);
        }
        host.addView(radialView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        levels.clear();
        levels.addLast(rootLevel());
        visible = true;
        radialView.setVisibility(View.VISIBLE);
        radialView.invalidate();
    }

    @Override
    public void hide() {
        visible = false;
        levels.clear();
        if (radialView != null) {
            radialView.setVisibility(View.GONE);
            if (radialView.getParent() instanceof ViewGroup) {
                ((ViewGroup) radialView.getParent()).removeView(radialView);
            }
        }
        host = null;
    }

    @Override
    public void open(RadialMenuContract.Request request) {
        if (request == null) return;
        activity.runOnUiThread(() -> {
            if (!uiManager.isOpen(VostokUiManager.Screen.RADIAL_MENU)) {
                uiManager.openScreen(VostokUiManager.Screen.RADIAL_MENU);
            }
            levels.clear();
            levels.addLast(new Level(
                    LevelType.EXTERNAL,
                    request.title,
                    request.items,
                    request,
                    0,
                    -1
            ));
            if (radialView != null) radialView.invalidate();
        });
    }

    @Override
    public void close() {
        activity.runOnUiThread(this::closeSelf);
    }

    public boolean handleBackInside() {
        if (!visible) return false;
        if (levels.size() > 1) {
            levels.removeLast();
            if (radialView != null) radialView.invalidate();
        } else {
            closeSelf();
        }
        return true;
    }

    public void setNearbyPlayers(List<NearbyPlayer> players) {
        activity.runOnUiThread(() -> {
            nearbyPlayers.clear();
            if (players != null) {
                for (NearbyPlayer player : players) {
                    if (player != null && player.playerId >= 0) nearbyPlayers.add(player);
                    if (nearbyPlayers.size() >= 8) break;
                }
            }
            Level current = levels.peekLast();
            if (current != null && (current.type == LevelType.PLAYERS_FOR_DOCUMENT
                    || current.type == LevelType.PLAYERS_FOR_ACTION)) {
                Level replacement = nearbyLevel(current.type, current.documentId);
                levels.removeLast();
                levels.addLast(replacement);
                if (radialView != null) radialView.invalidate();
            }
        });
    }

    public void shutdown() {
        hide();
        nearbyPlayers.clear();
    }

    private Level rootLevel() {
        List<RadialMenuContract.Item> items = new ArrayList<>();
        // Order is intentionally visual: TL, TR, BL, BR.
        items.add(item(ROOT_PERSON, 1, "Персонаж", "Статистика, навыки, стиль", true));
        items.add(item(ROOT_DOCUMENTS, 2, "Документы", "Паспорт, лицензии, медкарта", true));
        items.add(item(ROOT_ANIMATIONS, 3, "Анимации", "Эмоции, жесты, позы", true));
        items.add(item(ROOT_QUICK, 4, "Быстрые действия", "Взаимодействие с игроками", true));
        return new Level(LevelType.ROOT, "", items, null, 0, -1);
    }

    private Level documentsLevel() {
        List<RadialMenuContract.Item> items = new ArrayList<>();
        items.add(item(DOC_PASSPORT, 2, "Паспорт", "Показать документ", true));
        items.add(item(DOC_LICENSES, 2, "Лицензии", "Показать лицензии", true));
        items.add(item(DOC_MEDICAL, 2, "Медкарта", "Показать медкарту", true));
        return new Level(LevelType.DOCUMENTS, "Документы", items, null, 0, -1);
    }

    private Level animationsLevel() {
        List<RadialMenuContract.Item> items = new ArrayList<>();
        items.add(item(ANIM_EMOTIONS, 3, "Эмоции", "", true));
        items.add(item(ANIM_GESTURES, 3, "Жесты", "", true));
        items.add(item(ANIM_POSES, 3, "Позы", "", true));
        return new Level(LevelType.ANIMATIONS, "Анимации", items, null, 0, -1);
    }

    private Level nearbyLevel(LevelType type, int documentId) {
        List<RadialMenuContract.Item> items = new ArrayList<>();
        for (NearbyPlayer player : nearbyPlayers) {
            String title = player.name.isEmpty() ? ("Игрок " + player.playerId) : player.name;
            items.add(item(PLAYER_BASE + player.playerId, 5, title, "ID " + player.playerId, true));
        }
        if (items.isEmpty()) {
            items.add(item(-1, 5, "Нет игроков рядом", "Подойдите ближе к игроку", false));
        }
        return new Level(type, "Игроки рядом", items, null, documentId, -1);
    }

    private Level actionsLevel(int playerId) {
        List<RadialMenuContract.Item> items = new ArrayList<>();
        items.add(item(401, 4, "Поздороваться", "", true));
        items.add(item(402, 4, "Обмен", "", true));
        items.add(item(403, 4, "Передать деньги", "", true));
        return new Level(LevelType.ACTIONS_FOR_PLAYER, "Действия", items, null, 0, playerId);
    }

    private static RadialMenuContract.Item item(int id, int kind, String title,
                                                 String subtitle, boolean enabled) {
        return new RadialMenuContract.Item(id, kind, title, subtitle, enabled);
    }

    private void select(RadialMenuContract.Item selected) {
        if (selected == null || !selected.enabled) return;
        Level level = levels.peekLast();
        if (level == null) return;

        switch (level.type) {
            case ROOT:
                if (selected.id == ROOT_DOCUMENTS) {
                    levels.addLast(documentsLevel());
                } else if (selected.id == ROOT_ANIMATIONS) {
                    levels.addLast(animationsLevel());
                } else if (selected.id == ROOT_QUICK) {
                    levels.addLast(nearbyLevel(LevelType.PLAYERS_FOR_ACTION, 0));
                } else if (selected.id == ROOT_PERSON) {
                    // Character statistics is a later dedicated screen by design.
                    // Keep 031F free of a fake placeholder statistics window.
                }
                break;
            case DOCUMENTS:
                levels.addLast(nearbyLevel(LevelType.PLAYERS_FOR_DOCUMENT, selected.id));
                break;
            case PLAYERS_FOR_ACTION:
                if (selected.id >= PLAYER_BASE) {
                    levels.addLast(actionsLevel(selected.id - PLAYER_BASE));
                }
                break;
            case PLAYERS_FOR_DOCUMENT:
                // Selection is intentionally transport-neutral in 031F. A later authoritative
                // document bridge can consume documentId + playerId without redesigning UI.
                break;
            case EXTERNAL:
                if (level.externalRequest != null && level.externalRequest.selectionSink != null) {
                    level.externalRequest.selectionSink.onSelected(level.externalRequest, selected);
                }
                break;
            case ANIMATIONS:
            case ACTIONS_FOR_PLAYER:
                // 031F establishes navigation/visual contracts only; gameplay execution follows
                // through dedicated authoritative systems, never invented client-side here.
                break;
        }
        if (radialView != null) radialView.invalidate();
    }

    private void closeSelf() {
        if (visible) uiManager.closeScreen(VostokUiManager.Screen.RADIAL_MENU);
    }

    private final class RadialView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF outer = new RectF();
        private final RectF inner = new RectF();

        private float cx;
        private float cy;
        private float outerRadius;
        private float innerRadius;
        private int pressedIndex = -1;
        private boolean centerPressed;

        RadialView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.argb(70, 0, 0, 0));

            cx = getWidth() * 0.5f;
            cy = getHeight() * 0.5f;
            outerRadius = Math.min(getWidth(), getHeight()) * 0.30f;
            innerRadius = outerRadius * 0.29f;

            outer.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);
            inner.set(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);

            Level level = levels.peekLast();
            if (level == null) return;
            List<RadialMenuContract.Item> items = level.items;
            int count = Math.max(1, items.size());

            if (level.type == LevelType.ROOT && items.size() == 4) {
                // Fixed approved root geometry: TL, TR, BL, BR.
                drawSector(canvas, items.get(0), 180f, 90f, pressedIndex == 0, 0);
                drawSector(canvas, items.get(1), 270f, 90f, pressedIndex == 1, 1);
                drawSector(canvas, items.get(2), 90f, 90f, pressedIndex == 2, 2);
                drawSector(canvas, items.get(3), 0f, 90f, pressedIndex == 3, 3);
            } else {
                float sweep = 360f / count;
                float start = -90f - sweep * 0.5f;
                for (int i = 0; i < items.size(); i++) {
                    drawSector(canvas, items.get(i), start + i * sweep, sweep,
                            pressedIndex == i, i);
                }
            }

            drawCenter(canvas, levels.size() > 1 || level.type != LevelType.ROOT);
            drawLevelTitle(canvas, level);
        }

        private void drawSector(Canvas canvas, RadialMenuContract.Item item, float start,
                                float sweep, boolean pressed, int visualIndex) {
            path.reset();
            path.arcTo(outer, start + 1.1f, sweep - 2.2f, true);
            path.arcTo(inner, start + sweep - 1.1f, -(sweep - 2.2f), false);
            path.close();

            fill.setStyle(Paint.Style.FILL);
            int alpha = item.enabled ? (pressed ? 232 : 202) : 135;
            fill.setColor(pressed
                    ? Color.argb(alpha, 78, 31, 23)
                    : Color.argb(alpha, 16, 20, 23));
            fill.setShadowLayer(pressed ? dp(11) : dp(5), 0, 0,
                    pressed ? Color.argb(175, 255, 91, 31) : Color.argb(90, 255, 125, 32));
            canvas.drawPath(path, fill);
            fill.clearShadowLayer();

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(pressed ? dp(2.1f) : dp(1.2f));
            stroke.setColor(item.enabled
                    ? (pressed ? ORANGE_HOT : Color.argb(205, 255, 125, 32))
                    : Color.argb(90, 166, 175, 183));
            canvas.drawPath(path, stroke);

            float mid = start + sweep * 0.5f;
            double radians = Math.toRadians(mid);
            float labelRadius = innerRadius + (outerRadius - innerRadius) * 0.57f;
            float x = cx + (float) Math.cos(radians) * labelRadius;
            float y = cy + (float) Math.sin(radians) * labelRadius;
            drawItem(canvas, item, x, y, visualIndex);
        }

        private void drawItem(Canvas canvas, RadialMenuContract.Item item, float x, float y,
                              int visualIndex) {
            int color = item.enabled ? WHITE : Color.argb(140, 200, 205, 210);
            drawIcon(canvas, item.kind, x, y - dp(23), color);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setColor(color);
            text.setTextSize(dp(item.title.length() > 15 ? 8.6f : 10.5f));
            drawCenteredWrapped(canvas, item.title, x, y + dp(5), outerRadius * 0.70f, 2, text);

            if (!item.subtitle.isEmpty()) {
                text.setTypeface(Typeface.DEFAULT);
                text.setColor(item.enabled ? STEEL : Color.argb(115, 166, 175, 183));
                text.setTextSize(dp(7.2f));
                drawCenteredWrapped(canvas, item.subtitle, x, y + dp(19), outerRadius * 0.66f, 2, text);
            }
        }

        private void drawCenter(Canvas canvas, boolean nested) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(centerPressed ? Color.argb(235, 64, 29, 24) : Color.argb(235, GRAPHITE >> 16 & 255, GRAPHITE >> 8 & 255, GRAPHITE & 255));
            fill.setShadowLayer(centerPressed ? dp(10) : dp(5), 0, 0,
                    Color.argb(centerPressed ? 175 : 95, 255, 112, 32));
            canvas.drawCircle(cx, cy, innerRadius - dp(2), fill);
            fill.clearShadowLayer();

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(centerPressed ? ORANGE_HOT : ORANGE);
            canvas.drawCircle(cx, cy, innerRadius - dp(2), stroke);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setColor(WHITE);
            text.setTextSize(dp(12));
            canvas.drawText(nested ? "‹" : "×", cx, cy - dp(5), text);
            text.setTextSize(dp(7.8f));
            canvas.drawText(nested ? "Назад" : "Закрыть меню", cx, cy + dp(13), text);
        }

        private void drawLevelTitle(Canvas canvas, Level level) {
            if (level.type == LevelType.ROOT || level.title.isEmpty()) return;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setColor(Color.argb(215, 245, 247, 249));
            text.setTextSize(dp(9));
            canvas.drawText(level.title, cx, cy - outerRadius - dp(12), text);
        }

        private void drawIcon(Canvas canvas, int kind, float x, float y, int color) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);

            float s = dp(9);
            if (kind == 1) {
                canvas.drawCircle(x, y - s * 0.35f, s * 0.34f, stroke);
                RectF body = new RectF(x - s * 0.65f, y + s * 0.10f, x + s * 0.65f, y + s * 0.78f);
                canvas.drawArc(body, 185, 170, false, stroke);
            } else if (kind == 2) {
                RectF page = new RectF(x - s * 0.65f, y - s * 0.78f, x + s * 0.65f, y + s * 0.78f);
                canvas.drawRoundRect(page, dp(1.5f), dp(1.5f), stroke);
                canvas.drawCircle(x - s * 0.24f, y - s * 0.18f, s * 0.16f, stroke);
                canvas.drawLine(x + s * 0.02f, y - s * 0.28f, x + s * 0.43f, y - s * 0.28f, stroke);
                canvas.drawLine(x - s * 0.42f, y + s * 0.22f, x + s * 0.43f, y + s * 0.22f, stroke);
                canvas.drawLine(x - s * 0.42f, y + s * 0.48f, x + s * 0.25f, y + s * 0.48f, stroke);
            } else if (kind == 3) {
                canvas.drawCircle(x, y, s * 0.68f, stroke);
                canvas.drawCircle(x - s * 0.24f, y - s * 0.17f, dp(0.9f), fill);
                canvas.drawCircle(x + s * 0.24f, y - s * 0.17f, dp(0.9f), fill);
                RectF smile = new RectF(x - s * 0.35f, y - s * 0.02f, x + s * 0.35f, y + s * 0.42f);
                canvas.drawArc(smile, 15, 150, false, stroke);
            } else if (kind == 4) {
                path.reset();
                path.moveTo(x - s * 0.75f, y - s * 0.05f);
                path.lineTo(x - s * 0.28f, y + s * 0.32f);
                path.lineTo(x, y + s * 0.05f);
                path.lineTo(x + s * 0.28f, y + s * 0.32f);
                path.lineTo(x + s * 0.75f, y - s * 0.05f);
                canvas.drawPath(path, stroke);
                canvas.drawLine(x - s * 0.58f, y - s * 0.38f, x - s * 0.15f, y, stroke);
                canvas.drawLine(x + s * 0.58f, y - s * 0.38f, x + s * 0.15f, y, stroke);
            } else {
                canvas.drawCircle(x - s * 0.27f, y - s * 0.20f, s * 0.20f, stroke);
                canvas.drawCircle(x + s * 0.27f, y - s * 0.20f, s * 0.20f, stroke);
                canvas.drawArc(new RectF(x - s * 0.75f, y, x + s * 0.05f, y + s * 0.65f), 185, 170, false, stroke);
                canvas.drawArc(new RectF(x - s * 0.05f, y, x + s * 0.75f, y + s * 0.65f), 185, 170, false, stroke);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!visible) return false;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                centerPressed = distance <= innerRadius;
                pressedIndex = centerPressed ? -1 : sectorAt(dx, dy, distance);
                invalidate();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean wasCenter = centerPressed && distance <= innerRadius;
                int index = sectorAt(dx, dy, distance);
                int accepted = (index == pressedIndex) ? index : -1;
                centerPressed = false;
                pressedIndex = -1;
                invalidate();

                if (wasCenter) {
                    handleBackInside();
                    return true;
                }
                Level level = levels.peekLast();
                if (level != null && accepted >= 0 && accepted < level.items.size()) {
                    select(level.items.get(accepted));
                }
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                centerPressed = false;
                pressedIndex = -1;
                invalidate();
                return true;
            }
            return true;
        }

        private int sectorAt(float dx, float dy, float distance) {
            if (distance < innerRadius || distance > outerRadius) return -1;
            Level level = levels.peekLast();
            if (level == null || level.items.isEmpty()) return -1;
            double raw = Math.toDegrees(Math.atan2(dy, dx));
            float angle = (float) (raw < 0 ? raw + 360.0 : raw);

            if (level.type == LevelType.ROOT && level.items.size() == 4) {
                if (angle >= 180f && angle < 270f) return 0; // TL
                if (angle >= 270f) return 1;                 // TR
                if (angle >= 90f && angle < 180f) return 2;  // BL
                return 3;                                    // BR
            }

            int count = level.items.size();
            float sweep = 360f / count;
            float normalized = angle + 90f + sweep * 0.5f;
            while (normalized >= 360f) normalized -= 360f;
            return Math.min(count - 1, Math.max(0, (int) (normalized / sweep)));
        }

        private void drawCenteredWrapped(Canvas canvas, String value, float x, float y,
                                         float maxWidth, int maxLines, Paint paint) {
            if (value == null || value.trim().isEmpty()) return;
            String[] words = value.trim().split("\\s+");
            List<String> lines = new ArrayList<>();
            String current = "";
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && paint.measureText(candidate) > maxWidth) {
                    lines.add(current);
                    current = word;
                    if (lines.size() >= maxLines - 1) break;
                } else {
                    current = candidate;
                }
            }
            if (!current.isEmpty() && lines.size() < maxLines) lines.add(current);
            float lineHeight = paint.getTextSize() * 1.18f;
            for (int i = 0; i < lines.size(); i++) {
                canvas.drawText(lines.get(i), x, y + i * lineHeight, paint);
            }
        }

        private int dp(float value) {
            float density = Math.max(1f, getResources().getDisplayMetrics().density);
            return Math.round(value * density);
        }
    }
}
