#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('client')
JAVA = ROOT / 'app/src/main/java'


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'031F {label} anchor mismatch in {path}: {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


flags = JAVA / 'com/blackrussia/game/vostok/ui/VostokUiFeatureFlags.java'
replace_once(
    flags,
    '    public static final boolean VOSTOK_RADIAL_ENABLED = false;',
    '    public static final boolean VOSTOK_RADIAL_ENABLED = true;',
    'radial feature flag',
)

bridge = JAVA / 'com/blackrussia/game/vostok/ui/VostokUiBridge.java'
b = bridge.read_text(encoding='utf-8')

if 'import com.blackrussia.game.vostok.ui.radial.VostokRadialMenuController;' not in b:
    anchor = 'import com.blackrussia.game.vostok.ui.hud.VostokHudController;\n'
    if b.count(anchor) != 1:
        raise SystemExit('031F radial import anchor mismatch')
    b = b.replace(
        anchor,
        anchor + 'import com.blackrussia.game.vostok.ui.radial.VostokRadialMenuController;\n',
        1,
    )

if 'private final VostokRadialMenuController radialMenu;' not in b:
    anchor = '    private final VostokVehicleControls vehicleControls;\n'
    if b.count(anchor) != 1:
        raise SystemExit('031F vehicleControls field anchor missing; 031E lineage not applied')
    b = b.replace(
        anchor,
        anchor + '    private final VostokRadialMenuController radialMenu;\n',
        1,
    )

if 'radialMenu = new VostokRadialMenuController(activity, uiManager);' not in b:
    anchor = '        uiManager = new VostokUiManager(activity);\n\n'
    if b.count(anchor) != 1:
        raise SystemExit('031F UI manager constructor anchor mismatch')
    addition = (
        '        uiManager = new VostokUiManager(activity);\n'
        '        radialMenu = new VostokRadialMenuController(activity, uiManager);\n'
        '        if (VostokUiFeatureFlags.VOSTOK_RADIAL_ENABLED) {\n'
        '            uiManager.registerScreen(VostokUiManager.Screen.RADIAL_MENU, radialMenu);\n'
        '        }\n\n'
    )
    b = b.replace(anchor, addition, 1)

old_back = '''    public boolean onBackPressed() {
        return !destroyed && uiManager.handleBack();
    }'''
new_back = '''    public boolean onBackPressed() {
        if (destroyed) return false;
        // Nested radial levels consume Back internally before UI Core closes the screen.
        if (radialMenu.handleBackInside()) return true;
        return uiManager.handleBack();
    }'''
if new_back not in b:
    if b.count(old_back) != 1:
        raise SystemExit('031F VostokUiBridge back anchor mismatch')
    b = b.replace(old_back, new_back, 1)

if 'radialMenu.shutdown();' not in b:
    anchor = '            vehicleControls.shutdown();\n            vostokHud.shutdown();\n'
    if b.count(anchor) != 1:
        raise SystemExit('031F 031E shutdown anchor mismatch')
    b = b.replace(
        anchor,
        '            vehicleControls.shutdown();\n            radialMenu.shutdown();\n            vostokHud.shutdown();\n',
        1,
    )

bridge.write_text(b, encoding='utf-8')

hud = JAVA / 'com/blackrussia/game/vostok/ui/hud/VostokHudController.java'
h = hud.read_text(encoding='utf-8')
old_menu = '''        if (action == 0) {
            invoke("showMenuu", new Class<?>[0]);
            invoke("togglePlayer", new Class<?>[]{int.class}, 1);
        } else if (action == 1) {'''
new_menu = '''        if (action == 0) {
            // 031F: the VOSTOK menu slot opens the centered radial menu.
            invoke("showVostokUiScreen", new Class<?>[]{int.class}, 10);
        } else if (action == 1) {'''
if new_menu not in h:
    if h.count(old_menu) != 1:
        raise SystemExit('031F HUD menu action anchor mismatch')
    h = h.replace(old_menu, new_menu, 1)
hud.write_text(h, encoding='utf-8')

controller = JAVA / 'com/blackrussia/game/vostok/ui/radial/VostokRadialMenuController.java'
if not controller.exists():
    raise SystemExit('031F radial controller overlay missing')

combined = '\n'.join([
    flags.read_text(encoding='utf-8'),
    bridge.read_text(encoding='utf-8'),
    hud.read_text(encoding='utf-8'),
    controller.read_text(encoding='utf-8'),
])

for needle in (
    'VOSTOK_RADIAL_ENABLED = true',
    'new VostokRadialMenuController(activity, uiManager)',
    'registerScreen(VostokUiManager.Screen.RADIAL_MENU, radialMenu)',
    'radialMenu.handleBackInside()',
    'showVostokUiScreen", new Class<?>[]{int.class}, 10',
    '"Персонаж"',
    '"Документы"',
    '"Анимации"',
    '"Быстрые действия"',
    '"Закрыть меню"',
    '"Назад"',
    '"Паспорт"',
    '"Лицензии"',
    '"Медкарта"',
    '"Нет игроков рядом"',
):
    if needle not in combined:
        raise SystemExit(f'031F verification missing: {needle}')

# Protected 031E / 027F / 031D contracts must survive unchanged.
for needle in (
    'VOSTOK_VEHICLE_UI_ENABLED = true',
    'command = "/vui_engine"',
    'command = "/vui_lights"',
    'command = "/vui_lock"',
    'Charset.forName("windows-1251")',
    'public void setVehicleMode(boolean active)',
):
    if needle not in combined + '\n' + (JAVA / 'com/blackrussia/game/vostok/ui/InteractionUiManager.java').read_text(encoding='utf-8'):
        raise SystemExit(f'031F regression: protected 031E contract missing: {needle}')

activity = JAVA / 'com/nvidia/devtech/NvEventQueueActivity.java'
if 'mChooseServer' in activity.read_text(encoding='utf-8'):
    raise SystemExit('031F regression: ChooseServer returned')
chat = ROOT / 'Jni source/jni/chatwindow.cpp'
if '~VOSTOK_UI~INTERACT:SHOW:NPC:' not in chat.read_text(encoding='utf-8', errors='ignore'):
    raise SystemExit('031F regression: protected 027F NPC interaction protocol missing')

print('Applied VOSTOK Candidate 031F centered radial menu')
