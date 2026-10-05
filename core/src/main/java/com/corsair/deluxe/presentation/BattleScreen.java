package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;

public class BattleScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final SpriteBatch batch;
    private final BitmapFont font;

    public BattleScreen(CorsairGame game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
        this.font.getData().setScale(1.6f);
        this.font.setColor(Color.WHITE);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            game.setScreen(new WorldScreen(game));
            return;
        }

        ScreenUtils.clear(0.12f, 0.06f, 0.08f, 1f);

        float centerX = Gdx.graphics.getWidth() / 2f;
        float centerY = Gdx.graphics.getHeight() / 2f;

        batch.begin();
        font.draw(batch, "BITWA - WARSTWA LOGICZNA", centerX - 240f, centerY + 120f);
        font.draw(batch, "Tu bedzie statyczna plansza rozstrzygajaca wynik bitwy.", centerX - 330f, centerY + 40f);
        font.draw(batch, "ESC - powrot na mape", centerX - 150f, centerY - 40f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
