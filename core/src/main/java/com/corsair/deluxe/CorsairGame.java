package com.corsair.deluxe;

import com.badlogic.gdx.Game;
import com.corsair.deluxe.core.GameModule;
import com.corsair.deluxe.presentation.WorldScreen;

/**
 * Main entry point for the game. The game is structured around two layers:
 * 1. world exploration / sailing / encounter initiation
 * 2. tactical battle screen
 */
public class CorsairGame extends Game {
    @Override
    public void create() {
        GameModule.getInstance();
        setScreen(new WorldScreen(this));
    }
}

