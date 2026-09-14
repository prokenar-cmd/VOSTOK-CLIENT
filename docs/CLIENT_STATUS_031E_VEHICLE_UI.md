# CLIENT STATUS 031E — VEHICLE UI

Date: 2026-09-14

## State

**SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED**

Working branch: `candidate-031e-vehicle-ui`
Base candidate: `candidate-031d-hud-v1`
Verified 031E build head: `746936b409a86ec5ce8b683606fe93304e3a0b88`

GitHub Actions workflow: `VOSTOK 031E Vehicle UI Check`
Final successful run ID: `34791222558`
Result: **SUCCESS**

Artifact: `VOSTOK-031E-vehicle-ui-source-check-apk`
Artifact ID: `10327608011`
Artifact ZIP SHA-256: `23584d98381e317eabf8015ad9dd42ef8f20dba7db677ef95fe694c23f2847e2`
Extracted APK SHA-256: `d2aea203ef46b55beadd3cb7fc5c9e1d09bbbd260738cf7fe7be26822a2c5592`

## 031E implementation

- 031D VOSTOK speedometer remains the single vehicle visual; no duplicate dashboard was added.
- Small touch targets are embedded over the existing engine, lights and lock status icons.
- Vehicle actions are sent through the donor/native `sendCommand(byte[])` transport using Windows-1251.
- Client transport commands:
  - `/vui_engine`
  - `/vui_lights`
  - `/vui_lock`
- `Speedometer.ShowSpeed()` enters VOSTOK vehicle mode.
- `Speedometer.HideSpeed()` exits VOSTOK vehicle mode.
- Generic VOSTOK Interaction UI is suppressed while vehicle mode is active and can return after exit.
- Seatbelt remains status-only; no fake Seatbelt Core action was introduced.
- Native driving controls are intentionally preserved.
- 027F NPC Interaction transport and 031D telemetry bridge remain protected.

## Server companion

031E requires the matching server companion that routes `/vui_engine`, `/vui_lights`, `/vui_lock` through the existing server-authoritative Vehicle Core and removes the temporary SERVICE/JOB driver auto-start.

Server companion archive SHA-256:
`fdd89cbf7bf5639373a21ee9fc8d18b7ee246acb59702cd2722d5ad74f62edce`

Server static validation: **17/17 PASS**.
Protected DB and Interaction hashes are unchanged.
Pawn compile/runtime remains pending.

## CI history

The first 031E run reached Java compilation but failed because the patched donor `Speedometer.java` referenced `NvEventQueueActivity` without importing it. No architecture or contract gate failed.

The cumulative patch was corrected to inject `import com.nvidia.devtech.NvEventQueueActivity;`. The final run then passed:

- pinned donor checkout
- cumulative VOSTOK patches through 031E
- 031E contract checks
- Android resource processing
- Java compilation
- debug APK assembly
- artifact upload

## Promotion gate

Do **not** promote 031E to MASTER/main until device/server smoke verifies:

1. player can enter a vehicle and the VOSTOK vehicle HUD appears;
2. generic Interaction button disappears while in vehicle and returns after exit;
3. engine icon toggles the real server Vehicle Core engine state;
4. engine cannot start with zero fuel;
5. lights icon toggles the real server state;
6. lock icon obeys Vehicle Core management/ownership rules;
7. SERVICE/JOB vehicles no longer auto-start just from driver entry;
8. speed/fuel/condition/mileage/status telemetry still updates correctly;
9. native pedals/steering/horn/action controls remain usable;
10. leaving the vehicle removes the vehicle touch layer cleanly;
11. chat, minimap, HUD quick rail and 027F NPC Interaction still work;
12. launcher/auth/loading/031C entry flow still reaches the world.

Until those checks pass, 031E remains a **Candidate** only.
