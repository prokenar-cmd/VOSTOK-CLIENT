#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('client')
controller = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/hud/VostokHudController.java'
s = controller.read_text(encoding='utf-8')

# Imports.
if 'import android.content.SharedPreferences;' not in s:
    s = s.replace('import android.app.Activity;\n', 'import android.app.Activity;\nimport android.content.SharedPreferences;\n', 1)
if 'import android.widget.Toast;' not in s:
    s = s.replace('import android.widget.FrameLayout;\n', 'import android.widget.FrameLayout;\nimport android.widget.Toast;\n', 1)

# State / persisted editor layout.
field_anchor = '''    private final QuickButton[] quickButtons = new QuickButton[4];\n    private final float density;\n\n    private boolean hudVisible;'''
field_repl = '''    private final QuickButton[] quickButtons = new QuickButton[4];\n    private final float density;\n    private final SharedPreferences hudPrefs;\n    private VostokHudEditorView editorView;\n\n    private float leftOffsetX;\n    private float leftOffsetY;\n    private float leftScale = 1.0f;\n    private float quickOffsetX;\n    private float quickOffsetY;\n    private float quickScale = 1.0f;\n    private float questOffsetX;\n    private float questOffsetY;\n    private int[] editorRadarStartPos;\n    private int[] editorRadarStartScale;\n    private int[] editorFistStartPos;\n    private int[] editorFistStartScale;\n\n    private boolean hudVisible;'''
if 'private final SharedPreferences hudPrefs;' not in s:
    if s.count(field_anchor) != 1:
        raise SystemExit('031H fields anchor mismatch')
    s = s.replace(field_anchor, field_repl, 1)

# Load persisted VOSTOK overlay layout. Native elements are persisted by donor settings.
ctor_anchor = '''        this.activity = activity;\n        density = Math.max(1.0f, activity.getResources().getDisplayMetrics().density);\n\n        View content = activity.findViewById(android.R.id.content);'''
ctor_repl = '''        this.activity = activity;\n        density = Math.max(1.0f, activity.getResources().getDisplayMetrics().density);\n        hudPrefs = activity.getSharedPreferences("vostok_hud_editor", Activity.MODE_PRIVATE);\n        leftOffsetX = hudPrefs.getFloat("left_x", 0f);\n        leftOffsetY = hudPrefs.getFloat("left_y", 0f);\n        leftScale = hudPrefs.getFloat("left_scale", 1.0f);\n        quickOffsetX = hudPrefs.getFloat("quick_x", 0f);\n        quickOffsetY = hudPrefs.getFloat("quick_y", 0f);\n        quickScale = hudPrefs.getFloat("quick_scale", 1.0f);\n        questOffsetX = hudPrefs.getFloat("quest_x", 0f);\n        questOffsetY = hudPrefs.getFloat("quest_y", 0f);\n\n        View content = activity.findViewById(android.R.id.content);'''
if 'hudPrefs = activity.getSharedPreferences("vostok_hud_editor"' not in s:
    if s.count(ctor_anchor) != 1:
        raise SystemExit('031H constructor anchor mismatch')
    s = s.replace(ctor_anchor, ctor_repl, 1)

# Long press the VOSTOK menu button to open the dev editor.
loop_old = '''            QuickButton button = new QuickButton(activity, i);\n            button.setOnClickListener(v -> onQuickAction(action));\n            quickButtons[i] = button;\n            host.addView(button);\n        }\n\n        host.addOnLayoutChangeListener'''
loop_new = '''            QuickButton button = new QuickButton(activity, i);\n            button.setOnClickListener(v -> onQuickAction(action));\n            button.setOnLongClickListener(v -> {\n                if (action == 0) {\n                    toggleHudEditor();\n                    return true;\n                }\n                return false;\n            });\n            quickButtons[i] = button;\n            host.addView(button);\n        }\n\n        editorView = new VostokHudEditorView(activity, this);\n        editorView.setVisibility(View.GONE);\n        host.addView(editorView, new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n        ));\n\n        host.addOnLayoutChangeListener'''
if 'editorView = new VostokHudEditorView' not in s:
    if s.count(loop_old) != 1:
        raise SystemExit('031H editor attach anchor mismatch')
    s = s.replace(loop_old, loop_new, 1)

# From this candidate onward, do not silently move the radar every launch. The user calibrates it live.
s = s.replace('        alignNativeHudOnce();\n', '', 1)

# Quick rail uses persisted editor geometry.
quick_old = '''        int size = dp(42);\n        int gap = dp(5);\n        int top = dp(14);\n        int right = dp(18);'''
quick_new = '''        int size = Math.max(dp(28), Math.round(dp(42) * quickScale));\n        int gap = Math.max(dp(2), Math.round(dp(5) * quickScale));\n        int top = dp(14) + ref(quickOffsetY);\n        int right = dp(18) - ref(quickOffsetX);'''
if quick_new not in s:
    if s.count(quick_old) != 1:
        raise SystemExit('031H quick rail anchor mismatch')
    s = s.replace(quick_old, quick_new, 1)

# Apply editable transforms to VOSTOK overlay groups.
draw_old = '''            if (hudVisible) {\n                drawPlayerTelemetry(c);\n                drawQuickRail(c);\n                if (questVisible) drawQuest(c);\n            }'''
draw_new = '''            if (hudVisible) {\n                c.save();\n                c.translate(ref(leftOffsetX), ref(leftOffsetY));\n                c.scale(leftScale, leftScale);\n                drawPlayerTelemetry(c);\n                c.restore();\n\n                drawQuickRail(c);\n\n                if (questVisible) {\n                    c.save();\n                    c.translate(ref(questOffsetX), ref(questOffsetY));\n                    drawQuest(c);\n                    c.restore();\n                }\n            }'''
if draw_new not in s:
    if s.count(draw_old) != 1:
        raise SystemExit('031H draw transform anchor mismatch')
    s = s.replace(draw_old, draw_new, 1)

# Editor API, intentionally kept in controller so it manipulates real runtime elements.
method_anchor = '    private void onQuickAction(int action) {'
methods = r'''    public void toggleHudEditor() {
        if (editorView.getVisibility() == View.VISIBLE) {
            closeHudEditor();
            return;
        }
        captureNativeEditorDefaults();
        editorView.setVisibility(View.VISIBLE);
        editorView.bringToFront();
        editorView.requestFocus();
        Toast.makeText(activity, "HUD EDITOR: выберите элемент и перетащите его", Toast.LENGTH_SHORT).show();
    }

    public void closeHudEditor() {
        if (editorView != null) editorView.setVisibility(View.GONE);
    }

    public void editorMove(int target, float dxPixels, float dyPixels) {
        float k = Math.max(0.01f, referenceScale());
        if (target == VostokHudEditorView.TARGET_LEFT) {
            leftOffsetX += dxPixels / k;
            leftOffsetY += dyPixels / k;
            canvas.invalidate();
        } else if (target == VostokHudEditorView.TARGET_RADAR) {
            moveNativeHudElement(6, Math.round(dxPixels), Math.round(dyPixels));
        } else if (target == VostokHudEditorView.TARGET_TOP) {
            quickOffsetX += dxPixels / k;
            quickOffsetY += dyPixels / k;
            layoutQuickButtons();
        } else if (target == VostokHudEditorView.TARGET_QUEST) {
            questOffsetX += dxPixels / k;
            questOffsetY += dyPixels / k;
            canvas.invalidate();
        } else if (target == VostokHudEditorView.TARGET_FIST) {
            moveNativeHudElement(8, Math.round(dxPixels), Math.round(dyPixels));
        }
    }

    public void editorScale(int target, int direction) {
        if (target == VostokHudEditorView.TARGET_LEFT) {
            leftScale = clampFloat(leftScale + direction * 0.05f, 0.60f, 1.60f);
            canvas.invalidate();
        } else if (target == VostokHudEditorView.TARGET_RADAR) {
            scaleNativeHudElement(6, direction * 5);
        } else if (target == VostokHudEditorView.TARGET_TOP) {
            quickScale = clampFloat(quickScale + direction * 0.05f, 0.65f, 1.50f);
            layoutQuickButtons();
        } else if (target == VostokHudEditorView.TARGET_FIST) {
            scaleNativeHudElement(8, direction * 5);
        }
    }

    public void editorReset(int target) {
        if (target == VostokHudEditorView.TARGET_LEFT) {
            leftOffsetX = 0f; leftOffsetY = 0f; leftScale = 1f; canvas.invalidate();
        } else if (target == VostokHudEditorView.TARGET_RADAR) {
            restoreNative(6, editorRadarStartPos, editorRadarStartScale);
        } else if (target == VostokHudEditorView.TARGET_TOP) {
            quickOffsetX = 0f; quickOffsetY = 0f; quickScale = 1f; layoutQuickButtons();
        } else if (target == VostokHudEditorView.TARGET_QUEST) {
            questOffsetX = 0f; questOffsetY = 0f; canvas.invalidate();
        } else if (target == VostokHudEditorView.TARGET_FIST) {
            restoreNative(8, editorFistStartPos, editorFistStartScale);
        }
    }

    public void editorSave() {
        hudPrefs.edit()
                .putFloat("left_x", leftOffsetX).putFloat("left_y", leftOffsetY).putFloat("left_scale", leftScale)
                .putFloat("quick_x", quickOffsetX).putFloat("quick_y", quickOffsetY).putFloat("quick_scale", quickScale)
                .putFloat("quest_x", questOffsetX).putFloat("quest_y", questOffsetY)
                .apply();
        invoke("onSettingsWindowSave", new Class<?>[0]);
        Toast.makeText(activity, "Положение HUD сохранено", Toast.LENGTH_SHORT).show();
    }

    public String editorDescribe(int target) {
        if (target == VostokHudEditorView.TARGET_LEFT)
            return String.format(Locale.US, "ЛЕВЫЙ HUD  x %.0f  y %.0f  scale %.2f", leftOffsetX, leftOffsetY, leftScale);
        if (target == VostokHudEditorView.TARGET_TOP)
            return String.format(Locale.US, "ВЕРХ  x %.0f  y %.0f  scale %.2f", quickOffsetX, quickOffsetY, quickScale);
        if (target == VostokHudEditorView.TARGET_QUEST)
            return String.format(Locale.US, "ЗАДАНИЕ  x %.0f  y %.0f", questOffsetX, questOffsetY);
        int nativeId = target == VostokHudEditorView.TARGET_RADAR ? 6 : 8;
        int[] pos = getNativeArray("getNativeHudElementPosition", nativeId);
        int[] scale = getNativeArray("getNativeHudElementScale", nativeId);
        String name = target == VostokHudEditorView.TARGET_RADAR ? "РАДАР" : "КУЛАК";
        return name + "  x " + valueAt(pos, 0) + "  y " + valueAt(pos, 1) + "  scale " + valueAt(scale, 0);
    }

    private void captureNativeEditorDefaults() {
        editorRadarStartPos = getNativeArray("getNativeHudElementPosition", 6);
        editorRadarStartScale = getNativeArray("getNativeHudElementScale", 6);
        editorFistStartPos = getNativeArray("getNativeHudElementPosition", 8);
        editorFistStartScale = getNativeArray("getNativeHudElementScale", 8);
    }

    private int[] getNativeArray(String methodName, int id) {
        try {
            Method method = activity.getClass().getMethod(methodName, int.class);
            Object raw = method.invoke(activity, id);
            if (raw instanceof int[]) return ((int[]) raw).clone();
        } catch (Throwable ignored) { }
        return null;
    }

    private void moveNativeHudElement(int id, int dx, int dy) {
        int[] pos = getNativeArray("getNativeHudElementPosition", id);
        if (pos == null || pos.length < 2) return;
        try {
            Method setPos = activity.getClass().getMethod("setNativeHudElementPosition", int.class, int.class, int.class);
            setPos.invoke(null, id, Math.max(0, pos[0] + dx), Math.max(0, pos[1] + dy));
        } catch (Throwable ignored) { }
    }

    private void scaleNativeHudElement(int id, int delta) {
        int[] scale = getNativeArray("getNativeHudElementScale", id);
        if (scale == null || scale.length < 2) return;
        try {
            Method setScale = activity.getClass().getMethod("setNativeHudElementScale", int.class, int.class, int.class);
            setScale.invoke(null, id, Math.max(1, scale[0] + delta), Math.max(1, scale[1] + delta));
        } catch (Throwable ignored) { }
    }

    private void restoreNative(int id, int[] pos, int[] scale) {
        try {
            if (pos != null && pos.length >= 2) {
                Method setPos = activity.getClass().getMethod("setNativeHudElementPosition", int.class, int.class, int.class);
                setPos.invoke(null, id, pos[0], pos[1]);
            }
            if (scale != null && scale.length >= 2) {
                Method setScale = activity.getClass().getMethod("setNativeHudElementScale", int.class, int.class, int.class);
                setScale.invoke(null, id, scale[0], scale[1]);
            }
        } catch (Throwable ignored) { }
    }

    private int valueAt(int[] values, int index) {
        return values != null && values.length > index ? values[index] : -1;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

'''
if 'public void toggleHudEditor()' not in s:
    if s.count(method_anchor) != 1:
        raise SystemExit('031H method anchor mismatch')
    s = s.replace(method_anchor, methods + method_anchor, 1)

# Editor should not be removed by regular HUD hide; it is dev overlay and closes explicitly.
shutdown_old = '''        ViewGroup parent = (ViewGroup) host.getParent();\n        if (parent != null) parent.removeView(host);'''
shutdown_new = '''        if (editorView != null) editorView.setVisibility(View.GONE);\n        ViewGroup parent = (ViewGroup) host.getParent();\n        if (parent != null) parent.removeView(host);'''
if shutdown_new not in s:
    s = s.replace(shutdown_old, shutdown_new, 1)

controller.write_text(s, encoding='utf-8')

check = controller.read_text(encoding='utf-8')
for required in (
    'VostokHudEditorView editorView',
    'toggleHudEditor();',
    'public void editorMove(',
    'public void editorScale(',
    'public void editorSave()',
    'getNativeHudElementPosition',
    'getNativeHudElementScale',
    'c.translate(ref(leftOffsetX), ref(leftOffsetY));',
):
    if required not in check:
        raise SystemExit('031H verification missing: ' + required)
if 'drawJoystickVisual(c);' in check:
    raise SystemExit('031H regression: fake joystick draw call returned')

print('Applied VOSTOK Candidate 031H HUD Editor DEV')
