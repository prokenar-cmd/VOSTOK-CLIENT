# CLIENT STATUS 031G — INVENTORY UI FOUNDATION

Date: 2026-09-14

## State

**SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED**

Working branch: `candidate-031g-inventory`
Base candidate: `candidate-031f-radial-menu`
031F base head: `77c5d10ffce63ba4f8c76d01afd2e7973515fbc5`
031G successful build head: `aed401a703f221376a05249ded082cafb9b28663`

GitHub Actions workflow: `VOSTOK 031G Inventory Check`
Successful run ID: `34809401967`
Result: **SUCCESS**

Artifact: `VOSTOK-031G-inventory-source-check-apk`
Artifact ID: `10334555641`
Artifact ZIP digest: `sha256:753af7108abebe3496ffabf6d1fb3c11a3b3047fd22bd6f5f5fa43a6cbb8d1ad`
Extracted APK SHA-256: `ccb9befa8ccfbd598c9ad329761e302103b5e96e0a708fcf7d6365553d7c7871`

## Approved 031G inventory contract

- Inventory is a dedicated VOSTOK UI screen (`Screen 11`) opened by the existing HUD inventory button.
- The approved layout is a large centered graphite/warm-orange window with:
  - header/title;
  - current/max carried weight;
  - character/equipment column on the left;
  - 20 inventory slots in the center (5 x 4);
  - selected-item information and contextual actions on the right;
  - close button in the top-right.
- There is **no mobile quick-access row** at the bottom.
- Equipment slots prepared for: `Голова`, `Лицо`, `Торс`, `Руки`, `Спина`.
- No passport/licence/medical-card items are invented inside inventory; documents belong to the radial-menu document flow.
- Money is not represented as an inventory slot item.

## Item data / balance rules for this candidate

- The project does not yet have a final item catalogue.
- Temporary fallback weight: **100 grams per item unit**.
- Stack weight = `count * item weight`.
- Temporary inventory capacity: **30 kg**.
- These values are technical placeholders and will be tuned during later balancing/layout/optimization.
- 031G adds an item contract that allows per-item weight later without redesigning the UI.

## Icons

- 031G does not require final bespoke item art.
- Technical placeholder icons are drawn client-side by category (`food`, `drink`, `medical`, `tool`, `clothing`, `accessory`, `key`, `resource`, `electronics`, generic).
- Placeholder item art carries a small `ТЕХ` marker.
- Final item icons can be added incrementally as real server items are introduced, without changing Inventory Core.

## Authoritative data policy

- The default inventory snapshot is empty.
- 031G does not fabricate production items or fake server contents.
- A stable Java-side `InventoryContract.Snapshot` feed is prepared for later authoritative item data.
- Actions (`Использовать`, `Передать`, `Выбросить`, equip/unequip) are contextual and require an action sink; no fake server commands are invented in 031G.
- The left character area uses a technical mannequin in 031G; a real 3D character-preview bridge is a later enhancement and is not faked here.

## Protected lineage

031G is cumulative over 031F and preserves the protected systems from previous candidates:

- 031F radial menu remains enabled and registered;
- 031E Vehicle UI commands/transport remain present;
- 031D HUD/speedometer lineage remains present;
- 027F NPC Interaction protocol remains present;
- legacy server chooser remains removed.

APK comparison 031F -> 031G (excluding `META-INF`):

- added entries: **0**
- removed entries: **0**
- changed entries: `AndroidManifest.xml`, `classes2.dex`
- `lib/armeabi-v7a/libsamp.so` unchanged byte-for-byte
- preserved libsamp SHA-256: `8188216cec32fba5db9e81bcae0da6ed294c317b1fa5537c8076ee693a7ce2cf`

## Promotion gate

Do **not** promote 031G to MASTER/main until a later device smoke verifies at minimum:

1. launcher/auth/loading still reaches the world;
2. HUD inventory button opens the VOSTOK inventory screen;
3. inventory is centered and readable on the target Android device;
4. 20-slot grid, equipment column, info panel and close control fit without overlap;
5. game movement/camera input is blocked while inventory is open and returns after close;
6. Android Back closes inventory cleanly;
7. empty inventory state is stable and does not invent items;
8. 031F radial menu still opens/closes correctly;
9. 031E vehicle HUD/actions and Interaction suppression still work;
10. chat, minimap and native gameplay controls remain intact outside modal UI.

Until device smoke is available, 031G remains a **Candidate** only.
