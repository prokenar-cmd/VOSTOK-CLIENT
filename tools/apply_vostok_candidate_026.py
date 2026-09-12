#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("client")

# The donor JNI tree contains legacy 8-bit source files. latin-1 is used here
# only as a lossless byte-preserving transport so this patch never corrupts
# the rest of those source files while changing ASCII anchors.
def read_legacy(path: Path) -> str:
    return path.read_bytes().decode("latin-1")


def write_legacy(path: Path, text: str) -> None:
    path.write_bytes(text.encode("latin-1"))


# Candidate 026: donor cache localisation must never overwrite VOSTOK wording.
loc = ROOT / "Jni source/jni/CLocalisation.cpp"
s = read_legacy(loc)
new_messages = r'''char CLocalisation::m_szMessages[E_MSG::MSG_COUNT][MAX_LOCALISATION_LENGTH] = {
    "\xCF\xEE\xE4\xEA\xEB\xFE\xF7\xE5\xED\xE8\xE5\x20\xEA\x20VOSTOK RP...",
    "\xCF\xEE\xE4\xEA\xEB\xFE\xF7\xE5\xED\xEE\x2E\x20\xC2\xF5\xEE\xE4\xE8\xEC\x20\xE2\x20\xE8\xE3\xF0\xF3\x2E\x2E\x2E",
    "\xD1\xE5\xF0\xE2\xE5\xF0\x20\xE7\xE0\xEA\xF0\xFB\xEB\x20\xF1\xEE\xE5\xE4\xE8\xED\xE5\xED\xE8\xE5\x2E\x20\xCF\xE5\xF0\xE5\xEF\xEE\xE4\xEA\xEB\xFE\xF7\xE8\xF2\xE5\xF1\xFC\x2E",
    "\xD4\xE0\xE9\xEB\xFB\x20\xEA\xEB\xE8\xE5\xED\xF2\xE0\x20\xE8\xE7\xEC\xE5\xED\xE5\xED\xFB\x2E\x20\xCF\xE5\xF0\xE5\xF3\xF1\xF2\xE0\xED\xEE\xE2\xE8\xF2\xE5\x20\xEA\xEB\xE8\xE5\xED\xF2\x2E",
    "\xC7\xE0\xEF\xF3\xF1\xF2\xE8\xF2\xE5\x20\xE8\xE3\xF0\xF3\x20\xF7\xE5\xF0\xE5\xE7\x20\xEB\xE0\xF3\xED\xF7\xE5\xF0\x20VOSTOK\x2E",
    "\xC2\xFB\x20\xE1\xFB\xEB\xE8\x20\xE7\xE0\xE1\xEB\xEE\xEA\xE8\xF0\xEE\xE2\xE0\xED\xFB\x20\xF1\xE5\xF0\xE2\xE5\xF0\xEE\xEC\x2E",
    "\xD1\xEE\xE5\xE4\xE8\xED\xE5\xED\xE8\xE5\x20\xF1\x20\xF1\xE5\xF0\xE2\xE5\xF0\xEE\xEC\x20\xEF\xEE\xF2\xE5\xF0\xFF\xED\xEE\x2E\x20\xCF\xE5\xF0\xE5\xEF\xEE\xE4\xEA\xEB\xFE\xF7\xE5\xED\xE8\xE5\x2E\x2E\x2E",
    "\xCF\xF0\xEE\xE1\xEB\xE5\xEC\xFB\x20\xF1\x20\xF1\xE5\xF2\xFC\xFE\x2E\x20\xCF\xE5\xF0\xE5\xEF\xEE\xE4\xEA\xEB\xFE\xF7\xE5\xED\xE8\xE5\x2E\x2E\x2E",
    "\xD1\xE5\xF0\xE2\xE5\xF0\x20\xE7\xE0\xEF\xEE\xEB\xED\xE5\xED\x2E"
};'''
pattern = re.compile(
    r"char CLocalisation::m_szMessages\[E_MSG::MSG_COUNT\]\[MAX_LOCALISATION_LENGTH\] = \{.*?\n\};",
    re.S,
)
s, count = pattern.subn(lambda _: new_messages, s, count=1)
if count != 1:
    raise SystemExit(f"Candidate 026 localisation array mismatch: {count}")
write_legacy(loc, s)

main = ROOT / "Jni source/jni/main.cpp"
s = read_legacy(main)
old = 'CLocalisation::Initialise("ru.lc");'
if s.count(old) != 1:
    raise SystemExit(f"Candidate 026 donor localisation call mismatch: {s.count(old)}")
s = s.replace(old, '/* VOSTOK: built-in localisation; donor ru.lc is intentionally disabled. */', 1)
write_legacy(main, s)

netrpc = ROOT / "Jni source/jni/net/netrpc.cpp"
s = read_legacy(netrpc)
old = 'if(pChatWindow) pChatWindow->AddDebugMessage("Connected to {B9C9BF}%.64s", pNetGame->m_szHostName);'
new = r'if(pChatWindow) pChatWindow->AddDebugMessage("\xCF\xEE\xE4\xEA\xEB\xFE\xF7\xE5\xED\xEE\x20\xEA\x20{B9C9BF}%.64s", pNetGame->m_szHostName);'
if s.count(old) != 1:
    raise SystemExit(f"Candidate 026 connected-message mismatch: {s.count(old)}")
s = s.replace(old, new, 1)
write_legacy(netrpc, s)

# Guards: no donor brand or English connection status can survive in these paths.
loc_text = read_legacy(loc)
main_text = read_legacy(main)
netrpc_text = read_legacy(netrpc)
for bad in ("BLACK RUSSIA", "BLACK MOSCOW"):
    if bad in loc_text:
        raise SystemExit(f"Candidate 026 donor brand remains in localisation: {bad}")
if 'CLocalisation::Initialise("ru.lc")' in main_text:
    raise SystemExit("Candidate 026 donor ru.lc is still enabled")
if "Connected to " in netrpc_text:
    raise SystemExit("Candidate 026 English connected message remains")
if "VOSTOK RP" not in loc_text:
    raise SystemExit("Candidate 026 VOSTOK localisation missing")

print("Applied VOSTOK Candidate 026 native Russian text guard")
