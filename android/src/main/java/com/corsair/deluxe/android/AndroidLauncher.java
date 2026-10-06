package com.corsair.deluxe.android;

import android.content.Intent;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.corsair.deluxe.CorsairGame;
import com.corsair.deluxe.MenuLauncher;

/** Runs the game in a libGDX context and hands control back to the native main menu when asked. */
public class AndroidLauncher extends AndroidApplication {
    private CorsairGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidSettings.install(this);

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;

        game = new CorsairGame();
        game.setMenuLauncher(new MenuLauncher() {
            @Override
            public void openMainMenu() {
                Intent intent = new Intent(AndroidLauncher.this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });
        initialize(game, configuration);
    }

    @Override
    public void onBackPressed() {
        if (game != null) {
            game.openMainMenu();
        } else {
            super.onBackPressed();
        }
    }
}
