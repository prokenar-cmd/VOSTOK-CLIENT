package com.blackrussia.game.vostok.loading;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.blackrussia.game.R;

/**
 * VOSTOK in-game loading overlay.
 *
 * 031A contract:
 * - the background is a dedicated clean replaceable resource;
 * - progress/status/tips are real Android views, never baked into the image;
 * - no donor/system spinner is used;
 * - legacy native progress > 100 closes loading instead of opening ChooseServer.
 */
public final class VostokLoadingOverlay {

    private static final long TIP_INTERVAL_MS = 6500L;
    private static final long FINISH_FADE_MS = 240L;

    private final Activity activity;
    private final FrameLayout host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VostokLoadingStateManager stateManager = new VostokLoadingStateManager();

    private FrameLayout root;
    private FrameLayout progressRail;
    private View progressFill;
    private TextView statusView;
    private TextView percentView;
    private TextView tipView;
    private String[] tips = new String[0];
    private int tipIndex = 0;
    private boolean showing = false;
    private boolean finishing = false;

    private final Runnable rotateTipRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || tips.length == 0) return;
            tipIndex = (tipIndex + 1) % tips.length;
            showTip(tipIndex, true);
            handler.postDelayed(this, TIP_INTERVAL_MS);
        }
    };

    public VostokLoadingOverlay(Activity activity, FrameLayout host) {
        this.activity = activity;
        this.host = host;
        build();
    }

    private void build() {
        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);
        root.setAlpha(1.0f);
        root.setClickable(true);
        root.setFocusable(true);
        root.setBackgroundColor(Color.rgb(9, 7, 6));
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Clean background only. The resource contains no bar, percent, tip,
        // spinner or status text; all of those remain live UI below.
        ImageView background = new ImageView(activity);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImageResource(R.drawable.vostok_loading_background);
        root.addView(background, matchParent());

        View scrim = new View(activity);
        scrim.setBackgroundColor(Color.argb(78, 0, 0, 0));
        root.addView(scrim, matchParent());

        LinearLayout brand = new LinearLayout(activity);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);

        TextView title = text("VOSTOK", 43.0f, Color.WHITE, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.055f);
        }
        brand.addView(title, wrapContent());

        TextView subTitle = text("R O L E   P L A Y", 12.0f, Color.rgb(224, 218, 214), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            subTitle.setLetterSpacing(0.10f);
        }
        LinearLayout.LayoutParams subParams = wrapContentLinear();
        subParams.topMargin = dp(1);
        brand.addView(subTitle, subParams);

        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL
        );
        brandParams.bottomMargin = dp(42);
        root.addView(brand, brandParams);

        LinearLayout loadingPanel = new LinearLayout(activity);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setPadding(dp(2), dp(2), dp(2), dp(2));

        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        statusView = text(activity.getString(R.string.vostok_loading_status_start), 12.5f, Color.WHITE, false);
        percentView = text("0%", 12.5f, Color.WHITE, true);
        percentView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(28), 1.0f);
        statusRow.addView(statusView, statusParams);
        statusRow.addView(percentView, new LinearLayout.LayoutParams(dp(72), dp(28)));
        loadingPanel.addView(statusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        progressRail = new FrameLayout(activity);
        progressRail.setBackground(roundRect(Color.argb(150, 31, 28, 27), dp(4)));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(7)
        );
        railParams.topMargin = dp(2);
        loadingPanel.addView(progressRail, railParams);

        progressFill = new View(activity);
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(255, 111, 44), Color.rgb(229, 65, 38)}
        );
        fill.setCornerRadius(dp(4));
        progressFill.setBackground(fill);
        progressRail.addView(progressFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        tipView = text("", 11.5f, Color.rgb(229, 225, 222), false);
        tipView.setGravity(Gravity.CENTER_VERTICAL);
        tipView.setMaxLines(2);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        tipParams.topMargin = dp(10);
        loadingPanel.addView(tipView, tipParams);

        TextView footer = text("VOSTOK DEV", 9.5f, Color.argb(175, 255, 255, 255), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            footer.setLetterSpacing(0.08f);
        }
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        footerParams.topMargin = dp(1);
        loadingPanel.addView(footer, footerParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        panelParams.leftMargin = dp(76);
        panelParams.rightMargin = dp(76);
        panelParams.bottomMargin = dp(24);
        root.addView(loadingPanel, panelParams);

        tips = activity.getResources().getStringArray(R.array.vostok_loading_tips);
        if (tips.length > 0) showTip(0, false);

        host.addView(root);
    }

    public void show() {
        activity.runOnUiThread(() -> {
            if (root == null) return;
            finishing = false;
            showing = true;
            root.animate().cancel();
            root.setAlpha(1.0f);
            root.setVisibility(View.VISIBLE);
            tipIndex = 0;
            if (tips.length > 0) showTip(tipIndex, false);
            handler.removeCallbacks(rotateTipRunnable);
            handler.postDelayed(rotateTipRunnable, TIP_INTERVAL_MS);
        });
    }

    public void update(int nativePercent) {
        activity.runOnUiThread(() -> {
            if (root == null) return;
            if (!showing) showImmediate();

            VostokLoadingStateManager.State state = stateManager.fromNativeProgress(nativePercent);
            statusView.setText(state.statusStringRes);
            percentView.setText(state.percent + "%");
            updateProgressWidth(state.percent);

            if (state.complete) finishLoading();
        });
    }

    private void showImmediate() {
        finishing = false;
        showing = true;
        root.animate().cancel();
        root.setAlpha(1.0f);
        root.setVisibility(View.VISIBLE);
        handler.removeCallbacks(rotateTipRunnable);
        handler.postDelayed(rotateTipRunnable, TIP_INTERVAL_MS);
    }

    private void updateProgressWidth(final int percent) {
        if (progressRail == null || progressFill == null) return;
        progressRail.post(() -> {
            int total = progressRail.getWidth();
            if (total <= 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            params.width = Math.round(total * (percent / 100.0f));
            progressFill.setLayoutParams(params);
        });
    }

    private void finishLoading() {
        if (finishing || root == null) return;
        finishing = true;
        handler.removeCallbacks(rotateTipRunnable);
        root.animate()
                .alpha(0.0f)
                .setDuration(FINISH_FADE_MS)
                .withEndAction(() -> {
                    if (root == null) return;
                    root.setVisibility(View.GONE);
                    root.setAlpha(1.0f);
                    showing = false;
                    finishing = false;
                })
                .start();
    }

    private void showTip(int index, boolean animate) {
        if (tipView == null || tips.length == 0) return;
        final String text = "Совет: " + tips[Math.max(0, Math.min(index, tips.length - 1))];
        if (!animate) {
            tipView.setText(text);
            tipView.setAlpha(1.0f);
            return;
        }
        tipView.animate().cancel();
        tipView.animate().alpha(0.0f).setDuration(110L).withEndAction(() -> {
            if (tipView == null) return;
            tipView.setText(text);
            tipView.animate().alpha(1.0f).setDuration(160L).start();
        }).start();
    }

    public void shutdown() {
        activity.runOnUiThread(() -> {
            handler.removeCallbacksAndMessages(null);
            showing = false;
            finishing = false;
            if (root != null) {
                root.animate().cancel();
                host.removeView(root);
                root = null;
            }
        });
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private FrameLayout.LayoutParams wrapContent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapContentLinear() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
