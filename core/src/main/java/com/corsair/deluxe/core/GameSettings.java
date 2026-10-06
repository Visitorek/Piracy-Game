package com.corsair.deluxe.core;

/**
 * Player-adjustable options chosen in the main menu "Opcje" panel. They live in the core module so
 * that the native Android screens and the libGDX screens read the exact same values. Storing and
 * loading them is left to the platform through {@link Listener}.
 */
public final class GameSettings {

    /** How hard the pirates of the sea (and the boarding battles) are to beat. */
    public enum Difficulty {
        EASY(2, 1, 0.40f),
        NORMAL(3, 1, 0.20f),
        HARD(4, 2, 0.05f);

        private final int aiDepth;
        private final int enemyPiratesPerHatch;
        private final float randomMoveChance;

        Difficulty(int aiDepth, int enemyPiratesPerHatch, float randomMoveChance) {
            this.aiDepth = aiDepth;
            this.enemyPiratesPerHatch = enemyPiratesPerHatch;
            this.randomMoveChance = randomMoveChance;
        }

        /** How many turns ahead the enemy captain plans. */
        public int getAiDepth() {
            return aiDepth;
        }

        /** Pirates the enemy starts with inside every hatch. */
        public int getEnemyPiratesPerHatch() {
            return enemyPiratesPerHatch;
        }

        /** Chance (0..1) that the enemy captain ignores the plan and plays any legal move. */
        public float getRandomMoveChance() {
            return randomMoveChance;
        }

        /** Next state of the three-state toggle, wrapping around to the first one. */
        public Difficulty next() {
            Difficulty[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /** Platform hook fired after every change so the new values can be persisted. */
    public interface Listener {
        void onSettingsChanged();
    }

    private static boolean soundEnabled = true;
    private static boolean musicEnabled = true;
    private static Difficulty difficulty = Difficulty.NORMAL;
    private static boolean debugMode = true;
    private static Listener listener;

    private GameSettings() {
    }

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void setSoundEnabled(boolean enabled) {
        if (soundEnabled != enabled) {
            soundEnabled = enabled;
            notifyChanged();
        }
    }

    public static void toggleSound() {
        setSoundEnabled(!soundEnabled);
    }

    public static boolean isMusicEnabled() {
        return musicEnabled;
    }

    public static void setMusicEnabled(boolean enabled) {
        if (musicEnabled != enabled) {
            musicEnabled = enabled;
            notifyChanged();
        }
    }

    public static void toggleMusic() {
        setMusicEnabled(!musicEnabled);
    }

    public static Difficulty getDifficulty() {
        return difficulty;
    }

    public static void setDifficulty(Difficulty value) {
        if (difficulty != value) {
            difficulty = value;
            notifyChanged();
        }
    }

    public static void cycleDifficulty() {
        setDifficulty(difficulty.next());
    }

    /** Debug mode shows tile coordinates on the world map and the temporary battle shortcut button. */
    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setDebugMode(boolean enabled) {
        if (debugMode != enabled) {
            debugMode = enabled;
            notifyChanged();
        }
    }

    public static void toggleDebugMode() {
        setDebugMode(!debugMode);
    }

    /** How many turns ahead the enemy captain plans. */
    public static int aiDepth() {
        return difficulty.getAiDepth();
    }

    /** Pirates the enemy starts with inside every hatch. */
    public static int enemyPiratesPerHatch() {
        return difficulty.getEnemyPiratesPerHatch();
    }

    /** Chance (0..1) that the enemy captain plays a random move instead of the best one. */
    public static float aiRandomMoveChance() {
        return difficulty.getRandomMoveChance();
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    private static void notifyChanged() {
        if (listener != null) {
            listener.onSettingsChanged();
        }
    }
}
