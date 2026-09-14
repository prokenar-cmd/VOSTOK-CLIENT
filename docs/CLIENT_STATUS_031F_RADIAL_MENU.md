# VOSTOK Client — Candidate 031F Radial Menu

Date: 2026-09-14
State: BUILD VERIFIED / DEVICE RUNTIME PENDING
Base: `candidate-031e-vehicle-ui` @ `b64cb665776791b9b2ba99adb04b9678fe272351`
Build head: `2bb193d267caae8dd40f02e66bb885ab7952edff`

## Approved radial structure

The first-level radial menu is centered on screen and intentionally smaller than the earlier large mockup (initial implementation uses 60% of the shorter display dimension; device testing will tune it).

Only one wheel is visible at a time. There are no side branches, helper cards, mini-panels or extra blocks.

Root sectors are fixed:
- top-left: `Персонаж`;
- top-right: `Документы`;
- bottom-left: `Анимации`;
- bottom-right: `Быстрые действия`.

Root center: `Закрыть меню`.
Nested center: `Назад`.
A nested level fully replaces the previous wheel.

## Implemented navigation

- `Документы` -> `Паспорт` / `Лицензии` / `Медкарта` -> nearby-player level.
- `Быстрые действия` -> nearby-player level -> selected-player actions (`Поздороваться`, `Обмен`, `Передать деньги`).
- `Анимации` -> `Эмоции` / `Жесты` / `Позы`.
- `Персонаж` is intentionally not replaced with a fake statistics screen; the dedicated character-statistics window remains a later system.
- Nearby players are not fabricated client-side. Until an authoritative feed supplies players, the level reports `Нет игроков рядом`.

## Integration

- Uses existing `VostokUiManager.Screen.RADIAL_MENU` (wire id 10).
- Radial screen blocks GTA touch/camera input through the existing UI Core contract while open.
- The VOSTOK menu HUD action now opens screen 10 instead of the old donor menu path.
- Nested Back is consumed by the radial controller; root Back closes the radial screen.
- `RadialMenuContract` remains available for future authoritative/populated radial requests.
- `VOSTOK_RADIAL_ENABLED = true` for this candidate.

## Protected systems preserved

The full source lineage through 031E is applied before 031F. Static gates verified that the following contracts remain present:
- 031E vehicle UI and `/vui_engine`, `/vui_lights`, `/vui_lock` command bridge;
- 031E vehicle-mode suppression of generic Interaction UI;
- 031D HUD / speedometer telemetry integration;
- 027F NPC interaction control protocol;
- legacy `ChooseServer` remains absent.

Binary comparison with the uploaded/accepted 031E APK (excluding `META-INF`) found no added or removed APK entries. Only `AndroidManifest.xml` (candidate version identity) and `classes2.dex` (031F Java UI code) differ. `lib/armeabi-v7a/libsamp.so` is byte-identical to 031E.

## Build gate

GitHub Actions workflow: `VOSTOK 031F Radial Menu Check`
Run: `34798246285`
Result: **SUCCESS**
Artifact: `VOSTOK-031F-radial-menu-source-check-apk`
Artifact ID: `10330138594`

Final APK SHA-256:
`79f213962574de14513666254d0712ad70ab373d81c88dc0558e0ba6aeab353c`

Embedded `lib/armeabi-v7a/libsamp.so` SHA-256:
`8188216cec32fba5db9e81bcae0da6ed294c317b1fa5537c8076ee693a7ce2cf`

## Device gate

Do **not** promote 031F to MASTER yet. Device test must verify:
1. launcher/auth/loading flow remains unchanged;
2. normal entry into the world still works;
3. current 031E HUD / vehicle UI / Interaction behavior is unchanged;
4. menu HUD action opens one centered radial wheel;
5. root has exactly the four approved sectors and no side/mini blocks;
6. root center closes the menu;
7. Documents/Animations/Quick Actions replace the current wheel rather than opening side branches;
8. nested center returns one level with `Назад`;
9. opening the radial blocks character/camera gameplay input but radial taps work;
10. closing it restores normal gameplay input.

Note: CI debug APK signing keys can differ between workflow runs. If Android refuses an in-place update over 031E due to signature mismatch, uninstall the previous debug APK before installing 031F. This is a test-signing issue, not a gameplay/source regression.
