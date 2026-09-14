#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new and new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031G4 {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = Path("client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java")
render_layout = Path("client/app/src/main/res/layout/main_render_screen.xml")

# 031G3 still tied connection start to a synthetic '>100%' loading milestone.
# The pinned donor splash normalizes the local GTA progress to <=100, so that
# milestone is not a valid network lifecycle signal. The original server screen
# allowed its PLAY action while the splash was already visible. Replicate only
# that network action, automatically and exactly once, when the in-game splash
# becomes active. Spawn selection/ticket preparation has already completed in
# VostokEntryActivity before GTASA is started.
replace_once(
    activity,
    '''    public void updateSplash(int percent) {
        if (mVostokLoading != null) mVostokLoading.update(percent);
        if (percent > 100 && !mVostokAutoConnectIssued) {
            mVostokAutoConnectIssued = true;
            runOnUiThread(() -> {
                // Native action 2/0 is the proven VOSTOK DEV server-connect path.
                // The byte payload is unused by the native type=2/action=0 branch.
                sendRPC(2, "VOSTOK".getBytes(), 0);
            });
        }
    }''',
    '''    public void updateSplash(int percent) {
        if (mVostokLoading != null) mVostokLoading.update(percent);
    }''',
    "remove invalid >100 autoconnect trigger",
)

replace_once(
    activity,
    '''    public void showSplash() { if (mVostokLoading != null) mVostokLoading.show(); }''',
    '''    public void showSplash() {
        if (mVostokLoading != null) mVostokLoading.show();
        issueVostokAutoConnect();
    }

    private void issueVostokAutoConnect() {
        if (mVostokAutoConnectIssued) return;
        mVostokAutoConnectIssued = true;
        runOnUiThread(() -> {
            // This is the original server-screen PLAY native action, without
            // exposing or depending on the donor ChooseServer UI.
            sendRPC(2, "VOSTOK".getBytes(), 0);
        });
    }''',
    "splash-time autoconnect",
)

# The donor server selector is not merely a choice page: its layout also owns
# the old mylogo loading screen and is VISIBLE by default. 031A removed the
# ChooseServer controller but left this include in the active render hierarchy,
# which is why the old loading artwork reappears underneath the VOSTOK overlay.
# Remove the entire donor selector/loading subtree from the render screen.
replace_once(
    render_layout,
    '        <include layout="@layout/br_serverselect" />\n',
    '',
    "remove donor server selector include",
)

activity_text = activity.read_text(encoding="utf-8")
layout_text = render_layout.read_text(encoding="utf-8")
required = (
    "private boolean mVostokAutoConnectIssued = false;",
    "private void issueVostokAutoConnect()",
    'sendRPC(2, "VOSTOK".getBytes(), 0)',
    "mVostokLoading.completeWorldEntry()",
)
for needle in required:
    if needle not in activity_text:
        raise SystemExit(f"031G4 verification missing: {needle}")

for forbidden in (
    "percent > 100 && !mVostokAutoConnectIssued",
    'layout="@layout/br_serverselect"',
):
    haystack = activity_text if "percent" in forbidden else layout_text
    if forbidden in haystack:
        raise SystemExit(f"031G4 donor/lifecycle regression remains: {forbidden}")

for forbidden in (
    "mChooseServer.Update",
    "mChooseServer.Show",
    "new ChooseServer",
):
    if forbidden in activity_text:
        raise SystemExit(f"031G4 legacy ChooseServer controller returned: {forbidden}")

print("Applied VOSTOK Candidate 031G4 server-selector decoupling + splash-time autoconnect")
