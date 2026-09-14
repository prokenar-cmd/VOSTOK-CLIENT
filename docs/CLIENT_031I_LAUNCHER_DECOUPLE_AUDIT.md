# VOSTOK Client 031I — Launcher donor-decouple audit

Date: 2026-09-14
Base commit: `33b7d618ec09b6df06a8397d5f45eac4bd69d124` (`031H1` full-source export point)
Branch: `candidate-031i-launcher-decouple`
Promotion state: CANDIDATE ONLY until CI compile + device smoke pass.

## Why the donor UI kept returning

The active client CI reconstructs every candidate by cloning pinned `Parad1st/Black-Russia-Source` and then copying the VOSTOK overlay on top. Therefore donor launcher Activities, fragments, layouts and libraries physically return to every build unless they are explicitly removed from the final build tree. The old approach replaced selected files visually but did not establish ownership of the launcher stack.

The exported 031H1 full source confirmed the actual Android entry chain was still:

`MAIN/LAUNCHER -> com.blackrussia.launcher.activity.SplashActivity -> MainActivity`

and the missing-cache path was still:

`VOSTOK UI -> com.blackrussia.launcher.activity.LoaderActivity -> MainActivity`

The donor `SplashActivity` also initialized donor news/server models and Retrofit/Firebase calls. The donor `LoaderActivity` contained the old Black Russia cache downloader/unpacker.

## VOSTOK code that was incorrectly living inside the donor namespace

These files are VOSTOK-owned and are migrated to `com.vostok.launcher.*` by 031I:

- AccountApi
- AccountConfig
- AccountSessionStore
- GameLaunchIdentity
- VostokEntryRouter
- VostokAuthDialog
- VostokLauncherView
- current VOSTOK MainActivity logic -> VostokLauncherActivity
- VostokEntryActivity

## Donor launcher code safe to remove from the compiled client

031I physically removes the complete `com.blackrussia.launcher` source tree after migrating the VOSTOK-owned files. That deletes the donor:

- SplashActivity
- MainActivity shell
- LoaderActivity
- StoryActivity
- Donate/Forum/Monitoring/Settings fragments
- news/server/story adapters and models
- donor launcher Interface/Lists/Utils helpers

Player-facing donor layouts are removed as well, including activity_splash, activity_loader, activity_main, activity_story, donor fragments, donor server/news/story items and br_serverselect layouts.

The obsolete `com.blackrussia.game.gui.ChooseServer` class is also removed. 031H1 already has no `mChooseServer` integration and auto-connects through the proven VOSTOK native path.

Launcher-only donor artwork is removed when it becomes unreachable: old splash/server-selector/donate/forum/monitoring assets and the `AppTheme.WithSplash` window style are deleted. A residual resource audit after the patch leaves only `br_menu_*` and `br_notification` assets from the **in-game runtime bridge**, not launcher resources. Those remain because `NvEventQueueActivity`, `Menu`, `Notification`, `HudManager` and `Speedometer` still have live native/game call paths. Removing them belongs to the later game-UI decoupling stage and is deliberately not mixed into this launcher rescue.

## Dependency cleanup

Once the donor launcher package is removed, its launcher-only libraries are no longer required in the 031I build configuration. The candidate removes these build dependencies:

- Firebase Database
- Retrofit/Gson converter
- AndroidP7zip
- FileDownloader
- Glide
- RoundedImageView
- RoundCornerProgressBar
- MKLoader / other unused donor launcher UI dependencies

The remaining Android dependencies are those still referenced by the game/settings/VOSTOK launcher code (AppCompat, Material, ConstraintLayout, ini4j and the existing color picker).

## What cannot be removed yet

The game/runtime side remains intentionally isolated rather than renamed in this candidate:

- `com.blackrussia.game.core.GTASA`
- `NvEventQueueActivity` / WarMedia game host
- `libGTASA.so`
- current proven `libsamp.so` / source lineage
- the runtime-proven external cache root `BlackRussia`

The legacy cache path is now allowed to appear in the VOSTOK launcher only inside `com.vostok.launcher.game.VostokRuntimeContract`. `GameLaunchIdentity` reaches `SAMP/settings.ini` through that contract. No VOSTOK Activity, View or navigation class knows the legacy path directly.

Changing that storage root is a separate cache/native migration and must not be combined with launcher decoupling because it can invalidate the currently working GTA/SA-MP runtime.

## Updater finding

The older CLIENT_MASTER_STATE recorded a Stage 0.4 resumable/differential VOSTOK updater, but the actual exported 031H1 build tree does not contain it. The current cumulative GitHub pipeline has fallen back to the donor `LoaderActivity` from the pinned source.

031I does **not** keep that donor loader. If the existing runtime cache is present, the flow is:

`VOSTOK Launcher -> VOSTOK Entry/Spawn flow when applicable -> VOSTOK Game Loading -> GTASA -> in-game VOSTOK loading overlay -> world`

If the cache is missing, 031I fails closed on a VOSTOK screen and returns to the VOSTOK launcher. It deliberately does not download the old donor Black Russia archive. Restoring/reimplementing the proper VOSTOK updater is the next isolated launcher task after this ownership boundary is device-proven.

## Manifest ownership after 031I

The only Android launcher entry is `com.vostok.launcher.activity.VostokLauncherActivity`.

Registered player-facing VOSTOK Activities:

- VostokLauncherActivity
- VostokEntryActivity
- VostokGameLoadingActivity

The only retained old-package Activity is `com.blackrussia.game.core.GTASA`, treated strictly as the native game boundary.

Dedicated VOSTOK window themes use `vostok_loading_background` so Android does not flash the donor splash/window while transitioning between Activities.

## Preservation rule

031I is applied after the complete confirmed lineage through 031H1. It does not modify the current HUD, radial menu, inventory, interaction bridge, vehicle UI, native auto-connect, HUD editor/calibration or native crash fixes. Static CI assertions re-check those contracts before compiling.

## Promotion gate / device smoke

Do not promote 031I to MASTER until all are true:

1. GitHub Actions compile succeeds.
2. APK manifest reports VostokLauncherActivity as the launchable Activity.
3. Compiled classes contain no donor SplashActivity/MainActivity/LoaderActivity.
4. Cold app launch shows no Black Russia/donor splash, even for a single frame after Android's window appears.
5. VOSTOK launcher + Account session/auth work.
6. Character creation/spawn-selection VOSTOK flow still works when applicable.
7. Pressing PLAY shows only VOSTOK loading and enters GTASA/world.
8. Radial menu, inventory, interaction, HUD/HUD editor and vehicle UI still pass their existing smoke checks.
9. Back/relaunch/reconnect does not resurrect a donor Activity.
10. Optional destructive test: temporarily remove/rename the cache marker; result must be the VOSTOK missing-cache screen, never donor LoaderActivity.
