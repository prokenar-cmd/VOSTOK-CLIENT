#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031G3 {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = Path("client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java")
loading = Path("client/app/src/main/java/com/blackrussia/game/vostok/loading/VostokLoadingOverlay.java")

# 031A removed the donor ChooseServer screen, but that screen also used to issue
# sendRPC(type=2, action=0), which is the native trigger that constructs CNetGame.
# Restore that behavior automatically once GTA's local splash phase is complete.
replace_once(
    activity,
    "    private VostokLoadingOverlay mVostokLoading = null;\n",
    "    private VostokLoadingOverlay mVostokLoading = null;\n    private boolean mVostokAutoConnectIssued = false;\n",
    "autoconnect state",
)

replace_once(
    activity,
    "    public void updateSplash(int percent) { if (mVostokLoading != null) mVostokLoading.update(percent); }",
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
    "automatic native connect",
)

# Keep the VOSTOK loading layer on screen after local GTA asset loading has
# completed. It is dismissed only once the server/game HUD is actually shown,
# so the legacy donor splash cannot flash back during network authentication.
replace_once(
    activity,
    "    public void showHud() { runOnUiThread(() -> { mHudManager.ShowHud(); }); }",
    '''    public void showHud() {
        runOnUiThread(() -> {
            mHudManager.ShowHud();
            if (mVostokLoading != null) mVostokLoading.completeWorldEntry();
        });
    }''',
    "world-ready loading dismissal",
)

replace_once(
    loading,
    "    private boolean finishing = false;\n",
    "    private boolean finishing = false;\n    private boolean worldReady = false;\n",
    "world-ready state",
)

replace_once(
    loading,
    '''    public void show() {
        activity.runOnUiThread(() -> {
            if (root == null) return;
            finishing = false;''',
    '''    public void show() {
        activity.runOnUiThread(() -> {
            if (root == null || worldReady) return;
            finishing = false;''',
    "show guard",
)

replace_once(
    loading,
    '''    public void update(int nativePercent) {
        activity.runOnUiThread(() -> {
            if (root == null) return;
            if (!showing) showImmediate();''',
    '''    public void update(int nativePercent) {
        activity.runOnUiThread(() -> {
            if (root == null || worldReady) return;
            if (!showing) showImmediate();''',
    "update guard",
)

replace_once(
    loading,
    "            if (state.complete) finishLoading();\n",
    '''            // Native >100 means only that GTA's local splash phase ended.
            // Network authentication/spawn still follows, so do not uncover the
            // donor splash here. showHud() will call completeWorldEntry().
            if (state.complete) {
                statusView.setText(R.string.vostok_loading_status_enter);
                percentView.setText("100%");
                updateProgressWidth(100);
            }
''',
    "do not hide on local splash completion",
)

anchor = '''    private void finishLoading() {
        if (finishing || root == null) return;'''
replacement = '''    public void completeWorldEntry() {
        activity.runOnUiThread(() -> {
            if (root == null || worldReady) return;
            worldReady = true;
            finishLoading();
        });
    }

    private void finishLoading() {
        if (finishing || root == null) return;'''
replace_once(loading, anchor, replacement, "world entry completion bridge")

replace_once(
    loading,
    '''            showing = false;
            finishing = false;
            if (root != null) {''',
    '''            showing = false;
            finishing = false;
            worldReady = true;
            if (root != null) {''',
    "shutdown terminal state",
)

activity_text = activity.read_text(encoding="utf-8")
loading_text = loading.read_text(encoding="utf-8")
required = (
    "mVostokAutoConnectIssued",
    'sendRPC(2, "VOSTOK".getBytes(), 0)',
    "percent > 100",
    "mVostokLoading.completeWorldEntry()",
    "public void completeWorldEntry()",
    "if (root == null || worldReady) return",
)
combined = activity_text + "\n" + loading_text
for needle in required:
    if needle not in combined:
        raise SystemExit(f"031G3 verification missing: {needle}")

for forbidden in (
    "mChooseServer.Update",
    "mChooseServer.Show",
    "new ChooseServer",
):
    if forbidden in activity_text:
        raise SystemExit(f"031G3 regression: donor ChooseServer flow returned: {forbidden}")

print("Applied VOSTOK Candidate 031G3 autoconnect + loading transition fix")
