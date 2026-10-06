package com.corsair.deluxe;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.corsair.deluxe.core.GameModule;
import com.corsair.deluxe.presentation.WorldScreen;

/**
 * Main entry point for the game.
 */
public class CorsairGame extends Game {
    private MenuLauncher menuLauncher;

    public void setMenuLauncher(MenuLauncher menuLauncher) {
        this.menuLauncher = menuLauncher;
    }

    @Override
    public void create() {
        GameModule.getInstance();
        setScreen(new WorldScreen(this));
    }

    /** Leaves the libGDX session and shows the native main menu. */
    public void openMainMenu() {
        if (menuLauncher != null) {
            menuLauncher.openMainMenu();
        } else {
            Gdx.app.exit();
        }
    }
}

