package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.corsair.deluxe.CorsairGame;

public class MainMenuScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final Stage stage;
    private final Skin skin;
    private final SpriteBatch backgroundBatch;
    private final Texture background;
    private final Texture buttonTexture;
    private final Texture buttonPressedTexture;
    private BitmapFont font;

    public MainMenuScreen(CorsairGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin();
        this.backgroundBatch = new SpriteBatch();
        this.background = new Texture("menu_background.jpg");

        this.font = new BitmapFont();
        this.font.getData().setScale(4.8f);

        buttonTexture = createRoundedButtonTexture(1280, 280, new Color(0.07f, 0.12f, 0.24f, 0.92f));
        buttonPressedTexture = createRoundedButtonTexture(1280, 280, new Color(0.13f, 0.20f, 0.36f, 0.95f));

        TextButtonStyle buttonStyle = new TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.downFontColor = new Color(0.91f, 0.93f, 1f, 1f);
        buttonStyle.up = new TextureRegionDrawable(buttonTexture);
        buttonStyle.down = new TextureRegionDrawable(buttonPressedTexture);
        skin.add("default", buttonStyle);

        TextButton startButton = new TextButton("Zacznij gre", skin);
        TextButton exitButton = new TextButton("Wyjdz z gry", skin);

        startButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new WorldScreen(game));
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();
        table.add(startButton).width(980f).height(220f).padBottom(90f).row();
        table.add(exitButton).width(980f).height(220f);

        stage.addActor(table);
    }

    private Texture createRoundedButtonTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        int radius = height / 2;
        pixmap.setColor(color);
        pixmap.fillRectangle(radius, 0, width - 2 * radius, height);
        pixmap.fillCircle(radius, radius, radius);
        pixmap.fillCircle(width - radius, radius, radius);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float imageW = background.getWidth();
        float imageH = background.getHeight();

        float scale = Math.max(screenW / imageW, screenH / imageH);
        float drawW = imageW * scale;
        float drawH = imageH * scale;
        float drawX = (screenW - drawW) / 2f;
        float drawY = (screenH - drawH) / 2f;

        backgroundBatch.begin();
        backgroundBatch.draw(background, drawX, drawY, drawW, drawH);
        backgroundBatch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        backgroundBatch.dispose();
        background.dispose();
        buttonTexture.dispose();
        buttonPressedTexture.dispose();
        if (font != null) {
            font.dispose();
            font = null;
        }
    }
}
