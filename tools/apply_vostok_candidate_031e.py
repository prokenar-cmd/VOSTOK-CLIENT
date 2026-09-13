#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path('client')
JAVA = ROOT / 'app/src/main/java'


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'031E {label} anchor mismatch in {path}: {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


def unique_java(name: str) -> Path:
    matches = [p for p in JAVA.rglob(name) if p.is_file()]
    if len(matches) != 1:
        raise SystemExit(f'031E expected one {name}, found {len(matches)}: {matches}')
    return matches[0]


bridge = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/VostokUiBridge.java'
text = bridge.read_text(encoding='utf-8')

if 'import com.blackrussia.game.vostok.ui.vehicle.VostokVehicleControls;' not in text:
    text = text.replace(
        'import com.blackrussia.game.vostok.ui.hud.VostokHudController;\n',
        'import com.blackrussia.game.vostok.ui.hud.VostokHudController;\n'
        'import com.blackrussia.game.vostok.ui.vehicle.VostokVehicleControls;\n',
        1,
    )

if 'import java.lang.reflect.Method;' not in text:
    text = text.replace('import java.util.List;\n', 'import java.lang.reflect.Method;\nimport java.nio.charset.Charset;\nimport java.util.List;\n', 1)

if 'private final VostokVehicleControls vehicleControls;' not in text:
    text = text.replace(
        '    private final VostokHudController vostokHud;\n',
        '    private final VostokHudController vostokHud;\n    private final VostokVehicleControls vehicleControls;\n',
        1,
    )
if 'vehicleControls = new VostokVehicleControls' not in text:
    text = text.replace(
        '        vostokHud = VostokHudController.getOrCreate(activity);\n        notificationManager = new Notification(activity);\n',
        '        vostokHud = VostokHudController.getOrCreate(activity);\n'
        '        vehicleControls = new VostokVehicleControls(activity, this::dispatchVehicleAction);\n'
        '        notificationManager = new Notification(activity);\n',
        1,
    )

anchor = '''    public void hideQuest() {\n        runOnUiThread(vostokHud::hideQuest);\n    }'''
addition = '''    public void hideQuest() {\n        runOnUiThread(vostokHud::hideQuest);\n    }\n\n    public void setVehicleMode(boolean inVehicle) {\n        runOnUiThread(() -> {\n            vehicleControls.setVehicleMode(inVehicle);\n            interactionManager.setVehicleMode(inVehicle);\n        });\n    }'''
if 'public void setVehicleMode(boolean inVehicle)' not in text:
    if text.count(anchor) != 1:
        raise SystemExit('031E VostokUiBridge vehicle-mode anchor mismatch')
    text = text.replace(anchor, addition, 1)

if 'vehicleControls.shutdown();' not in text:
    text = text.replace(
        '            interactionManager.shutdown();\n            vostokHud.shutdown();\n',
        '            interactionManager.shutdown();\n            vehicleControls.shutdown();\n            vostokHud.shutdown();\n',
        1,
    )

anchor2 = '''    private void onInteractionPressed() {\n        if (!destroyed) interactionCore.onPrimaryPressed();\n    }'''
addition2 = '''    private void onInteractionPressed() {\n        if (!destroyed) interactionCore.onPrimaryPressed();\n    }\n\n    private void dispatchVehicleAction(int action) {\n        if (destroyed) return;\n        final String command;\n        if (action == VostokVehicleControls.ACTION_ENGINE) command = "/vui_engine";\n        else if (action == VostokVehicleControls.ACTION_LIGHTS) command = "/vui_lights";\n        else if (action == VostokVehicleControls.ACTION_LOCK) command = "/vui_lock";\n        else return;\n\n        try {\n            Method sendCommand = activity.getClass().getMethod("sendCommand", byte[].class);\n            byte[] encoded = command.getBytes(Charset.forName("windows-1251"));\n            sendCommand.invoke(activity, (Object) encoded);\n        } catch (Throwable ignored) {\n        }\n    }'''
if 'private void dispatchVehicleAction(int action)' not in text:
    if text.count(anchor2) != 1:
        raise SystemExit('031E VostokUiBridge action-dispatch anchor mismatch')
    text = text.replace(anchor2, addition2, 1)

bridge.write_text(text, encoding='utf-8')

interaction = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/InteractionUiManager.java'
it = interaction.read_text(encoding='utf-8')
if 'private boolean vehicleMode;' not in it:
    it = it.replace(
        '    private boolean weaponActive;\n',
        '    private boolean weaponActive;\n    private boolean vehicleMode;\n',
        1,
    )
show_anchor = '''        if (destroyed) {\n            return;\n        }\n\n        labelView.setText'''
show_replacement = '''        if (destroyed) {\n            return;\n        }\n        if (vehicleMode) {\n            if (visible) hide();\n            return;\n        }\n\n        labelView.setText'''
if 'if (vehicleMode) {' not in it:
    if it.count(show_anchor) != 1:
        raise SystemExit('031E Interaction show gate anchor mismatch')
    it = it.replace(show_anchor, show_replacement, 1)

set_context_anchor = '''    public boolean isVisible() {\n        return visible && !destroyed;\n    }'''
set_context_replacement = '''    public void setVehicleMode(boolean active) {\n        if (destroyed || vehicleMode == active) return;\n        vehicleMode = active;\n        if (active) hide();\n    }\n\n    public boolean isVisible() {\n        return visible && !destroyed;\n    }'''
if 'public void setVehicleMode(boolean active)' not in it:
    if it.count(set_context_anchor) != 1:
        raise SystemExit('031E Interaction vehicle-mode method anchor mismatch')
    it = it.replace(set_context_anchor, set_context_replacement, 1)
interaction.write_text(it, encoding='utf-8')

activity = JAVA / 'com/nvidia/devtech/NvEventQueueActivity.java'
a = activity.read_text(encoding='utf-8')
activity_anchor = '''    public void hideVostokQuest() {\n        if (mVostokUi != null) mVostokUi.hideQuest();\n    }'''
activity_replacement = '''    public void hideVostokQuest() {\n        if (mVostokUi != null) mVostokUi.hideQuest();\n    }\n\n    public void setVostokVehicleMode(boolean inVehicle) {\n        if (mVostokUi != null) mVostokUi.setVehicleMode(inVehicle);\n    }'''
if 'setVostokVehicleMode(boolean inVehicle)' not in a:
    if a.count(activity_anchor) != 1:
        raise SystemExit('031E activity vehicle-mode bridge anchor mismatch')
    a = a.replace(activity_anchor, activity_replacement, 1)
activity.write_text(a, encoding='utf-8')

speed = unique_java('Speedometer.java')
s = speed.read_text(encoding='utf-8')
if 'setVostokVehicleMode(true);' not in s:
    show = '        vostokHud.showVehicleHud();\n'
    if s.count(show) != 1:
        raise SystemExit(f'031E Speedometer show anchor mismatch: {s.count(show)}')
    s = s.replace(show, show + '        NvEventQueueActivity.getInstance().setVostokVehicleMode(true);\n', 1)
if 'setVostokVehicleMode(false);' not in s:
    hide = '        vostokHud.hideVehicleHud();\n'
    if s.count(hide) != 1:
        raise SystemExit(f'031E Speedometer hide anchor mismatch: {s.count(hide)}')
    s = s.replace(hide, hide + '        NvEventQueueActivity.getInstance().setVostokVehicleMode(false);\n', 1)
speed.write_text(s, encoding='utf-8')

flags = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/VostokUiFeatureFlags.java'
f = flags.read_text(encoding='utf-8')
if 'VOSTOK_VEHICLE_UI_ENABLED' not in f:
    f = f.replace(
        '    public static final boolean VOSTOK_SPEEDOMETER_ENABLED = true;\n',
        '    public static final boolean VOSTOK_SPEEDOMETER_ENABLED = true;\n'
        '    public static final boolean VOSTOK_VEHICLE_UI_ENABLED = true;\n',
        1,
    )
flags.write_text(f, encoding='utf-8')

controls = ROOT / 'app/src/main/java/com/blackrussia/game/vostok/ui/vehicle/VostokVehicleControls.java'
if not controls.exists():
    raise SystemExit('031E VostokVehicleControls overlay missing')

combined = '\n'.join([
    bridge.read_text(encoding='utf-8'),
    interaction.read_text(encoding='utf-8'),
    activity.read_text(encoding='utf-8'),
    speed.read_text(encoding='utf-8'),
    flags.read_text(encoding='utf-8'),
    controls.read_text(encoding='utf-8'),
])
for needle in (
    'VOSTOK_VEHICLE_UI_ENABLED = true',
    'new VostokVehicleControls(activity, this::dispatchVehicleAction)',
    'command = "/vui_engine"',
    'command = "/vui_lights"',
    'command = "/vui_lock"',
    'Charset.forName("windows-1251")',
    'public void setVehicleMode(boolean active)',
    'setVostokVehicleMode(true)',
    'setVostokVehicleMode(false)',
):
    if needle not in combined:
        raise SystemExit(f'031E verification missing: {needle}')

chat = ROOT / 'Jni source/jni/chatwindow.cpp'
if '~VOSTOK_UI~INTERACT:SHOW:NPC:' not in chat.read_text(encoding='utf-8', errors='ignore'):
    raise SystemExit('031E regression: protected 027F NPC interaction protocol missing')
if 'mChooseServer' in activity.read_text(encoding='utf-8'):
    raise SystemExit('031E regression: ChooseServer returned')
if 'vostokHud.updateVehicleState(speed, fuel, vostokCondition, mileage, engine, light, belt, lock);' not in speed.read_text(encoding='utf-8'):
    raise SystemExit('031E regression: 031D vehicle telemetry bridge missing')

print(f'Applied VOSTOK Candidate 031E vehicle UI using {speed}')
