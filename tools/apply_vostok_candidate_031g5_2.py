#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('client')
JAVA = ROOT / 'app/src/main/java'
controller = JAVA / 'com/blackrussia/game/vostok/ui/hud/VostokHudController.java'

s = controller.read_text(encoding='utf-8')

# 031G5.2: remove the decorative joystick completely. It was intentionally
# non-interactive and sat over the authoritative GTA touch controls on device.
s = s.replace('                drawTopLeftCleanup(c);\n', '', 1)
s = s.replace('                drawJoystickVisual(c);\n', '', 1)

# Apply the native HUD cleanup once per runtime session: keep the authoritative
# minimap, but move it left to the approved 031D composition and hide the old
# native fist/ammo HUD artwork that leaked into the top-left corner. This uses
# the existing native settings bridge; libsamp itself is not modified.
field_anchor = '    private boolean hudVisible;\n    private boolean vehicleVisible;\n'
field_repl = '    private boolean hudVisible;\n    private boolean vehicleVisible;\n    private boolean nativeHudAligned;\n'
if 'private boolean nativeHudAligned;' not in s:
    if s.count(field_anchor) != 1:
        raise SystemExit('031G5.2 state anchor mismatch')
    s = s.replace(field_anchor, field_repl, 1)

show_old = '''    public void showHud() {\n        hudVisible = true;\n        hideLegacyHudElements();\n        refreshVisibility();\n    }'''
show_new = '''    public void showHud() {\n        hudVisible = true;\n        hideLegacyHudElements();\n        alignNativeHudOnce();\n        refreshVisibility();\n    }'''
if show_new not in s:
    if s.count(show_old) != 1:
        raise SystemExit('031G5.2 showHud anchor mismatch')
    s = s.replace(show_old, show_new, 1)

method_anchor = '    public void shutdown() {'
method = '''    private void alignNativeHudOnce() {\n        if (nativeHudAligned) return;\n        nativeHudAligned = true;\n        try {\n            Method getPos = activity.getClass().getMethod("getNativeHudElementPosition", int.class);\n            Method setPos = activity.getClass().getMethod("setNativeHudElementPosition", int.class, int.class, int.class);\n            Object raw = getPos.invoke(activity, 6);\n            if (raw instanceof int[]) {\n                int[] pos = (int[]) raw;\n                if (pos.length >= 2 && pos[0] >= 0 && pos[1] >= 0) {\n                    // Device smoke showed the donor radar roughly 55 px too far right.\n                    // Shift relatively so custom/user HUD settings are still respected.\n                    setPos.invoke(null, 6, Math.max(0, pos[0] - 56), pos[1]);\n                }\n            }\n\n            Method setScale = activity.getClass().getMethod("setNativeHudElementScale", int.class, int.class, int.class);\n            // Native fist/ammo art is replaced by the right-side VOSTOK action controls.\n            setScale.invoke(null, 8, 0, 0);\n            setScale.invoke(null, 9, 0, 0);\n        } catch (Throwable ignored) {\n            // If a donor revision lacks the settings bridge, keep the game playable.\n        }\n    }\n\n'''
if 'private void alignNativeHudOnce()' not in s:
    if s.count(method_anchor) != 1:
        raise SystemExit('031G5.2 native HUD method anchor mismatch')
    s = s.replace(method_anchor, method + method_anchor, 1)

# Re-center the VOSTOK frame around the shifted native radar and pull the status
# spine toward the screen edge. These are the real 031G5 canvas literals after
# 031G5.1; only geometry changes here.
replacements = {
    'final float radarCx = 300f * k;': 'final float radarCx = 244f * k;',
    'path.moveTo(42f * k, 57f * k);': 'path.moveTo(14f * k, 57f * k);',
    'path.lineTo(79f * k, 38f * k);': 'path.lineTo(51f * k, 38f * k);',
    'path.lineTo(116f * k, 67f * k);': 'path.lineTo(88f * k, 67f * k);',
    'path.lineTo(91f * k, 111f * k);': 'path.lineTo(63f * k, 111f * k);',
    'path.lineTo(91f * k, 232f * k);': 'path.lineTo(63f * k, 232f * k);',
    'path.lineTo(126f * k, 285f * k);': 'path.lineTo(98f * k, 285f * k);',
    'path.lineTo(97f * k, 310f * k);': 'path.lineTo(69f * k, 310f * k);',
    'path.lineTo(51f * k, 263f * k);': 'path.lineTo(23f * k, 263f * k);',
    'c.drawLine(48f * k, 61f * k, 62f * k, 45f * k, stroke);': 'c.drawLine(20f * k, 61f * k, 34f * k, 45f * k, stroke);',
    'c.drawLine(54f * k, 255f * k, 73f * k, 278f * k, stroke);': 'c.drawLine(26f * k, 255f * k, 45f * k, 278f * k, stroke);',
    'c.drawLine(111f * k, 294f * k, 150f * k, 294f * k, stroke);': 'c.drawLine(83f * k, 294f * k, 122f * k, 294f * k, stroke);',
    'drawVerticalStat(c, 67f * k, 113f * k, 54f * k, health, 0, k);': 'drawVerticalStat(c, 39f * k, 113f * k, 54f * k, health, 0, k);',
    'drawVerticalStat(c, 67f * k, 177f * k, 48f * k, armour, 1, k);': 'drawVerticalStat(c, 39f * k, 177f * k, 48f * k, armour, 1, k);',
    'drawHungerStat(c, 105f * k, 278f * k, hunger, k);': 'drawHungerStat(c, 77f * k, 278f * k, hunger, k);',
}
for old, new in replacements.items():
    if new in s:
        continue
    if s.count(old) != 1:
        raise SystemExit(f'031G5.2 geometry anchor mismatch: {old!r} -> {s.count(old)}')
    s = s.replace(old, new, 1)

# Tighten the money cap so it reads as one unit with the radar rather than a
# separate floating banner.
s = s.replace('final float moneyX = 86f * k;', 'final float moneyX = 72f * k;', 1)
s = s.replace('final float moneyW = 260f * k;', 'final float moneyW = 230f * k;', 1)

controller.write_text(s, encoding='utf-8')

check = controller.read_text(encoding='utf-8')
for forbidden in ('drawJoystickVisual(c);', 'drawTopLeftCleanup(c);'):
    if forbidden in check:
        raise SystemExit(f'031G5.2 forbidden draw call remains: {forbidden}')
for required in (
    'alignNativeHudOnce();',
    'getNativeHudElementPosition',
    'setNativeHudElementScale',
    'setScale.invoke(null, 8, 0, 0);',
    'setScale.invoke(null, 9, 0, 0);',
    'final float radarCx = 244f * k;',
    'drawVerticalStat(c, 39f * k',
    'VOSTOK_INVENTORY_ENABLED',
):
    if required not in check and required != 'VOSTOK_INVENTORY_ENABLED':
        raise SystemExit(f'031G5.2 verification missing: {required}')

print('Applied VOSTOK Candidate 031G5.2 HUD cleanup')
