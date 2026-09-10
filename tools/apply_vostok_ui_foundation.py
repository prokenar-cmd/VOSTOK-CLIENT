from pathlib import Path

p = Path('client/app/src/main/java/com/nvidia/devtech/NvEventQueueActivity.java')
s = p.read_text()

replacements = [
    ('import com.blackrussia.game.gui.HudManager;\n', ''),
    ('import com.blackrussia.game.gui.Notification;\n', ''),
    ('import com.blackrussia.game.gui.ChooseServer;\n', 'import com.blackrussia.game.gui.ChooseServer;\nimport com.blackrussia.game.vostok.ui.VostokUiBridge;\n'),
    ('    private HudManager mHudManager = null;\n', ''),
    ('    private Notification mNotification = null;\n', ''),
    ('    private Dialog mDialog = null;\n', '    private Dialog mDialog = null;\n    private VostokUiBridge mVostokUi = null;\n'),
    ('        mNotification = new Notification(this);\n        mDialog = new Dialog(this);\n        mHudManager = new HudManager(this);\n',
     '        mDialog = new Dialog(this);\n        mVostokUi = new VostokUiBridge(this, () -> sendCommand("/interact".getBytes()));\n'),
    ('''    public void onDestroy()\n    {\n        System.out.println("**** onDestroy");\n\t\tif(supportPauseResume)\n\t\t{\n\t\t\tquitAndWait();\n\t\t\tfinish();\n\t\t}\n        super.onDestroy();\n\t\tsystemCleanup();\n    }''',
     '''    public void onDestroy()\n    {\n        System.out.println("**** onDestroy");\n        if (mVostokUi != null)\n        {\n            mVostokUi.shutdown();\n            mVostokUi = null;\n        }\n\t\tif(supportPauseResume)\n\t\t{\n\t\t\tquitAndWait();\n\t\t\tfinish();\n\t\t}\n        super.onDestroy();\n\t\tsystemCleanup();\n    }'''),
    ('''    public void updateHudInfo(int health, int armour, int hunger, int weaponid, int ammo, int playerid, int money, int wanted) { runOnUiThread(() -> { mHudManager.UpdateHudInfo(health, armour, hunger, weaponid, ammo, playerid, money, wanted); }); }\n\n    public void showHud() { runOnUiThread(() -> { mHudManager.ShowHud(); }); }\n\n    public void hideHud() { runOnUiThread(() -> { mHudManager.HideHud(); }); }''',
     '''    public void updateHudInfo(int health, int armour, int hunger, int weaponid, int ammo, int playerid, int money, int wanted) { if (mVostokUi != null) mVostokUi.updateHudInfo(health, armour, hunger, weaponid, ammo, playerid, money, wanted); }\n\n    public void showHud() { if (mVostokUi != null) mVostokUi.showHud(); }\n\n    public void hideHud() { if (mVostokUi != null) mVostokUi.hideHud(); }'''),
    ('    public void showNotification(int type, String text, int duration, String actionforBtn, String textBtn) { runOnUiThread(() -> mNotification.ShowNotification(type, text, duration, actionforBtn, textBtn)); }',
     '''    public void showNotification(int type, String text, int duration, String actionforBtn, String textBtn) { if (mVostokUi != null) mVostokUi.showNotification(type, text, duration, actionforBtn, textBtn); }\n\n    // VOSTOK Interaction UI Core. Visibility remains server/native-authoritative.\n    public void showVostokInteraction(float distanceMeters) { if (mVostokUi != null) mVostokUi.showInteraction(distanceMeters); }\n\n    public void hideVostokInteraction() { if (mVostokUi != null) mVostokUi.hideInteraction(); }\n\n    public void setVostokInteractionContext(int flags, int reservedActionSlots) { if (mVostokUi != null) mVostokUi.setInteractionContext(flags, reservedActionSlots); }'''),
]

for old, new in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one match, found {count}: {old[:100]!r}')
    s = s.replace(old, new, 1)

p.write_text(s)
print('Applied VOSTOK Client UI Foundation v1 + Interaction Java bridge')
