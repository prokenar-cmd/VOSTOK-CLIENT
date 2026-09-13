#!/usr/bin/env python3
from pathlib import Path

# Candidate 031A runs after the existing UI foundation + 024/026 lineage.
# It deliberately leaves the frozen launcher main screen untouched.


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031A {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = Path("client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java")

replace_once(
    activity,
    "import com.blackrussia.game.gui.ChooseServer;\n",
    "import com.blackrussia.game.vostok.loading.VostokLoadingOverlay;\n",
    "remove ChooseServer import",
)
replace_once(
    activity,
    "    private ChooseServer mChooseServer = null;\n",
    "    private VostokLoadingOverlay mVostokLoading = null;\n",
    "loading field",
)
replace_once(
    activity,
    "        mChooseServer = new ChooseServer(this);\n",
    "        mVostokLoading = new VostokLoadingOverlay(this, mAndroidUI);\n",
    "loading construction",
)
replace_once(
    activity,
    "    public void updateSplash(int percent) { runOnUiThread(() -> { mChooseServer.Update(percent); } ); }",
    "    public void updateSplash(int percent) { if (mVostokLoading != null) mVostokLoading.update(percent); }",
    "native updateSplash bridge",
)
replace_once(
    activity,
    "    public void showSplash() { runOnUiThread(() -> { mChooseServer.Show(); } ); }",
    "    public void showSplash() { if (mVostokLoading != null) mVostokLoading.show(); }",
    "native showSplash bridge",
)

foundation_shutdown = '''        if (mVostokUi != null)
        {
            mVostokUi.shutdown();
            mVostokUi = null;
        }
\t\tif(supportPauseResume)'''
loading_shutdown = '''        if (mVostokUi != null)
        {
            mVostokUi.shutdown();
            mVostokUi = null;
        }
        if (mVostokLoading != null)
        {
            mVostokLoading.shutdown();
            mVostokLoading = null;
        }
\t\tif(supportPauseResume)'''
replace_once(activity, foundation_shutdown, loading_shutdown, "loading shutdown")

launcher = Path("client/app/src/main/java/com/blackrussia/launcher/activity/MainActivity.java")
replace_once(
    launcher,
    "import com.blackrussia.launcher.account.GameLaunchIdentity;\n",
    "import com.blackrussia.launcher.account.GameLaunchIdentity;\nimport com.blackrussia.launcher.entry.VostokEntryRouter;\n",
    "entry router import",
)

old_route = '''        if (sessionToken.isEmpty()) {
            showAuthDialog();
            return;
        }
        if (selectedCharacter == null) {
            showCreateCharacterDialog();
            return;
        }
        accountRequestInFlight = true;'''
new_route = '''        VostokEntryRouter.Route entryRoute = VostokEntryRouter.resolve(sessionToken, selectedCharacter);
        if (entryRoute == VostokEntryRouter.Route.AUTHORIZATION) {
            showAuthDialog();
            return;
        }
        if (entryRoute == VostokEntryRouter.Route.CHARACTER_CREATION) {
            VostokEntryRouter.persistPending(this, entryRoute, 0L);
            // Temporary fallback until 031C replaces this with the full Character Creator.
            showCreateCharacterDialog();
            return;
        }

        VostokEntryRouter.persistPending(this, VostokEntryRouter.Route.SPAWN_SELECTION, selectedCharacter.id);
        accountRequestInFlight = true;'''
replace_once(launcher, old_route, new_route, "launcher entry route")

replace_once(
    launcher,
    "                    startGameAfterCacheCheck();\n",
    "                    startGameAfterCacheCheck(VostokEntryRouter.Route.SPAWN_SELECTION);\n",
    "ticket launch route",
)

old_start = '''    private void startGameAfterCacheCheck() {
        File marker = new File(Environment.getExternalStorageDirectory(), "BlackRussia/texdb/gta3.img");
        if (marker.exists()) {
            startActivity(new Intent(getApplicationContext(), com.blackrussia.game.core.GTASA.class));
        } else {
            startActivity(new Intent(getApplicationContext(), LoaderActivity.class));
        }
    }'''
new_start = '''    private void startGameAfterCacheCheck(VostokEntryRouter.Route entryRoute) {
        long characterId = selectedCharacter == null ? 0L : selectedCharacter.id;
        VostokEntryRouter.persistPending(this, entryRoute, characterId);

        File marker = new File(Environment.getExternalStorageDirectory(), "BlackRussia/texdb/gta3.img");
        if (marker.exists()) {
            Intent gameIntent = new Intent(getApplicationContext(), com.blackrussia.game.core.GTASA.class);
            startActivity(VostokEntryRouter.decorateGameIntent(gameIntent, entryRoute, characterId));
        } else {
            // LoaderActivity remains only the cache-recovery path. The persisted
            // entry route survives it and will be consumed by the future 031C UI.
            Intent loaderIntent = new Intent(getApplicationContext(), LoaderActivity.class);
            startActivity(VostokEntryRouter.decorateGameIntent(loaderIntent, entryRoute, characterId));
        }
    }'''
replace_once(launcher, old_start, new_start, "decorated game launch")

# Hard gates: 031A must not leave the legacy server-selection path reachable.
activity_text = activity.read_text(encoding="utf-8")
launcher_text = launcher.read_text(encoding="utf-8")
required = [
    "VostokLoadingOverlay",
    "mVostokLoading.update(percent)",
    "mVostokLoading.show()",
    "VostokEntryRouter.resolve",
    "VostokEntryRouter.Route.CHARACTER_CREATION",
    "VostokEntryRouter.Route.SPAWN_SELECTION",
]
all_text = activity_text + launcher_text
for needle in required:
    if needle not in all_text:
        raise SystemExit(f"031A verification missing: {needle}")

for forbidden in (
    "import com.blackrussia.game.gui.ChooseServer;",
    "private ChooseServer mChooseServer",
    "mChooseServer = new ChooseServer",
    "mChooseServer.Update",
    "mChooseServer.Show",
):
    if forbidden in activity_text:
        raise SystemExit(f"031A legacy ChooseServer flow still reachable: {forbidden}")

print("Applied VOSTOK Candidate 031A loading + entry cleanup")
