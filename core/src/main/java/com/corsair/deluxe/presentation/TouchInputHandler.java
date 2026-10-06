package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.corsair.deluxe.core.GameConfig;
import com.corsair.deluxe.domain.HexCoordinate;
import com.corsair.deluxe.domain.HexDirection;
import com.corsair.deluxe.domain.HexMath;

public class TouchInputHandler extends InputAdapter {
    private final WorldScreen worldScreen;
    private final OrthographicCamera camera;
    private float lastTouchX = -1;
    private float lastTouchY = -1;
    private float startTouchX;
    private float startTouchY;
    private boolean dragging;
    private static final float TOUCH_THRESHOLD = 50f;

    public TouchInputHandler(WorldScreen worldScreen, OrthographicCamera camera) {
        this.worldScreen = worldScreen;
        this.camera = camera;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        startTouchX = screenX;
        startTouchY = screenY;
        lastTouchX = screenX;
        lastTouchY = screenY;
        dragging = false;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (lastTouchX >= 0 && lastTouchY >= 0 && !dragging) {
            handleTap(screenX, screenY);
        }
        lastTouchX = -1;
        lastTouchY = -1;
        dragging = false;
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (lastTouchX < 0) {
            return true;
        }

        if (!dragging) {
            float fromStartX = screenX - startTouchX;
            float fromStartY = screenY - startTouchY;
            if (Math.sqrt(fromStartX * fromStartX + fromStartY * fromStartY) < TOUCH_THRESHOLD) {
                return true;
            }
            dragging = true;
        }

        // Convert finger movement in pixels to world units so the map follows the finger 1:1
        float worldPerPixelX = camera.viewportWidth * camera.zoom / Gdx.graphics.getWidth();
        float worldPerPixelY = camera.viewportHeight * camera.zoom / Gdx.graphics.getHeight();
        float panX = (screenX - lastTouchX) * worldPerPixelX;
        float panY = (lastTouchY - screenY) * worldPerPixelY;

        new CameraManager(camera).pan(panX, panY);

        lastTouchX = screenX;
        lastTouchY = screenY;
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
            worldScreen.getGame().setScreen(new MainMenuScreen(worldScreen.getGame()));
            return true;
        }

        if (keycode == Input.Keys.SPACE) {
            if (worldScreen.getSelectedTile() != null) {
                worldScreen.moveTo(worldScreen.getSelectedTile());
            }
            return true;
        }

        if (keycode == Input.Keys.B) {
            worldScreen.getGame().setScreen(new BattleScreen(worldScreen.getGame()));
            return true;
        }

        switch (keycode) {
            case Input.Keys.NUMPAD_1:
                selectNeighbor(HexDirection.SE);
                return true;
            case Input.Keys.NUMPAD_2:
                selectNeighbor(HexDirection.SW);
                return true;
            case Input.Keys.NUMPAD_3:
                selectNeighbor(HexDirection.W);
                return true;
            case Input.Keys.NUMPAD_4:
                selectNeighbor(HexDirection.NW);
                return true;
            case Input.Keys.NUMPAD_5:
                selectNeighbor(HexDirection.NE);
                return true;
            case Input.Keys.NUMPAD_6:
                selectNeighbor(HexDirection.E);
                return true;
        }

        return false;
    }

    private void handleTap(int screenX, int screenY) {
        if (worldScreen.handleHudTap(screenX, screenY)) {
            return;
        }

        if (worldScreen.isTraveling()) {
            worldScreen.stopTravel();
            return;
        }

        Vector3 screenCoords = new Vector3(screenX, screenY, 0);
        Vector3 worldCoords = camera.unproject(screenCoords);

        float worldX = worldCoords.x - 6f;
        float worldY = worldCoords.y - 4.2f;

        HexCoordinate tappedHex = HexMath.pixelToHex(worldX, worldY, GameConfig.HEX_SPACING);
        if (tappedHex != null) {
            HexCoordinate currentSelected = worldScreen.getSelectedTile();
            if (currentSelected != null && currentSelected.equals(tappedHex)
                    && !tappedHex.equals(worldScreen.getPlayerShip().getPosition())) {
                worldScreen.moveTo(tappedHex);
            } else {
                worldScreen.selectTile(tappedHex);
            }
        }
    }

    private void selectNeighbor(HexDirection direction) {
        HexCoordinate currentSelected = worldScreen.getSelectedTile();
        if (currentSelected != null) {
            HexCoordinate neighbor = currentSelected.neighbor(direction);
            worldScreen.selectTile(neighbor);
        }
    }

    public void dispose() {
    }
}
