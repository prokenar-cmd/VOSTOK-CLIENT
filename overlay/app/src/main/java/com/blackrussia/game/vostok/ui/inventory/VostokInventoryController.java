package com.blackrussia.game.vostok.ui.inventory;

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

import com.blackrussia.game.vostok.ui.VostokUiManager;
import com.blackrussia.game.vostok.ui.VostokUiMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Candidate 031G inventory surface.
 *
 * Visual baseline follows the approved VOSTOK graphite / warm-orange language.
 * The first candidate deliberately uses technical category icons and an empty
 * authoritative snapshot instead of inventing game items. Real item art can be
 * dropped in later without changing the inventory layout or data contract.
 */
public final class VostokInventoryController implements VostokUiManager.ScreenController {
    private static final int ORANGE = Color.rgb(255, 118, 35);
    private static final int ORANGE_HOT = Color.rgb(255, 83, 27);
    private static final int WHITE = Color.rgb(244, 246, 248);
    private static final int STEEL = Color.rgb(163, 170, 177);
    private static final int GRAPHITE = Color.rgb(16, 19, 22);
    private static final int PANEL = Color.rgb(22, 25, 29);

    private final Activity activity;
    private final VostokUiManager uiManager;

    private InventoryContract.Snapshot snapshot = InventoryContract.Snapshot.empty();
    private InventoryContract.ActionSink actionSink;
    private FrameLayout host;
    private InventoryView view;
    private int selectedIndex = -1;
    private boolean visible;

    public VostokInventoryController(Activity activity, VostokUiManager uiManager) {
        this.activity = activity;
        this.uiManager = uiManager;
    }

    @Override
    public void show(FrameLayout host, VostokUiMetrics metrics) {
        this.host = host;
        if (view == null) {
            view = new InventoryView(activity);
            view.setClickable(true);
            view.setFocusable(true);
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        host.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        visible = true;
        if (selectedIndex >= snapshot.items.size()) selectedIndex = -1;
        view.setVisibility(View.VISIBLE);
        view.invalidate();
    }

    @Override
    public void hide() {
        visible = false;
        if (view != null) {
            view.setVisibility(View.GONE);
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }
        host = null;
    }

    public void setSnapshot(InventoryContract.Snapshot next) {
        activity.runOnUiThread(() -> {
            snapshot = next == null ? InventoryContract.Snapshot.empty() : next;
            if (selectedIndex >= snapshot.items.size()) selectedIndex = -1;
            if (view != null) view.invalidate();
        });
    }

    public void setActionSink(InventoryContract.ActionSink sink) {
        actionSink = sink;
        if (view != null) view.invalidate();
    }

    public void shutdown() {
        hide();
        actionSink = null;
        snapshot = InventoryContract.Snapshot.empty();
        selectedIndex = -1;
    }

    private InventoryContract.Item selectedItem() {
        if (selectedIndex < 0 || selectedIndex >= snapshot.items.size()) return null;
        return snapshot.items.get(selectedIndex);
    }

    private void closeSelf() {
        if (visible) uiManager.closeScreen(VostokUiManager.Screen.INVENTORY);
    }

    private void dispatchAction(int action) {
        InventoryContract.Item item = selectedItem();
        InventoryContract.ActionSink sink = actionSink;
        if (item == null || sink == null) return;
        sink.onInventoryAction(action, item);
    }

    private final class InventoryView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private final RectF panel = new RectF();
        private final RectF closeHit = new RectF();
        private final RectF primaryAction = new RectF();
        private final RectF giveAction = new RectF();
        private final RectF dropAction = new RectF();
        private final List<RectF> itemHits = new ArrayList<>();

        private float s = 1.0f;

        InventoryView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.argb(105, 0, 0, 0));

            s = Math.min(getWidth() / 1920.0f, getHeight() / 1080.0f);
            if (s <= 0.0f) s = 1.0f;

            float panelW = 1760f * s;
            float panelH = 820f * s;
            float left = (getWidth() - panelW) * 0.5f;
            float top = (getHeight() - panelH) * 0.5f;
            panel.set(left, top, left + panelW, top + panelH);

            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(239, 13, 16, 19));
            fill.setShadowLayer(22f * s, 0, 8f * s, Color.argb(160, 0, 0, 0));
            canvas.drawRoundRect(panel, 24f * s, 24f * s, fill);
            fill.clearShadowLayer();

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.4f * s);
            stroke.setColor(Color.argb(110, 255, 112, 34));
            canvas.drawRoundRect(panel, 24f * s, 24f * s, stroke);

            drawHeader(canvas);
            drawBody(canvas);
        }

        private void drawHeader(Canvas canvas) {
            float headerH = 126f * s;
            float x = panel.left + 54f * s;
            float y = panel.top + 45f * s;

            drawBackpackIcon(canvas, x, y + 8f * s, 36f * s, WHITE);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(WHITE);
            text.setTextSize(42f * s);
            canvas.drawText("Инвентарь", x + 58f * s, y + 18f * s, text);

            text.setTypeface(Typeface.DEFAULT);
            text.setColor(STEEL);
            text.setTextSize(20f * s);
            canvas.drawText("Управляйте своими предметами", x + 60f * s, y + 50f * s, text);

            int total = snapshot.totalWeightGrams();
            int max = Math.max(1, snapshot.maxWeightGrams);
            float weightX = panel.right - 455f * s;
            float weightY = panel.top + 47f * s;
            drawWeightIcon(canvas, weightX, weightY - 5f * s, 18f * s, WHITE);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setColor(WHITE);
            text.setTextSize(22f * s);
            canvas.drawText(formatKg(total) + " / " + formatKg(max) + " кг",
                    weightX + 38f * s, weightY + 8f * s, text);

            float barLeft = weightX;
            float barTop = weightY + 30f * s;
            float barW = 280f * s;
            float barH = 10f * s;
            fill.setColor(Color.argb(105, 140, 148, 155));
            canvas.drawRoundRect(new RectF(barLeft, barTop, barLeft + barW, barTop + barH),
                    barH * 0.5f, barH * 0.5f, fill);
            float ratio = Math.max(0f, Math.min(1f, total / (float) max));
            if (ratio > 0f) {
                fill.setColor(ratio >= 0.9f ? ORANGE_HOT : ORANGE);
                canvas.drawRoundRect(new RectF(barLeft, barTop, barLeft + barW * ratio, barTop + barH),
                        barH * 0.5f, barH * 0.5f, fill);
            }

            closeHit.set(panel.right - 91f * s, panel.top + 24f * s,
                    panel.right - 31f * s, panel.top + 84f * s);
            fill.setColor(Color.argb(200, 50, 24, 21));
            canvas.drawRoundRect(closeHit, 14f * s, 14f * s, fill);
            stroke.setColor(Color.argb(155, 255, 99, 41));
            stroke.setStrokeWidth(1.2f * s);
            canvas.drawRoundRect(closeHit, 14f * s, 14f * s, stroke);
            stroke.setStrokeWidth(4f * s);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setColor(ORANGE_HOT);
            canvas.drawLine(closeHit.centerX() - 12f * s, closeHit.centerY() - 12f * s,
                    closeHit.centerX() + 12f * s, closeHit.centerY() + 12f * s, stroke);
            canvas.drawLine(closeHit.centerX() + 12f * s, closeHit.centerY() - 12f * s,
                    closeHit.centerX() - 12f * s, closeHit.centerY() + 12f * s, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);

            stroke.setStrokeWidth(1f * s);
            stroke.setColor(Color.argb(75, 160, 168, 175));
            canvas.drawLine(panel.left + 30f * s, panel.top + headerH,
                    panel.right - 30f * s, panel.top + headerH, stroke);
        }

        private void drawBody(Canvas canvas) {
            float bodyTop = panel.top + 146f * s;
            float bodyBottom = panel.bottom - 28f * s;
            float bodyH = bodyBottom - bodyTop;
            float gap = 22f * s;
            float outerPad = 30f * s;

            float leftW = 480f * s;
            float centerW = 735f * s;
            float leftX = panel.left + outerPad;
            float centerX = leftX + leftW + gap;
            float rightX = centerX + centerW + gap;
            float rightW = panel.right - outerPad - rightX;

            RectF leftPanel = new RectF(leftX, bodyTop, leftX + leftW, bodyBottom);
            RectF centerPanel = new RectF(centerX, bodyTop, centerX + centerW, bodyBottom);
            RectF rightPanel = new RectF(rightX, bodyTop, rightX + rightW, bodyBottom);

            drawSubPanel(canvas, leftPanel);
            drawSubPanel(canvas, centerPanel);
            drawSubPanel(canvas, rightPanel);

            drawCharacterAndEquipment(canvas, leftPanel);
            drawItems(canvas, centerPanel, bodyH);
            drawItemInfo(canvas, rightPanel);
        }

        private void drawSubPanel(Canvas canvas, RectF rect) {
            fill.setColor(Color.argb(180, 20, 23, 27));
            canvas.drawRoundRect(rect, 18f * s, 18f * s, fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1f * s);
            stroke.setColor(Color.argb(65, 176, 184, 191));
            canvas.drawRoundRect(rect, 18f * s, 18f * s, stroke);
        }

        private void drawCharacterAndEquipment(Canvas canvas, RectF rect) {
            float avatarLeft = rect.left + 20f * s;
            float avatarRight = rect.left + 292f * s;
            float avatarTop = rect.top + 50f * s;
            float avatarBottom = rect.bottom - 30f * s;

            // Approved layout keeps a character/equipment column. 031G uses a
            // technical mannequin until a dedicated 3D preview bridge is wired.
            fill.setColor(Color.argb(110, 255, 102, 32));
            fill.setShadowLayer(32f * s, 0, 0, Color.argb(150, 255, 95, 28));
            canvas.drawOval(new RectF(
                    avatarLeft + 42f * s, avatarBottom - 24f * s,
                    avatarRight - 42f * s, avatarBottom + 2f * s
            ), fill);
            fill.clearShadowLayer();

            float cx = (avatarLeft + avatarRight) * 0.5f;
            float top = avatarTop + 38f * s;
            fill.setColor(Color.rgb(34, 37, 41));
            canvas.drawCircle(cx, top + 35f * s, 31f * s, fill);

            RectF torso = new RectF(cx - 55f * s, top + 70f * s,
                    cx + 55f * s, top + 265f * s);
            canvas.drawRoundRect(torso, 28f * s, 28f * s, fill);
            canvas.drawRoundRect(new RectF(cx - 87f * s, top + 82f * s,
                    cx - 50f * s, top + 255f * s), 19f * s, 19f * s, fill);
            canvas.drawRoundRect(new RectF(cx + 50f * s, top + 82f * s,
                    cx + 87f * s, top + 255f * s), 19f * s, 19f * s, fill);
            canvas.drawRoundRect(new RectF(cx - 48f * s, top + 247f * s,
                    cx - 7f * s, top + 455f * s), 20f * s, 20f * s, fill);
            canvas.drawRoundRect(new RectF(cx + 7f * s, top + 247f * s,
                    cx + 48f * s, top + 455f * s), 20f * s, 20f * s, fill);

            stroke.setColor(Color.argb(170, 255, 120, 35));
            stroke.setStrokeWidth(1.5f * s);
            canvas.drawLine(cx - 42f * s, top + 266f * s,
                    cx + 42f * s, top + 266f * s, stroke);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(16f * s);
            text.setColor(Color.argb(180, 205, 210, 215));
            canvas.drawText("ПЕРСОНАЖ", cx, avatarBottom - 35f * s, text);

            float slotX = rect.left + 310f * s;
            float slotW = 145f * s;
            float slotH = 94f * s;
            float slotGap = 15f * s;
            String[] labels = {"Голова", "Лицо", "Торс", "Руки", "Спина"};
            int[] types = {
                    InventoryContract.EQUIP_HEAD,
                    InventoryContract.EQUIP_FACE,
                    InventoryContract.EQUIP_TORSO,
                    InventoryContract.EQUIP_HANDS,
                    InventoryContract.EQUIP_BACK
            };
            for (int i = 0; i < labels.length; i++) {
                float y = rect.top + 24f * s + i * (slotH + slotGap);
                RectF slot = new RectF(slotX, y, slotX + slotW, y + slotH);
                fill.setColor(Color.argb(130, 32, 35, 39));
                canvas.drawRoundRect(slot, 14f * s, 14f * s, fill);
                stroke.setColor(Color.argb(82, 170, 178, 184));
                stroke.setStrokeWidth(1f * s);
                canvas.drawRoundRect(slot, 14f * s, 14f * s, stroke);
                drawEquipmentIcon(canvas, slot.centerX(), slot.top + 30f * s, types[i]);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(15f * s);
                text.setColor(STEEL);
                canvas.drawText(labels[i], slot.centerX(), slot.bottom - 13f * s, text);
            }
        }

        private void drawItems(Canvas canvas, RectF rect, float bodyH) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(22f * s);
            text.setColor(WHITE);
            canvas.drawText("Предметы", rect.left + 22f * s, rect.top + 34f * s, text);

            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(18f * s);
            text.setColor(STEEL);
            canvas.drawText(snapshot.items.size() + " / " + InventoryContract.VISIBLE_SLOT_COUNT,
                    rect.right - 22f * s, rect.top + 34f * s, text);

            itemHits.clear();
            int columns = 5;
            int rows = 4;
            float gap = 14f * s;
            float gridLeft = rect.left + 22f * s;
            float gridTop = rect.top + 58f * s;
            float gridRight = rect.right - 22f * s;
            float gridBottom = rect.bottom - 20f * s;
            float cellW = (gridRight - gridLeft - gap * (columns - 1)) / columns;
            float cellH = (gridBottom - gridTop - gap * (rows - 1)) / rows;

            for (int index = 0; index < InventoryContract.VISIBLE_SLOT_COUNT; index++) {
                int row = index / columns;
                int col = index % columns;
                float x = gridLeft + col * (cellW + gap);
                float y = gridTop + row * (cellH + gap);
                RectF cell = new RectF(x, y, x + cellW, y + cellH);
                itemHits.add(cell);

                boolean selected = index == selectedIndex && index < snapshot.items.size();
                fill.setColor(selected
                        ? Color.argb(225, 53, 28, 23)
                        : Color.argb(150, 33, 36, 40));
                if (selected) {
                    fill.setShadowLayer(12f * s, 0, 0, Color.argb(180, 255, 86, 27));
                }
                canvas.drawRoundRect(cell, 13f * s, 13f * s, fill);
                fill.clearShadowLayer();

                stroke.setStrokeWidth(selected ? 2.2f * s : 1f * s);
                stroke.setColor(selected
                        ? ORANGE_HOT
                        : Color.argb(75, 169, 177, 184));
                canvas.drawRoundRect(cell, 13f * s, 13f * s, stroke);

                if (index < snapshot.items.size()) {
                    InventoryContract.Item item = snapshot.items.get(index);
                    drawTechnicalItemIcon(canvas, cell, item);
                    drawItemCount(canvas, cell, item);
                } else {
                    drawEmptySlotMark(canvas, cell);
                }
            }

            if (snapshot.items.isEmpty()) {
                text.setTextAlign(Paint.Align.CENTER);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(19f * s);
                text.setColor(Color.argb(185, 184, 190, 196));
                canvas.drawText("Инвентарь пока пуст", rect.centerX(), rect.bottom - 22f * s, text);
            }
        }

        private void drawItemInfo(Canvas canvas, RectF rect) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(20f * s);
            text.setColor(WHITE);
            canvas.drawText("Информация о предмете", rect.left + 20f * s,
                    rect.top + 34f * s, text);

            InventoryContract.Item item = selectedItem();
            float previewTop = rect.top + 58f * s;
            RectF preview = new RectF(rect.left + 20f * s, previewTop,
                    rect.right - 20f * s, previewTop + 205f * s);
            fill.setColor(Color.argb(125, 28, 31, 35));
            canvas.drawRoundRect(preview, 15f * s, 15f * s, fill);
            stroke.setColor(Color.argb(65, 170, 178, 184));
            stroke.setStrokeWidth(1f * s);
            canvas.drawRoundRect(preview, 15f * s, 15f * s, stroke);

            if (item == null) {
                drawGenericTechnicalMark(canvas, preview.centerX(), preview.centerY() - 10f * s,
                        54f * s, false);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(18f * s);
                text.setColor(STEEL);
                canvas.drawText("Выберите предмет", preview.centerX(), preview.bottom - 24f * s, text);
                drawEmptyInfo(canvas, rect);
                return;
            }

            RectF iconRect = new RectF(preview.centerX() - 65f * s, preview.top + 27f * s,
                    preview.centerX() + 65f * s, preview.bottom - 27f * s);
            drawTechnicalItemIcon(canvas, iconRect, item);

            float y = preview.bottom + 37f * s;
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(24f * s);
            text.setColor(WHITE);
            canvas.drawText(ellipsize(item.name, 27), rect.left + 20f * s, y, text);

            y += 34f * s;
            drawCategoryPill(canvas, rect.left + 20f * s, y - 19f * s, item.category);

            y += 38f * s;
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(16f * s);
            text.setColor(Color.rgb(205, 210, 214));
            y = drawWrappedText(canvas, item.description, rect.left + 20f * s, y,
                    rect.width() - 40f * s, 21f * s, 3);

            float dividerY = Math.min(rect.bottom - 188f * s, y + 18f * s);
            stroke.setColor(Color.argb(75, 165, 173, 180));
            stroke.setStrokeWidth(1f * s);
            canvas.drawLine(rect.left + 20f * s, dividerY,
                    rect.right - 20f * s, dividerY, stroke);

            drawWeightIcon(canvas, rect.left + 22f * s, dividerY + 28f * s,
                    15f * s, STEEL);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(16f * s);
            text.setColor(STEEL);
            canvas.drawText("Вес: " + formatKg(item.weightGrams) + " кг / шт.",
                    rect.left + 50f * s, dividerY + 34f * s, text);

            drawActions(canvas, rect, item);
        }

        private void drawEmptyInfo(Canvas canvas, RectF rect) {
            float y = rect.top + 326f * s;
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(17f * s);
            text.setColor(STEEL);
            y = drawWrappedText(canvas,
                    "Здесь появятся название, описание, вес и доступные действия выбранного предмета.",
                    rect.left + 20f * s, y, rect.width() - 40f * s, 23f * s, 4);

            primaryAction.setEmpty();
            giveAction.setEmpty();
            dropAction.setEmpty();
        }

        private void drawActions(Canvas canvas, RectF rect, InventoryContract.Item item) {
            float bottom = rect.bottom - 18f * s;
            float secondaryH = 54f * s;
            float primaryH = 62f * s;
            float gap = 12f * s;
            float side = 20f * s;

            giveAction.set(rect.left + side, bottom - secondaryH,
                    rect.centerX() - gap * 0.5f, bottom);
            dropAction.set(rect.centerX() + gap * 0.5f, bottom - secondaryH,
                    rect.right - side, bottom);
            primaryAction.set(rect.left + side,
                    giveAction.top - gap - primaryH,
                    rect.right - side,
                    giveAction.top - gap);

            boolean canPrimary = actionSink != null && (item.canUse || item.equipmentSlot != InventoryContract.EQUIP_NONE);
            boolean canGive = actionSink != null && item.canGive;
            boolean canDrop = actionSink != null && item.canDrop;

            String primaryLabel;
            int primaryCode;
            if (item.equipmentSlot != InventoryContract.EQUIP_NONE) {
                primaryLabel = item.equipped ? "Снять" : "Надеть";
                primaryCode = item.equipped ? InventoryContract.ACTION_UNEQUIP : InventoryContract.ACTION_EQUIP;
            } else {
                primaryLabel = "Использовать";
                primaryCode = InventoryContract.ACTION_USE;
            }

            drawActionButton(canvas, primaryAction, primaryLabel, canPrimary, true, 0);
            drawActionButton(canvas, giveAction, "Передать", canGive, false, 1);
            drawActionButton(canvas, dropAction, "Выбросить", canDrop, false, 2);

            primaryAction.setTag(primaryCode);
        }

        private void drawActionButton(Canvas canvas, RectF rect, String label,
                                      boolean enabled, boolean primary, int icon) {
            if (primary) {
                fill.setColor(enabled ? ORANGE : Color.argb(150, 70, 47, 38));
            } else {
                fill.setColor(enabled
                        ? Color.argb(185, 42, 45, 49)
                        : Color.argb(125, 35, 38, 41));
            }
            canvas.drawRoundRect(rect, 13f * s, 13f * s, fill);
            stroke.setStrokeWidth(1f * s);
            stroke.setColor(enabled
                    ? (primary ? Color.argb(220, 255, 151, 80) : Color.argb(90, 182, 190, 197))
                    : Color.argb(55, 140, 146, 151));
            canvas.drawRoundRect(rect, 13f * s, 13f * s, stroke);

            float iconX = rect.left + 30f * s;
            float iconY = rect.centerY();
            drawActionIcon(canvas, iconX, iconY, icon, enabled ? WHITE : Color.rgb(110, 116, 121));

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize((primary ? 19f : 16f) * s);
            text.setColor(enabled ? WHITE : Color.rgb(108, 114, 119));
            canvas.drawText(label, rect.centerX() + 8f * s,
                    rect.centerY() + 6f * s, text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null) return true;
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float x = event.getX();
            float y = event.getY();

            if (closeHit.contains(x, y)) {
                closeSelf();
                return true;
            }

            for (int i = 0; i < itemHits.size(); i++) {
                if (itemHits.get(i).contains(x, y)) {
                    if (i < snapshot.items.size()) selectedIndex = i;
                    else selectedIndex = -1;
                    invalidate();
                    return true;
                }
            }

            InventoryContract.Item item = selectedItem();
            if (item != null && actionSink != null) {
                if (primaryAction.contains(x, y)) {
                    int action = item.equipmentSlot != InventoryContract.EQUIP_NONE
                            ? (item.equipped ? InventoryContract.ACTION_UNEQUIP : InventoryContract.ACTION_EQUIP)
                            : InventoryContract.ACTION_USE;
                    if (item.canUse || item.equipmentSlot != InventoryContract.EQUIP_NONE) {
                        dispatchAction(action);
                    }
                    return true;
                }
                if (giveAction.contains(x, y) && item.canGive) {
                    dispatchAction(InventoryContract.ACTION_GIVE);
                    return true;
                }
                if (dropAction.contains(x, y) && item.canDrop) {
                    dispatchAction(InventoryContract.ACTION_DROP);
                    return true;
                }
            }
            return true;
        }

        private void drawTechnicalItemIcon(Canvas canvas, RectF cell, InventoryContract.Item item) {
            float size = Math.min(cell.width(), cell.height()) * 0.46f;
            float cx = cell.centerX();
            float cy = cell.centerY() - 5f * s;
            int color = Color.rgb(218, 222, 225);

            switch (item.category) {
                case InventoryContract.CATEGORY_FOOD:
                    drawFoodIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_DRINK:
                    drawDrinkIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_MEDICAL:
                    drawMedicalIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_TOOL:
                    drawToolIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_CLOTHING:
                    drawClothingIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_ACCESSORY:
                    drawAccessoryIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_KEY:
                    drawKeyIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_RESOURCE:
                    drawResourceIcon(canvas, cx, cy, size, color);
                    break;
                case InventoryContract.CATEGORY_ELECTRONICS:
                    drawElectronicsIcon(canvas, cx, cy, size, color);
                    break;
                default:
                    drawGenericTechnicalMark(canvas, cx, cy, size, true);
                    break;
            }

            // Every 031G placeholder remains visibly technical until bespoke art arrives.
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(10f * s);
            text.setColor(Color.argb(190, 255, 124, 45));
            canvas.drawText("ТЕХ", cx, cell.bottom - 10f * s, text);
        }

        private void drawItemCount(Canvas canvas, RectF cell, InventoryContract.Item item) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(14f * s);
            text.setColor(WHITE);
            canvas.drawText(item.count + " шт.", cell.right - 8f * s,
                    cell.bottom - 8f * s, text);
        }

        private void drawEmptySlotMark(Canvas canvas, RectF cell) {
            float cx = cell.centerX();
            float cy = cell.centerY();
            stroke.setColor(Color.argb(90, 172, 179, 185));
            stroke.setStrokeWidth(2f * s);
            canvas.drawLine(cx - 10f * s, cy, cx + 10f * s, cy, stroke);
            canvas.drawLine(cx, cy - 10f * s, cx, cy + 10f * s, stroke);
        }

        private void drawCategoryPill(Canvas canvas, float x, float y, int category) {
            String label;
            switch (category) {
                case InventoryContract.CATEGORY_FOOD: label = "Еда"; break;
                case InventoryContract.CATEGORY_DRINK: label = "Напиток"; break;
                case InventoryContract.CATEGORY_MEDICAL: label = "Медицина"; break;
                case InventoryContract.CATEGORY_TOOL: label = "Инструмент"; break;
                case InventoryContract.CATEGORY_CLOTHING: label = "Одежда"; break;
                case InventoryContract.CATEGORY_ACCESSORY: label = "Аксессуар"; break;
                case InventoryContract.CATEGORY_KEY: label = "Ключ"; break;
                case InventoryContract.CATEGORY_RESOURCE: label = "Ресурс"; break;
                case InventoryContract.CATEGORY_ELECTRONICS: label = "Электроника"; break;
                default: label = "Предмет"; break;
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(14f * s);
            float width = text.measureText(label) + 30f * s;
            RectF pill = new RectF(x, y, x + width, y + 28f * s);
            fill.setColor(Color.argb(180, 78, 42, 30));
            canvas.drawRoundRect(pill, 8f * s, 8f * s, fill);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.rgb(255, 170, 118));
            canvas.drawText(label, pill.centerX(), pill.centerY() + 5f * s, text);
        }

        private float drawWrappedText(Canvas canvas, String value, float x, float y,
                                      float maxWidth, float lineHeight, int maxLines) {
            String[] words = (value == null ? "" : value).trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            int lines = 0;
            for (String word : words) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (text.measureText(candidate) <= maxWidth || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    canvas.drawText(line.toString(), x, y, text);
                    y += lineHeight;
                    lines++;
                    if (lines >= maxLines) return y;
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0 && lines < maxLines) {
                canvas.drawText(line.toString(), x, y, text);
                y += lineHeight;
            }
            return y;
        }

        private void drawEquipmentIcon(Canvas canvas, float cx, float cy, int type) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2f * s);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setColor(Color.rgb(178, 184, 189));
            float z = 18f * s;
            if (type == InventoryContract.EQUIP_HEAD) {
                canvas.drawArc(new RectF(cx - z, cy - z * 0.7f, cx + z, cy + z * 0.9f),
                        200, 140, false, stroke);
                canvas.drawLine(cx - z, cy + z * 0.45f, cx + z, cy + z * 0.45f, stroke);
            } else if (type == InventoryContract.EQUIP_FACE) {
                canvas.drawCircle(cx - z * 0.55f, cy, z * 0.45f, stroke);
                canvas.drawCircle(cx + z * 0.55f, cy, z * 0.45f, stroke);
                canvas.drawLine(cx - z * 0.1f, cy, cx + z * 0.1f, cy, stroke);
            } else if (type == InventoryContract.EQUIP_TORSO) {
                path.reset();
                path.moveTo(cx - z, cy - z * 0.7f);
                path.lineTo(cx - z * 0.45f, cy - z);
                path.lineTo(cx, cy - z * 0.55f);
                path.lineTo(cx + z * 0.45f, cy - z);
                path.lineTo(cx + z, cy - z * 0.7f);
                path.lineTo(cx + z * 0.65f, cy + z);
                path.lineTo(cx - z * 0.65f, cy + z);
                path.close();
                canvas.drawPath(path, stroke);
            } else if (type == InventoryContract.EQUIP_HANDS) {
                canvas.drawCircle(cx, cy, z * 0.52f, stroke);
                for (int i = -2; i <= 2; i++) {
                    canvas.drawLine(cx + i * 4f * s, cy - z * 0.5f,
                            cx + i * 4f * s, cy - z, stroke);
                }
            } else {
                RectF bag = new RectF(cx - z * 0.8f, cy - z * 0.75f,
                        cx + z * 0.8f, cy + z);
                canvas.drawRoundRect(bag, 5f * s, 5f * s, stroke);
                canvas.drawArc(new RectF(cx - z * 0.45f, cy - z * 1.05f,
                        cx + z * 0.45f, cy - z * 0.35f), 180, 180, false, stroke);
            }
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawGenericTechnicalMark(Canvas canvas, float cx, float cy, float size, boolean compact) {
            RectF r = new RectF(cx - size * 0.48f, cy - size * 0.48f,
                    cx + size * 0.48f, cy + size * 0.48f);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2f * s);
            stroke.setColor(Color.rgb(196, 202, 207));
            canvas.drawRoundRect(r, size * 0.12f, size * 0.12f, stroke);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize((compact ? 14f : 18f) * s);
            text.setColor(Color.rgb(209, 214, 218));
            canvas.drawText("ID", cx, cy + 6f * s, text);
        }

        private void drawFoodIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            RectF base = new RectF(cx - size * 0.5f, cy - size * 0.12f,
                    cx + size * 0.5f, cy + size * 0.28f);
            canvas.drawRoundRect(base, 7f * s, 7f * s, fill);
            fill.setColor(Color.argb(220, 255, 120, 36));
            canvas.drawRect(cx - size * 0.42f, cy - size * 0.02f,
                    cx + size * 0.42f, cy + size * 0.08f, fill);
        }

        private void drawDrinkIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            RectF bottle = new RectF(cx - size * 0.24f, cy - size * 0.42f,
                    cx + size * 0.24f, cy + size * 0.48f);
            canvas.drawRoundRect(bottle, 8f * s, 8f * s, fill);
            canvas.drawRect(cx - size * 0.12f, cy - size * 0.58f,
                    cx + size * 0.12f, cy - size * 0.38f, fill);
            fill.setColor(ORANGE);
            canvas.drawRect(cx - size * 0.22f, cy, cx + size * 0.22f, cy + size * 0.09f, fill);
        }

        private void drawMedicalIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(Color.rgb(179, 45, 39));
            RectF kit = new RectF(cx - size * 0.5f, cy - size * 0.35f,
                    cx + size * 0.5f, cy + size * 0.38f);
            canvas.drawRoundRect(kit, 8f * s, 8f * s, fill);
            fill.setColor(WHITE);
            canvas.drawRect(cx - size * 0.09f, cy - size * 0.22f,
                    cx + size * 0.09f, cy + size * 0.25f, fill);
            canvas.drawRect(cx - size * 0.24f, cy - size * 0.07f,
                    cx + size * 0.24f, cy + size * 0.10f, fill);
        }

        private void drawToolIcon(Canvas canvas, float cx, float cy, float size, int color) {
            stroke.setColor(color);
            stroke.setStrokeWidth(size * 0.12f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx - size * 0.32f, cy + size * 0.32f,
                    cx + size * 0.25f, cy - size * 0.25f, stroke);
            canvas.drawCircle(cx + size * 0.31f, cy - size * 0.31f, size * 0.18f, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawClothingIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            path.reset();
            path.moveTo(cx - size * 0.48f, cy - size * 0.30f);
            path.lineTo(cx - size * 0.18f, cy - size * 0.48f);
            path.lineTo(cx, cy - size * 0.25f);
            path.lineTo(cx + size * 0.18f, cy - size * 0.48f);
            path.lineTo(cx + size * 0.48f, cy - size * 0.30f);
            path.lineTo(cx + size * 0.32f, cy + size * 0.48f);
            path.lineTo(cx - size * 0.32f, cy + size * 0.48f);
            path.close();
            canvas.drawPath(path, fill);
        }

        private void drawAccessoryIcon(Canvas canvas, float cx, float cy, float size, int color) {
            stroke.setColor(color);
            stroke.setStrokeWidth(size * 0.10f);
            canvas.drawCircle(cx, cy, size * 0.34f, stroke);
            fill.setColor(ORANGE);
            canvas.drawCircle(cx + size * 0.28f, cy - size * 0.28f, size * 0.11f, fill);
        }

        private void drawKeyIcon(Canvas canvas, float cx, float cy, float size, int color) {
            stroke.setColor(color);
            stroke.setStrokeWidth(size * 0.10f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawCircle(cx - size * 0.22f, cy - size * 0.12f, size * 0.18f, stroke);
            canvas.drawLine(cx - size * 0.08f, cy + size * 0.02f,
                    cx + size * 0.36f, cy + size * 0.42f, stroke);
            canvas.drawLine(cx + size * 0.17f, cy + size * 0.25f,
                    cx + size * 0.30f, cy + size * 0.13f, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawResourceIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            float q = size * 0.34f;
            canvas.drawRect(cx - q, cy - q, cx, cy, fill);
            fill.setColor(Color.rgb(172, 179, 184));
            canvas.drawRect(cx + size * 0.04f, cy - q, cx + q + size * 0.04f, cy, fill);
            fill.setColor(Color.rgb(212, 216, 219));
            canvas.drawRect(cx - q * 0.5f, cy + size * 0.04f,
                    cx + q * 0.5f, cy + q + size * 0.04f, fill);
        }

        private void drawElectronicsIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            RectF phone = new RectF(cx - size * 0.28f, cy - size * 0.52f,
                    cx + size * 0.28f, cy + size * 0.52f);
            canvas.drawRoundRect(phone, 8f * s, 8f * s, fill);
            fill.setColor(GRAPHITE);
            canvas.drawRoundRect(new RectF(phone.left + 4f * s, phone.top + 6f * s,
                    phone.right - 4f * s, phone.bottom - 10f * s),
                    4f * s, 4f * s, fill);
            fill.setColor(ORANGE);
            canvas.drawCircle(cx, phone.bottom - 5f * s, 2.5f * s, fill);
        }

        private void drawBackpackIcon(Canvas canvas, float cx, float cy, float size, int color) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3f * s);
            stroke.setColor(color);
            RectF bag = new RectF(cx - size * 0.43f, cy - size * 0.38f,
                    cx + size * 0.43f, cy + size * 0.52f);
            canvas.drawRoundRect(bag, 8f * s, 8f * s, stroke);
            canvas.drawArc(new RectF(cx - size * 0.25f, cy - size * 0.68f,
                    cx + size * 0.25f, cy - size * 0.22f), 180, 180, false, stroke);
            canvas.drawLine(cx - size * 0.25f, cy + size * 0.08f,
                    cx + size * 0.25f, cy + size * 0.08f, stroke);
        }

        private void drawWeightIcon(Canvas canvas, float cx, float cy, float size, int color) {
            fill.setColor(color);
            path.reset();
            path.moveTo(cx - size * 0.45f, cy + size * 0.55f);
            path.lineTo(cx - size * 0.32f, cy - size * 0.15f);
            path.lineTo(cx + size * 0.32f, cy - size * 0.15f);
            path.lineTo(cx + size * 0.45f, cy + size * 0.55f);
            path.close();
            canvas.drawPath(path, fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2f * s);
            stroke.setColor(color);
            canvas.drawArc(new RectF(cx - size * 0.20f, cy - size * 0.52f,
                    cx + size * 0.20f, cy - size * 0.10f), 180, 180, false, stroke);
        }

        private void drawActionIcon(Canvas canvas, float cx, float cy, int type, int color) {
            stroke.setColor(color);
            fill.setColor(color);
            stroke.setStrokeWidth(2f * s);
            if (type == 0) {
                drawKeyIcon(canvas, cx, cy, 22f * s, color);
            } else if (type == 1) {
                canvas.drawCircle(cx, cy - 7f * s, 6f * s, fill);
                RectF body = new RectF(cx - 9f * s, cy + 1f * s,
                        cx + 9f * s, cy + 13f * s);
                canvas.drawRoundRect(body, 5f * s, 5f * s, fill);
            } else {
                RectF bin = new RectF(cx - 7f * s, cy - 5f * s,
                        cx + 7f * s, cy + 11f * s);
                canvas.drawRoundRect(bin, 2f * s, 2f * s, fill);
                canvas.drawRect(cx - 10f * s, cy - 9f * s,
                        cx + 10f * s, cy - 5f * s, fill);
            }
        }

        private String formatKg(int grams) {
            return String.format(Locale.US, "%.1f", Math.max(0, grams) / 1000.0f);
        }

        private String ellipsize(String value, int maxChars) {
            if (value == null) return "";
            if (value.length() <= maxChars) return value;
            return value.substring(0, Math.max(1, maxChars - 1)) + "…";
        }
    }
}
