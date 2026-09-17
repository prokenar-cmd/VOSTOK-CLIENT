package com.samp.mobile.diag;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class DiagFiles {
    private DiagFiles() {}
    static void write(File file, String value) {
        try {
            FileOutputStream out = new FileOutputStream(file, false);
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        } catch (Throwable ignored) {}
    }
}
