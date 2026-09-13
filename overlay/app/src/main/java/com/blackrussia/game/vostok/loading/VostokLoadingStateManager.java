package com.blackrussia.game.vostok.loading;

import com.blackrussia.game.R;

/**
 * Pure loading-state policy for the in-game VOSTOK loading overlay.
 * Native code may report values above 100 to signal that its legacy splash
 * phase is complete. 031A consumes that signal without opening ChooseServer.
 */
public final class VostokLoadingStateManager {

    public static final class State {
        public final int percent;
        public final int statusStringRes;
        public final boolean complete;

        State(int percent, int statusStringRes, boolean complete) {
            this.percent = percent;
            this.statusStringRes = statusStringRes;
            this.complete = complete;
        }
    }

    public State fromNativeProgress(int rawPercent) {
        if (rawPercent > 100) {
            return new State(100, R.string.vostok_loading_status_enter, true);
        }

        int percent = Math.max(0, Math.min(100, rawPercent));
        int status;
        if (percent < 15) {
            status = R.string.vostok_loading_status_start;
        } else if (percent < 40) {
            status = R.string.vostok_loading_status_world;
        } else if (percent < 72) {
            status = R.string.vostok_loading_status_resources;
        } else if (percent < 94) {
            status = R.string.vostok_loading_status_sync;
        } else {
            status = R.string.vostok_loading_status_finish;
        }
        return new State(percent, status, false);
    }
}
