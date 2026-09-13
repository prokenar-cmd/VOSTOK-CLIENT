# VOSTOK Client — STATUS 031A / Loading & Entry Foundation

Date: 2026-09-13
State: SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED

## Protected baseline

The last full APK delivered before 031A was:

`VOSTOK_CLIENT_CANDIDATE_027F_NPC_ONLY_INTERACTION.apk`

Known delivered APK SHA-256:

`3e301764b4d1196c7f552666aa9ba19d42a16594bb756fe2cd30e5dc32718a20`

Candidate 027F was made from the verified Candidate 026 launcher-fixed APK by replacing only `libsamp.so` apart from APK signature metadata. The accepted launcher visual therefore remains a protected baseline.

Accepted launcher JPEG contract:

- source visual: `VOSTOK_LAUNCHER_BACKGROUND_CLEAN_019.jpg`
- dimensions: 1891x831
- SHA-256: `8ede863d84340efc989af2fa35040d8df566c90e9c96fe7c2fede8aa8c6406e0`
- do not replace it with the old 15 KB `vostok_launcher_reference.webp`, donor artwork, recompressed/cropped copies, screenshots or generated approximations.

## 031A scope implemented

### Block A — VOSTOK loading layer

- Added `VostokLoadingOverlay`.
- Loading background is a separate replaceable artwork resource.
- Progress bar is a live Android UI element, not baked into the image.
- Percentage/status are live UI elements.
- Tips are live UI text and are not baked into the image.
- Tips are centralized in `res/values/vostok_loading.xml` and can be edited later without replacing loading artwork or native code.
- No Android/donor spinner is used by the VOSTOK loading overlay.
- Added `VostokLoadingStateManager` for loading state/progress mapping.

### Block B — remove server chooser from normal flow

- Legacy `ChooseServer` is no longer opened when native loading completes.
- Normal VOSTOK entry no longer requires a user-facing server selection page.
- Existing auth/ticket/startup architecture is preserved.

### Block C — entry routing foundation

Added `VostokEntryRouter` with explicit future destinations:

- `AUTHORIZATION`
- `CHARACTER_CREATION`
- `SPAWN_SELECTION`

Visual Character Creation and Spawn Selection screens are intentionally deferred until their design is approved. 031A only provides the routing/state foundation.

## Preserved cumulative lineage

031A source-check applies the confirmed client lineage in order:

1. VOSTOK UI Foundation
2. native UI bridge
3. Candidate 022 native control restore
4. Candidate 023 Interaction transport
5. Account Core validation
6. Candidate 024 Russian/compact UI patch
7. Candidate 026 native Russian text guard
8. Candidate 027F NPC-only Interaction transport guard
9. Candidate 031A loading/entry patch

Interaction protocol remains typed `~VOSTOK_UI~INTERACT:SHOW:NPC:`.

## Build validation

Workflow: `VOSTOK 031A Source Check`
Run ID: `34764816540`
Result: SUCCESS

Verified in CI:

- cumulative patch application: PASS
- Account Core validator: PASS
- 031A source guards: PASS
- Android resources: PASS
- Java compile: PASS
- debug APK assembly: PASS

The source-check APK is not the promotion artifact because the canonical approved launcher JPEG is not currently stored as a complete binary in the GitHub repository. The source-check intentionally uses a technical resource fallback only to prove source compilation.

## Launcher recovery blocker

Do not bypass the launcher lock and do not promote an APK containing the old/truncated 15 KB WebP or another substitute.

Historical raw GitHub Actions APK artifacts were created before the accepted launcher JPEG was post-patched, so extracting the launcher from those raw artifacts does not reproduce the accepted hash. The final accepted 026/027F APK was locally repacked with the exact 1891x831 JPEG.

Until the exact protected binary is recovered into the canonical repository/build input, 031A remains a source candidate rather than a device candidate.

## Promotion gate

Before CLIENT MASTER promotion:

1. restore the exact approved launcher JPEG with SHA-256 `8ede863d84340efc989af2fa35040d8df566c90e9c96fe7c2fede8aa8c6406e0`;
2. build the cumulative full APK with current 027F native lineage + 031A;
3. verify launcher image hash inside the APK;
4. device smoke: launcher -> Play -> VOSTOK loading -> no ChooseServer -> ticket/auth -> world;
5. verify no spinner, live progress/status/tips, controls, Interaction and reconnect regression;
6. only then promote.
