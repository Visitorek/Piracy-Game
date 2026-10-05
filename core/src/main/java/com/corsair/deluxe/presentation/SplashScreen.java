package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;

public class SplashScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final SpriteBatch batch;
    private final Texture splashImage;
    private float elapsed;

    public SplashScreen(CorsairGame game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.splashImage = new Texture("splash.jpg");
    }

    @Override
    public void render(float delta) {
        elapsed += delta;

        ScreenUtils.clear(58f / 255f, 60f / 255f, 111f / 255f, 1f);

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float imageW = splashImage.getWidth();
        float imageH = splashImage.getHeight();

        float scale = Math.min(screenW / imageW, screenH / imageH);
        float drawW = imageW * scale;
        float drawH = imageH * scale;
        float drawX = (screenW - drawW) / 2f;
        float drawY = (screenH - drawH) / 2f;

        batch.begin();
        batch.draw(splashImage, drawX, drawY, drawW, drawH);
        batch.end();

        if (elapsed >= 3f) {
            game.setScreen(new MainMenuScreen(game));
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        splashImage.dispose();
    }
}


