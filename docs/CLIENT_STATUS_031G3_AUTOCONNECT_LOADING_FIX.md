# VOSTOK Client — STATUS 031G3 / Autoconnect & Loading Transition Fix

Date: 2026-09-14
State: BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED

## Runtime evidence

Device recording after 031G2 showed this sequence after Spawn Selection -> Start Game:

1. legacy donor loading artwork appears;
2. VOSTOK loading overlay appears and completes local GTA loading;
3. VOSTOK overlay disappears;
4. legacy donor loading artwork returns and remains indefinitely;
5. the game world is never reached.

031G2 already fixed the immediate native BASS null-call crash, so this is a later entry-flow defect.

## Root cause

031A correctly removed the user-facing donor ChooseServer page, but the donor ChooseServer also performed a non-visual responsibility: its Play action called `sendRPC(2, ..., 0)`.

Native type=2/action=0 is the proven path that constructs `CNetGame` for the configured VOSTOK DEV endpoint. Removing ChooseServer without replacing that call left the client after local GTA loading with no automatic multiplayer connection trigger.

The legacy artwork visible in the recording is the donor `res/drawable/mylogo.jpg` resource underneath the VOSTOK overlay.

## 031G3 source fix

- after native loading reports `percent > 100`, issue `sendRPC(2, "VOSTOK".getBytes(), 0)` once;
- do not dismiss the VOSTOK loading overlay merely because local GTA loading reached completion;
- keep VOSTOK loading visible during multiplayer authentication/spawn;
- dismiss the VOSTOK loading overlay only when native/server flow calls `showHud()`;
- block later native splash callbacks from re-showing the overlay after world entry;
- do not restore the donor ChooseServer screen;
- preserve Inventory, Radial Menu, Vehicle UI, HUD, Interaction 027F and Account/Entry flows.

## Build validation

Workflow: `VOSTOK 031G3 Autoconnect Loading Fix`
Run: `34843462679`
Result: SUCCESS

Validated:
- cumulative lineage through 031G3: PASS;
- 031G3 source guards: PASS;
- Android resources/Java: PASS;
- APK assembly: PASS.

Source-build artifact ID: `10347106050`.

## Final device-candidate packaging

The final device APK is post-packaged from the successful 031G3 source build with the protected runtime assets already proven in 031G2/G1:

- `libsamp.so` = 031G2 package-safe BASS/native Interaction binary;
- approved launcher payload SHA-256 = `8ede863d84340efc989af2fa35040d8df566c90e9c96fe7c2fede8aa8c6406e0`;
- donor `mylogo.jpg` replaced with a neutral VOSTOK-dark technical background so donor artwork cannot flash before/after the Java loading overlay;
- same signing certificate as 031G1/031G2, so normal update install is supported.

Final APK:
`VOSTOK_CLIENT_CANDIDATE_031G3_AUTOCONNECT_LOADING_FIX.apk`

Final APK SHA-256:
`7eeea5c7253a2432cba2b49784e7ec75846c6afcb5cb19173208d42426305ee8`

## Promotion gate

Do not promote to CLIENT MASTER until device smoke verifies:

1. Spawn Selection -> Start Game;
2. no donor tiger/car loading artwork appears;
3. VOSTOK loading remains on-screen through multiplayer connection;
4. automatic connection reaches VOSTOK DEV without ChooseServer;
5. ticket authentication succeeds and character enters the world;
6. HUD/controls appear after entry;
7. no regression in Interaction, Vehicle UI, Radial Menu or Inventory.
