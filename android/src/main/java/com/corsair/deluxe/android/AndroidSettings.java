package com.corsair.deluxe.android;

import android.content.Context;
import android.content.SharedPreferences;

import com.corsair.deluxe.core.GameSettings;

/**
 * Persists {@link GameSettings} in {@link SharedPreferences} and loads them back before the game
 * starts, so the options picked in the main menu survive an app restart.
 */
public final class AndroidSettings {
    private static final String PREFS_NAME = "corsair_deluxe_settings";
    private static final String KEY_SOUND = "soundEnabled";
    private static final String KEY_MUSIC = "musicEnabled";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_DEBUG = "debugMode";

    private static SharedPreferences preferences;

    private AndroidSettings() {
    }

    /** Loads the stored options and keeps saving every later change. Safe to call from every activity. */
    public static void install(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences = prefs;

        GameSettings.setSoundEnabled(prefs.getBoolean(KEY_SOUND, GameSettings.isSoundEnabled()));
        GameSettings.setMusicEnabled(prefs.getBoolean(KEY_MUSIC, GameSettings.isMusicEnabled()));
        GameSettings.setDifficulty(readDifficulty(prefs));
        GameSettings.setDebugMode(prefs.getBoolean(KEY_DEBUG, GameSettings.isDebugMode()));

        GameSettings.setListener(new GameSettings.Listener() {
            @Override
            public void onSettingsChanged() {
                save();
            }
        });
    }

    private static GameSettings.Difficulty readDifficulty(SharedPreferences prefs) {
        String stored = prefs.getString(KEY_DIFFICULTY, null);
        if (stored != null) {
            for (GameSettings.Difficulty difficulty : GameSettings.Difficulty.values()) {
                if (difficulty.name().equals(stored)) {
                    return difficulty;
                }
            }
        }
        return GameSettings.getDifficulty();
    }

    private static void save() {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putBoolean(KEY_SOUND, GameSettings.isSoundEnabled())
                .putBoolean(KEY_MUSIC, GameSettings.isMusicEnabled())
                .putString(KEY_DIFFICULTY, GameSettings.getDifficulty().name())
                .putBoolean(KEY_DEBUG, GameSettings.isDebugMode())
                .apply();
    }
}
