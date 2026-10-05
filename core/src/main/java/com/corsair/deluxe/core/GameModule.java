package com.corsair.deluxe.core;

public final class GameModule {
    private static final GameModule INSTANCE = new GameModule();

    private final WorldState worldState = new WorldState();

    private GameModule() {
    }

    public static GameModule getInstance() {
        return INSTANCE;
    }

    public WorldState getWorldState() {
        return worldState;
    }
}


