package com.samp.mobile.diag;

import android.content.Context;
import java.io.File;

public final class StartupMarker {
    private StartupMarker() {}

    public static void mark(Context context, String stage) {
        if (context == null) return;
        try {
            DiagFiles.write(new File(context.getFilesDir(), "vostok_diag_marker.txt"), stage);
        } catch (Throwable ignored) {}
    }
}
