#!/usr/bin/env python3
from pathlib import Path
import re, shutil, sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else 'client')
SRC = ROOT / 'app/src/main'
JAVA = SRC / 'java'
RES = SRC / 'res'
OLD = JAVA / 'com/blackrussia/launcher'
NEW = JAVA / 'com/vostok/launcher'

if not OLD.exists():
    raise SystemExit(f'expected donor launcher tree missing: {OLD}')

# 031I is intentionally applied after the full 031H1 lineage. Assert the exact
# architecture we are replacing rather than silently patching an unknown base.
manifest = SRC / 'AndroidManifest.xml'
man = manifest.read_text(encoding='utf-8')
for needle in (
    'com.blackrussia.launcher.activity.SplashActivity',
    'com.blackrussia.launcher.activity.MainActivity',
    'com.blackrussia.launcher.activity.LoaderActivity',
    'com.blackrussia.game.core.GTASA',
):
    if needle not in man:
        raise SystemExit(f'031I base contract missing from manifest: {needle}')

# Migrate only VOSTOK-owned launcher/account/entry code to a project-owned
# namespace. Everything else under com.blackrussia.launcher is donor UI and is
# deleted below from the actual build tree before compilation.
migrate = {
    'account/AccountApi.java': 'account/AccountApi.java',
    'account/AccountConfig.java': 'account/AccountConfig.java',
    'account/AccountSessionStore.java': 'account/AccountSessionStore.java',
    'account/GameLaunchIdentity.java': 'account/GameLaunchIdentity.java',
    'entry/VostokEntryRouter.java': 'entry/VostokEntryRouter.java',
    'ui/VostokAuthDialog.java': 'ui/VostokAuthDialog.java',
    'ui/VostokLauncherView.java': 'ui/VostokLauncherView.java',
    'activity/MainActivity.java': 'activity/VostokLauncherActivity.java',
    'activity/VostokEntryActivity.java': 'activity/VostokEntryActivity.java',
}

NEW.mkdir(parents=True, exist_ok=True)
for src_rel, dst_rel in migrate.items():
    src = OLD / src_rel
    if not src.exists():
        raise SystemExit(f'missing 031H1 VOSTOK source: {src}')
    text = src.read_text(encoding='utf-8')
    text = text.replace('com.blackrussia.launcher.account', 'com.vostok.launcher.account')
    text = text.replace('com.blackrussia.launcher.entry', 'com.vostok.launcher.entry')
    text = text.replace('com.blackrussia.launcher.ui', 'com.vostok.launcher.ui')
    text = text.replace('package com.blackrussia.launcher.activity;', 'package com.vostok.launcher.activity;')
    text = text.replace('package com.blackrussia.launcher.account;', 'package com.vostok.launcher.account;')
    text = text.replace('package com.blackrussia.launcher.entry;', 'package com.vostok.launcher.entry;')
    text = text.replace('package com.blackrussia.launcher.ui;', 'package com.vostok.launcher.ui;')
    if src_rel == 'activity/MainActivity.java':
        text = text.replace('public class MainActivity extends AppCompatActivity {',
                            'public final class VostokLauncherActivity extends AppCompatActivity {')
        text = text.replace('MainActivity.this', 'VostokLauncherActivity.this')
        old_oncreate = '''        enterImmersiveMode();\n        setContentView(R.layout.activity_main);\n\n        launcherView = findViewById(R.id.vostokLauncherView);\n        launcherView.setActionListener(this::onLauncherAction);'''
        new_oncreate = '''        enterImmersiveMode();\n        launcherView = new VostokLauncherView(this);\n        setContentView(launcherView);\n        launcherView.setActionListener(this::onLauncherAction);'''
        if old_oncreate not in text:
            raise SystemExit('031I MainActivity onCreate anchor changed')
        text = text.replace(old_oncreate, new_oncreate)
        text = text.replace('import android.app.AlertDialog;\n',
                            'import android.Manifest;\nimport android.app.AlertDialog;\n')
        text = text.replace('import android.content.Intent;\n',
                            'import android.content.Intent;\nimport android.content.pm.PackageManager;\n')
        insert_anchor = '        sessionStore = new AccountSessionStore(this);\n\n        restoreCachedAccountState();'
        insert = '''        sessionStore = new AccountSessionStore(this);\n        ensureLauncherPermissions();\n\n        restoreCachedAccountState();'''
        if insert_anchor not in text:
            raise SystemExit('031I MainActivity permission insertion anchor changed')
        text = text.replace(insert_anchor, insert)
        pattern = re.compile(r'    private void startGameAfterCacheCheck\(VostokEntryRouter\.Route entryRoute\) \{.*?\n    \}\n\n    private void showAccountError', re.S)
        replacement = '''    private void startGameAfterCacheCheck(VostokEntryRouter.Route entryRoute) {\n        long characterId = selectedCharacter == null ? 0L : selectedCharacter.id;\n        VostokEntryRouter.persistPending(this, entryRoute, characterId);\n        Intent loadingIntent = new Intent(getApplicationContext(), VostokGameLoadingActivity.class);\n        startActivity(VostokEntryRouter.decorateGameIntent(loadingIntent, entryRoute, characterId));\n    }\n\n    private void ensureLauncherPermissions() {\n        if (Build.VERSION.SDK_INT < 23) return;\n        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED\n                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED\n                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {\n            requestPermissions(new String[]{\n                    Manifest.permission.READ_EXTERNAL_STORAGE,\n                    Manifest.permission.WRITE_EXTERNAL_STORAGE,\n                    Manifest.permission.RECORD_AUDIO\n            }, 1000);\n        }\n    }\n\n    private void showAccountError'''
        text, n = pattern.subn(replacement, text)
        if n != 1:
            raise SystemExit(f'031I MainActivity cache-gate replacement count={n}')
    elif src_rel == 'activity/VostokEntryActivity.java':
        pattern = re.compile(r'    private void startGameAfterCacheCheck\(\) \{.*?\n    \}\n\n    private AccountApi\.CharacterInfo findCharacter', re.S)
        replacement = '''    private void startGameAfterCacheCheck() {\n        Intent intent = new Intent(getApplicationContext(), VostokGameLoadingActivity.class);\n        if (getIntent() != null && getIntent().getExtras() != null) {\n            intent.putExtras(getIntent().getExtras());\n        }\n        startActivity(intent);\n        finish();\n    }\n\n    private AccountApi.CharacterInfo findCharacter'''
        text, n = pattern.subn(replacement, text)
        if n != 1:
            raise SystemExit(f'031I VostokEntry cache-gate replacement count={n}')
    elif src_rel == 'account/GameLaunchIdentity.java':
        text = text.replace('import android.os.Environment;\n\n', '')
        text = text.replace('import java.io.File;\n', '')
        old = '        File sampDirectory = new File(Environment.getExternalStorageDirectory(), "BlackRussia/SAMP");'
        new = '        java.io.File sampDirectory = com.vostok.launcher.game.VostokRuntimeContract.getSampDirectory();'
        if old not in text:
            raise SystemExit('031I GameLaunchIdentity legacy path anchor changed')
        text = text.replace(old, new)
    elif src_rel == 'entry/VostokEntryRouter.java':
        text = text.replace('Persist the route because LoaderActivity may sit between launcher and\n     * GTASA when the cache is missing. The future in-game Entry UI can consume',
                            'Persist the route across the VOSTOK loading handoff and GTASA.\n     * The in-game Entry UI can consume')

    dst = NEW / dst_rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text, encoding='utf-8')

game_dir = NEW / 'game'
game_dir.mkdir(parents=True, exist_ok=True)
(game_dir / 'VostokRuntimeContract.java').write_text(r'''package com.vostok.launcher.game;

import android.os.Environment;

import java.io.File;

/**
 * The only Android-launcher boundary that knows about the runtime-proven legacy
 * GTA/SA-MP storage root. This is technical compatibility debt, not UI/navigation.
 * Rename/migrate it only with a cache migration + native runtime smoke test.
 */
public final class VostokRuntimeContract {
    private static final String LEGACY_RUNTIME_ROOT = "BlackRussia";

    private VostokRuntimeContract() { }

    public static File getRuntimeRoot() {
        return new File(Environment.getExternalStorageDirectory(), LEGACY_RUNTIME_ROOT);
    }

    public static File getSampDirectory() {
        return new File(getRuntimeRoot(), "SAMP");
    }

    public static File getCacheMarker() {
        return new File(getRuntimeRoot(), "texdb/gta3.img");
    }

    public static boolean isRuntimeCacheReady() {
        File marker = getCacheMarker();
        return marker.isFile() && marker.length() > 0L;
    }
}
''', encoding='utf-8')

activity_dir = NEW / 'activity'
(activity_dir / 'VostokGameLoadingActivity.java').write_text(r'''package com.vostok.launcher.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.blackrussia.game.R;
import com.vostok.launcher.game.VostokRuntimeContract;

/** VOSTOK-only Android handoff before the native in-game VOSTOK loading overlay. */
public final class VostokGameLoadingActivity extends AppCompatActivity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        if (!VostokRuntimeContract.isRuntimeCacheReady()) {
            showMissingCache();
            return;
        }

        content.postDelayed(this::startNativeGame, 180L);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 10, 12));
        setContentView(root);

        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.vostok_loading_background);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackgroundColor(0x99000000);
        root.addView(shade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        int pad = dp(28);
        content.setPadding(pad, pad, pad, pad);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView brand = text("VOSTOK", 34, true);
        content.addView(brand);
        TextView status = text("ПОДГОТОВКА ИГРОВОГО МИРА", 16, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(10);
        content.addView(status, statusParams);
    }

    private void showMissingCache() {
        TextView message = text(
                "Игровые файлы VOSTOK не найдены. Старый загрузчик отключён и больше не используется.",
                14, false);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(0xFFD5D0CC);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                dp(620), LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(18);
        content.addView(message, msgParams);

        Button back = new Button(this);
        back.setText("ВЕРНУТЬСЯ В ЛАУНЧЕР");
        back.setTextColor(Color.WHITE);
        back.setTextSize(14);
        back.setAllCaps(false);
        back.setBackgroundColor(0xFFFF6A21);
        back.setOnClickListener(v -> {
            Intent intent = new Intent(this, VostokLauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(310), dp(54));
        buttonParams.topMargin = dp(22);
        content.addView(back, buttonParams);
    }

    private void startNativeGame() {
        Intent game = new Intent(getApplicationContext(), com.blackrussia.game.core.GTASA.class);
        if (getIntent() != null && getIntent().getExtras() != null) {
            game.putExtras(getIntent().getExtras());
        }
        startActivity(game);
        finish();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }
}
''', encoding='utf-8')

shutil.rmtree(OLD)

choose_server = JAVA / 'com/blackrussia/game/gui/ChooseServer.java'
if choose_server.exists():
    choose_server.unlink()

remove_layouts = [
    'activity_splash.xml', 'activity_loader.xml', 'activity_story.xml', 'activity_main.xml',
    'fragment_donate.xml', 'fragment_forum.xml', 'fragment_monitoring.xml', 'fragment_settings.xml',
    'item_server.xml', 'item_slider_story.xml', 'item_news.xml', 'br_serverselect.xml', 'br_serverselect_server.xml',
]
for name in remove_layouts:
    p = RES / 'layout' / name
    if p.exists():
        p.unlink()

remove_launcher_resources = [
    ('drawable-hdpi-v4', 'btn_forum.png'),
    ('drawable-hdpi-v4', 'servers_saint.png'),
    ('drawable', 'br_highlight_button_play.png'),
    ('drawable', 'br_peaw_servers.png'),
    ('drawable', 'button_br_red_unfilled_ss.xml'),
    ('drawable', 'divider_br_red_s_corn.xml'),
    ('drawable', 'donate_btn.png'),
    ('drawable', 'donate_scrollbar_track.xml'),
    ('drawable', 'forum_btn.png'),
    ('drawable', 'gradient_select_server.png'),
    ('drawable', 'ic_clock_black_24dp.xml'),
    ('drawable', 'ic_close_black_24dp.png'),
    ('drawable', 'logo_splash.png'),
    ('drawable', 'monitoring_btn.png'),
    ('drawable', 'rounded_rectangle_black.xml'),
    ('drawable', 'server_rectangle.xml'),
    ('drawable', 'server_rectangle_choose.xml'),
    ('drawable', 'server_rectangle_gradient.xml'),
    ('drawable', 'server_select_background.png'),
    ('drawable', 'splash.xml'),
]
for folder, name in remove_launcher_resources:
    p = RES / folder / name
    if p.exists():
        p.unlink()

styles = RES / 'values/styles.xml'
if styles.exists():
    style_text = styles.read_text(encoding='utf-8')
    donor_splash_style = '''    <style name="AppTheme.WithSplash">
    <item name="android:windowBackground">@drawable/splash</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowFullscreen">true</item>
    <item name="windowActionBar">false</item>
    <item name="windowNoTitle">true</item>
    <item name="android:windowTitleSize">0dp</item>
    </style>
'''
    style_text = style_text.replace(donor_splash_style, '')
    styles.write_text(style_text, encoding='utf-8')

values = RES / 'values'
values.mkdir(parents=True, exist_ok=True)
(values / 'vostok_launcher_styles.xml').write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="VostokLauncherTheme" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowBackground">@drawable/vostok_loading_background</item>
        <item name="android:navigationBarColor">#000000</item>
        <item name="android:colorAccent">#FF6A21</item>
    </style>
    <style name="VostokGameTheme" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowBackground">@drawable/vostok_loading_background</item>
        <item name="android:navigationBarColor">#000000</item>
    </style>
</resources>
''', encoding='utf-8')

manifest.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="com.android.vending.CHECK_LICENSE" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature android:glEsVersion="0x00020000" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <supports-screens
        android:anyDensity="true"
        android:largeScreens="true"
        android:normalScreens="true"
        android:resizeable="true"
        android:smallScreens="true" />

    <application
        android:name="com.blackrussia.game.vostok.VostokApplication"
        android:hardwareAccelerated="false"
        android:icon="@drawable/vostok_app_icon"
        android:isGame="true"
        android:label="VOSTOK"
        android:largeHeap="true"
        android:logo="@drawable/vostok_app_icon"
        android:usesCleartextTraffic="true"
        android:requestLegacyExternalStorage="true"
        tools:replace="android:icon,android:label,android:logo">

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/provider_paths" />
        </provider>

        <activity
            android:name="com.vostok.launcher.activity.VostokLauncherActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:theme="@style/VostokLauncherTheme">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.vostok.launcher.activity.VostokEntryActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/VostokLauncherTheme" />

        <activity
            android:name="com.vostok.launcher.activity.VostokGameLoadingActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/VostokGameTheme" />

        <activity
            android:name="com.blackrussia.game.core.GTASA"
            android:screenOrientation="landscape"
            android:theme="@style/VostokGameTheme" />

        <meta-data android:name="android.max_aspect" android:value="2.1" />
        <meta-data android:name="preloaded_fonts" android:resource="@array/preloaded_fonts" />
    </application>
</manifest>
''', encoding='utf-8')

all_java = '\n'.join(p.read_text(encoding='utf-8', errors='ignore') for p in JAVA.rglob('*.java'))
for forbidden in (
    'com.blackrussia.launcher.activity.SplashActivity',
    'com.blackrussia.launcher.activity.MainActivity',
    'com.blackrussia.launcher.activity.LoaderActivity',
    'com.blackrussia.launcher.activity.StoryActivity',
    'import com.blackrussia.launcher.',
    'new ChooseServer(',
):
    if forbidden in all_java:
        raise SystemExit(f'031I forbidden donor launcher dependency remains: {forbidden}')

legacy_hits = []
for p in NEW.rglob('*.java'):
    text = p.read_text(encoding='utf-8', errors='ignore')
    if 'BlackRussia' in text:
        legacy_hits.append(p.relative_to(JAVA).as_posix())
allowed_legacy = {'com/vostok/launcher/game/VostokRuntimeContract.java'}
if set(legacy_hits) != allowed_legacy:
    raise SystemExit(f'031I legacy path leaked outside runtime boundary: {legacy_hits}')

print('VOSTOK 031I launcher decouple applied')
print('launcher source root:', NEW)
print('legacy runtime boundary:', sorted(legacy_hits))
