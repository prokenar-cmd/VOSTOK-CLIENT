#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path('client')
JAVA = ROOT / 'app/src/main/java'

bridge = JAVA / 'com/blackrussia/game/vostok/ui/VostokUiBridge.java'
controller = JAVA / 'com/blackrussia/game/vostok/ui/hud/VostokHudController.java'

# 031G5: donor bhud_layout is kept alive as an object for native callbacks, but its
# visible root must stay hidden. It is the source of the stray yellow disc/weapon/
# old status artwork seen on device. Chat/radar/native controls are independent.
b = bridge.read_text(encoding='utf-8')
old = '''    public void showHud() {\n        runOnUiThread(() -> {\n            hudManager.ShowHud();\n            vostokHud.showHud();\n        });\n    }'''
new = '''    public void showHud() {\n        runOnUiThread(() -> {\n            // 031G5: do not expose donor bhud_layout. Keep the manager alive only\n            // for authoritative telemetry/chat/native compatibility.\n            hudManager.HideHud();\n            vostokHud.showHud();\n        });\n    }'''
if new not in b:
    if b.count(old) != 1:
        raise SystemExit(f'031G5 bridge showHud anchor mismatch: {b.count(old)}')
    b = b.replace(old, new, 1)
bridge.write_text(b, encoding='utf-8')

s = controller.read_text(encoding='utf-8')

# Hard-hide every donor top-left bhud child as an additional regression guard.
old_ids = '''        String[] ids = {\n                "hud_money", "ruble_png", "progress_hp", "progress_armor",\n                "imageView", "imageView2", "imageView3", "imageView4", "imageView6", "imageView7",\n                "hud_menu", "hud_BP", "hud_maga", "hud_yvedomlenia",\n                "hud_camera", "camera_button", "button_camera", "btn_camera"\n        };'''
new_ids = '''        String[] ids = {\n                "hud_main", "hud_money", "ruble_png", "progress_hp", "progress_armor",\n                "hud_health_pb", "hud_armor_pb", "hud_weapon", "linearLayout3",\n                "hud_star_1", "hud_star_2", "hud_star_3", "hud_star_4", "hud_star_5", "hud_star_6",\n                "imageView", "imageView2", "imageView3", "imageView4", "imageView6", "imageView7",\n                "hud_menu", "hud_BP", "hud_maga", "hud_yvedomlenia",\n                "hud_camera", "camera_button", "button_camera", "btn_camera"\n        };'''
if new_ids not in s:
    if s.count(old_ids) != 1:
        raise SystemExit(f'031G5 legacy-id anchor mismatch: {s.count(old_ids)}')
    s = s.replace(old_ids, new_ids, 1)

# Reference-pixel scaling. The approved 031D HUD art is 1640x720; using Android dp
# pushed the custom telemetry hundreds of pixels to the right on the test device.
needle = '''    private int dp(float value) {\n        return Math.round(value * density);\n    }'''
replacement = '''    private float referenceScale() {\n        int w = host.getWidth();\n        int h = host.getHeight();\n        if (w <= 0 || h <= 0) return 1.0f;\n        return Math.min(w / 1640.0f, h / 720.0f);\n    }\n\n    private int ref(float value) {\n        return Math.round(value * referenceScale());\n    }\n\n    private int dp(float value) {\n        return Math.round(value * density);\n    }'''
if 'private float referenceScale()' not in s:
    if s.count(needle) != 1:
        raise SystemExit('031G5 scale anchor missing')
    s = s.replace(needle, replacement, 1)

# Rebuild only the persistent player telemetry around the existing native radar.
# This follows the approved 031D drawing: money cap above the radar, graphite frame,
# vertical HP/armour bars on the left and hunger at the lower-left edge.
pattern = re.compile(r'''        private void drawPlayerTelemetry\(Canvas c\) \{.*?\n        private void drawQuickRail\(Canvas c\) \{''', re.S)
new_block = '''        private void drawPlayerTelemetry(Canvas c) {\n            final float k = referenceScale();\n            final float moneyX = 86f * k;\n            final float moneyY = 18f * k;\n            final float moneyW = 260f * k;\n            final float moneyH = 55f * k;\n\n            fill.setStyle(Paint.Style.FILL);\n            fill.setColor(Color.argb(188, 16, 20, 24));\n            drawCutPanel(c, moneyX, moneyY, moneyW, moneyH, 14f * k, fill);\n\n            stroke.setStyle(Paint.Style.STROKE);\n            stroke.setStrokeWidth(3f * k);\n            stroke.setColor(ORANGE);\n            c.drawLine(moneyX + 8f * k, moneyY + 7f * k, moneyX + 26f * k, moneyY - 7f * k, stroke);\n            c.drawLine(moneyX + moneyW - 26f * k, moneyY + moneyH, moneyX + moneyW - 7f * k, moneyY + moneyH - 18f * k, stroke);\n\n            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);\n            text.setTextAlign(Paint.Align.LEFT);\n            text.setColor(ORANGE);\n            text.setTextSize(24f * k);\n            c.drawText("₽", moneyX + 24f * k, moneyY + 36f * k, text);\n            text.setColor(WHITE);\n            text.setTextSize(22f * k);\n            c.drawText(moneyFormat.format(money), moneyX + 62f * k, moneyY + 36f * k, text);\n\n            // Accent frame follows the native round radar without replacing it.\n            final float radarCx = 300f * k;\n            final float radarCy = 137f * k;\n            final float radarR = 99f * k;\n            arc.set(radarCx - radarR, radarCy - radarR, radarCx + radarR, radarCy + radarR);\n            stroke.setStrokeWidth(3f * k);\n            stroke.setColor(Color.argb(160, 36, 42, 47));\n            c.drawArc(arc, 132, 274, false, stroke);\n            stroke.setColor(ORANGE);\n            c.drawArc(arc, 205, 41, false, stroke);\n            c.drawArc(arc, 318, 28, false, stroke);\n\n            // Graphite side spine from the approved 031D HUD.\n            fill.setColor(Color.argb(178, 18, 22, 26));\n            path.reset();\n            path.moveTo(42f * k, 57f * k);\n            path.lineTo(79f * k, 38f * k);\n            path.lineTo(116f * k, 67f * k);\n            path.lineTo(91f * k, 111f * k);\n            path.lineTo(91f * k, 232f * k);\n            path.lineTo(126f * k, 285f * k);\n            path.lineTo(97f * k, 310f * k);\n            path.lineTo(51f * k, 263f * k);\n            path.close();\n            c.drawPath(path, fill);\n\n            stroke.setStrokeWidth(3f * k);\n            stroke.setColor(ORANGE);\n            c.drawLine(48f * k, 61f * k, 62f * k, 45f * k, stroke);\n            c.drawLine(54f * k, 255f * k, 73f * k, 278f * k, stroke);\n            c.drawLine(111f * k, 294f * k, 150f * k, 294f * k, stroke);\n\n            drawVerticalStat(c, 67f * k, 113f * k, 54f * k, health, 0, k);\n            drawVerticalStat(c, 67f * k, 177f * k, 48f * k, armour, 1, k);\n            drawHungerStat(c, 105f * k, 278f * k, hunger, k);\n        }\n\n        private void drawVerticalStat(Canvas c, float x, float y, float length, int value, int kind, float k) {\n            drawReferenceStatusIcon(c, x, y - 15f * k, kind, k);\n            stroke.setStrokeWidth(7f * k);\n            stroke.setStrokeCap(Paint.Cap.ROUND);\n            stroke.setColor(Color.argb(120, 210, 216, 220));\n            c.drawLine(x, y, x, y + length, stroke);\n            stroke.setColor(kind == 0 ? Color.rgb(255, 55, 55) : WHITE);\n            float filled = length * (Math.max(0, Math.min(100, value)) / 100f);\n            c.drawLine(x, y + length, x, y + length - filled, stroke);\n            stroke.setStrokeCap(Paint.Cap.SQUARE);\n        }\n\n        private void drawHungerStat(Canvas c, float x, float y, int value, float k) {\n            drawReferenceStatusIcon(c, x, y, 2, k);\n            float sx = x + 25f * k;\n            float ex = sx + 63f * k;\n            stroke.setStrokeWidth(6f * k);\n            stroke.setStrokeCap(Paint.Cap.ROUND);\n            stroke.setColor(Color.argb(105, 205, 212, 216));\n            c.drawLine(sx, y, ex, y, stroke);\n            stroke.setColor(ORANGE);\n            c.drawLine(sx, y, sx + (ex - sx) * (Math.max(0, Math.min(100, value)) / 100f), y, stroke);\n            stroke.setStrokeCap(Paint.Cap.SQUARE);\n        }\n\n        private void drawReferenceStatusIcon(Canvas c, float cx, float cy, int kind, float k) {\n            if (kind == 0) {\n                fill.setColor(Color.rgb(255, 58, 58));\n                path.reset();\n                path.moveTo(cx, cy + 9f * k);\n                path.cubicTo(cx - 17f * k, cy - 2f * k, cx - 14f * k, cy - 17f * k, cx - 4f * k, cy - 15f * k);\n                path.cubicTo(cx, cy - 14f * k, cx, cy - 9f * k, cx, cy - 9f * k);\n                path.cubicTo(cx, cy - 9f * k, cx + 1f * k, cy - 14f * k, cx + 7f * k, cy - 15f * k);\n                path.cubicTo(cx + 18f * k, cy - 17f * k, cx + 19f * k, cy - 1f * k, cx, cy + 9f * k);\n                path.close();\n                c.drawPath(path, fill);\n            } else if (kind == 1) {\n                stroke.setStyle(Paint.Style.STROKE);\n                stroke.setStrokeWidth(3f * k);\n                stroke.setColor(WHITE);\n                path.reset();\n                path.moveTo(cx, cy - 14f * k);\n                path.lineTo(cx + 13f * k, cy - 8f * k);\n                path.lineTo(cx + 10f * k, cy + 8f * k);\n                path.lineTo(cx, cy + 15f * k);\n                path.lineTo(cx - 10f * k, cy + 8f * k);\n                path.lineTo(cx - 13f * k, cy - 8f * k);\n                path.close();\n                c.drawPath(path, stroke);\n            } else {\n                stroke.setStyle(Paint.Style.STROKE);\n                stroke.setStrokeWidth(3f * k);\n                stroke.setColor(ORANGE);\n                c.drawLine(cx - 5f * k, cy - 12f * k, cx - 5f * k, cy + 12f * k, stroke);\n                c.drawLine(cx - 12f * k, cy - 12f * k, cx - 12f * k, cy - 2f * k, stroke);\n                c.drawLine(cx - 8f * k, cy - 12f * k, cx - 8f * k, cy - 2f * k, stroke);\n                c.drawLine(cx - 12f * k, cy - 2f * k, cx - 5f * k, cy - 2f * k, stroke);\n                c.drawLine(cx + 7f * k, cy - 12f * k, cx + 7f * k, cy + 12f * k, stroke);\n                c.drawArc(new RectF(cx + 2f * k, cy - 12f * k, cx + 12f * k, cy - 1f * k), 180, 180, false, stroke);\n            }\n        }\n\n        private void drawQuickRail(Canvas c) {'''
s, count = pattern.subn(new_block, s, count=1)
if count != 1:
    raise SystemExit(f'031G5 player telemetry block mismatch: {count}')

# Shop icon must visually be a cart, inventory icon a backpack/bag, matching the
# approved 031D reference instead of the temporary lock/cube drawings.
old_icons = '''            } else if (kind == 1) {\n                c.drawRect(cx - dp(7), cy - dp(3), cx + dp(7), cy + dp(8), paint);\n                c.drawArc(new RectF(cx - dp(4), cy - dp(8), cx + dp(4), cy), 180, 180, false, paint);\n            } else if (kind == 2) {\n                path.reset();\n                path.moveTo(cx, cy - dp(8));\n                path.lineTo(cx + dp(8), cy - dp(4));\n                path.lineTo(cx + dp(8), cy + dp(5));\n                path.lineTo(cx, cy + dp(9));\n                path.lineTo(cx - dp(8), cy + dp(5));\n                path.lineTo(cx - dp(8), cy - dp(4));\n                path.close();\n                c.drawPath(path, paint);\n                c.drawLine(cx, cy, cx, cy + dp(9), paint);\n                c.drawLine(cx - dp(8), cy - dp(4), cx, cy, paint);\n                c.drawLine(cx + dp(8), cy - dp(4), cx, cy, paint);'''
new_icons = '''            } else if (kind == 1) {\n                // Shop: trolley.\n                c.drawLine(cx - dp(9), cy - dp(8), cx - dp(6), cy - dp(8), paint);\n                c.drawLine(cx - dp(6), cy - dp(8), cx - dp(3), cy + dp(5), paint);\n                c.drawLine(cx - dp(3), cy + dp(5), cx + dp(8), cy + dp(5), paint);\n                c.drawLine(cx - dp(4), cy - dp(4), cx + dp(9), cy - dp(4), paint);\n                c.drawLine(cx + dp(9), cy - dp(4), cx + dp(6), cy + dp(2), paint);\n                c.drawCircle(cx - dp(1), cy + dp(9), dp(1.6f), paint);\n                c.drawCircle(cx + dp(7), cy + dp(9), dp(1.6f), paint);\n            } else if (kind == 2) {\n                // Inventory: compact backpack.\n                c.drawRoundRect(new RectF(cx - dp(7), cy - dp(5), cx + dp(7), cy + dp(9)), dp(2), dp(2), paint);\n                c.drawArc(new RectF(cx - dp(4), cy - dp(10), cx + dp(4), cy - dp(1)), 180, 180, false, paint);\n                c.drawLine(cx - dp(4), cy + dp(1), cx + dp(4), cy + dp(1), paint);\n                c.drawCircle(cx, cy + dp(5), dp(1), paint);'''
if new_icons not in s:
    if s.count(old_icons) != 1:
        raise SystemExit(f'031G5 quick icon anchor mismatch: {s.count(old_icons)}')
    s = s.replace(old_icons, new_icons, 1)

# Guard that 031D vehicle HUD, quest HUD and all later systems remain present.
for needle in (
    'drawVehicleHud(c)',
    'drawQuest(c)',
    'VOSTOK HUD requires android.R.id.content ViewGroup',
    'private float referenceScale()',
    '"hud_main", "hud_money"',
    'Shop: trolley',
    'Inventory: compact backpack',
):
    if needle not in s:
        raise SystemExit(f'031G5 verification missing: {needle}')

controller.write_text(s, encoding='utf-8')

print('Applied VOSTOK Candidate 031G5 HUD restore to approved 031D reference')
