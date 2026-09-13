# CLIENT STATUS 031D — HUD V1

Date: 2026-09-14

## State

**SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED**

Working branch: `candidate-031d-hud-v1`
Protected base/main before 031D: `f094bd4d0f14230deb34c88c2694c460279733f4` (031C)
Verified 031D build head: `f171fcbbe228d7de6ff8e3f9e6ad3f5a54413840`

GitHub Actions workflow: `VOSTOK 031D HUD v1 Check`
Run ID: `34787910146`
Result: **SUCCESS**

Artifact: `VOSTOK-031D-hud-v1-source-check-apk`
Artifact ID: `10326847584`
Artifact ZIP SHA-256: `4cbfc862c9b1a56584fe5e4e8e5fa7b9657855bc1e5275bcc9d2a4d788621e40`
Extracted APK SHA-256: `9e9e3bba9f293c535c85637abb04d32985ee46c44093d3ef1d72fd0d98cd97c1`

## Verified source/build scope

Full cumulative lineage was applied successfully:

`UI foundation -> native UI bridge -> 022 -> 023 -> Account validation -> 024 -> 026 -> 027F -> 031A -> 031B -> 031C -> 031D`

The following gates passed:

- pinned donor checkout
- cumulative VOSTOK patches through 031D
- 031D HUD contract checks
- protected 027F NPC interaction protocol check
- no legacy `ChooseServer` regression
- no forbidden slogan copy in HUD
- Android resource processing
- Java compilation
- debug APK assembly
- artifact upload

## 031D implementation

### Player HUD

- VOSTOK programmatic HUD surface (`VostokHudController`)
- real money value
- real HP value
- real armor value
- real hunger/satiety value from the existing `UpdateHudInfo` callback
- graphite/orange angular telemetry language
- thin telemetry instead of donor status cards
- donor radar/chat/weapon/wanted behavior kept alive for compatibility
- replaced donor quick-access/status visuals hidden at runtime

### Quick access rail

Icon-only controls, no captions:

- Menu — reuses existing menu transport
- Shop — reuses existing donor shop transport
- Inventory — routed to VOSTOK UI Core screen id 11; visual button exists but remains functionally inert until the 031G Inventory controller is registered
- Tablet — routed to VOSTOK UI Core screen id 13; visual button exists but remains functionally inert until the 031H Tablet controller is registered

Camera-button removal is implemented as best-effort suppression of known Java/XML view ids. Device smoke must confirm whether the actual current runtime camera control is one of those views or requires a separate native control change.

### Quest block

- hidden by default
- no fake/default quest is shown
- added authoritative Java/native-facing bridge:
  - `showVostokQuest(title, objective, current, total)`
  - `hideVostokQuest()`
- actual gameplay/server quest wiring remains a later integration task; 031D only provides the HUD endpoint

### Vehicle HUD

The existing donor `Speedometer.UpdateSpeedInfo(...)` remains the authoritative data source. 031D consumes the real values:

- speed
- fuel
- vehicle condition
- mileage
- engine
- lights
- belt
- lock

The old donor speedometer visual is hidden when vehicle HUD is shown; VOSTOK draws the replacement instrument module. Native/donor driving controls are intentionally not deleted in this candidate.

## Promotion gate still required

031D must **not** be merged/promoted to MASTER/main until device smoke verifies at minimum:

1. launcher/auth/loading/031C entry flow still reaches the world;
2. chat remains visible and usable;
3. minimap remains visible and is not covered incorrectly;
4. VOSTOK money/HP/armor/satiety update correctly;
5. old replaced HUD visuals do not reappear after opening/closing chat keyboard;
6. Menu and Shop quick buttons invoke the intended existing actions;
7. Inventory and Tablet do not crash while their future controllers are absent;
8. camera button is actually absent on the current device/runtime;
9. entering/exiting a vehicle switches the VOSTOK speedometer correctly;
10. speed/fuel/condition/mileage/status values update correctly;
11. native driving/action controls remain usable;
12. Interaction 027F behavior remains correct;
13. no touch layer blocks normal player/camera controls outside intended VOSTOK buttons.

Until those checks pass, 031D remains a **Candidate** only.
