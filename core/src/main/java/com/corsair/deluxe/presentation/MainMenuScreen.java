package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;

public class MainMenuScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final SpriteBatch batch;
    private final BitmapFont font;

    public MainMenuScreen(CorsairGame game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
        this.font.getData().setScale(1.8f);
        this.font.setColor(Color.WHITE);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1)) {
            game.setScreen(new WorldScreen(game));
            return;
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_2)
            || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }

        ScreenUtils.clear(0.04f, 0.11f, 0.18f, 1f);

        float centerX = Gdx.graphics.getWidth() / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;

        batch.begin();
        font.draw(batch, "C O R S A I R   D E L U X E", centerX - 250f, centerY + 140f);
        font.draw(batch, "1. Zacznij gre", centerX - 140f, centerY + 20f);
        font.draw(batch, "2. Wyjdz z gry", centerX - 140f, centerY - 40f);
        font.draw(batch, "Wybierz opcje klawiszami 1 / 2", centerX - 220f, centerY - 140f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
