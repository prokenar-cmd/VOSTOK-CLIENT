#!/usr/bin/env python3
from pathlib import Path

# Candidate 027F is deliberately native-only. The launcher, Java UI, HUD,
# speedometer and account code are not touched. The server now emits an explicit
# NPC target tag; the client accepts only that typed Interaction SHOW record.
chat = Path('client/Jni source/jni/chatwindow.cpp')
s = chat.read_text()

old = 'static const char kVostokInteractionShow[] = "~VOSTOK_UI~INTERACT:SHOW:";'
new = 'static const char kVostokInteractionShow[] = "~VOSTOK_UI~INTERACT:SHOW:NPC:";'
if s.count(old) != 1:
    raise SystemExit(f'Candidate 027F interaction SHOW anchor mismatch: {s.count(old)}')
s = s.replace(old, new, 1)

# Fail closed: an old untyped SHOW packet must not be accepted by this build.
if '"~VOSTOK_UI~INTERACT:SHOW:"' in s:
    raise SystemExit('Candidate 027F legacy untyped interaction SHOW remains')
if '"~VOSTOK_UI~INTERACT:SHOW:NPC:"' not in s:
    raise SystemExit('Candidate 027F typed NPC interaction SHOW missing')
if 'g_pJavaWrapper->ShowVostokInteraction(distanceMeters);' not in s:
    raise SystemExit('Candidate 027F Java interaction bridge missing')

chat.write_text(s)
print('Applied VOSTOK Candidate 027F NPC-only native Interaction transport guard')
