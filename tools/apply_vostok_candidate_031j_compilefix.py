#!/usr/bin/env python3
from pathlib import Path

p = Path('client/app/src/main/java/com/vostok/launcher/ui/VostokLauncherView.java')
if not p.exists():
    raise SystemExit(f'missing 031J launcher source: {p}')

text = p.read_text(encoding='utf-8')
bad = 'Color.argb(255, 248, 154, 90), Color.argb(255, bases[index]), Shader.TileMode.CLAMP'
good = 'Color.argb(255, 248, 154, 90), bases[index], Shader.TileMode.CLAMP'

if bad in text:
    text = text.replace(bad, good, 1)
elif good not in text:
    raise SystemExit('031J compilefix anchor not found')

p.write_text(text, encoding='utf-8')
print('Applied VOSTOK 031J compilefix: news thumbnail gradient color')
