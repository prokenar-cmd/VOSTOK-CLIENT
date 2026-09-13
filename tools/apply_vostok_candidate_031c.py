#!/usr/bin/env python3
from pathlib import Path
import re


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"031C {label} anchor mismatch in {path}: {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# ---------------------------------------------------------------------------
# Account API: preserve old methods for compatibility and add the 031C fields.
# ---------------------------------------------------------------------------
api = Path("client/app/src/main/java/com/blackrussia/launcher/account/AccountApi.java")

replace_once(
    api,
    "public final class AccountApi {\n",
    """public final class AccountApi {\n    public static final int SPAWN_START = 0;\n    public static final int SPAWN_LAST = 1;\n    public static final int SPAWN_HOME = 2;\n    public static final int SPAWN_APARTMENT = 3;\n""",
    "spawn constants",
)

replace_once(
    api,
    """        public final int money;\n\n        private CharacterInfo(JSONObject json) {""",
    """        public final int money;\n        public final long lastPlayed;\n\n        private CharacterInfo(JSONObject json) {""",
    "character lastPlayed field",
)
replace_once(
    api,
    """            money = json.optInt(\"money\", 0);\n        }""",
    """            money = json.optInt(\"money\", 0);\n            lastPlayed = json.optLong(\"last_played\", 0L);\n        }""",
    "character lastPlayed parse",
)

old_create = '''    public void createCharacter(String token, String name, Callback<CharacterInfo> callback) {\n        JSONObject body = new JSONObject();\n        try {\n            body.put("name", name);\n            body.put("gender", 0);\n            body.put("skin", 0);\n        } catch (Exception ignored) { }\n        request("POST", "/v1/characters", token, body, new Callback<JSONObject>() {\n            @Override public void onSuccess(JSONObject value) {\n                JSONObject character = value.optJSONObject("character");\n                if (character == null) callback.onError("Backend не вернул персонажа");\n                else callback.onSuccess(new CharacterInfo(character));\n            }\n            @Override public void onError(String message) { callback.onError(message); }\n        });\n    }'''
new_create = '''    public void createCharacter(String token, String name, Callback<CharacterInfo> callback) {\n        JSONObject body = new JSONObject();\n        try {\n            body.put("name", name);\n            body.put("gender", 0);\n            body.put("skin", 0);\n        } catch (Exception ignored) { }\n        createCharacterRequest(token, body, callback);\n    }\n\n    public void createCharacter(String token, String firstName, String lastName,\n                                String birthDate, String nationality, int gender, int skin,\n                                Callback<CharacterInfo> callback) {\n        JSONObject body = new JSONObject();\n        try {\n            // name is retained for backward compatibility with the current backend.\n            // The 031C backend additionally persists structured profile fields.\n            body.put("name", firstName + "_" + lastName);\n            body.put("first_name", firstName);\n            body.put("last_name", lastName);\n            body.put("birth_date", birthDate);\n            body.put("nationality", nationality);\n            body.put("gender", gender);\n            body.put("skin", skin);\n        } catch (Exception ignored) { }\n        createCharacterRequest(token, body, callback);\n    }\n\n    private void createCharacterRequest(String token, JSONObject body, Callback<CharacterInfo> callback) {\n        request("POST", "/v1/characters", token, body, new Callback<JSONObject>() {\n            @Override public void onSuccess(JSONObject value) {\n                JSONObject character = value.optJSONObject("character");\n                if (character == null) callback.onError("Backend не вернул персонажа");\n                else callback.onSuccess(new CharacterInfo(character));\n            }\n            @Override public void onError(String message) { callback.onError(message); }\n        });\n    }'''
replace_once(api, old_create, new_create, "structured character creation")

old_ticket = '''    public void issueGameTicket(String token, long characterId, Callback<GameTicket> callback) {\n        JSONObject body = new JSONObject();\n        try { body.put("character_id", characterId); } catch (Exception ignored) { }\n        request("POST", "/v1/game-ticket", token, body, new Callback<JSONObject>() {\n            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new GameTicket(value)); }\n            @Override public void onError(String message) { callback.onError(message); }\n        });\n    }'''
new_ticket = '''    public void issueGameTicket(String token, long characterId, Callback<GameTicket> callback) {\n        issueGameTicket(token, characterId, SPAWN_START, callback);\n    }\n\n    public void issueGameTicket(String token, long characterId, int spawnMode, Callback<GameTicket> callback) {\n        JSONObject body = new JSONObject();\n        try {\n            body.put("character_id", characterId);\n            body.put("spawn_mode", spawnMode);\n        } catch (Exception ignored) { }\n        request("POST", "/v1/game-ticket", token, body, new Callback<JSONObject>() {\n            @Override public void onSuccess(JSONObject value) { callback.onSuccess(new GameTicket(value)); }\n            @Override public void onError(String message) { callback.onError(message); }\n        });\n    }'''
replace_once(api, old_ticket, new_ticket, "ticket spawn mode")

# ---------------------------------------------------------------------------
# Main launcher flow: auth remains here; creation/spawn routing moves to 031C.
# ---------------------------------------------------------------------------
main = Path("client/app/src/main/java/com/blackrussia/launcher/activity/MainActivity.java")
text = main.read_text(encoding="utf-8")

replace_once(
    main,
    '''            case ACCOUNT:\n                if (sessionToken.isEmpty()) showAuthDialog();\n                else if (selectedCharacter == null) showCreateCharacterDialog();\n                break;''',
    '''            case ACCOUNT:\n                if (sessionToken.isEmpty()) showAuthDialog();\n                else if (selectedCharacter == null) openEntryFlow(VostokEntryRouter.Route.CHARACTER_CREATION);\n                break;''',
    "account shortcut",
)

text = main.read_text(encoding="utf-8")
pattern = re.compile(
    r'    public void onClickPlay\(\) \{.*?\n    \}\n\n    private void startGameAfterCacheCheck\(VostokEntryRouter\.Route entryRoute\)',
    re.S,
)
match = pattern.search(text)
if not match:
    raise SystemExit("031C MainActivity onClickPlay anchor not found")
replacement = '''    public void onClickPlay() {\n        if (accountRequestInFlight) {\n            Toast.makeText(this, "Проверяем VOSTOK Account...", Toast.LENGTH_SHORT).show();\n            return;\n        }\n\n        VostokEntryRouter.Route entryRoute = VostokEntryRouter.resolve(sessionToken, selectedCharacter);\n        if (entryRoute == VostokEntryRouter.Route.AUTHORIZATION) {\n            showAuthDialog();\n            return;\n        }\n\n        openEntryFlow(entryRoute);\n    }\n\n    private void openEntryFlow(VostokEntryRouter.Route entryRoute) {\n        long characterId = selectedCharacter == null ? 0L : selectedCharacter.id;\n        VostokEntryRouter.persistPending(this, entryRoute, characterId);\n        Intent intent = new Intent(getApplicationContext(), VostokEntryActivity.class);\n        startActivity(VostokEntryRouter.decorateGameIntent(intent, entryRoute, characterId));\n    }\n\n    private void startGameAfterCacheCheck(VostokEntryRouter.Route entryRoute)'''
text = text[:match.start()] + replacement + text[match.end():]
main.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Manifest: entry flow is a dedicated launcher-side landscape activity.
# ---------------------------------------------------------------------------
manifest = Path("client/app/src/main/AndroidManifest.xml")
replace_once(
    manifest,
    '''        <activity\n            android:name="com.blackrussia.launcher.activity.LoaderActivity"\n            android:screenOrientation="landscape"\n            android:theme="@style/AppTheme.NoActionBar" />''',
    '''        <activity\n            android:name="com.blackrussia.launcher.activity.VostokEntryActivity"\n            android:exported="false"\n            android:screenOrientation="landscape"\n            android:theme="@style/AppTheme.NoActionBar" />\n        <activity\n            android:name="com.blackrussia.launcher.activity.LoaderActivity"\n            android:screenOrientation="landscape"\n            android:theme="@style/AppTheme.NoActionBar" />''',
    "entry activity manifest",
)

# ---------------------------------------------------------------------------
# Hard contract gates.
# ---------------------------------------------------------------------------
all_text = "\n".join([
    api.read_text(encoding="utf-8"),
    main.read_text(encoding="utf-8"),
    manifest.read_text(encoding="utf-8"),
    Path("client/app/src/main/java/com/blackrussia/launcher/activity/VostokEntryActivity.java").read_text(encoding="utf-8"),
])

required = (
    "SPAWN_START = 0",
    "SPAWN_LAST = 1",
    "birth_date",
    "nationality",
    "VostokEntryActivity.class",
    "СОЗДАТЬ ПЕРСОНАЖА",
    "ВЫБОР МЕСТА СПАВНА",
    "Точка выхода",
    "Дом",
    "Квартира",
    "launchCharacter(character, AccountApi.SPAWN_START",
)
for needle in required:
    if needle not in all_text:
        raise SystemExit(f"031C verification missing: {needle}")

for forbidden in (
    "Одежда",
    "Причёска",
    "Прическа",
    "Тип лица",
    "Цвет волос",
    "Телосложение",
    "Подтверждение",
    "Больше чем игра",
    "Твоя история начинается",
    "Начни свой путь",
    "Играй. Развивайся",
):
    if forbidden in Path("client/app/src/main/java/com/blackrussia/launcher/activity/VostokEntryActivity.java").read_text(encoding="utf-8"):
        raise SystemExit(f"031C forbidden UI copy present: {forbidden}")

print("Applied VOSTOK Candidate 031C entry flow")
