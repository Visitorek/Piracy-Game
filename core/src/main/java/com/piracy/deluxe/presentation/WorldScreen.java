package com.piracy.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.piracy.deluxe.PiracyGame;
import com.piracy.deluxe.core.GameConfig;

public class WorldScreen extends ScreenAdapter {
    private final PiracyGame game;
    private final OrthographicCamera camera;
    private final CameraManager cameraManager;
    private final ShapeRenderer shapeRenderer;

    public WorldScreen(PiracyGame game) {
        this.game = game;
        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        this.camera.position.set(GameConfig.VIEWPORT_WIDTH / 2f, GameConfig.VIEWPORT_HEIGHT / 2f, 0f);
        this.cameraManager = new CameraManager(camera);
        this.shapeRenderer = new ShapeRenderer();
    }

    @Override
    public void render(float delta) {
        handleInput(delta);
        cameraManager.update();

        ScreenUtils.clear(0.05f, 0.19f, 0.33f, 1f);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int row = 0; row < 12; row++) {
            for (int col = 0; col < 18; col++) {
                float x = col * 0.9f;
                float y = row * 0.9f;
                shapeRenderer.setColor(new Color(0.18f, 0.42f, 0.68f, 1f));
                shapeRenderer.rect(x, y, 0.75f, 0.75f);
            }
        }

        shapeRenderer.setColor(new Color(0.9f, 0.82f, 0.38f, 1f));
        shapeRenderer.circle(5f, 5f, 0.65f);

        shapeRenderer.setColor(new Color(0.7f, 0.7f, 0.74f, 1f));
        shapeRenderer.rect(8f, 2.5f, 1.2f, 0.7f);

        shapeRenderer.end();
    }

    private void handleInput(float delta) {
        float cameraSpeed = 4f * delta;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
            cameraManager.pan(cameraSpeed, 0f);
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
            cameraManager.pan(-cameraSpeed, 0f);
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP)) {
            cameraManager.pan(0f, -cameraSpeed);
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
            cameraManager.pan(0f, cameraSpeed);
        }

        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.Q)) {
            cameraManager.zoom(0.01f);
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.E)) {
            cameraManager.zoom(-0.01f);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = GameConfig.VIEWPORT_WIDTH;
        camera.viewportHeight = GameConfig.VIEWPORT_HEIGHT;
        camera.update();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
