package com.samp.mobile.diag;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DiagnosticsActivity extends Activity {
    private TextView info;
    private TextView marker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (marker != null) marker.setText("Последний этап: " + readMarker());
        if (info != null) info.setText(buildInfo());
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 28, 36, 28);
        root.setBackgroundColor(Color.rgb(16, 18, 22));

        TextView title = text("VOSTOK DIAG STARTED", 25, Color.WHITE);
        root.addView(title);

        TextView subtitle = text("Этот экран не загружает GTA/SAMP. Если ты его видишь — Android-часть APK запускается.", 16, Color.LTGRAY);
        subtitle.setPadding(0, 12, 0, 18);
        root.addView(subtitle);

        info = text(buildInfo(), 14, Color.LTGRAY);
        root.addView(info);

        marker = text("Последний этап: " + readMarker(), 16, Color.WHITE);
        marker.setPadding(0, 16, 0, 16);
        root.addView(marker);

        root.addView(button("1. ПРОВЕРИТЬ libGTASA", new View.OnClickListener() {
            @Override public void onClick(View v) {
                writeMarker("OPEN_GTASA_PROBE");
                startActivity(new Intent(DiagnosticsActivity.this, GtasaProbeActivity.class));
            }
        }));

        root.addView(button("2. ПРОВЕРИТЬ ВСЮ NATIVE-ЦЕПОЧКУ", new View.OnClickListener() {
            @Override public void onClick(View v) {
                writeMarker("OPEN_FULL_NATIVE_PROBE");
                startActivity(new Intent(DiagnosticsActivity.this, FullNativeProbeActivity.class));
            }
        }));

        root.addView(button("3. ЗАПУСТИТЬ ОРИГИНАЛЬНЫЙ SAMP", new View.OnClickListener() {
            @Override public void onClick(View v) {
                writeMarker("START_ORIGINAL_SAMP");
                Intent i = new Intent();
                i.setClassName("com.samp.mobile", "com.samp.mobile.game.SAMP");
                startActivity(i);
            }
        }));

        if (Build.VERSION.SDK_INT >= 30) {
            root.addView(button("РАЗРЕШИТЬ ДОСТУП КО ВСЕМ ФАЙЛАМ", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }
            }));
        }

        scroll.addView(root);
        setContentView(scroll);
        writeMarker("DIAG_MAIN_OK");
    }

    private String buildInfo() {
        File gta = new File("/storage/emulated/0/GTA");
        String[] names = null;
        try { names = gta.list(); } catch (Throwable ignored) {}
        boolean allFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        return "SDK: " + Build.VERSION.SDK_INT +
                "\nABIs: " + Arrays.toString(Build.SUPPORTED_ABIS) +
                "\nPackage: " + getPackageName() +
                "\nStorage: " + Environment.getExternalStorageState() +
                "\nAll files access: " + allFiles +
                "\nGTA folder exists: " + gta.exists() +
                "\nGTA folder readable: " + gta.canRead() +
                "\nGTA entries: " + (names == null ? "<нет доступа>" : names.length);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextIsSelectable(true);
        return t;
    }

    private Button button(String s, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private File markerFile() { return new File(getFilesDir(), "vostok_diag_marker.txt"); }

    private void writeMarker(String s) {
        DiagFiles.write(markerFile(), s);
        if (marker != null) marker.setText("Последний этап: " + s);
    }

    private String readMarker() {
        File f = markerFile();
        if (!f.isFile()) return "<нет>";
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int)Math.min(f.length(), 4096)];
            int n = in.read(data);
            in.close();
            return n <= 0 ? "<пусто>" : new String(data, 0, n, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "READ_ERROR: " + t.getClass().getSimpleName();
        }
    }
}
