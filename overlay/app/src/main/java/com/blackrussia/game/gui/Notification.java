package com.blackrussia.game.gui;

import android.app.Activity;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.blackrussia.game.R;
import com.blackrussia.game.gui.util.Utils;
import com.nvidia.devtech.NvEventQueueActivity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * VOSTOK Notification Core v1.
 *
 * Keeps the legacy Java/JNI entry point intact while providing a bounded queue,
 * duplicate suppression, sane timer cadence and lifecycle-safe cleanup.
 */
public final class Notification {
    public static final int TYPE_MONEY_ERROR = 0;
    public static final int TYPE_MONEY_SUCCESS = 1;
    public static final int TYPE_ERROR = 2;
    public static final int TYPE_SUCCESS = 3;
    public static final int TYPE_ACTION_PRIMARY = 4;
    public static final int TYPE_ACTION_SECONDARY = 5;
    public static final int TYPE_INFO = 6;
    public static final int TYPE_WARNING = 7;
    public static final int TYPE_SYSTEM = 8;

    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MAX_DURATION_SECONDS = 60;
    private static final int MAX_QUEUE_SIZE = 8;
    private static final long PROGRESS_TICK_MS = 50L;
    private static final long TRANSITION_MS = 120L;
    private static final long DUPLICATE_WINDOW_MS = 750L;

    private final Activity activity;
    private final ConstraintLayout constraintLayout;
    private final View accentView;
    private final LinearLayout main;
    private final Button button;
    private final ProgressBar progressBar;
    private final TextView ruble;
    private final TextView textView;
    private final Deque<Request> queue = new ArrayDeque<>();

    private CountDownTimer countDownTimer;
    private Request activeRequest;
    private long lastAcceptedAtMs;
    private String lastAcceptedKey = "";
    private boolean transitioning;
    private boolean destroyed;

    public Notification(Activity activity) {
        this.activity = activity;
        constraintLayout = activity.findViewById(R.id.constraintLayout_notif);
        button = activity.findViewById(R.id.br_not_button);
        accentView = activity.findViewById(R.id.br_not_view);
        ruble = activity.findViewById(R.id.br_not_ruble);
        textView = activity.findViewById(R.id.br_not_text);
        main = activity.findViewById(R.id.dw_root);
        progressBar = activity.findViewById(R.id.br_not_progress);

        Utils.HideLayout(constraintLayout, false);
        button.setOnClickListener(null);
    }

    /**
     * Legacy-compatible entry point used by NvEventQueueActivity/CJavaWrapper.
     * duration is expressed in seconds to preserve the existing RPC contract.
     */
    public void ShowNotification(int type, String text, int duration, String actionForButton, String buttonText) {
        if (destroyed) {
            return;
        }

        Request request = new Request(
                normalizeType(type),
                safe(text),
                clamp(duration, MIN_DURATION_SECONDS, MAX_DURATION_SECONDS),
                safe(actionForButton),
                safe(buttonText)
        );

        long now = SystemClock.elapsedRealtime();
        String key = request.dedupeKey();
        if (key.equals(lastAcceptedKey) && now - lastAcceptedAtMs <= DUPLICATE_WINDOW_MS) {
            return;
        }
        lastAcceptedKey = key;
        lastAcceptedAtMs = now;

        if (activeRequest == null && !transitioning) {
            showNow(request);
            return;
        }

        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.pollFirst();
        }
        queue.offerLast(request);
    }

    public void ShowInfo(String text, int duration) {
        ShowNotification(TYPE_INFO, text, duration, "", "");
    }

    public void ShowSuccess(String text, int duration) {
        ShowNotification(TYPE_SUCCESS, text, duration, "", "");
    }

    public void ShowWarning(String text, int duration) {
        ShowNotification(TYPE_WARNING, text, duration, "", "");
    }

    public void ShowError(String text, int duration) {
        ShowNotification(TYPE_ERROR, text, duration, "", "");
    }

    public void HideNotification() {
        if (destroyed) {
            return;
        }
        finishActive(true);
    }

    /** Releases timers and queued UI work when the game Activity is destroyed. */
    public void Shutdown() {
        destroyed = true;
        transitioning = false;
        queue.clear();
        activeRequest = null;
        cancelTimer();
        button.setOnClickListener(null);
        constraintLayout.animate().cancel();
        constraintLayout.clearAnimation();
        constraintLayout.setVisibility(View.GONE);
    }

    public int GetPendingCount() {
        return queue.size() + (activeRequest == null ? 0 : 1);
    }

    private void showNow(Request request) {
        if (destroyed) {
            return;
        }

        activeRequest = request;
        transitioning = false;
        cancelTimer();
        constraintLayout.animate().cancel();
        constraintLayout.clearAnimation();
        constraintLayout.setAlpha(1.0f);

        textView.setText(request.text);
        applyVisualType(request.type);
        configureAction(request);

        int durationMs = request.durationSeconds * 1000;
        progressBar.setMax(durationMs);
        progressBar.setProgress(durationMs);

        Utils.ShowLayout(constraintLayout, true);
        countDownTimer = new CountDownTimer(durationMs, PROGRESS_TICK_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressBar.setProgress((int) millisUntilFinished);
            }

            @Override
            public void onFinish() {
                progressBar.setProgress(0);
                finishActive(true);
            }
        }.start();
    }

    private void finishActive(boolean animate) {
        if (activeRequest == null || transitioning) {
            return;
        }

        cancelTimer();
        activeRequest = null;
        button.setOnClickListener(null);
        transitioning = true;

        Runnable finish = () -> {
            if (destroyed) {
                return;
            }
            constraintLayout.setVisibility(View.GONE);
            constraintLayout.setAlpha(1.0f);
            transitioning = false;
            Request next = queue.pollFirst();
            if (next != null) {
                showNow(next);
            }
        };

        constraintLayout.animate().cancel();
        if (animate) {
            constraintLayout.animate()
                    .alpha(0.0f)
                    .setDuration(TRANSITION_MS)
                    .withEndAction(finish)
                    .start();
        } else {
            finish.run();
        }
    }

    private void applyVisualType(int type) {
        button.setVisibility(View.GONE);
        ruble.setVisibility(View.GONE);

        switch (type) {
            case TYPE_MONEY_ERROR:
                ruble.setVisibility(View.VISIBLE);
                accentView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_br_notification_red));
                break;
            case TYPE_MONEY_SUCCESS:
                ruble.setVisibility(View.VISIBLE);
                accentView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_br_notification_green));
                break;
            case TYPE_SUCCESS:
            case TYPE_SYSTEM:
                accentView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_br_notification_green));
                break;
            case TYPE_ACTION_PRIMARY:
            case TYPE_ACTION_SECONDARY:
            case TYPE_INFO:
            case TYPE_WARNING:
                accentView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_br_notification_orange));
                break;
            case TYPE_ERROR:
            default:
                accentView.setBackground(ContextCompat.getDrawable(activity, R.drawable.background_br_notification_red));
                break;
        }

        if (type == TYPE_ACTION_PRIMARY || type == TYPE_ACTION_SECONDARY) {
            button.setVisibility(View.VISIBLE);
            button.setBackground(ContextCompat.getDrawable(
                    activity,
                    type == TYPE_ACTION_PRIMARY
                            ? R.drawable.button_red_square_vector
                            : R.drawable.button_red_square
            ));
        }
    }

    private void configureAction(Request request) {
        if (request.type != TYPE_ACTION_PRIMARY && request.type != TYPE_ACTION_SECONDARY) {
            button.setOnClickListener(null);
            return;
        }

        button.setText(request.buttonText.isEmpty() ? "OK" : request.buttonText);
        button.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.button_click));
            NvEventQueueActivity gameActivity = NvEventQueueActivity.getInstance();
            if (gameActivity != null && !request.actionForButton.isEmpty()) {
                try {
                    gameActivity.sendCommand(request.actionForButton.getBytes("windows-1251"));
                } catch (UnsupportedEncodingException ignored) {
                    // windows-1251 is available on Android; ignore defensively.
                }
            }
            finishActive(true);
        });
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private static int normalizeType(int type) {
        return type >= TYPE_MONEY_ERROR && type <= TYPE_SYSTEM ? type : TYPE_INFO;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Request {
        final int type;
        final String text;
        final int durationSeconds;
        final String actionForButton;
        final String buttonText;

        Request(int type, String text, int durationSeconds, String actionForButton, String buttonText) {
            this.type = type;
            this.text = text;
            this.durationSeconds = durationSeconds;
            this.actionForButton = actionForButton;
            this.buttonText = buttonText;
        }

        String dedupeKey() {
            return type + "\n" + text + "\n" + actionForButton + "\n" + buttonText;
        }
    }
}
