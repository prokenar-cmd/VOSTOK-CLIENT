package com.blackrussia.launcher.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;

import com.blackrussia.game.R;
import com.blackrussia.launcher.account.AccountApi;
import com.blackrussia.launcher.account.AccountSessionStore;
import com.blackrussia.launcher.account.GameLaunchIdentity;
import com.blackrussia.launcher.ui.VostokLauncherView;
import com.blackrussia.launcher.ui.VostokAuthDialog;

/**
 * VOSTOK launcher account flow on top of the approved main-screen art direction.
 * The main screen owns character selection; unauthenticated PLAY opens the
 * matching VOSTOK authorization modal before the one-time game-ticket launch.
 */
public class MainActivity extends AppCompatActivity {

    private VostokLauncherView launcherView;
    private AccountApi accountApi;
    private AccountSessionStore sessionStore;
    private String sessionToken = "";
    private long accountId = 0L;
    private AccountApi.CharacterInfo selectedCharacter;
    private java.util.List<AccountApi.CharacterInfo> availableCharacters = new java.util.ArrayList<>();
    private boolean accountRequestInFlight = false;
    private VostokAuthDialog authDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        setContentView(R.layout.activity_main);

        launcherView = findViewById(R.id.vostokLauncherView);
        launcherView.setActionListener(this::onLauncherAction);
        launcherView.setCharacterSelectionListener(this::selectCharacter);
        accountApi = new AccountApi(this);
        sessionStore = new AccountSessionStore(this);

        restoreCachedAccountState();
        restoreBackendSession();
    }

    private void restoreCachedAccountState() {
        sessionToken = sessionStore.getToken();
        accountId = sessionStore.getAccountId();
        long characterId = sessionStore.getCharacterId();
        String characterName = sessionStore.getCharacterName();
        launcherView.setAccountState(!sessionToken.isEmpty(), accountId, characterId, characterName);
    }

    private void restoreBackendSession() {
        if (sessionToken.isEmpty()) return;
        accountRequestInFlight = true;
        accountApi.restoreSession(sessionToken, new AccountApi.Callback<AccountApi.AuthResult>() {
            @Override
            public void onSuccess(AccountApi.AuthResult result) {
                accountRequestInFlight = false;
                applyAuthResult(result, false);
            }

            @Override
            public void onError(String message) {
                accountRequestInFlight = false;
                clearAccountState();
                Toast.makeText(MainActivity.this, "Сессия VOSTOK завершена. Войдите снова.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onLauncherAction(VostokLauncherView.Action action) {
        switch (action) {
            case PLAY:
                onClickPlay();
                break;
            case ACCOUNT:
                if (sessionToken.isEmpty()) showAuthDialog();
                else if (selectedCharacter == null) showCreateCharacterDialog();
                break;
            case SERVER:
                showShellMessage("Технический сервер: VOSTOK DEV.");
                break;
            case NEWS:
                showShellMessage("Новости пока показывают утверждённый макет.");
                break;
            case SITE:
                showShellMessage("Ссылка на сайт ещё не задана.");
                break;
            case VK:
                showShellMessage("Ссылка ВКонтакте ещё не задана.");
                break;
            case TELEGRAM:
                showShellMessage("Ссылка Telegram ещё не задана.");
                break;
            case SUPPORT:
                showShellMessage("Раздел поддержки подключим позже.");
                break;
            case SETTINGS:
                showShellMessage("Настройки VOSTOK будут оформлены отдельным проходом.");
                break;
        }
    }

    private void performDevLogin() {
        if (accountRequestInFlight) return;
        accountRequestInFlight = true;
        if (authDialog != null) authDialog.setBusy(true);
        accountApi.devLogin(new AccountApi.Callback<AccountApi.AuthResult>() {
            @Override
            public void onSuccess(AccountApi.AuthResult result) {
                accountRequestInFlight = false;
                applyAuthResult(result, true);
                if (authDialog != null) authDialog.dismiss();
            }

            @Override
            public void onError(String message) {
                accountRequestInFlight = false;
                if (authDialog != null && authDialog.isShowing()) authDialog.showError(message);
                else showAccountError(message);
            }
        });
    }

    private void showAuthDialog() {
        if (authDialog != null && authDialog.isShowing()) return;
        authDialog = new VostokAuthDialog(this, new VostokAuthDialog.Listener() {
            @Override
            public void onRequestEmailCode(String email) {
                requestEmailCode(email);
            }

            @Override
            public void onVerifyEmailCode(String email, String code) {
                verifyEmailCode(email, code);
            }

            @Override
            public void onProviderSelected(String provider) {
                String name;
                if ("google".equals(provider)) name = "Google";
                else if ("vk".equals(provider)) name = "ВКонтакте";
                else name = "Яндекс";
                Toast.makeText(MainActivity.this, name + ": подключим после добавления ключей.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDevLogin() {
                performDevLogin();
            }
        });
        authDialog.show();
    }

    private void requestEmailCode(String email) {
        if (accountRequestInFlight) return;
        accountRequestInFlight = true;
        if (authDialog != null) authDialog.setBusy(true);
        accountApi.requestEmailCode(email, new AccountApi.Callback<AccountApi.CodeRequest>() {
            @Override
            public void onSuccess(AccountApi.CodeRequest request) {
                accountRequestInFlight = false;
                if (authDialog != null && authDialog.isShowing()) {
                    authDialog.showCodeStep(email, request.devCode);
                }
            }

            @Override
            public void onError(String message) {
                accountRequestInFlight = false;
                if (authDialog != null && authDialog.isShowing()) authDialog.showError(message);
                else showAccountError(message);
            }
        });
    }

    private void verifyEmailCode(String email, String code) {
        if (accountRequestInFlight) return;
        accountRequestInFlight = true;
        if (authDialog != null) authDialog.setBusy(true);
        accountApi.verifyEmailCode(email, code, new AccountApi.Callback<AccountApi.AuthResult>() {
            @Override
            public void onSuccess(AccountApi.AuthResult result) {
                accountRequestInFlight = false;
                applyAuthResult(result, true);
                if (authDialog != null) authDialog.dismiss();
            }

            @Override
            public void onError(String message) {
                accountRequestInFlight = false;
                if (authDialog != null && authDialog.isShowing()) authDialog.showError(message);
                else showAccountError(message);
            }
        });
    }

    private void applyAuthResult(AccountApi.AuthResult result, boolean notify) {
        if (result == null || result.sessionToken == null || result.sessionToken.isEmpty() || result.accountId <= 0) {
            showAccountError("Backend вернул неполную сессию");
            return;
        }

        long preferredCharacterId = sessionStore.getCharacterId();
        sessionToken = result.sessionToken;
        accountId = result.accountId;
        availableCharacters = new java.util.ArrayList<>(result.characters);
        selectedCharacter = findCharacter(preferredCharacterId);
        if (selectedCharacter == null) selectedCharacter = result.getPrimaryCharacter();

        sessionStore.save(result);
        if (selectedCharacter != null) sessionStore.saveCharacter(selectedCharacter);
        launcherView.setAccountState(
                true,
                accountId,
                availableCharacters,
                selectedCharacter == null ? 0L : selectedCharacter.id
        );
        if (notify) {
            Toast.makeText(this,
                    selectedCharacter == null ? "Аккаунт открыт. Создайте персонажа." : "Вход выполнен: " + selectedCharacter.name,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private AccountApi.CharacterInfo findCharacter(long characterId) {
        if (characterId <= 0L) return null;
        for (AccountApi.CharacterInfo character : availableCharacters) {
            if (character.id == characterId) return character;
        }
        return null;
    }

    private void selectCharacter(long characterId) {
        AccountApi.CharacterInfo character = findCharacter(characterId);
        if (character == null) return;
        selectedCharacter = character;
        sessionStore.saveCharacter(character);
    }

    private void showCreateCharacterDialog() {
        EditText name = new EditText(this);
        name.setHint("Имя персонажа");
        name.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Техническое создание персонажа")
                .setMessage("Финальный 3D Character Creator будет отдельным этапом.")
                .setView(name)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (d, w) -> createCharacter(name.getText().toString().trim()))
                .show();
    }

    private void createCharacter(String name) {
        if (sessionToken.isEmpty() || name.isEmpty()) return;
        accountApi.createCharacter(sessionToken, name, new AccountApi.Callback<AccountApi.CharacterInfo>() {
            @Override
            public void onSuccess(AccountApi.CharacterInfo character) {
                selectedCharacter = character;
                availableCharacters.add(character);
                sessionStore.saveCharacter(character);
                launcherView.setAccountState(true, accountId, availableCharacters, character.id);
                Toast.makeText(MainActivity.this, "Персонаж создан: " + character.name, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                showAccountError(message);
            }
        });
    }

    private void performLogout() {
        String token = sessionToken;
        clearAccountState();
        if (token.isEmpty()) return;
        accountApi.logout(token, new AccountApi.Callback<Boolean>() {
            @Override public void onSuccess(Boolean value) { }
            @Override public void onError(String message) { }
        });
    }

    private void clearAccountState() {
        sessionStore.clear();
        sessionToken = "";
        accountId = 0L;
        selectedCharacter = null;
        availableCharacters.clear();
        launcherView.setAccountState(false, 0L, 0L, "");
    }

    public void onClickPlay() {
        if (accountRequestInFlight) {
            Toast.makeText(this, "Проверяем VOSTOK Account...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sessionToken.isEmpty()) {
            showAuthDialog();
            return;
        }
        if (selectedCharacter == null) {
            showCreateCharacterDialog();
            return;
        }
        accountRequestInFlight = true;
        Toast.makeText(this, "Получаем игровой ticket...", Toast.LENGTH_SHORT).show();
        accountApi.issueGameTicket(sessionToken, selectedCharacter.id, new AccountApi.Callback<AccountApi.GameTicket>() {
            @Override
            public void onSuccess(AccountApi.GameTicket ticket) {
                accountRequestInFlight = false;
                try {
                    GameLaunchIdentity.writeTransportName(ticket.connectName);
                    startGameAfterCacheCheck();
                } catch (Exception e) {
                    showAccountError("Не удалось подготовить вход в игру: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                accountRequestInFlight = false;
                showAccountError(message);
            }
        });
    }

    private void startGameAfterCacheCheck() {
        File marker = new File(Environment.getExternalStorageDirectory(), "BlackRussia/texdb/gta3.img");
        if (marker.exists()) {
            startActivity(new Intent(getApplicationContext(), com.blackrussia.game.core.GTASA.class));
        } else {
            startActivity(new Intent(getApplicationContext(), LoaderActivity.class));
        }
    }

    private void showAccountError(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void showShellMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void enterImmersiveMode() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
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

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    public boolean isRecordAudioPermissionGranted() {
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission("android.permission.RECORD_AUDIO") == 0) {
            return true;
        }
        requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 2);
        return false;
    }
}
