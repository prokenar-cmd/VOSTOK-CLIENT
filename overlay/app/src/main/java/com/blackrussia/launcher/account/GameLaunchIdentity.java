package com.blackrussia.launcher.account;

import android.os.Environment;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

public final class GameLaunchIdentity {
    private static final Pattern TRANSPORT_NAME = Pattern.compile("^VT_[0-9A-F]{16}$");

    private GameLaunchIdentity() {
    }

    public static void writeTransportName(String connectName) throws IOException {
        if (connectName == null || !TRANSPORT_NAME.matcher(connectName).matches()) {
            throw new IOException("Invalid VOSTOK transport identity");
        }

        File sampDirectory = new File(Environment.getExternalStorageDirectory(), "BlackRussia/SAMP");
        if (!sampDirectory.exists() && !sampDirectory.mkdirs()) {
            throw new IOException("Could not create SAMP settings directory");
        }

        File settings = new File(sampDirectory, "settings.ini");
        if (!settings.exists() && !settings.createNewFile()) {
            throw new IOException("Could not create settings.ini");
        }

        Wini ini = new Wini(settings);
        ini.put("client", "name", connectName);
        ini.store();
    }
}
