# VOSTOK Client — STATUS 031G4 / Server Selector Decouple

Date: 2026-09-14
State: SOURCE BUILD PASS / DEVICE RUNTIME PENDING / NOT PROMOTED

## Runtime finding from 031G3

Device test reached the VOSTOK HUD, but the old donor loading artwork still reappeared and the game-entry transition remained broken.

Root cause was confirmed in the pinned donor layout/flow:

- `main_render_screen.xml` still included `@layout/br_serverselect` even after 031A removed the `ChooseServer` controller from `NvEventQueueActivity`.
- `br_serverselect.xml` owns `choose_server_root_loading`, which is `visible` by default and uses the donor `@drawable/mylogo` loading background.
- therefore the donor loading tree remained underneath the VOSTOK loading overlay; when the VOSTOK overlay was dismissed the old loading art became visible again.
- the original `ChooseServer` screen also owned the PLAY action that calls native `sendRPC(type=2, action=0)` and constructs `CNetGame`.
- 031G3 tried to restore that action on `percent > 100`, but the pinned donor splash normalizes the local GTA progress to values <= 100, so that milestone is not a reliable connection trigger.

## 031G4 correction

1. Removed `<include layout="@layout/br_serverselect" />` from the active `main_render_screen.xml` hierarchy.
   - The donor server selection/loading subtree is no longer inflated during normal VOSTOK gameplay.
   - The old `mylogo` loading background cannot reappear underneath the VOSTOK overlay through this path.

2. Removed the invalid `percent > 100` autoconnect dependency.

3. The original native server PLAY action is now issued automatically and exactly once when the VOSTOK in-game splash is shown:
   - `showSplash()` -> `issueVostokAutoConnect()` -> `sendRPC(2, "VOSTOK".getBytes(), 0)`.
   - This preserves spawn selection before connection: `VostokEntryActivity` still selects spawn mode, obtains the one-time game ticket and writes the transport identity before GTASA starts.

4. VOSTOK loading still remains until the real local-player spawn path shows the HUD, then `completeWorldEntry()` dismisses it.

## Protected lineage

031G4 must preserve:

- approved launcher art SHA-256 `8ede863d84340efc989af2fa35040d8df566c90e9c96fe7c2fede8aa8c6406e0`;
- the device-tested 031G3 native `libsamp.so` SHA-256 `b2ea73d9b46647592106b08b3f5432c67dd215ae0b41c975301f7d2da7592305`;
- 031G inventory;
- 031F radial menu;
- 031E Vehicle UI;
- 031D HUD;
- 027F Interaction protocol;
- launcher/auth/character/spawn-ticket flow.

## Source build gate

Workflow: `VOSTOK 031G4 Server Selector Decouple`
Run ID: `34848936537`
Result: SUCCESS

Validated:

- cumulative lineage through 031G4: PASS;
- donor `@layout/br_serverselect` removed from active render layout: PASS;
- no `mChooseServer` controller path in `NvEventQueueActivity`: PASS;
- splash-time one-shot autoconnect present: PASS;
- invalid `percent > 100` trigger removed: PASS;
- Android resource/Java build: PASS;
- APK assembly: PASS.

## Local device candidate

`VOSTOK_CLIENT_CANDIDATE_031G4_SERVERSELECT_DECOUPLE.apk`

SHA-256:
`9fce165ffd183bea35ab5324318684d4d38cc344e9b29c73173da387e838b813`

Final repack preserves the exact accepted launcher artwork and the device-tested 031G3 native library while taking the 031G4 Java/layout changes from the successful source build.

## Device promotion gate

Test sequence:

1. launcher main screen remains correct;
2. account/character/spawn selection remains correct;
3. press `НАЧАТЬ ИГРУ`;
4. donor loading/server-selection artwork must never appear;
5. only VOSTOK loading is visible during GTA/network startup;
6. native connection starts automatically without a server-choice screen;
7. player reaches world and VOSTOK HUD appears;
8. loading overlay disappears only after actual player spawn;
9. smoke-test Interaction, Vehicle UI, radial menu and inventory.

Do not promote to CLIENT MASTER until this device gate passes.
