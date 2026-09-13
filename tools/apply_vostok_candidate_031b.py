#!/usr/bin/env python3
from pathlib import Path

# Candidate 031B runs after 031A. It adds only generic UI-core hooks to the
# activity and does not replace any working visual surface.


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031B {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = Path("client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java")

old_back = '''    @Override
    public void onBackPressed() {
        super.onBackPressed();
        onEventBackPressed();
    }'''
new_back = '''    @Override
    public void onBackPressed() {
        // VOSTOK UI Core gets first refusal. If no VOSTOK modal is open,
        // preserve the donor/native back behavior exactly as before.
        if (mVostokUi != null && mVostokUi.onBackPressed()) {
            return;
        }
        super.onBackPressed();
        onEventBackPressed();
    }'''
replace_once(activity, old_back, new_back, "Back dispatch")

old_touch = '''    @Override
    public boolean onTouch(View view, MotionEvent event)
    {
        if(view == mRootFrame)'''
new_touch = '''    @Override
    public boolean onTouch(View view, MotionEvent event)
    {
        // Full-screen VOSTOK screens own touch/camera input while open.
        // Persistent HUD/Interaction overlays do not set this lock.
        if (mVostokUi != null && mVostokUi.shouldBlockGameInput()) {
            return true;
        }
        if(view == mRootFrame)'''
replace_once(activity, old_touch, new_touch, "touch input lock")

old_key_back = '''        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            onEventBackPressed();
        }'''
new_key_back = '''        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mVostokUi != null && mVostokUi.onBackPressed()) {
                return true;
            }
            onEventBackPressed();
        }'''
replace_once(activity, old_key_back, new_key_back, "hardware Back dispatch")

interaction_bridge = '''    public void setVostokInteractionContext(int flags, int reservedActionSlots) { if (mVostokUi != null) mVostokUi.setInteractionContext(flags, reservedActionSlots); }'''
ui_bridge = '''    public void setVostokInteractionContext(int flags, int reservedActionSlots) { if (mVostokUi != null) mVostokUi.setInteractionContext(flags, reservedActionSlots); }

    // 031B stable Java/native screen bridge. Concrete screen controllers are
    // registered by later candidates; unknown/unregistered ids are ignored.
    public void showVostokUiScreen(int screenId) { if (mVostokUi != null) mVostokUi.showUiScreen(screenId); }

    public void hideVostokUiScreen(int screenId) { if (mVostokUi != null) mVostokUi.hideUiScreen(screenId); }

    public void closeAllVostokUi() { if (mVostokUi != null) mVostokUi.closeAllTransientUi(); }'''
replace_once(activity, interaction_bridge, ui_bridge, "generic screen bridge")

text = activity.read_text(encoding="utf-8")
required = (
    "mVostokUi.onBackPressed()",
    "mVostokUi.shouldBlockGameInput()",
    "showVostokUiScreen(int screenId)",
    "hideVostokUiScreen(int screenId)",
    "closeAllVostokUi()",
)
for needle in required:
    if needle not in text:
        raise SystemExit(f"031B verification missing: {needle}")

# 031B must not re-introduce server chooser or bypass the 031A loading layer.
for forbidden in (
    "private ChooseServer mChooseServer",
    "mChooseServer = new ChooseServer",
    "mChooseServer.Show",
    "mChooseServer.Update",
):
    if forbidden in text:
        raise SystemExit(f"031B legacy ChooseServer regression: {forbidden}")

print("Applied VOSTOK Candidate 031B UI Core integration")
