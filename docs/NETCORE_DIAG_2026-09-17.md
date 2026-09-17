# JOHN ARM64 late-init SAMP diagnostic

Date: 2026-09-17

Observed device result:
- Clean GTA 2.11.311 ARM64 boots through disclaimer and RenderWare.
- `VOSTOK LATEINIT: original CGame::InitialiseRenderWare returned 1`.
- Main menu auto-start invokes John `CGame::StartGame()` and returns.
- John NvFOpen then redirects clean GTA startup files to donor `SAMP/*` files (`vehicles.ide`, `peds.ide`, `gta.dat`, `script.img`, `weapon.dat`, `main.scm`).
- Startup reaches repeated `Loading mainv1` / `SAMP/main.scm` and does not reach `DoInitStuff()` / network creation.
- No `VOSTOK LATEINIT: GTA ready, starting headless SAMP core`, no `connecting host`, no `CNetGame created` in the latest run.

Conclusion:
The next POC must split donor game/script replacement from the networking core. Preserve the confirmed clean GTA boot path; after clean world/render is alive, install only the Render2d network loop and initialize RakNet from settings.ini. Do not call the full John `CGame::StartGame()` path and do not redirect core GTA data/script files to donor `SAMP/*` assets for this POC.

031K MASTER remains untouched.
