package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;
import com.corsair.deluxe.core.GameConfig;
import com.corsair.deluxe.domain.HexCoordinate;
import com.corsair.deluxe.domain.HexDirection;
import com.corsair.deluxe.domain.HexMap;
import com.corsair.deluxe.domain.HexMath;
import com.corsair.deluxe.domain.HexPathfinder;
import com.corsair.deluxe.domain.HexTile;
import com.corsair.deluxe.domain.Ship;

import java.util.ArrayList;
import java.util.List;

public class WorldScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final OrthographicCamera camera;
    private final CameraManager cameraManager;
    private final ShapeRenderer shapeRenderer;
    private final HexMap map;
    private final Ship playerShip;
    private HexCoordinate selectedTile;
    private List<HexCoordinate> route = new ArrayList<HexCoordinate>();
    private TouchInputHandler touchInputHandler;

    public WorldScreen(CorsairGame game) {
        this.game = game;
        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        this.camera.position.set(GameConfig.VIEWPORT_WIDTH / 2f, GameConfig.VIEWPORT_HEIGHT / 2f, 0f);
        this.cameraManager = new CameraManager(camera);
        this.shapeRenderer = new ShapeRenderer();
        this.map = new HexMap(6);
        this.playerShip = new Ship("Sea Wren");
        this.playerShip.setPosition(new HexCoordinate(0, 0));
        this.selectedTile = playerShip.getPosition();
        this.touchInputHandler = new TouchInputHandler(this, camera);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(touchInputHandler);
    }

    @Override
    public void render(float delta) {
        handleInput(delta);
        cameraManager.update();

        ScreenUtils.clear(0.05f, 0.19f, 0.33f, 1f);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        for (HexTile tile : map.getTiles()) {
            Vector2 pixel = HexMath.hexToPixel(tile.getCoordinate(), 0.72f);
            float centerX = 6f + pixel.x;
            float centerY = 4.2f + pixel.y;

            if (selectedTile != null && selectedTile.equals(tile.getCoordinate())) {
                shapeRenderer.setColor(1f, 0.98f, 0.7f, 0.8f);
            } else {
                Color color = colorFor(tile.getTerrain());
                shapeRenderer.setColor(color.r, color.g, color.b, 0.6f);
            }

            drawHexOutline(centerX, centerY, 0.70f);
        }

        if (!route.isEmpty()) {
            shapeRenderer.setColor(1f, 0.9f, 0.2f, 0.8f);
            for (int i = 0; i < route.size() - 1; i++) {
                Vector2 from = HexMath.hexToPixel(route.get(i), 0.72f);
                Vector2 to = HexMath.hexToPixel(route.get(i + 1), 0.72f);
                shapeRenderer.rectLine(6f + from.x, 4.2f + from.y, 6f + to.x, 4.2f + to.y, 0.1f);
            }
        }

        Vector2 shipPixel = HexMath.hexToPixel(playerShip.getPosition(), 0.72f);
        shapeRenderer.setColor(1f, 0.82f, 0.38f, 1f);
        shapeRenderer.circle(6f + shipPixel.x, 4.2f + shipPixel.y, 0.3f);

        // Draw selected tile coordinates as simple lines
        if (selectedTile != null) {
            Vector2 selPixel = HexMath.hexToPixel(selectedTile, 0.72f);
            float selX = 6f + selPixel.x;
            float selY = 4.2f + selPixel.y;
            String coords = "Q:" + selectedTile.getQ() + " R:" + selectedTile.getR();
            
            shapeRenderer.setColor(1f, 1f, 0f, 0.8f);
            float textY = selY + 0.5f;
            drawSimpleText(selX, textY, coords);
        }

        shapeRenderer.end();
    }

    private void drawSimpleText(float x, float y, String text) {
        float charWidth = 0.08f;
        float charHeight = 0.15f;
        float currentX = x - (text.length() * charWidth / 2f);
        
        for (char c : text.toCharArray()) {
            if (c == ':') {
                shapeRenderer.line(currentX, y + 0.05f, currentX, y - 0.05f);
            } else if (c == ' ') {
                // Space
            } else if (Character.isDigit(c)) {
                drawDigit(currentX, y, c - '0');
            } else if (c == '-') {
                shapeRenderer.line(currentX, y, currentX + charWidth, y);
            }
            currentX += charWidth;
        }
    }

    private void drawDigit(float x, float y, int digit) {
        float w = 0.04f;
        float h = 0.06f;
        
        switch (digit) {
            case 0:
                shapeRenderer.line(x, y + h, x, y - h);
                shapeRenderer.line(x, y + h, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x + w, y - h);
                shapeRenderer.line(x + w, y - h, x, y - h);
                break;
            case 1:
                shapeRenderer.line(x + w, y + h, x + w, y - h);
                break;
            case 2:
                shapeRenderer.line(x, y + h, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x + w, y);
                shapeRenderer.line(x + w, y, x, y);
                shapeRenderer.line(x, y, x, y - h);
                shapeRenderer.line(x, y - h, x + w, y - h);
                break;
            case 3:
                shapeRenderer.line(x, y + h, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x + w, y - h);
                shapeRenderer.line(x + w, y, x, y);
                shapeRenderer.line(x, y - h, x + w, y - h);
                break;
            case 4:
                shapeRenderer.line(x, y + h, x, y);
                shapeRenderer.line(x, y, x + w, y);
                shapeRenderer.line(x + w, y + h, x + w, y - h);
                break;
            case 5:
                shapeRenderer.line(x + w, y + h, x, y + h);
                shapeRenderer.line(x, y + h, x, y);
                shapeRenderer.line(x, y, x + w, y);
                shapeRenderer.line(x + w, y, x + w, y - h);
                shapeRenderer.line(x + w, y - h, x, y - h);
                break;
            case 6:
                shapeRenderer.line(x + w, y + h, x, y + h);
                shapeRenderer.line(x, y + h, x, y - h);
                shapeRenderer.line(x, y - h, x + w, y - h);
                shapeRenderer.line(x + w, y - h, x + w, y);
                shapeRenderer.line(x + w, y, x, y);
                break;
            case 7:
                shapeRenderer.line(x, y + h, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x, y - h);
                break;
            case 8:
                shapeRenderer.line(x, y + h, x, y - h);
                shapeRenderer.line(x, y + h, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x + w, y - h);
                shapeRenderer.line(x + w, y - h, x, y - h);
                shapeRenderer.line(x, y, x + w, y);
                break;
            case 9:
                shapeRenderer.line(x + w, y - h, x, y - h);
                shapeRenderer.line(x, y - h, x, y);
                shapeRenderer.line(x, y, x + w, y);
                shapeRenderer.line(x + w, y, x + w, y + h);
                shapeRenderer.line(x + w, y + h, x, y + h);
                break;
        }
    }

    private void drawHexOutline(float centerX, float centerY, float radius) {
        float[] vertices = buildHexVertices(centerX, centerY, radius);
        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            shapeRenderer.line(vertices[i * 2], vertices[i * 2 + 1],
                    vertices[next * 2], vertices[next * 2 + 1]);
        }
    }

    private float[] buildHexVertices(float centerX, float centerY, float radius) {
        float[] vertices = new float[12];
        for (int i = 0; i < 6; i++) {
            double angleDeg = 60 * i - 30;
            double angle = Math.toRadians(angleDeg);
            vertices[i * 2] = centerX + (float) (radius * Math.cos(angle));
            vertices[i * 2 + 1] = centerY + (float) (radius * Math.sin(angle));
        }
        return vertices;
    }

    private Color colorFor(HexTile.Terrain terrain) {
        switch (terrain) {
            case ISLAND:
                return new Color(0.12f, 0.5f, 0.23f, 1f);
            case SHALLOW:
                return new Color(0.23f, 0.74f, 0.84f, 1f);
            default:
                return new Color(0.09f, 0.29f, 0.53f, 1f);
        }
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

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_1)) {
            selectNeighbor(HexDirection.SE);
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_2)) {
            selectNeighbor(HexDirection.SW);
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_3)) {
            selectNeighbor(HexDirection.W);
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_4)) {
            selectNeighbor(HexDirection.NW);
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_5)) {
            selectNeighbor(HexDirection.NE);
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_6)) {
            selectNeighbor(HexDirection.E);
        }

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            if (selectedTile != null) {
                route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile);
                if (route.size() > 1) {
                    playerShip.setPosition(route.get(route.size() - 1));
                    selectedTile = playerShip.getPosition();
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.B)) {
            game.setScreen(new BattleScreen(game));
        }
    }

    private void selectNeighbor(HexDirection direction) {
        selectedTile = playerShip.getPosition().neighbor(direction);
        route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile);
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
        touchInputHandler.dispose();
    }

    public void selectTile(HexCoordinate coordinate) {
        this.selectedTile = coordinate;
        this.route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile);
    }

    public void moveTo(HexCoordinate coordinate) {
        selectTile(coordinate);
        if (route.size() > 1) {
            playerShip.setPosition(route.get(route.size() - 1));
            selectedTile = playerShip.getPosition();
        }
    }

    public HexCoordinate getSelectedTile() {
        return selectedTile;
    }

    public Ship getPlayerShip() {
        return playerShip;
    }

    public CorsairGame getGame() {
        return game;
    }
}

