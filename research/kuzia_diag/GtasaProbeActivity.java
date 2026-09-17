package com.samp.mobile.diag;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;

public class GtasaProbeActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(16, 18, 22));
        status.setTextSize(20);
        status.setPadding(40, 40, 40, 40);
        status.setText("Проверка libGTASA...\nЕсли этот процесс упадёт, снова открой VOSTOK DIAG.");
        setContentView(status);
        new Thread(new Runnable() {
            @Override public void run() { probe(); }
        }, "VostokGtasaProbe").start();
    }

    private void mark(String s) {
        DiagFiles.write(new File(getFilesDir(), "vostok_diag_marker.txt"), s);
    }

    private void probe() {
        try {
            mark("GTASA_LOAD_BEGIN");
            System.loadLibrary("GTASA");
            mark("GTASA_LOAD_OK");
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    status.setText("libGTASA: OK\n\nНажми Назад и переходи к пункту 2.");
                }
            });
        } catch (final Throwable t) {
            mark("GTASA_JAVA_ERROR_" + t.getClass().getSimpleName());
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    status.setText("libGTASA: JAVA ERROR\n" + t.getClass().getName() + "\n" + String.valueOf(t.getMessage()));
                }
            });
        }
    }
}
