#!/usr/bin/env python3
from pathlib import Path

p = Path('client/app/src/main/java/com/vostok/launcher/account/GameLaunchIdentity.java')
if not p.exists():
    raise SystemExit(f'missing 031I GameLaunchIdentity: {p}')

text = p.read_text(encoding='utf-8')
if 'import java.io.File;' not in text:
    anchor = 'import org.ini4j.Wini;\n\n'
    if anchor not in text:
        raise SystemExit('031I compilefix import anchor changed')
    text = text.replace(anchor, anchor + 'import java.io.File;\n')

p.write_text(text, encoding='utf-8')
print('Applied VOSTOK 031I compilefix: restored java.io.File import only')
