package com.blackrussia.launcher.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.blackrussia.launcher.account.AccountApi;
import com.blackrussia.launcher.account.AccountSessionStore;
import com.blackrussia.launcher.account.GameLaunchIdentity;
import com.blackrussia.launcher.entry.VostokEntryRouter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * VOSTOK 031C entry UI.
 *
 * Product contract:
 * - character creation = ready in-game model + data only;
 * - no face, hair, clothing, body or confirmation systems;
 * - a newly created character starts at the fixed starter spawn immediately;
 * - an existing character chooses a spawn card before a ticket is issued;
 * - no slogans or decorative advertising copy are present in this UI.
 */
public final class VostokEntryActivity extends AppCompatActivity {

    private static final int BG = 0xFF0B0E10;
    private static final int PANEL = 0xEE15191C;
    private static final int PANEL_ALT = 0xEE1B2024;
    private static final int BORDER = 0xFF333A3F;
    private static final int ACCENT = 0xFFFF6A21;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFF9BA1A6;
    private static final int DISABLED = 0xFF62676B;

    private static final Pattern NAME_PART = Pattern.compile("^[A-Za-z]{2,12}$");

    private static final class SkinOption {
        final int skinId;
        final int gender;
        final String drawableName;

        SkinOption(int skinId, int gender, String drawableName) {
            this.skinId = skinId;
            this.gender = gender;
            this.drawableName = drawableName;
        }
    }

    // These are real GTA/CRMP model ids. Preview images are resolved only when
    // the installed client actually contains a matching drawable; otherwise the
    // UI shows the model id instead of inventing a fake character image.
    private static final SkinOption[] STARTER_SKINS = new SkinOption[] {
            new SkinOption(18, 0, "auc_skin_18"),
            new SkinOption(23, 0, "auc_skin_23"),
            new SkinOption(24, 0, "auc_skin_24"),
            new SkinOption(33, 0, "auc_skin_33"),
            new SkinOption(50, 0, "auc_skin_50"),
            new SkinOption(122, 0, "skin_122"),
            new SkinOption(180, 0, "auc_skin_180"),
            new SkinOption(293, 0, "auc_skin_293")
    };

    private static final class SpawnOption {
        final int mode;
        final String title;
        final boolean available;
        final String drawableName;

        SpawnOption(int mode, String title, boolean available, String drawableName) {
            this.mode = mode;
            this.title = title;
            this.available = available;
            this.drawableName = drawableName;
        }
    }

    private AccountApi accountApi;
    private AccountSessionStore sessionStore;
    private String sessionToken = "";
    private VostokEntryRouter.Route route = VostokEntryRouter.Route.SPAWN_SELECTION;

    private FrameLayout root;
    private LinearLayout pageHost;
    private TextView headerTitle;
    private boolean requestInFlight;

    private int creationStep = 1;
    private SkinOption selectedSkin = STARTER_SKINS[0];
    private ImageView selectedSkinPreview;
    private TextView selectedSkinFallback;

    private EditText firstNameField;
    private EditText lastNameField;
    private EditText birthDateField;
    private EditText nationalityField;
    private String draftFirstName = "";
    private String draftLastName = "";
    private String draftBirthDate = "";
    private String draftNationality = "";

    private AccountApi.CharacterInfo selectedCharacter;
    private int selectedSpawnMode = AccountApi.SPAWN_START;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();

        accountApi = new AccountApi(this);
        sessionStore = new AccountSessionStore(this);
        sessionToken = sessionStore.getToken();
        route = readRoute();

        buildShell();

        if (sessionToken == null || sessionToken.isEmpty()) {
            Toast.makeText(this, "Требуется авторизация VOSTOK", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (route == VostokEntryRouter.Route.CHARACTER_CREATION) {
            showCharacterModelStep();
        } else {
            restoreCharacterForSpawn();
        }
    }

    private VostokEntryRouter.Route readRoute() {
        String raw = getIntent().getStringExtra(VostokEntryRouter.EXTRA_ENTRY_ROUTE);
        if (raw != null) {
            try {
                return VostokEntryRouter.Route.valueOf(raw);
            } catch (Exception ignored) { }
        }
        return VostokEntryRouter.readPendingRoute(this);
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        int backgroundId = getResources().getIdentifier(
                "vostok_loading_background", "drawable", getPackageName());
        if (backgroundId != 0) {
            ImageView background = new ImageView(this);
            background.setImageResource(backgroundId);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setAlpha(0.22f);
            root.addView(background, matchParent());
        }

        View shade = new View(this);
        GradientDrawable shadeDrawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xEB080B0D, 0xD90A0D0F, 0xEF080A0C}
        );
        shade.setBackground(shadeDrawable);
        root.addView(shade, matchParent());

        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setPadding(dp(24), dp(16), dp(24), dp(20));
        root.addView(chrome, matchParent());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        chrome.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(TEXT);
        back.setBackground(roundRect(PANEL_ALT, dp(12), BORDER, dp(1)));
        back.setOnClickListener(v -> handleBackAction());
        header.addView(back, new LinearLayout.LayoutParams(dp(54), dp(48)));

        TextView brand = text("VOSTOK", 24, TEXT, true);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(dp(170), dp(48));
        brandParams.leftMargin = dp(14);
        header.addView(brand, brandParams);

        headerTitle = text("", 22, TEXT, true);
        headerTitle.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(48), 1f));

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        chrome.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void showCharacterModelStep() {
        creationStep = 1;
        headerTitle.setText("СОЗДАНИЕ ПЕРСОНАЖА");
        pageHost.removeAllViews();

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        pageHost.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(buildSelectedModelPane(), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.38f));

        LinearLayout right = panelColumn();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        rightParams.leftMargin = dp(18);
        body.addView(right, rightParams);

        right.addView(stepHeader("1", "Выбор модели", "2", "Данные"));
        TextView title = text("ВЫБОР МОДЕЛИ", 21, TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        titleParams.topMargin = dp(12);
        right.addView(title, titleParams);

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(0, dp(4), 0, dp(4));
        scroll.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (SkinOption option : STARTER_SKINS) {
            grid.addView(buildSkinCard(option), new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)));
        }

        Button next = primaryButton("ДАЛЕЕ");
        next.setOnClickListener(v -> showCharacterDataStep());
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        nextParams.topMargin = dp(12);
        right.addView(next, nextParams);
    }

    private View buildSelectedModelPane() {
        LinearLayout pane = panelColumn();
        pane.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(roundRect(0x99101417, dp(16), BORDER, dp(1)));
        pane.addView(previewFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        selectedSkinPreview = new ImageView(this);
        selectedSkinPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        selectedSkinPreview.setPadding(dp(10), dp(10), dp(10), dp(10));
        previewFrame.addView(selectedSkinPreview, matchParent());

        selectedSkinFallback = text("", 30, MUTED, true);
        selectedSkinFallback.setGravity(Gravity.CENTER);
        previewFrame.addView(selectedSkinFallback, matchParent());
        updateLargeSkinPreview();

        TextView modelLabel = text("Модель " + selectedSkin.skinId, 16, TEXT, true);
        modelLabel.setTag("model_label");
        modelLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        labelParams.topMargin = dp(8);
        pane.addView(modelLabel, labelParams);
        return pane;
    }

    private View buildSkinCard(SkinOption option) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(6), dp(6), dp(4));
        card.setBackground(cardBackground(option.skinId == selectedSkin.skinId, true));
        card.setOnClickListener(v -> {
            selectedSkin = option;
            showCharacterModelStep();
        });

        FrameLayout preview = new FrameLayout(this);
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));

        int drawableId = resolveDrawable(option.drawableName);
        if (drawableId != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(drawableId);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.addView(image, matchParent());
        } else {
            TextView fallback = text(String.valueOf(option.skinId), 24, MUTED, true);
            fallback.setGravity(Gravity.CENTER);
            preview.addView(fallback, matchParent());
        }

        TextView id = text("Модель " + option.skinId, 13, TEXT, true);
        id.setGravity(Gravity.CENTER);
        card.addView(id, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(132);
        params.height = dp(154);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(params);
        return card;
    }

    private void showCharacterDataStep() {
        creationStep = 2;
        headerTitle.setText("СОЗДАНИЕ ПЕРСОНАЖА");
        pageHost.removeAllViews();

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        pageHost.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(buildSelectedModelPane(), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.38f));

        LinearLayout right = panelColumn();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        rightParams.leftMargin = dp(18);
        body.addView(right, rightParams);

        right.addView(stepHeader("1", "Выбор модели", "2", "Данные"));
        TextView title = text("ДАННЫЕ ПЕРСОНАЖА", 21, TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        titleParams.topMargin = dp(12);
        right.addView(title, titleParams);

        ScrollView formScroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        formScroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(formScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        firstNameField = field(form, "Имя", "Введите имя", draftFirstName);
        lastNameField = field(form, "Фамилия", "Введите фамилию", draftLastName);
        birthDateField = field(form, "Дата рождения", "ГГГГ-ММ-ДД", draftBirthDate);
        nationalityField = field(form, "Национальность", "Введите национальность", draftNationality);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        buttonsParams.topMargin = dp(12);
        right.addView(buttons, buttonsParams);

        Button previous = secondaryButton("НАЗАД");
        previous.setOnClickListener(v -> {
            captureDraft();
            showCharacterModelStep();
        });
        buttons.addView(previous, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.32f));

        Button create = primaryButton("СОЗДАТЬ ПЕРСОНАЖА");
        create.setOnClickListener(v -> submitCharacterCreation(create));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.68f);
        createParams.leftMargin = dp(10);
        buttons.addView(create, createParams);
    }

    private EditText field(LinearLayout parent, String label, String hint, String value) {
        TextView labelView = text(label, 14, TEXT, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        labelParams.topMargin = dp(4);
        parent.addView(labelView, labelParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setHintTextColor(0xFF6F767B);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(PANEL_ALT, dp(10), BORDER, dp(1)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        inputParams.bottomMargin = dp(7);
        parent.addView(input, inputParams);
        return input;
    }

    private void captureDraft() {
        if (firstNameField != null) draftFirstName = firstNameField.getText().toString().trim();
        if (lastNameField != null) draftLastName = lastNameField.getText().toString().trim();
        if (birthDateField != null) draftBirthDate = birthDateField.getText().toString().trim();
        if (nationalityField != null) draftNationality = nationalityField.getText().toString().trim();
    }

    private void submitCharacterCreation(Button button) {
        if (requestInFlight) return;
        captureDraft();

        if (!NAME_PART.matcher(draftFirstName).matches()
                || !NAME_PART.matcher(draftLastName).matches()) {
            Toast.makeText(this, "Имя и фамилия: 2–12 латинских букв", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isValidDate(draftBirthDate)) {
            Toast.makeText(this, "Дата рождения должна быть в формате ГГГГ-ММ-ДД", Toast.LENGTH_LONG).show();
            return;
        }
        if (draftNationality.length() < 2 || draftNationality.length() > 32) {
            Toast.makeText(this, "Укажите национальность", Toast.LENGTH_LONG).show();
            return;
        }

        requestInFlight = true;
        button.setEnabled(false);
        button.setText("СОЗДАНИЕ...");
        accountApi.createCharacter(
                sessionToken,
                draftFirstName,
                draftLastName,
                draftBirthDate,
                draftNationality,
                selectedSkin.gender,
                selectedSkin.skinId,
                new AccountApi.Callback<AccountApi.CharacterInfo>() {
                    @Override
                    public void onSuccess(AccountApi.CharacterInfo character) {
                        requestInFlight = false;
                        selectedCharacter = character;
                        sessionStore.saveCharacter(character);
                        // Product decision: a brand-new character does not see
                        // Spawn Selection. It receives the fixed starter spawn.
                        launchCharacter(character, AccountApi.SPAWN_START, button);
                    }

                    @Override
                    public void onError(String message) {
                        requestInFlight = false;
                        button.setEnabled(true);
                        button.setText("СОЗДАТЬ ПЕРСОНАЖА");
                        Toast.makeText(VostokEntryActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void restoreCharacterForSpawn() {
        headerTitle.setText("ВЫБОР МЕСТА СПАВНА");
        showFunctionalStatus("Загрузка персонажа...");
        requestInFlight = true;
        accountApi.restoreSession(sessionToken, new AccountApi.Callback<AccountApi.AuthResult>() {
            @Override
            public void onSuccess(AccountApi.AuthResult result) {
                requestInFlight = false;
                long preferredId = sessionStore.getCharacterId();
                selectedCharacter = findCharacter(result.characters, preferredId);
                if (selectedCharacter == null) selectedCharacter = result.getPrimaryCharacter();
                if (selectedCharacter == null) {
                    VostokEntryRouter.persistPending(VostokEntryActivity.this,
                            VostokEntryRouter.Route.CHARACTER_CREATION, 0L);
                    route = VostokEntryRouter.Route.CHARACTER_CREATION;
                    showCharacterModelStep();
                    return;
                }
                sessionStore.saveCharacter(selectedCharacter);
                showSpawnSelection(selectedCharacter);
            }

            @Override
            public void onError(String message) {
                requestInFlight = false;
                Toast.makeText(VostokEntryActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void showSpawnSelection(AccountApi.CharacterInfo character) {
        creationStep = 0;
        route = VostokEntryRouter.Route.SPAWN_SELECTION;
        headerTitle.setText("ВЫБОР МЕСТА СПАВНА");
        pageHost.removeAllViews();
        selectedSpawnMode = AccountApi.SPAWN_START;

        boolean lastAvailable = character.lastPlayed > 0L;
        SpawnOption[] options = new SpawnOption[] {
                new SpawnOption(AccountApi.SPAWN_START, "Вокзал", true, "vostok_spawn_station"),
                new SpawnOption(AccountApi.SPAWN_LAST, "Точка выхода", lastAvailable, "vostok_spawn_last"),
                new SpawnOption(AccountApi.SPAWN_HOME, "Дом", false, "vostok_spawn_home"),
                new SpawnOption(AccountApi.SPAWN_APARTMENT, "Квартира", false, "vostok_spawn_apartment")
        };

        LinearLayout panel = panelColumn();
        pageHost.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        cards.setPadding(dp(8), dp(6), dp(8), dp(6));
        scroll.addView(cards, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (SpawnOption option : options) cards.addView(buildSpawnCard(option));

        Button start = primaryButton("НАЧАТЬ ИГРУ");
        start.setOnClickListener(v -> launchCharacter(character, selectedSpawnMode, start));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(360), dp(60));
        startParams.gravity = Gravity.CENTER_HORIZONTAL;
        startParams.topMargin = dp(12);
        panel.addView(start, startParams);
    }

    private View buildSpawnCard(SpawnOption option) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(cardBackground(
                option.available && option.mode == selectedSpawnMode, option.available));
        card.setAlpha(option.available ? 1.0f : 0.48f);

        FrameLayout visual = new FrameLayout(this);
        visual.setBackground(roundRect(0xFF101519, dp(12), 0xFF262D32, dp(1)));
        card.addView(visual, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int drawableId = resolveDrawable(option.drawableName);
        if (drawableId != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(drawableId);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            visual.addView(image, matchParent());
        } else {
            TextView marker = text(spawnMarker(option.mode), 44,
                    option.available ? MUTED : DISABLED, true);
            marker.setGravity(Gravity.CENTER);
            visual.addView(marker, matchParent());
        }

        if (!option.available) {
            TextView lock = text("НЕДОСТУПНО", 13, TEXT, true);
            lock.setGravity(Gravity.CENTER);
            lock.setBackgroundColor(0x99000000);
            visual.addView(lock, matchParent());
        }

        TextView title = text(option.title, 19, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        titleParams.topMargin = dp(8);
        card.addView(title, titleParams);

        if (option.available) {
            card.setOnClickListener(v -> {
                selectedSpawnMode = option.mode;
                showSpawnSelection(selectedCharacter);
                selectedSpawnMode = option.mode;
                // Rebuild once more with the selected mode retained.
                rebuildSpawnWithSelection(selectedCharacter, option.mode);
            });
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(230),
                ViewGroup.LayoutParams.MATCH_PARENT);
        cardParams.setMargins(dp(8), 0, dp(8), 0);
        card.setLayoutParams(cardParams);
        return card;
    }

    private void rebuildSpawnWithSelection(AccountApi.CharacterInfo character, int mode) {
        // showSpawnSelection initializes START; this small wrapper preserves the
        // user's card after rebuilding without maintaining parallel card state.
        selectedSpawnMode = mode;
        headerTitle.setText("ВЫБОР МЕСТА СПАВНА");
        pageHost.removeAllViews();

        boolean lastAvailable = character.lastPlayed > 0L;
        SpawnOption[] options = new SpawnOption[] {
                new SpawnOption(AccountApi.SPAWN_START, "Вокзал", true, "vostok_spawn_station"),
                new SpawnOption(AccountApi.SPAWN_LAST, "Точка выхода", lastAvailable, "vostok_spawn_last"),
                new SpawnOption(AccountApi.SPAWN_HOME, "Дом", false, "vostok_spawn_home"),
                new SpawnOption(AccountApi.SPAWN_APARTMENT, "Квартира", false, "vostok_spawn_apartment")
        };

        LinearLayout panel = panelColumn();
        pageHost.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        cards.setPadding(dp(8), dp(6), dp(8), dp(6));
        scroll.addView(cards, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        for (SpawnOption option : options) cards.addView(buildSpawnCardStable(option));

        Button start = primaryButton("НАЧАТЬ ИГРУ");
        start.setOnClickListener(v -> launchCharacter(character, selectedSpawnMode, start));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(360), dp(60));
        startParams.gravity = Gravity.CENTER_HORIZONTAL;
        startParams.topMargin = dp(12);
        panel.addView(start, startParams);
    }

    private View buildSpawnCardStable(SpawnOption option) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(cardBackground(
                option.available && option.mode == selectedSpawnMode, option.available));
        card.setAlpha(option.available ? 1.0f : 0.48f);

        FrameLayout visual = new FrameLayout(this);
        visual.setBackground(roundRect(0xFF101519, dp(12), 0xFF262D32, dp(1)));
        card.addView(visual, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        int drawableId = resolveDrawable(option.drawableName);
        if (drawableId != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(drawableId);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            visual.addView(image, matchParent());
        } else {
            TextView marker = text(spawnMarker(option.mode), 44,
                    option.available ? MUTED : DISABLED, true);
            marker.setGravity(Gravity.CENTER);
            visual.addView(marker, matchParent());
        }
        if (!option.available) {
            TextView lock = text("НЕДОСТУПНО", 13, TEXT, true);
            lock.setGravity(Gravity.CENTER);
            lock.setBackgroundColor(0x99000000);
            visual.addView(lock, matchParent());
        }
        TextView title = text(option.title, 19, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        titleParams.topMargin = dp(8);
        card.addView(title, titleParams);
        if (option.available) card.setOnClickListener(v -> rebuildSpawnWithSelection(selectedCharacter, option.mode));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(230),
                ViewGroup.LayoutParams.MATCH_PARENT);
        cardParams.setMargins(dp(8), 0, dp(8), 0);
        card.setLayoutParams(cardParams);
        return card;
    }

    private String spawnMarker(int mode) {
        if (mode == AccountApi.SPAWN_START) return "В";
        if (mode == AccountApi.SPAWN_LAST) return "•";
        if (mode == AccountApi.SPAWN_HOME) return "Д";
        return "К";
    }

    private void launchCharacter(AccountApi.CharacterInfo character, int spawnMode, Button button) {
        if (requestInFlight || character == null) return;
        requestInFlight = true;
        button.setEnabled(false);
        String old = button.getText().toString();
        button.setText("ПОДКЛЮЧЕНИЕ...");

        accountApi.issueGameTicket(sessionToken, character.id, spawnMode,
                new AccountApi.Callback<AccountApi.GameTicket>() {
                    @Override
                    public void onSuccess(AccountApi.GameTicket ticket) {
                        requestInFlight = false;
                        try {
                            GameLaunchIdentity.writeTransportName(ticket.connectName);
                            VostokEntryRouter.persistPending(VostokEntryActivity.this,
                                    VostokEntryRouter.Route.SPAWN_SELECTION, character.id);
                            startGameAfterCacheCheck();
                        } catch (Exception e) {
                            button.setEnabled(true);
                            button.setText(old);
                            Toast.makeText(VostokEntryActivity.this,
                                    "Не удалось подготовить вход: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        requestInFlight = false;
                        button.setEnabled(true);
                        button.setText(old);
                        Toast.makeText(VostokEntryActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startGameAfterCacheCheck() {
        File marker = new File(Environment.getExternalStorageDirectory(), "BlackRussia/texdb/gta3.img");
        Intent intent;
        if (marker.exists()) {
            intent = new Intent(getApplicationContext(), com.blackrussia.game.core.GTASA.class);
        } else {
            intent = new Intent(getApplicationContext(), LoaderActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private AccountApi.CharacterInfo findCharacter(List<AccountApi.CharacterInfo> characters, long id) {
        if (characters == null || id <= 0L) return null;
        for (AccountApi.CharacterInfo character : characters) {
            if (character.id == id) return character;
        }
        return null;
    }

    private void showFunctionalStatus(String value) {
        pageHost.removeAllViews();
        TextView status = text(value, 18, TEXT, true);
        status.setGravity(Gravity.CENTER);
        pageHost.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout panelColumn() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(roundRect(PANEL, dp(18), 0xFF3A4146, dp(1)));
        return panel;
    }

    private View stepHeader(String leftIndex, String leftTitle, String rightIndex, String rightTitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        row.addView(stepChip(leftIndex, leftTitle, creationStep == 1),
                new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        secondParams.leftMargin = dp(8);
        row.addView(stepChip(rightIndex, rightTitle, creationStep == 2), secondParams);
        return row;
    }

    private View stepChip(String index, String title, boolean active) {
        TextView chip = text(index + "  " + title, 14, active ? TEXT : MUTED, active);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(roundRect(active ? 0xCC7B3215 : 0x66171B1E,
                dp(10), active ? ACCENT : BORDER, dp(1)));
        return chip;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(ACCENT, dp(12), 0xFFFF9A62, dp(1)));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(PANEL_ALT, dp(12), BORDER, dp(1)));
        return button;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable cardBackground(boolean selected, boolean enabled) {
        int fill = enabled ? PANEL_ALT : 0xEE111416;
        int stroke = selected ? ACCENT : BORDER;
        return roundRect(fill, dp(14), stroke, dp(selected ? 2 : 1));
    }

    private GradientDrawable roundRect(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void updateLargeSkinPreview() {
        if (selectedSkinPreview == null || selectedSkinFallback == null) return;
        int drawableId = resolveDrawable(selectedSkin.drawableName);
        if (drawableId != 0) {
            selectedSkinPreview.setImageResource(drawableId);
            selectedSkinPreview.setVisibility(View.VISIBLE);
            selectedSkinFallback.setVisibility(View.GONE);
        } else {
            selectedSkinPreview.setImageDrawable(null);
            selectedSkinPreview.setVisibility(View.GONE);
            selectedSkinFallback.setText("Модель " + selectedSkin.skinId);
            selectedSkinFallback.setVisibility(View.VISIBLE);
        }
    }

    private int resolveDrawable(String name) {
        if (name == null || name.isEmpty()) return 0;
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private boolean isValidDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            Date parsed = format.parse(value);
            return parsed != null && parsed.getTime() <= System.currentTimeMillis();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void handleBackAction() {
        if (route == VostokEntryRouter.Route.CHARACTER_CREATION && creationStep == 2) {
            captureDraft();
            showCharacterModelStep();
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }
}
