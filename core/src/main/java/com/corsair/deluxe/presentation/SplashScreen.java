package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;

public class SplashScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final SpriteBatch batch;
    private final BitmapFont titleFont;
    private final Texture logo;
    private float elapsed;

    public SplashScreen(CorsairGame game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.titleFont = new BitmapFont();
        this.titleFont.getData().setScale(2.2f);
        this.titleFont.setColor(Color.GOLD);
        this.logo = new Texture("libgdx.png");
    }

    @Override
    public void render(float delta) {
        elapsed += delta;

        ScreenUtils.clear(0.03f, 0.08f, 0.16f, 1f);

        batch.begin();
        String title = "Corsair Deluxe";
        float titleWidth = title.length() * 22f;
        float centerX = Gdx.graphics.getWidth() / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;
        titleFont.draw(batch, title, centerX - titleWidth / 2f, centerY + 180f);

        float logoX = centerX - logo.getWidth() / 2f;
        float logoY = centerY - logo.getHeight() / 2f;
        batch.draw(logo, logoX, logoY);
        batch.end();

        if (elapsed >= 3f) {
            game.setScreen(new MainMenuScreen(game));
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        titleFont.dispose();
        logo.dispose();
    }
}
