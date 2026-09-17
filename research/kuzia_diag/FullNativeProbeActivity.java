package com.samp.mobile.diag;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import com.bytedance.shadowhook.ShadowHook;
import java.io.File;

public class FullNativeProbeActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(16, 18, 22));
        status.setTextSize(20);
        status.setPadding(40, 40, 40, 40);
        status.setText("Проверка native-цепочки GTASA...\nМаркер сохраняется перед каждым этапом.");
        setContentView(status);
        new Thread(new Runnable() {
            @Override public void run() { probe(); }
        }, "VostokNativeProbe").start();
    }

    private void mark(String s) {
        DiagFiles.write(new File(getFilesDir(), "vostok_diag_marker.txt"), s);
    }

    private void probe() {
        try {
            mark("SHADOWHOOK_BEGIN");
            ShadowHook.init(new ShadowHook.ConfigBuilder().setMode(ShadowHook.Mode.UNIQUE).build());
            mark("SHADOWHOOK_OK");

            try {
                mark("IMM_LOAD_BEGIN");
                System.loadLibrary("ImmEmulatorJ");
                mark("IMM_LOAD_OK");
            } catch (Throwable t) {
                mark("IMM_SKIPPED_" + t.getClass().getSimpleName());
            }

            mark("GTASA_CHAIN_BEGIN");
            System.loadLibrary("GTASA");
            mark("GTASA_CHAIN_OK");

            mark("BASS_LOAD_BEGIN");
            System.loadLibrary("bass");
            mark("BASS_LOAD_OK");

            mark("SAMP_LOAD_BEGIN");
            System.loadLibrary("samp");
            mark("FULL_NATIVE_OK");

            runOnUiThread(new Runnable() {
                @Override public void run() {
                    status.setText("NATIVE ЦЕПОЧКА: OK\nShadowHook + GTASA + bass + samp загрузились.\n\nНажми Назад и пробуй пункт 3.");
                }
            });
        } catch (final Throwable t) {
            mark("NATIVE_JAVA_ERROR_" + t.getClass().getSimpleName());
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    status.setText("NATIVE: JAVA ERROR\n" + t.getClass().getName() + "\n" + String.valueOf(t.getMessage()));
                }
            });
        }
    }
}
