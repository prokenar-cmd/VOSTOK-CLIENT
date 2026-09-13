# VOSTOK Client — STATUS 031C / Character Creation + Spawn Selection

Date: 2026-09-13
State: SOURCE BUILD VERIFIED / DEVICE RUNTIME PENDING / NOT PROMOTED

## Base

031C is cumulative on the confirmed client lineage through 031B UI Core. 031A loading/entry cleanup, 027F Interaction transport, auth/ticket flow and protected launcher behavior are preserved.

## Product contract

### New character

`Authorization -> Character Creation -> fixed starter spawn -> game`

Character Creation contains only:

- ready in-game model/skin selection;
- first name;
- last name;
- birth date;
- nationality;
- Create Character action.

There is no face editor, clothing system, hairstyle editor, hair-color editor, body editor or confirmation page.

After successful creation the new character does NOT see Spawn Selection. A START game ticket is issued immediately.

### Existing character

`Authorization -> Spawn Selection -> game`

Spawn cards:

- `Вокзал` — available;
- `Точка выхода` — available only when the character has a saved previous session (`last_played > 0`);
- `Дом` — visible but locked until House Core exists and ownership is available;
- `Квартира` — visible but locked until Apartment Core exists and ownership is available.

031C backend/server currently accepts only START and LAST modes. HOME/APARTMENT fail closed and are not presented as usable actions.

## UI implementation

Added launcher-side `VostokEntryActivity` in landscape mode. This is intentionally pre-game: a new account does not yet have a character/game ticket, so creation must happen before GTASA connects to the game server.

Visual direction follows the approved VOSTOK concept:

- dark graphite panels;
- orange functional accent;
- model cards + selected model panel;
- two creation steps: `Выбор модели` and `Данные`;
- large horizontal spawn cards.

No slogans, advertising phrases or decorative marketing copy are allowed in this surface. Only functional UI labels/status messages are present.

Skin preview resources are resolved from the installed client when available. 031C does not fabricate gameplay screenshots or fake character renders; unavailable preview assets fall back to the real model id until a canonical preview pipeline is added.

## API contract

`AccountApi` now supports:

- structured character creation fields;
- selected skin id;
- `SPAWN_START=0`;
- `SPAWN_LAST=1`;
- reserved HOME/APARTMENT wire ids;
- game-ticket `spawn_mode`;
- `last_played` in character state.

The old create/ticket overloads remain for compatibility with existing launcher code.

## Build validation

Workflow: `VOSTOK 031C Entry Flow Check`
Run ID: `34774254168`
Result: **SUCCESS**

Passed:

- complete cumulative patch application through 031C;
- 031C product guards;
- forbidden customization/slogan guards;
- ChooseServer regression guard;
- Interaction 027F transport guard;
- Android resource compile;
- Java compile;
- debug APK assembly;
- artifact upload.

Artifact: `VOSTOK-031C-entry-flow-source-check-apk`
Artifact digest: `sha256:9022233ee7dce8bfeac2e246f2bb352b5d3b0db2bba5bfe0792ddf12b1c5f91a`

This is a SOURCE-CHECK artifact, not a promoted device candidate. The protected launcher-art/device gate remains separate.

## Required companion

Server/account backend must persist structured character metadata and carry `spawn_mode` in one-time game tickets. The server must apply START vs saved LAST position only at authenticated login. New characters must always use START.

## Device gate

Before promotion verify on phone:

1. no-character account -> Character Creation;
2. choose a real model and enter data;
3. successful creation -> direct game start, no spawn selector;
4. first spawn uses fixed VOSTOK starter point;
5. reconnect existing character -> Spawn Selection;
6. `Вокзал` -> fixed starter point;
7. after a played/saved session, `Точка выхода` -> saved position;
8. `Дом` / `Квартира` remain locked;
9. no face/clothes/hair/body/confirmation surfaces;
10. no slogans;
11. no ChooseServer regression;
12. loading/auth/Interaction/control regressions absent.

031C remains CANDIDATE until the companion server compiles and the device/runtime gates pass.
