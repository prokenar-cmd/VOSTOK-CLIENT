#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new and new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031G4.1 {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = Path("client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java")
render_layout = Path("client/app/src/main/res/layout/main_render_screen.xml")

# KEEP 031G3's proven late autoconnect trigger. Native InitInGame emits
# UpdateSplash(101) only after pSettings/pGame have been initialized. The 031G4
# regression moved sendRPC(2,...) into showSplash(), which runs from the very
# first splash frame and can execute while pSettings is still null, causing the
# observed native SIGSEGV in Java_com_nvidia_devtech_NvEventQueueActivity_sendRPC.
activity_text = activity.read_text(encoding="utf-8")
required_safe_trigger = '''        if (percent > 100 && !mVostokAutoConnectIssued) {
            mVostokAutoConnectIssued = true;
            runOnUiThread(() -> {
                // Native action 2/0 is the proven VOSTOK DEV server-connect path.
                // The byte payload is unused by the native type=2/action=0 branch.
                sendRPC(2, "VOSTOK".getBytes(), 0);
            });
        }'''
if required_safe_trigger not in activity_text:
    raise SystemExit("031G4.1 safe >100 autoconnect trigger from 031G3 is missing")
if "issueVostokAutoConnect()" in activity_text:
    raise SystemExit("031G4.1 unsafe showSplash autoconnect must not be present")

# Remove only the donor server-selector/loading subtree. It owns the old mylogo
# loading background and is unrelated to the safe native-ready milestone above.
replace_once(
    render_layout,
    '        <include layout="@layout/br_serverselect" />\n',
    '',
    "remove donor server selector include",
)

activity_text = activity.read_text(encoding="utf-8")
layout_text = render_layout.read_text(encoding="utf-8")
for needle in (
    "private boolean mVostokAutoConnectIssued = false;",
    "percent > 100 && !mVostokAutoConnectIssued",
    'sendRPC(2, "VOSTOK".getBytes(), 0)',
    "mVostokLoading.completeWorldEntry()",
):
    if needle not in activity_text:
        raise SystemExit(f"031G4.1 verification missing: {needle}")

if 'layout="@layout/br_serverselect"' in layout_text:
    raise SystemExit("031G4.1 donor server-selector include still present")

for forbidden in (
    "mChooseServer.Update",
    "mChooseServer.Show",
    "new ChooseServer",
    "private void issueVostokAutoConnect()",
):
    if forbidden in activity_text:
        raise SystemExit(f"031G4.1 legacy/unsafe lifecycle code present: {forbidden}")

print("Applied VOSTOK Candidate 031G4.1: server-selector removed, safe native-ready autoconnect preserved")
