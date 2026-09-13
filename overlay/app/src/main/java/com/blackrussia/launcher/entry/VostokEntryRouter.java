package com.blackrussia.launcher.entry;

import android.content.Context;
import android.content.Intent;

import com.blackrussia.launcher.account.AccountApi;

/**
 * Single entry-state contract for the launcher -> game transition.
 * 031A keeps the current temporary character dialog functional, while making
 * the future Character Creator and Spawn Selection explicit destinations.
 */
public final class VostokEntryRouter {

    public static final String EXTRA_ENTRY_ROUTE = "vostok_entry_route";
    public static final String EXTRA_CHARACTER_ID = "vostok_character_id";

    private static final String PREFS = "vostok_entry_state";
    private static final String KEY_ROUTE = "pending_route";
    private static final String KEY_CHARACTER_ID = "pending_character_id";

    public enum Route {
        AUTHORIZATION,
        CHARACTER_CREATION,
        SPAWN_SELECTION
    }

    private VostokEntryRouter() { }

    public static Route resolve(String sessionToken, AccountApi.CharacterInfo selectedCharacter) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return Route.AUTHORIZATION;
        }
        if (selectedCharacter == null || selectedCharacter.id <= 0L) {
            return Route.CHARACTER_CREATION;
        }
        return Route.SPAWN_SELECTION;
    }

    /**
     * Persist the route because LoaderActivity may sit between launcher and
     * GTASA when the cache is missing. The future in-game Entry UI can consume
     * the same state even if Android Intent extras are not forwarded.
     */
    public static void persistPending(Context context, Route route, long characterId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ROUTE, route.name())
                .putLong(KEY_CHARACTER_ID, characterId)
                .apply();
    }

    public static Route readPendingRoute(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROUTE, Route.SPAWN_SELECTION.name());
        try {
            return Route.valueOf(raw);
        } catch (Exception ignored) {
            return Route.SPAWN_SELECTION;
        }
    }

    public static long readPendingCharacterId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_CHARACTER_ID, 0L);
    }

    public static Intent decorateGameIntent(Intent intent, Route route, long characterId) {
        intent.putExtra(EXTRA_ENTRY_ROUTE, route.name());
        intent.putExtra(EXTRA_CHARACTER_ID, characterId);
        return intent;
    }
}
