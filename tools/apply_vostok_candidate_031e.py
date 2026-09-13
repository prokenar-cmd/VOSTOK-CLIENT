#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("client")
JAVA_ROOT = ROOT / "app/src/main/java"


def unique_java(name: str) -> Path:
    matches = [p for p in JAVA_ROOT.rglob(name) if p.is_file()]
    if len(matches) != 1:
        raise SystemExit(f"031E expected one {name}, found {len(matches)}: {matches}")
    return matches[0]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031E {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def add_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    m = re.search(r"^package\s+[^;]+;\s*\n", text, re.M)
    if not m:
        raise SystemExit("031E package anchor missing")
    return text[:m.end()] + "\n" + import_line + "\n" + text[m.end():]


# ---------------------------------------------------------------------------
# Speedometer: keep 031D visual telemetry, add one real engine hit target and
# vehicle-mode lifecycle. The native transport remains sendRadialClick(3).
# ---------------------------------------------------------------------------
speed = unique_java("Speedometer.java")
s = speed.read_text(encoding="utf-8")
s = add_import(s, "import com.blackrussia.game.vostok.ui.vehicle.VostokVehicleUiController;")

if "private final VostokVehicleUiController vostokVehicleUi;" not in s:
    anchor = "private final VostokHudController vostokHud;"
    if s.count(anchor) != 1:
        raise SystemExit(f"031E Speedometer VOSTOK HUD field anchor mismatch: {s.count(anchor)}")
    s = s.replace(anchor, anchor + "\n    private final VostokVehicleUiController vostokVehicleUi;", 1)

if "vostokVehicleUi = VostokVehicleUiController.getOrCreate(" not in s:
    pattern = re.compile(r"(vostokHud\s*=\s*VostokHudController\.getOrCreate\((\w+)\);)")
    match = pattern.search(s)
    if not match:
        raise SystemExit("031E Speedometer 031D constructor integration missing")
    arg = match.group(2)
    replacement = match.group(1) + f"\n        vostokVehicleUi = VostokVehicleUiController.getOrCreate({arg});"
    s = s[:match.start()] + replacement + s[match.end():]

state_anchor = "vostokHud.updateVehicleState(speed, fuel, vostokCondition, mileage, engine, light, belt, lock);"
state_new = state_anchor + "\n        vostokVehicleUi.updateState(engine, light, belt, lock);"
if "vostokVehicleUi.updateState(engine, light, belt, lock);" not in s:
    if s.count(state_anchor) != 1:
        raise SystemExit(f"031E vehicle-state anchor mismatch: {s.count(state_anchor)}")
    s = s.replace(state_anchor, state_new, 1)

show_anchor = "vostokHud.showVehicleHud();"
show_new = show_anchor + "\n        vostokVehicleUi.show();"
if "vostokVehicleUi.show();" not in s:
    if s.count(show_anchor) != 1:
        raise SystemExit(f"031E ShowSpeed anchor mismatch: {s.count(show_anchor)}")
    s = s.replace(show_anchor, show_new, 1)

hide_anchor = "vostokHud.hideVehicleHud();"
hide_new = hide_anchor + "\n        vostokVehicleUi.hide();"
if "vostokVehicleUi.hide();" not in s:
    if s.count(hide_anchor) != 1:
        raise SystemExit(f"031E HideSpeed anchor mismatch: {s.count(hide_anchor)}")
    s = s.replace(hide_anchor, hide_new, 1)

speed.write_text(s, encoding="utf-8")


# ---------------------------------------------------------------------------
# UI bridge: when a vehicle speedometer is active, interaction is forcibly
# hidden and incoming proximity presentations are ignored until vehicle exit.
# This is client-side protection in addition to server-side target rules.
# ---------------------------------------------------------------------------
bridge = ROOT / "app/src/main/java/com/blackrussia/game/vostok/ui/VostokUiBridge.java"
b = bridge.read_text(encoding="utf-8")

if "private volatile boolean vehicleMode;" not in b:
    anchor = "    private volatile boolean destroyed;"
    if b.count(anchor) != 1:
        raise SystemExit(f"031E bridge destroyed anchor mismatch: {b.count(anchor)}")
    b = b.replace(anchor, anchor + "\n    private volatile boolean vehicleMode;", 1)

old_show_npc = '''    public void showNpcInteraction(float distanceMeters) {
        runOnUiThread(() -> interactionCore.presentNpc(distanceMeters));
    }'''
new_show_npc = '''    public void showNpcInteraction(float distanceMeters) {
        runOnUiThread(() -> {
            if (vehicleMode) {
                interactionCore.hide();
                return;
            }
            interactionCore.presentNpc(distanceMeters);
        });
    }'''
if new_show_npc not in b:
    if b.count(old_show_npc) != 1:
        raise SystemExit(f"031E NPC interaction gate anchor mismatch: {b.count(old_show_npc)}")
    b = b.replace(old_show_npc, new_show_npc, 1)

old_present = '''    public void presentInteractionTargets(List<InteractionCore.Target> targets) {
        runOnUiThread(() -> interactionCore.present(targets));
    }'''
new_present = '''    public void presentInteractionTargets(List<InteractionCore.Target> targets) {
        runOnUiThread(() -> {
            if (vehicleMode) {
                interactionCore.hide();
                return;
            }
            interactionCore.present(targets);
        });
    }'''
if new_present not in b:
    if b.count(old_present) != 1:
        raise SystemExit(f"031E target interaction gate anchor mismatch: {b.count(old_present)}")
    b = b.replace(old_present, new_present, 1)

vehicle_method = '''    public void setVehicleMode(boolean inVehicle) {
        runOnUiThread(() -> {
            vehicleMode = inVehicle;
            if (inVehicle) interactionCore.hide();
        });
    }

'''
if "public void setVehicleMode(boolean inVehicle)" not in b:
    anchor = '''    public void hideInteraction() {
        runOnUiThread(interactionCore::hide);
    }

'''
    if b.count(anchor) != 1:
        raise SystemExit(f"031E bridge vehicle-mode insertion anchor mismatch: {b.count(anchor)}")
    b = b.replace(anchor, anchor + vehicle_method, 1)

bridge.write_text(b, encoding="utf-8")


# ---------------------------------------------------------------------------
# Activity bridge used by VostokVehicleUiController through reflection.
# ---------------------------------------------------------------------------
activity = JAVA_ROOT / "com/nvidia/devtech/NvEventQueueActivity.java"
a = activity.read_text(encoding="utf-8")

vehicle_bridge = '''    public void setVostokVehicleMode(boolean inVehicle) {
        if (mVostokUi != null) mVostokUi.setVehicleMode(inVehicle);
    }

'''
if "setVostokVehicleMode(boolean inVehicle)" not in a:
    pattern = re.compile(
        r'(    public void hideVostokQuest\(\) \{\s*\n\s*if \(mVostokUi != null\) mVostokUi\.hideQuest\(\);\s*\n\s*\}\s*\n)',
        re.M,
    )
    match = pattern.search(a)
    if not match:
        raise SystemExit("031E activity quest bridge anchor missing")
    a = a[:match.end()] + "\n" + vehicle_bridge + a[match.end():]

activity.write_text(a, encoding="utf-8")


# ---------------------------------------------------------------------------
# Contract gates.
# ---------------------------------------------------------------------------
vehicle = ROOT / "app/src/main/java/com/blackrussia/game/vostok/ui/vehicle/VostokVehicleUiController.java"
if not vehicle.exists():
    raise SystemExit("031E VostokVehicleUiController overlay missing")

combined = "\n".join((
    speed.read_text(encoding="utf-8"),
    bridge.read_text(encoding="utf-8"),
    activity.read_text(encoding="utf-8"),
    vehicle.read_text(encoding="utf-8"),
))

required = (
    "VostokVehicleUiController",
    "vostokVehicleUi.show();",
    "vostokVehicleUi.hide();",
    "vostokVehicleUi.updateState(engine, light, belt, lock);",
    "sendRadialClick",
    "setVostokVehicleMode(boolean inVehicle)",
    "public void setVehicleMode(boolean inVehicle)",
    "if (vehicleMode)",
    '"speedometer"',
    '"speed_engine_ico"',
)
for needle in required:
    if needle not in combined:
        raise SystemExit(f"031E verification missing: {needle}")

if "public native void sendRadialClick(int id);" not in activity.read_text(encoding="utf-8", errors="ignore"):
    raise SystemExit("031E donor native engine transport missing")

chat = ROOT / "Jni source/jni/chatwindow.cpp"
if "~VOSTOK_UI~INTERACT:SHOW:NPC:" not in chat.read_text(encoding="utf-8", errors="ignore"):
    raise SystemExit("031E regression: protected 027F NPC interaction protocol missing")
if "mChooseServer" in activity.read_text(encoding="utf-8"):
    raise SystemExit("031E regression: ChooseServer returned")

print(f"Applied VOSTOK Candidate 031E Vehicle UI using {speed}")
