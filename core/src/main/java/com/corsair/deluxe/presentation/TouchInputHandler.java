package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.corsair.deluxe.domain.HexCoordinate;
import com.corsair.deluxe.domain.HexDirection;
import com.corsair.deluxe.domain.HexMath;

public class TouchInputHandler extends InputAdapter {
    private final WorldScreen worldScreen;
    private final OrthographicCamera camera;
    private float lastTouchX = -1;
    private float lastTouchY = -1;
    private static final float TOUCH_THRESHOLD = 50f;

    public TouchInputHandler(WorldScreen worldScreen, OrthographicCamera camera) {
        this.worldScreen = worldScreen;
        this.camera = camera;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        lastTouchX = screenX;
        lastTouchY = screenY;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (lastTouchX >= 0 && lastTouchY >= 0) {
            float deltaX = screenX - lastTouchX;
            float deltaY = screenY - lastTouchY;
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

            if (distance < TOUCH_THRESHOLD) {
                handleTap(screenX, screenY);
            }
        }
        lastTouchX = -1;
        lastTouchY = -1;
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        Vector3 screenCoords = new Vector3(screenX, screenY, 0);
        Vector3 worldCoords = camera.unproject(screenCoords);

        float panX = screenX - lastTouchX;
        float panY = lastTouchY - screenY;

        CameraManager cameraManager = new CameraManager(camera);
        cameraManager.pan(panX * 0.02f, panY * 0.02f);

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
        Vector3 screenCoords = new Vector3(screenX, screenY, 0);
        Vector3 worldCoords = camera.unproject(screenCoords);

        float worldX = worldCoords.x - 6f;
        float worldY = worldCoords.y - 4.2f;

        HexCoordinate tappedHex = HexMath.pixelToHex(worldX, worldY, 0.72f);
        if (tappedHex != null) {
            worldScreen.selectTile(tappedHex);
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
