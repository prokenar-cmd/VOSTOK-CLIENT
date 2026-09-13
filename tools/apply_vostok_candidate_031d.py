#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("client")
JAVA_ROOT = ROOT / "app/src/main/java"


def unique_java(name: str) -> Path:
    matches = [p for p in JAVA_ROOT.rglob(name) if p.is_file()]
    if len(matches) != 1:
        raise SystemExit(f"031D expected one {name}, found {len(matches)}: {matches}")
    return matches[0]


def add_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    m = re.search(r"^package\s+[^;]+;\s*\n", text, re.M)
    if not m:
        raise SystemExit("031D package anchor missing")
    return text[:m.end()] + "\n" + import_line + "\n" + text[m.end():]


# ---------------------------------------------------------------------------
# Legacy HUD visual suppression. Do not delete donor HUD: radar/chat/weapon and
# native callbacks stay intact. Replaced quick-access widgets must never be
# re-shown after opening/closing chat keyboard.
# ---------------------------------------------------------------------------
hud = unique_java("HudManager.java")
hud_text = hud.read_text(encoding="utf-8")
for field in ("hud_menu", "hud_yved", "hud_quest", "hud_donate"):
    hud_text = hud_text.replace(
        f"{field}.setVisibility(View.VISIBLE);",
        f"{field}.setVisibility(View.GONE);"
    )
hud.write_text(hud_text, encoding="utf-8")


# ---------------------------------------------------------------------------
# Vehicle telemetry: use the donor's real authoritative values, replace only
# the old speedometer visual. Native driving controls remain untouched.
# ---------------------------------------------------------------------------
speed = unique_java("Speedometer.java")
s = speed.read_text(encoding="utf-8")
s = add_import(s, "import com.blackrussia.game.vostok.ui.hud.VostokHudController;")

if "private final VostokHudController vostokHud;" not in s:
    count = s.count("public class Speedometer {")
    if count != 1:
        raise SystemExit(f"031D Speedometer class anchor mismatch: {count}")
    s = s.replace(
        "public class Speedometer {",
        "public class Speedometer {\n    private final VostokHudController vostokHud;",
        1,
    )

if "vostokHud = VostokHudController.getOrCreate(activity);" not in s:
    pattern = re.compile(
        r"(public\s+Speedometer\s*\(\s*Activity\s+activity\s*\)\s*\{\s*\n\s*this\.activity\s*=\s*activity\s*;)"
    )
    s, count = pattern.subn(
        r"\1\n        vostokHud = VostokHudController.getOrCreate(activity);",
        s,
        count=1,
    )
    if count != 1:
        raise SystemExit("031D Speedometer constructor anchor missing")

marker = "vostokHud.updateVehicleState(speed, fuel, vostokCondition, mileage, engine, light, belt, lock);"
if marker not in s:
    pattern = re.compile(
        r"(public\s+void\s+UpdateSpeedInfo\s*\(\s*int\s+speed\s*,\s*int\s+fuel\s*,\s*int\s+hp\s*,\s*int\s+mileage\s*,\s*int\s+engine\s*,\s*int\s+light\s*,\s*int\s+belt\s*,\s*int\s+lock\s*\)\s*\{)"
    )
    insertion = (
        r"\1\n        int vostokCondition = Math.max(0, Math.min(100, hp / 10));"
        "\n        " + marker
    )
    s, count = pattern.subn(insertion, s, count=1)
    if count != 1:
        raise SystemExit("031D UpdateSpeedInfo signature anchor missing")

show_pattern = re.compile(r"public\s+void\s+ShowSpeed\s*\(\s*\)\s*\{[^{}]*\}", re.S)
hide_pattern = re.compile(r"public\s+void\s+HideSpeed\s*\(\s*\)\s*\{[^{}]*\}", re.S)

show_replacement = '''public void ShowSpeed() {
        // 031D replaces the donor speedometer visual only. Vehicle control input
        // remains native/donor-owned and continues to receive the same data.
        Utils.HideLayout(mInputLayout, false);
        vostokHud.showVehicleHud();
    }'''
hide_replacement = '''public void HideSpeed() {
        Utils.HideLayout(mInputLayout, false);
        vostokHud.hideVehicleHud();
    }'''

if "vostokHud.showVehicleHud();" not in s:
    s, count = show_pattern.subn(show_replacement, s, count=1)
    if count != 1:
        raise SystemExit("031D ShowSpeed anchor missing")
if "vostokHud.hideVehicleHud();" not in s:
    s, count = hide_pattern.subn(hide_replacement, s, count=1)
    if count != 1:
        raise SystemExit("031D HideSpeed anchor missing")

speed.write_text(s, encoding="utf-8")


# ---------------------------------------------------------------------------
# Native/server-facing quest bridge. 031D never invents a quest: the block is
# hidden by default and appears only when authoritative code calls this bridge.
# ---------------------------------------------------------------------------
activity = JAVA_ROOT / "com/nvidia/devtech/NvEventQueueActivity.java"
a = activity.read_text(encoding="utf-8")
anchor = "    public void closeAllVostokUi() { if (mVostokUi != null) mVostokUi.closeAllTransientUi(); }"
quest_bridge = '''    public void closeAllVostokUi() { if (mVostokUi != null) mVostokUi.closeAllTransientUi(); }

    // 031D authoritative quest HUD bridge. Hidden until the server/gameplay layer supplies data.
    public void showVostokQuest(String title, String objective, int current, int total) {
        if (mVostokUi != null) mVostokUi.showQuest(title, objective, current, total);
    }

    public void hideVostokQuest() {
        if (mVostokUi != null) mVostokUi.hideQuest();
    }'''
if "showVostokQuest(String title" not in a:
    if a.count(anchor) != 1:
        raise SystemExit(f"031D quest bridge anchor mismatch: {a.count(anchor)}")
    a = a.replace(anchor, quest_bridge, 1)
activity.write_text(a, encoding="utf-8")


# ---------------------------------------------------------------------------
# Contract gates before Gradle gets a chance to build.
# ---------------------------------------------------------------------------
controller = ROOT / "app/src/main/java/com/blackrussia/game/vostok/ui/hud/VostokHudController.java"
if not controller.exists():
    raise SystemExit("031D VostokHudController overlay missing")
controller_text = controller.read_text(encoding="utf-8")

required = (
    "updatePlayerState(health, armour, hunger, money)",
    "showVostokQuest(String title",
    "vostokHud.updateVehicleState(speed, fuel, vostokCondition, mileage, engine, light, belt, lock)",
    "vostokHud.showVehicleHud()",
    "vostokHud.hideVehicleHud()",
    "ТОПЛИВО",
    "СОСТОЯНИЕ",
    "КМ/Ч",
    '"hud_camera"',
    '"camera_button"',
)
combined = "\n".join((
    (ROOT / "app/src/main/java/com/blackrussia/game/vostok/ui/VostokUiBridge.java").read_text(encoding="utf-8"),
    activity.read_text(encoding="utf-8"),
    speed.read_text(encoding="utf-8"),
    controller_text,
))
for needle in required:
    if needle not in combined:
        raise SystemExit(f"031D verification missing: {needle}")

for forbidden in (
    "Больше чем игра",
    "Твоя история начинается",
    "Начни свой путь",
    "Играй. Развивайся",
):
    if forbidden in controller_text:
        raise SystemExit(f"031D forbidden slogan present: {forbidden}")

# Preserve working 027F interaction and 031A no-ChooseServer contracts.
chat = ROOT / "Jni source/jni/chatwindow.cpp"
if "~VOSTOK_UI~INTERACT:SHOW:NPC:" not in chat.read_text(encoding="utf-8", errors="ignore"):
    raise SystemExit("031D regression: protected 027F NPC interaction protocol missing")
if "mChooseServer" in activity.read_text(encoding="utf-8"):
    raise SystemExit("031D regression: ChooseServer returned")

print(f"Applied VOSTOK Candidate 031D HUD v1 using {hud} and {speed}")
