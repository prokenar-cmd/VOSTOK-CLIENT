# VOSTOK Client — STATUS 031B / Unified UI Core

Date: 2026-09-13
State: SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED

## Base

031B is cumulative on the confirmed client lineage through Candidate 027F and Candidate 031A.

Protected behavior remains unchanged:

- launcher/auth/ticket flow;
- 031A VOSTOK loading architecture and no-ChooseServer route;
- Candidate 027F NPC-only Interaction protocol and button;
- native controls and confirmed 022/023/024/026 fixes;
- legacy HUD/speedometer remain enabled until their VOSTOK replacements pass separate gates.

031A device smoke is still pending and therefore 031B must not be promoted to CLIENT MASTER yet.

## Implemented in 031B

### Central screen manager

Added `VostokUiManager` as the common owner for future VOSTOK client screens.

It provides:

- registered screen controllers;
- persistent overlay state;
- transient/modal screen stack;
- `openScreen` / `closeScreen` / `closeAllTransient`;
- common Back handling;
- common game-input lock state;
- stable numeric wire ids for future native/server UI commands.

Reserved screen ids currently include HUD, speedometer, Interaction, radial menu, inventory, phone, tablet, character creation, spawn selection and settings.

No new visual HUD/menu is drawn in 031B.

### Safe area + scaling

Added `VostokUiMetrics`:

- shared 1920x1080 design coordinate scale;
- dp conversion;
- runtime screen size tracking;
- system inset tracking;
- Android display-cutout safe insets on supported devices;
- listener API for future responsive screens.

### Controlled migration flags

Added `VostokUiFeatureFlags`.

Only the already confirmed VOSTOK Interaction surface is marked active. VOSTOK HUD, speedometer, radial, inventory, phone, tablet, character creator and spawn selector remain disabled so 031B cannot silently replace a working legacy surface.

### Activity integration

`apply_vostok_candidate_031b.py` adds generic hooks to the cumulative `NvEventQueueActivity`:

- VOSTOK UI Core gets first refusal on Back;
- full-screen VOSTOK screens can block native touch/camera input;
- when no VOSTOK modal is open, original native Back/touch behavior remains unchanged;
- generic `showVostokUiScreen`, `hideVostokUiScreen`, `closeAllVostokUi` bridge methods are available for later candidates;
- the 031A no-ChooseServer contract is explicitly rechecked.

The working Interaction UI code itself was not rewritten in 031B.

## Build validation

Workflow: `VOSTOK 031B UI Core Check`
Run ID: `34768106770`
Result: SUCCESS
Head SHA tested: `21660324aa2b71da1a261ccb0c89fccf95afb167`

Successful gates:

- full cumulative VOSTOK patch application through 031B: PASS;
- Account Core validator: PASS;
- 031B architecture/static guards: PASS;
- 031A no-ChooseServer regression guard: PASS;
- 027F typed Interaction transport guard: PASS;
- Android resources: PASS;
- Java compile: PASS;
- debug APK assembly: PASS;

Source-check artifact:

- name: `VOSTOK-031B-ui-core-source-check-apk`
- artifact id: `10321069019`
- artifact ZIP digest: `sha256:037cf7b674d21df5ef4d9ff1712ed277a69baa1b88f0deefdb7dcf9ea84112c9`

This artifact is a source/build check only; do not promote it as the final device APK because the protected canonical launcher-art binary is still a separate packaging gate.

## Device gate

Before 031B promotion, run cumulative device smoke after/with 031A:

1. launcher opens and auth/ticket still works;
2. Play opens VOSTOK loading and never ChooseServer;
3. loading progress/status/tips behave correctly;
4. world entry succeeds;
5. character movement/camera remain normal when no VOSTOK modal is open;
6. Android/native Back behavior remains normal when no VOSTOK modal is open;
7. Interaction appears for NPC only and executes normally;
8. reconnect regression passes.

031B remains CURRENT SOURCE CANDIDATE until that device gate passes.
