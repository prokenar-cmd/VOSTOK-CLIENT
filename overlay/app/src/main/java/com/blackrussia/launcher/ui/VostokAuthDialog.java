package com.blackrussia.launcher.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.blackrussia.game.R;

import androidx.core.content.res.ResourcesCompat;

/** Warm glass-style authorization modal matching the approved VOSTOK launcher. */
public final class VostokAuthDialog {

    public interface Listener {
        void onRequestEmailCode(String email);
        void onVerifyEmailCode(String email, String code);
        void onProviderSelected(String provider);
        void onDevLogin();
    }

    private final Context context;
    private final Listener listener;
    private final Dialog dialog;
    private final Typeface regular;
    private final Typeface bold;

    private EditText emailInput;
    private EditText codeInput;
    private TextView primaryButton;
    private TextView helperText;
    private TextView errorText;
    private TextView emailLabel;
    private TextView providerDivider;
    private LinearLayout providerArea;
    private String activeEmail = "";
    private boolean codeStep = false;
    private boolean busy = false;

    public VostokAuthDialog(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        Typeface r = ResourcesCompat.getFont(context, R.font.pt_root_ui_regular);
        Typeface b = ResourcesCompat.getFont(context, R.font.pt_root_ui_bold);
        regular = r == null ? Typeface.DEFAULT : r;
        bold = b == null ? Typeface.DEFAULT_BOLD : b;

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.58f;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int preferred = Math.min((int) (metrics.widthPixels * 0.47f), dp(720));
            int width = Math.max(preferred, dp(330));
            width = Math.min(width, (int) (metrics.widthPixels * 0.92f));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public void setBusy(boolean value) {
        busy = value;
        primaryButton.setEnabled(!value);
        primaryButton.setAlpha(value ? 0.55f : 1.0f);
        emailInput.setEnabled(!value && !codeStep);
        codeInput.setEnabled(!value && codeStep);
    }

    public void showCodeStep(String email, String devCode) {
        codeStep = true;
        activeEmail = email == null ? "" : email;
        setBusy(false);

        emailLabel.setText("Код из письма");
        emailInput.setVisibility(View.GONE);
        codeInput.setVisibility(View.VISIBLE);
        codeInput.setText("");
        codeInput.requestFocus();
        primaryButton.setText("ПОДТВЕРДИТЬ");
        helperText.setText(devCode == null || devCode.isEmpty()
                ? "Введите 6-значный код из письма"
                : "DEV-код: " + devCode + "  •  позже здесь будет реальное письмо");
        providerDivider.setVisibility(View.GONE);
        providerArea.setVisibility(View.GONE);
        clearError();
    }

    public void showError(String message) {
        setBusy(false);
        errorText.setText(message == null || message.isEmpty() ? "Ошибка авторизации" : message);
        errorText.setVisibility(View.VISIBLE);
    }

    public void clearError() {
        errorText.setText("");
        errorText.setVisibility(View.GONE);
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(context);
        root.setPadding(dp(1), dp(1), dp(1), dp(1));
        root.setBackground(panelBackground());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout header = new FrameLayout(context);
        content.addView(header, lpMatchWrap());

        LinearLayout headerTexts = new LinearLayout(context);
        headerTexts.setOrientation(LinearLayout.VERTICAL);
        header.addView(headerTexts, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("АВТОРИЗАЦИЯ", 28, bold, Color.WHITE);
        headerTexts.addView(title);
        TextView subtitle = text("Войдите, чтобы продолжить игру", 16, regular, Color.argb(220, 235, 229, 224));
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.topMargin = dp(3);
        headerTexts.addView(subtitle, subLp);

        TextView close = text("×", 34, regular, Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        closeLp.topMargin = dp(-10);
        closeLp.rightMargin = dp(-10);
        header.addView(close, closeLp);

        emailLabel = text("Войти по почте", 15, bold, Color.WHITE);
        LinearLayout.LayoutParams labelLp = lpMatchWrap();
        labelLp.topMargin = dp(24);
        content.addView(emailLabel, labelLp);

        emailInput = input("Введите ваш email", false);
        LinearLayout.LayoutParams inputLp = lpMatchWrap();
        inputLp.topMargin = dp(8);
        content.addView(emailInput, inputLp);

        codeInput = input("Введите 6-значный код", true);
        codeInput.setVisibility(View.GONE);
        content.addView(codeInput, inputLp);

        helperText = text("", 13, regular, Color.argb(205, 212, 202, 197));
        LinearLayout.LayoutParams helperLp = lpMatchWrap();
        helperLp.topMargin = dp(7);
        content.addView(helperText, helperLp);

        errorText = text("", 13, regular, Color.rgb(255, 153, 124));
        errorText.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = lpMatchWrap();
        errorLp.topMargin = dp(7);
        content.addView(errorText, errorLp);

        primaryButton = text("ПРОДОЛЖИТЬ", 17, bold, Color.WHITE);
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setBackground(primaryButtonBackground());
        primaryButton.setOnClickListener(v -> onPrimary());
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        primaryLp.topMargin = dp(16);
        content.addView(primaryButton, primaryLp);

        providerDivider = text("────────   ИЛИ ВОЙДИТЕ ЧЕРЕЗ   ────────", 12, regular,
                Color.argb(170, 226, 216, 210));
        providerDivider.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dividerLp = lpMatchWrap();
        dividerLp.topMargin = dp(16);
        content.addView(providerDivider, dividerLp);

        providerArea = new LinearLayout(context);
        providerArea.setOrientation(LinearLayout.HORIZONTAL);
        providerArea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams providersLp = lpMatchWrap();
        providersLp.topMargin = dp(12);
        content.addView(providerArea, providersLp);
        providerArea.addView(providerButton("G", "Google", "google", Color.rgb(244, 180, 0)), providerLp());
        providerArea.addView(providerButton("VK", "ВК", "vk", Color.rgb(0, 119, 255)), providerLp());
        providerArea.addView(providerButton("Я", "Яндекс", "yandex", Color.rgb(255, 72, 72)), providerLp());

        TextView dev = text("DEV ВХОД", 16, bold, Color.WHITE);
        dev.setGravity(Gravity.CENTER);
        dev.setBackground(secondaryButtonBackground());
        dev.setOnClickListener(v -> {
            if (busy) return;
            clearError();
            listener.onDevLogin();
        });
        LinearLayout.LayoutParams devLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        devLp.topMargin = dp(16);
        content.addView(dev, devLp);

        TextView devHint = text("Временный технический вход для разработки", 12, regular,
                Color.argb(165, 205, 197, 192));
        devHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams devHintLp = lpMatchWrap();
        devHintLp.topMargin = dp(6);
        content.addView(devHint, devHintLp);

        return root;
    }

    private void onPrimary() {
        if (busy) return;
        clearError();
        if (!codeStep) {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty() || !email.contains("@")) {
                showError("Введите корректную электронную почту");
                return;
            }
            setBusy(true);
            listener.onRequestEmailCode(email);
        } else {
            String code = codeInput.getText().toString().trim();
            if (code.length() != 6) {
                showError("Введите 6-значный код");
                return;
            }
            setBusy(true);
            listener.onVerifyEmailCode(activeEmail, code);
        }
    }

    private View providerButton(String mark, String label, String provider, int markColor) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), 0, dp(8), 0);
        box.setBackground(secondaryButtonBackground());
        box.setOnClickListener(v -> {
            if (busy) return;
            clearError();
            listener.onProviderSelected(provider);
        });

        TextView icon = text(mark, mark.length() > 1 ? 13 : 19, bold, markColor);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, new LinearLayout.LayoutParams(dp(31), dp(42)));

        TextView caption = text(label, 14, bold, Color.WHITE);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        captionLp.leftMargin = dp(3);
        box.addView(caption, captionLp);
        return box;
    }

    private LinearLayout.LayoutParams providerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1.0f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        return lp;
    }

    private EditText input(String hint, boolean numeric) {
        EditText edit = new EditText(context);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setHintTextColor(Color.argb(150, 225, 217, 211));
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(15.0f);
        edit.setTypeface(regular);
        edit.setPadding(dp(16), 0, dp(16), 0);
        edit.setBackground(inputBackground());
        edit.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        edit.setSelectAllOnFocus(false);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        ));
        return edit;
    }

    private TextView text(String value, int sp, Typeface typeface, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(typeface);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.argb(247, 88, 48, 34),
                        Color.argb(247, 40, 31, 30),
                        Color.argb(247, 31, 30, 31)
                }
        );
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), Color.argb(165, 255, 156, 112));
        return drawable;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(205, 39, 35, 35));
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(1), Color.argb(145, 235, 211, 198));
        return drawable;
    }

    private GradientDrawable primaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(255, 121, 44), Color.rgb(255, 54, 54) }
        );
        drawable.setCornerRadius(dp(13));
        return drawable;
    }

    private GradientDrawable secondaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(190, 43, 39, 39));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), Color.argb(120, 220, 205, 197));
        return drawable;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
