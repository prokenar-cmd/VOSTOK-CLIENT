#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path('client')
HUD = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/hud/VostokHudController.java'
s = HUD.read_text(encoding='utf-8')

# Donor commit b757 exposes native HUD setters as instance methods.
s = s.replace('NvEventQueueActivity.setNativeHudElementPosition(', '((NvEventQueueActivity) activity).setNativeHudElementPosition(')
s = s.replace('NvEventQueueActivity.setNativeHudElementScale(', '((NvEventQueueActivity) activity).setNativeHudElementScale(')

# TARGET_FIST was intentionally removed from the editor UI in 031H1; remove
# leftover controller branches inherited from 031H.
s = re.sub(r'\n\s*\} else if \(target == VostokHudEditorView\.TARGET_FIST\) \{\n\s*moveNativeHudElement\(8, Math\.round\(dxPixels\), Math\.round\(dyPixels\)\);', '', s)
s = re.sub(r'\n\s*\} else if \(target == VostokHudEditorView\.TARGET_FIST\) \{\n\s*scaleNativeHudElement\(8, direction \* 5\);', '', s)
s = re.sub(r'\n\s*\} else if \(target == VostokHudEditorView\.TARGET_FIST\) \{\n\s*restoreNative\(8, editorFistStartPos, editorFistStartScale\);', '', s)

# Android Paint uses setColor(), not setStrokeColor().
s = s.replace('stroke.setStrokeColor(', 'stroke.setColor(')

HUD.write_text(s, encoding='utf-8')

check = HUD.read_text(encoding='utf-8')
if 'VostokHudEditorView.TARGET_FIST' in check:
    raise SystemExit('031H1 compilefix: TARGET_FIST controller reference remains')
if 'NvEventQueueActivity.setNativeHudElementPosition(' in check:
    raise SystemExit('031H1 compilefix: static native position call remains')
if 'NvEventQueueActivity.setNativeHudElementScale(' in check:
    raise SystemExit('031H1 compilefix: static native scale call remains')
if 'setStrokeColor(' in check:
    raise SystemExit('031H1 compilefix: invalid Paint method remains')

print('Applied VOSTOK Candidate 031H1 compile/runtime fix')
