#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path('client')
HUD = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/hud/VostokHudController.java'
EDITOR = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/hud/VostokHudEditorView.java'

s = HUD.read_text(encoding='utf-8')

if 'import com.nvidia.devtech.NvEventQueueActivity;' not in s:
    s = s.replace('import android.widget.Toast;\n', 'import android.widget.Toast;\n\nimport com.nvidia.devtech.NvEventQueueActivity;\n', 1)

# Native radar calibration state. We keep explicit coordinates instead of reading
# the native values on every ACTION_MOVE; some donor builds return stale/default
# values while the renderer is already using the just-applied position.
field_anchor = '    private int[] editorFistStartScale;\n'
field_repl = '''    private int[] editorFistStartScale;\n    private int editorRadarX = Integer.MIN_VALUE;\n    private int editorRadarY = Integer.MIN_VALUE;\n    private int editorRadarScaleX = Integer.MIN_VALUE;\n    private int editorRadarScaleY = Integer.MIN_VALUE;\n'''
if 'private int editorRadarX' not in s:
    if field_anchor not in s:
        raise SystemExit('031H1 radar state anchor missing')
    s = s.replace(field_anchor, field_repl, 1)

# Completely remove donor/native fist and ammo HUD. We deliberately move these
# real native elements far outside the viewport instead of drawing over them.
show_old = '''    public void showHud() {\n        hudVisible = true;\n        hideLegacyHudElements();\n        refreshVisibility();\n    }'''
show_new = '''    public void showHud() {\n        hudVisible = true;\n        hideLegacyHudElements();\n        hideNativeWeaponHud();\n        refreshVisibility();\n    }'''
if show_new not in s:
    if show_old not in s:
        raise SystemExit('031H1 showHud anchor missing')
    s = s.replace(show_old, show_new, 1)

# Replace editor native access with direct calls to the real NvEventQueueActivity
# API. This avoids reflection ambiguity and makes radar drag/scale authoritative.
start = s.find('    private void captureNativeEditorDefaults() {')
end = s.find('    private int valueAt(', start)
if start < 0 or end < 0:
    raise SystemExit('031H1 native editor methods block not found')
new_native = r'''    private void captureNativeEditorDefaults() {
        int[] pos = getNativeArray("getNativeHudElementPosition", 6);
        int[] scale = getNativeArray("getNativeHudElementScale", 6);
        editorRadarStartPos = pos == null ? null : pos.clone();
        editorRadarStartScale = scale == null ? null : scale.clone();
        if (pos != null && pos.length >= 2) {
            editorRadarX = pos[0] > 5 ? pos[0] : 170;
            editorRadarY = pos[1] > 5 ? pos[1] : 75;
        } else {
            editorRadarX = 170;
            editorRadarY = 75;
        }
        if (scale != null && scale.length >= 2) {
            editorRadarScaleX = scale[0] > 5 ? scale[0] : 210;
            editorRadarScaleY = scale[1] > 5 ? scale[1] : 210;
        } else {
            editorRadarScaleX = 210;
            editorRadarScaleY = 210;
        }
        applyRadarEditorState();
        hideNativeWeaponHud();
    }

    private int[] getNativeArray(String methodName, int id) {
        try {
            if (!(activity instanceof NvEventQueueActivity)) return null;
            NvEventQueueActivity nativeActivity = (NvEventQueueActivity) activity;
            if ("getNativeHudElementPosition".equals(methodName)) {
                int[] values = nativeActivity.getNativeHudElementPosition(id);
                return values == null ? null : values.clone();
            }
            if ("getNativeHudElementScale".equals(methodName)) {
                int[] values = nativeActivity.getNativeHudElementScale(id);
                return values == null ? null : values.clone();
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private void moveNativeHudElement(int id, int dx, int dy) {
        if (id != 6) return;
        if (editorRadarX == Integer.MIN_VALUE || editorRadarY == Integer.MIN_VALUE) {
            captureNativeEditorDefaults();
        }
        editorRadarX = Math.max(6, editorRadarX + dx);
        editorRadarY = Math.max(6, editorRadarY + dy);
        applyRadarEditorState();
    }

    private void scaleNativeHudElement(int id, int delta) {
        if (id != 6) return;
        if (editorRadarScaleX == Integer.MIN_VALUE || editorRadarScaleY == Integer.MIN_VALUE) {
            captureNativeEditorDefaults();
        }
        editorRadarScaleX = Math.max(40, Math.min(420, editorRadarScaleX + delta));
        editorRadarScaleY = Math.max(40, Math.min(420, editorRadarScaleY + delta));
        applyRadarEditorState();
    }

    private void applyRadarEditorState() {
        try {
            NvEventQueueActivity.setNativeHudElementPosition(6, editorRadarX, editorRadarY);
            NvEventQueueActivity.setNativeHudElementScale(6, editorRadarScaleX, editorRadarScaleY);
            canvas.invalidate();
        } catch (Throwable ignored) { }
    }

    private void restoreNative(int id, int[] pos, int[] scale) {
        if (id != 6) return;
        try {
            if (pos != null && pos.length >= 2) {
                editorRadarX = pos[0] > 5 ? pos[0] : 170;
                editorRadarY = pos[1] > 5 ? pos[1] : 75;
            }
            if (scale != null && scale.length >= 2) {
                editorRadarScaleX = scale[0] > 5 ? scale[0] : 210;
                editorRadarScaleY = scale[1] > 5 ? scale[1] : 210;
            }
            applyRadarEditorState();
        } catch (Throwable ignored) { }
    }

    private void hideNativeWeaponHud() {
        Runnable hide = () -> {
            try {
                NvEventQueueActivity.setNativeHudElementPosition(8, 5000, 5000);
                NvEventQueueActivity.setNativeHudElementPosition(9, 5000, 5000);
                NvEventQueueActivity.setNativeHudElementScale(8, 6, 6);
                NvEventQueueActivity.setNativeHudElementScale(9, 6, 6);
            } catch (Throwable ignored) { }
        };
        hide.run();
        host.postDelayed(hide, 250L);
        host.postDelayed(hide, 900L);
    }

'''
s = s[:start] + new_native + s[end:]

# Radar description should use the explicit calibration state, not a possibly
# stale native getter. Fist is no longer a user-editable target.
s = s.replace('''        int nativeId = target == VostokHudEditorView.TARGET_RADAR ? 6 : 8;\n        int[] pos = getNativeArray("getNativeHudElementPosition", nativeId);\n        int[] scale = getNativeArray("getNativeHudElementScale", nativeId);\n        String name = target == VostokHudEditorView.TARGET_RADAR ? "РАДАР" : "КУЛАК";\n        return name + "  x " + valueAt(pos, 0) + "  y " + valueAt(pos, 1) + "  scale " + valueAt(scale, 0);''', '''        if (target == VostokHudEditorView.TARGET_RADAR) {\n            return "РАДАР  x " + editorRadarX + "  y " + editorRadarY + "  scale " + editorRadarScaleX;\n        }\n        return "HUD";''')

# Persist calibrated radar explicitly and re-apply after save.
s = s.replace('''                .putFloat("quest_x", questOffsetX).putFloat("quest_y", questOffsetY)\n                .apply();''', '''                .putFloat("quest_x", questOffsetX).putFloat("quest_y", questOffsetY)\n                .putInt("radar_x", editorRadarX).putInt("radar_y", editorRadarY)\n                .putInt("radar_sx", editorRadarScaleX).putInt("radar_sy", editorRadarScaleY)\n                .apply();\n        applyRadarEditorState();\n        hideNativeWeaponHud();''')

# Load saved radar calibration at startup if present.
ctor_marker = '        questOffsetY = hudPrefs.getFloat("quest_y", 0f);\n'
ctor_add = '''        questOffsetY = hudPrefs.getFloat("quest_y", 0f);\n        editorRadarX = hudPrefs.getInt("radar_x", Integer.MIN_VALUE);\n        editorRadarY = hudPrefs.getInt("radar_y", Integer.MIN_VALUE);\n        editorRadarScaleX = hudPrefs.getInt("radar_sx", Integer.MIN_VALUE);\n        editorRadarScaleY = hudPrefs.getInt("radar_sy", Integer.MIN_VALUE);\n'''
if ctor_add not in s:
    if ctor_marker not in s:
        raise SystemExit('031H1 constructor prefs anchor missing')
    s = s.replace(ctor_marker, ctor_add, 1)

# If saved calibration exists, apply it after host is attached.
layout_listener = '        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutQuickButtons());\n'
layout_repl = '''        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {\n            layoutQuickButtons();\n            if (editorRadarX != Integer.MIN_VALUE && editorRadarY != Integer.MIN_VALUE\n                    && editorRadarScaleX != Integer.MIN_VALUE && editorRadarScaleY != Integer.MIN_VALUE) {\n                applyRadarEditorState();\n            }\n            hideNativeWeaponHud();\n        });\n'''
if layout_repl not in s:
    if layout_listener not in s:
        raise SystemExit('031H1 layout listener anchor missing')
    s = s.replace(layout_listener, layout_repl, 1)

# Redraw the left HUD as one coherent VOSTOK component. No donor PNG pieces,
# no fake joystick, no fake minimap. The real minimap remains visible through
# the open circular area and can be calibrated independently in HUD EDITOR.
pattern = re.compile(r'        private void drawPlayerTelemetry\(Canvas c\) \{.*?\n        \}\n\n        private void draw', re.S)
m = pattern.search(s)
if not m:
    raise SystemExit('031H1 telemetry method not found')
new_telemetry = r'''        private void drawPlayerTelemetry(Canvas c) {
            final float k = referenceScale();
            final float left = 22f * k;
            final float top = 14f * k;
            final float radarCx = 214f * k;
            final float radarCy = 154f * k;
            final float radarR = 104f * k;

            // Money cap: compact, aligned to the upper edge of the minimap.
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(220, 12, 15, 19));
            path.reset();
            path.moveTo(118f * k, top);
            path.lineTo(302f * k, top);
            path.lineTo(322f * k, 31f * k);
            path.lineTo(310f * k, 56f * k);
            path.lineTo(130f * k, 56f * k);
            path.lineTo(108f * k, 38f * k);
            path.close();
            c.drawPath(path, fill);

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(2f, 2.2f * k));
            stroke.setColor(ORANGE);
            c.drawLine(132f * k, 56f * k, 188f * k, 56f * k, stroke);

            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(22f * k);
            text.setColor(WHITE);
            c.drawText(moneyFormat.format(money), 286f * k, 43f * k, text);
            text.setTextSize(14f * k);
            text.setColor(ORANGE);
            c.drawText("₽", 306f * k, 43f * k, text);

            // One graphite spine to the left of the real radar.
            fill.setColor(Color.argb(208, 12, 15, 19));
            path.reset();
            path.moveTo(left + 22f * k, 58f * k);
            path.lineTo(left + 70f * k, 58f * k);
            path.lineTo(left + 96f * k, 83f * k);
            path.lineTo(left + 80f * k, 250f * k);
            path.lineTo(left + 108f * k, 286f * k);
            path.lineTo(left + 77f * k, 316f * k);
            path.lineTo(left + 22f * k, 270f * k);
            path.close();
            c.drawPath(path, fill);

            // Clean orange/steel frame accents around the minimap opening.
            stroke.setStrokeWidth(Math.max(2f, 2.0f * k));
            stroke.setColor(Color.argb(220, 170, 178, 185));
            arc.set(radarCx - radarR, radarCy - radarR, radarCx + radarR, radarCy + radarR);
            c.drawArc(arc, -76f, 222f, false, stroke);
            stroke.setStrokeWidth(Math.max(3f, 3.0f * k));
            stroke.setColor(ORANGE);
            c.drawArc(arc, -74f, 38f, false, stroke);
            c.drawArc(arc, 132f, 34f, false, stroke);
            c.drawLine(47f * k, 64f * k, 64f * k, 47f * k, stroke);
            c.drawLine(47f * k, 260f * k, 68f * k, 283f * k, stroke);
            c.drawLine(94f * k, 300f * k, 137f * k, 300f * k, stroke);

            drawVerticalStatClean(c, 48f * k, 105f * k, 58f * k, health, 0, k);
            drawVerticalStatClean(c, 48f * k, 178f * k, 52f * k, armour, 1, k);
            drawHungerClean(c, 78f * k, 275f * k, hunger, k);
        }

        private void drawVerticalStatClean(Canvas c, float x, float y, float h, int value, int icon, float k) {
            float barX = x + 18f * k;
            float top = y;
            float bottom = y + h;
            stroke.setStrokeWidth(Math.max(3f, 3f * k));
            stroke.setStrokeColor(Color.argb(90, 195, 202, 208));
            c.drawLine(barX, top, barX, bottom, stroke);
            stroke.setColor(icon == 0 ? Color.rgb(238, 68, 61) : Color.rgb(232, 236, 239));
            float filled = h * (Math.max(0, Math.min(100, value)) / 100f);
            c.drawLine(barX, bottom, barX, bottom - filled, stroke);

            stroke.setStrokeWidth(Math.max(2f, 2f * k));
            if (icon == 0) {
                stroke.setColor(Color.rgb(238, 68, 61));
                path.reset();
                path.moveTo(x, y - 9f * k);
                path.cubicTo(x - 7f * k, y - 17f * k, x - 16f * k, y - 5f * k, x, y + 8f * k);
                path.cubicTo(x + 16f * k, y - 5f * k, x + 7f * k, y - 17f * k, x, y - 9f * k);
                c.drawPath(path, stroke);
            } else {
                stroke.setColor(WHITE);
                path.reset();
                path.moveTo(x, y - 13f * k);
                path.lineTo(x + 10f * k, y - 8f * k);
                path.lineTo(x + 8f * k, y + 5f * k);
                path.lineTo(x, y + 12f * k);
                path.lineTo(x - 8f * k, y + 5f * k);
                path.lineTo(x - 10f * k, y - 8f * k);
                path.close();
                c.drawPath(path, stroke);
            }
        }

        private void drawHungerClean(Canvas c, float x, float y, int value, float k) {
            stroke.setStrokeWidth(Math.max(2f, 2f * k));
            stroke.setColor(ORANGE);
            c.drawLine(x - 9f * k, y - 12f * k, x - 9f * k, y + 10f * k, stroke);
            c.drawLine(x - 14f * k, y - 12f * k, x - 14f * k, y - 2f * k, stroke);
            c.drawLine(x - 4f * k, y - 12f * k, x - 4f * k, y - 2f * k, stroke);
            c.drawLine(x + 7f * k, y - 12f * k, x + 7f * k, y + 10f * k, stroke);
            c.drawLine(x + 7f * k, y - 12f * k, x + 14f * k, y - 7f * k, stroke);

            float bx = x + 24f * k;
            float bw = 62f * k;
            stroke.setStrokeWidth(Math.max(4f, 4f * k));
            stroke.setColor(Color.argb(100, 195, 202, 208));
            c.drawLine(bx, y, bx + bw, y, stroke);
            stroke.setColor(ORANGE);
            c.drawLine(bx, y, bx + bw * (Math.max(0, Math.min(100, value)) / 100f), y, stroke);
        }

        private void draw'''
s = s[:m.start()] + new_telemetry + s[m.end():]

HUD.write_text(s, encoding='utf-8')

# Remove fist tab entirely from editor: only left HUD, radar, top and quest remain.
e = EDITOR.read_text(encoding='utf-8')
e = e.replace('    public static final int TARGET_FIST = 4;\n', '')
e = e.replace('final String[] names = {"ЛЕВЫЙ HUD", "РАДАР", "ВЕРХ", "ЗАДАНИЕ", "КУЛАК"};', 'final String[] names = {"ЛЕВЫЙ HUD", "РАДАР", "ВЕРХ", "ЗАДАНИЕ"};')
e = e.replace('float tabW = (w - pad * 2f) / 5f;', 'float tabW = (w - pad * 2f) / 4f;')
e = e.replace('if (next >= 0 && next < 5)', 'if (next >= 0 && next < 4)')
EDITOR.write_text(e, encoding='utf-8')

check = HUD.read_text(encoding='utf-8')
for required in (
    'hideNativeWeaponHud();',
    'NvEventQueueActivity.setNativeHudElementPosition(6, editorRadarX, editorRadarY);',
    'NvEventQueueActivity.setNativeHudElementPosition(8, 5000, 5000);',
    'drawVerticalStatClean(',
    'drawHungerClean(',
    'putInt("radar_x", editorRadarX)',
):
    if required not in check:
        raise SystemExit('031H1 verification missing: ' + required)
if 'TARGET_FIST' in EDITOR.read_text(encoding='utf-8'):
    raise SystemExit('031H1 regression: fist tab still present')

print('Applied VOSTOK Candidate 031H1 HUD calibration')
