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
        raise SystemExit(f'031G {label} anchor mismatch in {path}: {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


flags = JAVA / 'com/blackrussia/game/vostok/ui/VostokUiFeatureFlags.java'
replace_once(
    flags,
    '    public static final boolean VOSTOK_INVENTORY_ENABLED = false;',
    '    public static final boolean VOSTOK_INVENTORY_ENABLED = true;',
    'inventory feature flag',
)

controller = JAVA / 'com/blackrussia/game/vostok/ui/inventory/VostokInventoryController.java'
contract = JAVA / 'com/blackrussia/game/vostok/ui/inventory/InventoryContract.java'
if not controller.exists() or not contract.exists():
    raise SystemExit('031G inventory overlay files missing')

# Keep overlay source easy to review while fixing one Android RectF-only typo in the
# generated candidate tree. No gameplay behavior is changed by this cleanup.
c = controller.read_text(encoding='utf-8')
c = c.replace('            primaryAction.setTag(primaryCode);\n', '')
controller.write_text(c, encoding='utf-8')

bridge = JAVA / 'com/blackrussia/game/vostok/ui/VostokUiBridge.java'
b = bridge.read_text(encoding='utf-8')

if 'import com.blackrussia.game.vostok.ui.inventory.InventoryContract;' not in b:
    anchor = 'import com.blackrussia.game.vostok.ui.hud.VostokHudController;\n'
    if b.count(anchor) != 1:
        raise SystemExit('031G inventory import anchor mismatch')
    b = b.replace(
        anchor,
        anchor
        + 'import com.blackrussia.game.vostok.ui.inventory.InventoryContract;\n'
        + 'import com.blackrussia.game.vostok.ui.inventory.VostokInventoryController;\n',
        1,
    )

if 'private final VostokInventoryController inventory;' not in b:
    anchor = '    private final VostokRadialMenuController radialMenu;\n'
    if b.count(anchor) != 1:
        raise SystemExit('031G radial field anchor missing; 031F lineage not applied')
    b = b.replace(anchor, anchor + '    private final VostokInventoryController inventory;\n', 1)

if 'inventory = new VostokInventoryController(activity, uiManager);' not in b:
    anchor = '''        radialMenu = new VostokRadialMenuController(activity, uiManager);
        if (VostokUiFeatureFlags.VOSTOK_RADIAL_ENABLED) {
            uiManager.registerScreen(VostokUiManager.Screen.RADIAL_MENU, radialMenu);
        }

'''
    if b.count(anchor) != 1:
        raise SystemExit('031G radial constructor anchor missing; 031F lineage not applied')
    addition = anchor + '''        inventory = new VostokInventoryController(activity, uiManager);
        if (VostokUiFeatureFlags.VOSTOK_INVENTORY_ENABLED) {
            uiManager.registerScreen(VostokUiManager.Screen.INVENTORY, inventory);
        }

'''
    b = b.replace(anchor, addition, 1)

snapshot_method = '''    public void setInventorySnapshot(List<InventoryContract.Item> items, int maxWeightGrams) {
        runOnUiThread(() -> inventory.setSnapshot(new InventoryContract.Snapshot(items, maxWeightGrams)));
    }

    public void setInventoryActionSink(InventoryContract.ActionSink sink) {
        runOnUiThread(() -> inventory.setActionSink(sink));
    }
'''
if 'public void setInventorySnapshot(' not in b:
    anchor = '''    public void setVehicleMode(boolean inVehicle) {
        runOnUiThread(() -> {
            vehicleControls.setVehicleMode(inVehicle);
            interactionManager.setVehicleMode(inVehicle);
        });
    }
'''
    if b.count(anchor) != 1:
        raise SystemExit('031G vehicle-mode anchor missing; 031E lineage not applied')
    b = b.replace(anchor, anchor + '\n' + snapshot_method, 1)

if 'inventory.shutdown();' not in b:
    anchor = '''            vehicleControls.shutdown();
            radialMenu.shutdown();
            vostokHud.shutdown();
'''
    if b.count(anchor) != 1:
        raise SystemExit('031G shutdown anchor missing; 031F lineage not applied')
    b = b.replace(
        anchor,
        '''            vehicleControls.shutdown();
            radialMenu.shutdown();
            inventory.shutdown();
            vostokHud.shutdown();
''',
        1,
    )

bridge.write_text(b, encoding='utf-8')

hud = JAVA / 'com/blackrussia/game/vostok/ui/hud/VostokHudController.java'
h = hud.read_text(encoding='utf-8')
if 'showVostokUiScreen", new Class<?>[]{int.class}, 11' not in h:
    raise SystemExit('031G inventory HUD slot 11 missing')

combined = '\n'.join([
    flags.read_text(encoding='utf-8'),
    bridge.read_text(encoding='utf-8'),
    hud.read_text(encoding='utf-8'),
    contract.read_text(encoding='utf-8'),
    controller.read_text(encoding='utf-8'),
])

for needle in (
    'VOSTOK_INVENTORY_ENABLED = true',
    'new VostokInventoryController(activity, uiManager)',
    'registerScreen(VostokUiManager.Screen.INVENTORY, inventory)',
    'setInventorySnapshot(List<InventoryContract.Item> items, int maxWeightGrams)',
    'DEFAULT_ITEM_WEIGHT_GRAMS = 100',
    'DEFAULT_CAPACITY_GRAMS = 30_000',
    'VISIBLE_SLOT_COUNT = 20',
    '"Инвентарь"',
    '"Управляйте своими предметами"',
    '"Голова"',
    '"Лицо"',
    '"Торс"',
    '"Руки"',
    '"Спина"',
    '"Информация о предмете"',
    '"Использовать"',
    '"Передать"',
    '"Выбросить"',
    '"ТЕХ"',
    'snapshot.items.size() + " / " + InventoryContract.VISIBLE_SLOT_COUNT',
):
    if needle not in combined:
        raise SystemExit(f'031G verification missing: {needle}')

for forbidden in (
    'Быстрый доступ',
    'Паспорт, лицензии',
    'primaryAction.setTag',
):
    if forbidden in controller.read_text(encoding='utf-8'):
        raise SystemExit(f'031G forbidden inventory UI fragment present: {forbidden}')

# Protected lineage checks: 031F radial, 031E vehicle UI and 027F Interaction
# remain present and are not reimplemented inside the inventory candidate.
for needle in (
    'VOSTOK_RADIAL_ENABLED = true',
    'VOSTOK_VEHICLE_UI_ENABLED = true',
    'registerScreen(VostokUiManager.Screen.RADIAL_MENU, radialMenu)',
    'radialMenu.handleBackInside()',
    'command = "/vui_engine"',
    'command = "/vui_lights"',
    'command = "/vui_lock"',
):
    if needle not in combined:
        raise SystemExit(f'031G regression: protected client contract missing: {needle}')

activity = JAVA / 'com/nvidia/devtech/NvEventQueueActivity.java'
if 'mChooseServer' in activity.read_text(encoding='utf-8'):
    raise SystemExit('031G regression: ChooseServer returned')
chat = ROOT / 'Jni source/jni/chatwindow.cpp'
if '~VOSTOK_UI~INTERACT:SHOW:NPC:' not in chat.read_text(encoding='utf-8', errors='ignore'):
    raise SystemExit('031G regression: protected 027F NPC Interaction protocol missing')

print('Applied VOSTOK Candidate 031G inventory UI foundation')
