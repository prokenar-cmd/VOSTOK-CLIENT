#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('client')
JAVA = ROOT / 'app/src/main/java'
controller = JAVA / 'com/blackrussia/game/vostok/ui/hud/VostokHudController.java'

s = controller.read_text(encoding='utf-8')

# 031G5.1: the real device screenshot still showed one native/donor camera view and
# several ancillary donor ids. Hide the Android-side views whenever they exist.
needle = '"hud_camera", "camera_button", "button_camera", "btn_camera"'
replacement = '"hud_camera", "camera_button", "button_camera", "btn_camera", "imageView15", "grzona", "imageView16", "imageView17", "logobr"'
if replacement not in s:
    if s.count(needle) != 1:
        raise SystemExit(f'031G5.1 legacy camera-id anchor mismatch: {s.count(needle)}')
    s = s.replace(needle, replacement, 1)

# The approved 031D reference has a visible passive joystick at bottom-left. The
# actual native touch zone remains authoritative; this is only a non-clickable
# visual surface drawn by the existing HUD canvas.
on_draw_old = '''            if (hudVisible) {\n                drawPlayerTelemetry(c);\n                drawQuickRail(c);\n                if (questVisible) drawQuest(c);\n            }'''
on_draw_new = '''            if (hudVisible) {\n                drawTopLeftCleanup(c);\n                drawPlayerTelemetry(c);\n                drawJoystickVisual(c);\n                drawQuickRail(c);\n                if (questVisible) drawQuest(c);\n            }'''
if on_draw_new not in s:
    if s.count(on_draw_old) != 1:
        raise SystemExit(f'031G5.1 onDraw anchor mismatch: {s.count(on_draw_old)}')
    s = s.replace(on_draw_old, on_draw_new, 1)

anchor = '        private void drawPlayerTelemetry(Canvas c) {'
methods = '''        private void drawTopLeftCleanup(Canvas c) {\n            final float k = referenceScale();\n            // Device smoke of 031G5 exposed a native yellow disc at the extreme\n            // top-left. Cover only that reserved HUD corner and integrate the mask\n            // into the graphite/orange VOSTOK spine instead of painting over game UI.\n            fill.setStyle(Paint.Style.FILL);\n            fill.setColor(Color.argb(245, 13, 16, 19));\n            path.reset();\n            path.moveTo(0f, 0f);\n            path.lineTo(43f * k, 0f);\n            path.lineTo(55f * k, 11f * k);\n            path.lineTo(48f * k, 48f * k);\n            path.lineTo(11f * k, 48f * k);\n            path.lineTo(0f, 37f * k);\n            path.close();\n            c.drawPath(path, fill);\n\n            stroke.setStyle(Paint.Style.STROKE);\n            stroke.setStrokeWidth(3f * k);\n            stroke.setColor(ORANGE);\n            c.drawLine(43f * k, 4f * k, 54f * k, 15f * k, stroke);\n            c.drawLine(12f * k, 47f * k, 28f * k, 47f * k, stroke);\n        }\n\n        private void drawJoystickVisual(Canvas c) {\n            final float k = referenceScale();\n            final float cx = 108f * k;\n            final float cy = getHeight() - 96f * k;\n            final float radius = 76f * k;\n\n            fill.setStyle(Paint.Style.FILL);\n            fill.setColor(Color.argb(38, 18, 22, 25));\n            c.drawCircle(cx, cy, radius, fill);\n\n            stroke.setStyle(Paint.Style.STROKE);\n            stroke.setStrokeWidth(2f * k);\n            stroke.setColor(Color.argb(120, 185, 193, 198));\n            c.drawCircle(cx, cy, radius - 2f * k, stroke);\n\n            arc.set(cx - radius, cy - radius, cx + radius, cy + radius);\n            stroke.setStrokeWidth(3f * k);\n            stroke.setColor(ORANGE);\n            c.drawArc(arc, 155, 28, false, stroke);\n            c.drawArc(arc, 337, 26, false, stroke);\n\n            fill.setColor(Color.argb(72, 178, 184, 188));\n            c.drawCircle(cx, cy, 25f * k, fill);\n\n            stroke.setStrokeWidth(6f * k);\n            stroke.setStrokeCap(Paint.Cap.SQUARE);\n            stroke.setColor(Color.argb(120, 205, 211, 215));\n            c.drawLine(cx - 51f * k, cy, cx - 37f * k, cy - 14f * k, stroke);\n            c.drawLine(cx - 51f * k, cy, cx - 37f * k, cy + 14f * k, stroke);\n            c.drawLine(cx + 51f * k, cy, cx + 37f * k, cy - 14f * k, stroke);\n            c.drawLine(cx + 51f * k, cy, cx + 37f * k, cy + 14f * k, stroke);\n            stroke.setStrokeCap(Paint.Cap.SQUARE);\n\n            fill.setColor(ORANGE);\n            path.reset();\n            path.moveTo(cx, cy - radius + 8f * k);\n            path.lineTo(cx - 8f * k, cy - radius + 18f * k);\n            path.lineTo(cx + 8f * k, cy - radius + 18f * k);\n            path.close();\n            c.drawPath(path, fill);\n            path.reset();\n            path.moveTo(cx, cy + radius - 8f * k);\n            path.lineTo(cx - 8f * k, cy + radius - 18f * k);\n            path.lineTo(cx + 8f * k, cy + radius - 18f * k);\n            path.close();\n            c.drawPath(path, fill);\n        }\n\n'''
if 'private void drawJoystickVisual(Canvas c)' not in s:
    if s.count(anchor) != 1:
        raise SystemExit(f'031G5.1 telemetry method anchor mismatch: {s.count(anchor)}')
    s = s.replace(anchor, methods + anchor, 1)

# The first device pass made the hunger tail too long. Match the compact 031D rail.
s = s.replace('float ex = sx + 63f * k;', 'float ex = sx + 46f * k;', 1)

controller.write_text(s, encoding='utf-8')

check = controller.read_text(encoding='utf-8')
for required in (
    'drawTopLeftCleanup(c);',
    'drawJoystickVisual(c);',
    '"imageView15"',
    'float ex = sx + 46f * k;',
    'private float referenceScale()',
    'drawVehicleHud(c)',
    'drawQuest(c)',
):
    if required not in check:
        raise SystemExit(f'031G5.1 verification missing: {required}')

print('Applied VOSTOK Candidate 031G5.1 HUD polish')
