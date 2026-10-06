package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;
import com.corsair.deluxe.core.GameConfig;
import com.corsair.deluxe.core.GameSettings;
import com.corsair.deluxe.domain.HexCoordinate;
import com.corsair.deluxe.domain.HexDirection;
import com.corsair.deluxe.domain.HexMap;
import com.corsair.deluxe.domain.HexMath;
import com.corsair.deluxe.domain.HexPathfinder;
import com.corsair.deluxe.domain.HexTile;
import com.corsair.deluxe.domain.Ship;
import com.corsair.deluxe.domain.battle.BattleOutcome;
import com.corsair.deluxe.domain.battle.BattleResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorldScreen extends ScreenAdapter {
    private final CorsairGame game;
    private final OrthographicCamera camera;
    private final OrthographicCamera hudCamera;
    private final CameraManager cameraManager;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch spriteBatch;
    private final BitmapFont font;
    private final GlyphLayout glyphLayout;
    private final HexMap map;
    private final Ship playerShip;
    private Ship enemyShip;
    private HexCoordinate lastPosition;
    private HexCoordinate selectedTile;
    private List<HexCoordinate> route = new ArrayList<HexCoordinate>();
    private TouchInputHandler touchInputHandler;
    private static final float SECONDS_PER_HEX = 1f;
    private static final int ENEMY_MIN_DISTANCE = 4;
    private List<HexCoordinate> travelPath = new ArrayList<HexCoordinate>();
    private int travelIndex;
    private float travelProgress;
    private boolean traveling;
    private boolean stopRequested;
    private float lineThickness;
    private static final int LOOK_AROUND_TURNS = 3;
    private static final int LOOK_AROUND_BONUS_RANGE = 1;
    private boolean lookingAround;
    private float lookElapsed;
    private final Set<HexCoordinate> discovered = new HashSet<HexCoordinate>();

    private static final float TILE_RADIUS = 0.70f;
    private static final float TILE_ALPHA_VISIBLE = 0.85f;
    private static final float TILE_ALPHA_FOGGED = 0.30f;
    private static final float TILE_FILL_ALPHA_VISIBLE = 0.80f;
    private static final float TILE_FILL_ALPHA_FOGGED = 0.22f;
    /** Terrain borders are drawn thicker than the plain hex grid so the transitions read clearly. */
    private static final float TERRAIN_EDGE_SCALE = 2.0f;
    private static final Color BACKGROUND_COLOR = new Color(0.45f, 0.45f, 0.45f, 1f);
    private static final Color CATEGORY_ISLAND = new Color(0.45f, 0.52f, 0.30f, 1f);
    private static final Color CATEGORY_SHALLOW = new Color(0.32f, 0.70f, 0.74f, 1f);
    private static final Color CATEGORY_WATER = new Color(0.13f, 0.27f, 0.46f, 1f);
    /** Land meets the shallows: a strip of yellow sand. */
    private static final Color SAND_COLOR = new Color(0.94f, 0.85f, 0.46f, 1f);
    /**
     * Land drops straight into the deep: a rocky cliff. Land is always ringed by shallows, so this
     * only shows up if generation ever lets the two touch.
     */
    private static final Color CLIFF_COLOR = new Color(0.38f, 0.31f, 0.25f, 1f);
    /** Shallows meet the deep: a line of pale surf. */
    private static final Color REEF_COLOR = new Color(0.66f, 0.84f, 0.88f, 1f);
    /** Which of the six hex edges faces each neighbour, derived from the hex layout math. */
    private static final int[] EDGE_INDEX = new int[HexDirection.values().length];

    static {
        for (HexDirection direction : HexDirection.values()) {
            Vector2 offset = HexMath.hexToPixel(direction.toOffset(), 1f);
            double angle = Math.toDegrees(Math.atan2(offset.y, offset.x));
            EDGE_INDEX[direction.ordinal()] = (int) (((Math.round(angle / 60.0) % 6) + 6) % 6);
        }
    }

    public WorldScreen(CorsairGame game) {
        this.game = game;
        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        this.camera.position.set(GameConfig.VIEWPORT_WIDTH / 2f, GameConfig.VIEWPORT_HEIGHT / 2f, 0f);
        this.hudCamera = new OrthographicCamera();
        this.hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.cameraManager = new CameraManager(camera);
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
        this.font = new BitmapFont();
        this.font.getData().setScale(2.0f);
        this.font.setColor(Color.WHITE);
        this.glyphLayout = new GlyphLayout();
        this.map = new HexMap(6);
        this.playerShip = new Ship("Sea Wren");
        this.playerShip.setPosition(new HexCoordinate(0, 0));
        this.selectedTile = playerShip.getPosition();
        revealAroundShip();
        HexCoordinate enemyStart = map.findRandomWaterTile(playerShip.getPosition(), ENEMY_MIN_DISTANCE);
        if (enemyStart != null) {
            this.enemyShip = new Ship("Pirate");
            this.enemyShip.setCrew(15);
            this.enemyShip.setPosition(enemyStart);
        }
        this.touchInputHandler = new TouchInputHandler(this, camera);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(touchInputHandler);
    }

    @Override
    public void render(float delta) {
        handleInput(delta);
        updateTravel(delta);
        updateLookAround(delta);
        cameraManager.update();

        ScreenUtils.clear(BACKGROUND_COLOR);

        shapeRenderer.setProjectionMatrix(camera.combined);
        lineThickness = GameConfig.HEX_LINE_WIDTH_PX * camera.viewportHeight * camera.zoom / Gdx.graphics.getHeight();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        boolean debug = GameSettings.isDebugMode();
        for (HexTile tile : map.getTiles()) {
            if (!discovered.contains(tile.getCoordinate())) {
                continue;
            }
            Vector2 pixel = HexMath.hexToPixel(tile.getCoordinate(), GameConfig.HEX_SPACING);
            float centerX = 6f + pixel.x;
            float centerY = 4.2f + pixel.y;

            if (selectedTile != null && selectedTile.equals(tile.getCoordinate())) {
                shapeRenderer.setColor(1f, 0.98f, 0.7f, 0.8f);
                drawHexOutline(centerX, centerY, TILE_RADIUS);
            } else if (debug) {
                drawDebugTile(tile, centerX, centerY);
            } else {
                drawPaintedTile(tile, centerX, centerY);
            }
        }

        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        if (!route.isEmpty()) {
            shapeRenderer.setColor(1f, 0.9f, 0.2f, 0.8f);
            for (int i = 0; i < route.size() - 1; i++) {
                Vector2 from = HexMath.hexToPixel(route.get(i), GameConfig.HEX_SPACING);
                Vector2 to = HexMath.hexToPixel(route.get(i + 1), GameConfig.HEX_SPACING);
                shapeRenderer.rectLine(6f + from.x, 4.2f + from.y, 6f + to.x, 4.2f + to.y, 0.1f);
            }
        }

        if (enemyShip != null && discovered.contains(enemyShip.getPosition())) {
            Vector2 enemyPixel = HexMath.hexToPixel(enemyShip.getPosition(), GameConfig.HEX_SPACING);
            shapeRenderer.setColor(0.85f, 0.1f, 0.1f, 1f);
            shapeRenderer.circle(6f + enemyPixel.x, 4.2f + enemyPixel.y, 0.3f);
        }

        Vector2 shipPixel = HexMath.hexToPixel(playerShip.getPosition(), GameConfig.HEX_SPACING);
        if (traveling) {
            Vector2 nextPixel = HexMath.hexToPixel(travelPath.get(travelIndex + 1), GameConfig.HEX_SPACING);
            shipPixel = new Vector2(shipPixel).lerp(nextPixel, travelProgress);
        }
        shapeRenderer.setColor(1f, 0.82f, 0.38f, 1f);
        shapeRenderer.circle(6f + shipPixel.x, 4.2f + shipPixel.y, 0.3f);

        shapeRenderer.end();

        // Draw coordinates in screen-space (fixed pixel size, unaffected by zoom)
        if (debug) {
            drawTileCoordinates();
        }

        drawLookAroundButton();
        if (debug) {
            drawDebugBattleButton();
        }
        drawMenuButton();
    }

    /** Debug helper: prints the axial coordinate of every discovered tile. */
    private void drawTileCoordinates() {
        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin();
        Vector3 screenPos = new Vector3();
        for (HexTile tile : map.getTiles()) {
            if (!discovered.contains(tile.getCoordinate())) {
                continue;
            }
            font.setColor(1f, 1f, 1f, isVisible(tile.getCoordinate()) ? 1f : 0.35f);
            Vector2 pixel = HexMath.hexToPixel(tile.getCoordinate(), GameConfig.HEX_SPACING);
            float worldX = 6f + pixel.x;
            float worldY = 4.2f + pixel.y;

            screenPos.set(worldX, worldY, 0f);
            camera.project(screenPos, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

            String coords = tile.getCoordinate().getQ() + "," + tile.getCoordinate().getR();
            glyphLayout.setText(font, coords);
            font.draw(spriteBatch, coords, screenPos.x - glyphLayout.width / 2f, screenPos.y + glyphLayout.height / 2f);
        }
        spriteBatch.end();
    }

    // TEMPORARY: debug button that jumps straight into a battle
    private float debugButtonCenterY() {
        return buttonCenterY() - buttonRadius() * 2.5f;
    }

    private void drawDebugBattleButton() {
        float cx = buttonCenterX();
        float cy = debugButtonCenterY();
        float radius = buttonRadius() * 0.7f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.65f, 0.1f, 0.1f, 0.92f);
        shapeRenderer.circle(cx, cy, radius, 32);
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin();
        float oldScale = font.getData().scaleX;
        font.getData().setScale(oldScale * 0.6f);
        font.setColor(Color.WHITE);
        glyphLayout.setText(font, "DBG");
        font.draw(spriteBatch, glyphLayout, cx - glyphLayout.width / 2f, cy + glyphLayout.height / 2f);
        font.getData().setScale(oldScale);
        spriteBatch.end();
    }

    private float buttonRadius() {
        return 36f * Gdx.graphics.getDensity();
    }

    private float menuButtonRadius() {
        return buttonRadius() * 0.9f;
    }

    private float menuButtonCenterY() {
        return buttonCenterY() + buttonRadius() * 2.5f;
    }

    /** Leaves the world map and shows the native main menu (same as the system back button). */
    private void drawMenuButton() {
        float cx = buttonCenterX();
        float cy = menuButtonCenterY();
        float radius = menuButtonRadius();
        float u = radius;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.36f, 0.09f, 0.09f, 0.92f);
        shapeRenderer.circle(cx, cy, radius, 48);

        // A door left through the doorway: return to the main menu
        shapeRenderer.setColor(0.97f, 0.85f, 0.55f, 1f);
        shapeRenderer.rect(cx + 0.30f * u, cy - 0.44f * u, 0.14f * u, 0.88f * u);
        shapeRenderer.rect(cx + 0.08f * u, cy + 0.40f * u, 0.36f * u, 0.14f * u);
        shapeRenderer.rect(cx + 0.08f * u, cy - 0.54f * u, 0.36f * u, 0.14f * u);
        shapeRenderer.rect(cx - 0.58f * u, cy - 0.07f * u, 0.56f * u, 0.14f * u);
        shapeRenderer.triangle(cx - 0.72f * u, cy,
                cx - 0.26f * u, cy + 0.26f * u,
                cx - 0.26f * u, cy - 0.26f * u);
        shapeRenderer.end();
    }

    private float buttonCenterX() {
        return Gdx.graphics.getWidth() - buttonRadius() - 24f * Gdx.graphics.getDensity();
    }

    private float buttonCenterY() {
        return Gdx.graphics.getHeight() / 2f;
    }

    private void drawLookAroundButton() {
        float cx = buttonCenterX();
        float cy = buttonCenterY();
        float radius = buttonRadius();
        float alpha = traveling ? 0.45f : 0.92f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(0.07f, 0.12f, 0.24f, alpha);
        shapeRenderer.circle(cx, cy, radius, 48);

        if (lookingAround) {
            float progress = Math.min(1f, lookElapsed / (LOOK_AROUND_TURNS * SECONDS_PER_HEX));
            shapeRenderer.setColor(0.97f, 0.85f, 0.55f, 1f);
            shapeRenderer.arc(cx, cy, radius, 90f - 360f * progress, 360f * progress, 48);
            shapeRenderer.setColor(0.07f, 0.12f, 0.24f, 1f);
            shapeRenderer.circle(cx, cy, radius * 0.84f, 48);
        }

        // Pirate spyglass: tapered brown tube with brass bands, drawn along a tilted axis
        float alphaTint = alpha;
        shapeRenderer.translate(cx, cy, 0f);
        shapeRenderer.rotate(0f, 0f, 1f, 35f);
        float u = radius;

        shapeRenderer.setColor(0.45f, 0.27f, 0.13f, alphaTint);
        shapeRenderer.rect(-0.66f * u, -0.08f * u, 0.22f * u, 0.16f * u);
        shapeRenderer.rect(-0.44f * u, -0.12f * u, 0.40f * u, 0.24f * u);
        shapeRenderer.rect(-0.04f * u, -0.16f * u, 0.46f * u, 0.32f * u);
        shapeRenderer.rect(0.40f * u, -0.21f * u, 0.24f * u, 0.42f * u);

        shapeRenderer.setColor(0.88f, 0.68f, 0.26f, alphaTint);
        shapeRenderer.rect(-0.46f * u, -0.13f * u, 0.05f * u, 0.26f * u);
        shapeRenderer.rect(-0.06f * u, -0.17f * u, 0.05f * u, 0.34f * u);
        shapeRenderer.rect(0.38f * u, -0.22f * u, 0.05f * u, 0.44f * u);
        shapeRenderer.rect(0.62f * u, -0.23f * u, 0.05f * u, 0.46f * u);

        shapeRenderer.setColor(0.7f, 0.9f, 1f, alphaTint);
        shapeRenderer.rect(0.67f * u, -0.17f * u, 0.03f * u, 0.34f * u);

        shapeRenderer.identity();

        shapeRenderer.end();
    }

    /** Returns true when the tap hit a HUD button and should not reach the map. */
    public boolean handleHudTap(float screenX, float screenY) {
        float menuDx = screenX - buttonCenterX();
        float menuDy = (Gdx.graphics.getHeight() - screenY) - menuButtonCenterY();
        float menuRadius = menuButtonRadius();
        if (menuDx * menuDx + menuDy * menuDy <= menuRadius * menuRadius) {
            game.openMainMenu();
            return true;
        }

        if (GameSettings.isDebugMode()) {
            float debugDx = screenX - buttonCenterX();
            float debugDy = (Gdx.graphics.getHeight() - screenY) - debugButtonCenterY();
            float debugRadius = buttonRadius() * 0.7f;
            if (debugDx * debugDx + debugDy * debugDy <= debugRadius * debugRadius) {
                startBattle();
                return true;
            }
        }

        float dx = screenX - buttonCenterX();
        float dy = (Gdx.graphics.getHeight() - screenY) - buttonCenterY();
        if (dx * dx + dy * dy > buttonRadius() * buttonRadius()) {
            return false;
        }
        if (traveling) {
            return false;
        }
        if (!lookingAround) {
            lookingAround = true;
            lookElapsed = 0f;
        }
        return true;
    }

    private void updateLookAround(float delta) {
        if (!lookingAround) {
            return;
        }
        lookElapsed += delta;
        if (lookElapsed >= LOOK_AROUND_TURNS * SECONDS_PER_HEX) {
            lookingAround = false;
            lookElapsed = 0f;
            revealAroundShip(playerShip.getVisionRadius() + LOOK_AROUND_BONUS_RANGE);
        }
    }

    private void drawHexOutline(float centerX, float centerY, float radius) {
        float[] vertices = buildHexVertices(centerX, centerY, radius);
        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            shapeRenderer.rectLine(vertices[i * 2], vertices[i * 2 + 1],
                    vertices[next * 2], vertices[next * 2 + 1], lineThickness);
        }
    }

    /** Fills a hex with a triangle fan, in the colour the caller has already set. */
    private void drawHexFill(float centerX, float centerY, float radius) {
        float[] vertices = buildHexVertices(centerX, centerY, radius);
        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            shapeRenderer.triangle(centerX, centerY,
                    vertices[i * 2], vertices[i * 2 + 1],
                    vertices[next * 2], vertices[next * 2 + 1]);
        }
    }

    /** Paints a single edge of a hex, in the colour the caller has already set. */
    private void drawHexEdge(float centerX, float centerY, float radius, int edgeIndex) {
        float[] vertices = buildHexVertices(centerX, centerY, radius);
        int from = edgeIndex * 2;
        int to = ((edgeIndex + 1) % 6) * 2;
        shapeRenderer.rectLine(vertices[from], vertices[from + 1],
                vertices[to], vertices[to + 1], lineThickness * TERRAIN_EDGE_SCALE);
    }

    /** Debug view: flat terrain colours, exactly as the map has always been drawn. */
    private void drawDebugTile(HexTile tile, float centerX, float centerY) {
        Color color = debugColorFor(tile.getTerrain());
        float alpha = isVisible(tile.getCoordinate()) ? 0.6f : 0.25f;
        shapeRenderer.setColor(color.r, color.g, color.b, alpha);
        drawHexOutline(centerX, centerY, TILE_RADIUS);
    }

    /**
     * Normal view: the hex is filled with its terrain category colour and every edge is painted by
     * what lies on its other side - the tile's own category colour when the neighbour shares its
     * terrain, otherwise the colour of the transition between the two (sand, cliff, reef). Both
     * tiles paint a shared edge, so borders always match.
     */
    private void drawPaintedTile(HexTile tile, float centerX, float centerY) {
        boolean visible = isVisible(tile.getCoordinate());
        Color fill = categoryColorFor(tile.getTerrain());
        shapeRenderer.setColor(fill.r, fill.g, fill.b,
                visible ? TILE_FILL_ALPHA_VISIBLE : TILE_FILL_ALPHA_FOGGED);
        drawHexFill(centerX, centerY, TILE_RADIUS);

        float edgeAlpha = visible ? TILE_ALPHA_VISIBLE : TILE_ALPHA_FOGGED;
        for (HexDirection direction : HexDirection.values()) {
            HexCoordinate neighborCoordinate = tile.getCoordinate().neighbor(direction);
            HexTile neighbor = map.getTile(neighborCoordinate);
            HexTile.Terrain neighborTerrain = neighbor == null || !discovered.contains(neighborCoordinate)
                    ? tile.getTerrain()
                    : neighbor.getTerrain();
            Color color = neighborTerrain == tile.getTerrain()
                    ? categoryColorFor(tile.getTerrain())
                    : transitionColorFor(tile.getTerrain(), neighborTerrain);
            shapeRenderer.setColor(color.r, color.g, color.b, edgeAlpha);
            drawHexEdge(centerX, centerY, TILE_RADIUS, EDGE_INDEX[direction.ordinal()]);
        }
    }

    /** Colour of the border between two different terrains. */
    private Color transitionColorFor(HexTile.Terrain terrain, HexTile.Terrain neighbor) {
        if (terrain == HexTile.Terrain.ISLAND || neighbor == HexTile.Terrain.ISLAND) {
            boolean deepWater = terrain == HexTile.Terrain.WATER || neighbor == HexTile.Terrain.WATER;
            return deepWater ? CLIFF_COLOR : SAND_COLOR;
        }
        return REEF_COLOR;
    }

    private Color categoryColorFor(HexTile.Terrain terrain) {
        switch (terrain) {
            case ISLAND:
                return CATEGORY_ISLAND;
            case SHALLOW:
                return CATEGORY_SHALLOW;
            default:
                return CATEGORY_WATER;
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

    private Color debugColorFor(HexTile.Terrain terrain) {
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
                moveTo(selectedTile);
            }
        }

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.B)) {
            startBattle();
        }
    }

    private void selectNeighbor(HexDirection direction) {
        HexCoordinate target = playerShip.getPosition().neighbor(direction);
        if (isBusy() || !discovered.contains(target)) {
            return;
        }
        selectedTile = target;
        route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile);
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = GameConfig.VIEWPORT_WIDTH;
        camera.viewportHeight = GameConfig.VIEWPORT_HEIGHT;
        camera.update();
        hudCamera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        touchInputHandler.dispose();
    }

    public void startBattle() {
        Ship opponent = enemyShip != null ? enemyShip : new Ship("Pirate");
        traveling = false;
        stopRequested = false;
        travelProgress = 0f;
        lookingAround = false;
        selectedTile = playerShip.getPosition();
        route = new ArrayList<HexCoordinate>();
        game.setScreen(new BattleScreen(game, this, playerShip, opponent));
    }

    public void onBattleFinished(BattleResult result) {
        playerShip.setCrew(Math.max(1, playerShip.getCrew() - result.getPlayerCrewLost()));
        if (result.getOutcome() == BattleOutcome.VICTORY) {
            playerShip.setRum(playerShip.getRum() + 3);
            playerShip.setFoodSupplies(playerShip.getFoodSupplies() + 4);
            enemyShip = null;
        } else if (lastPosition != null) {
            // Fleeing or a draw pushes the ship back to where it came from
            playerShip.setPosition(lastPosition);
            revealAroundShip();
        }
        selectedTile = playerShip.getPosition();
        route = new ArrayList<HexCoordinate>();
    }

    private boolean isBusy() {
        return traveling || lookingAround;
    }

    private boolean isVisible(HexCoordinate coordinate) {
        return playerShip.getPosition().distanceTo(coordinate) <= playerShip.getVisionRadius();
    }

    private void revealAroundShip() {
        revealAroundShip(playerShip.getVisionRadius());
    }

    private void revealAroundShip(int range) {
        for (HexTile tile : map.getTiles()) {
            if (playerShip.getPosition().distanceTo(tile.getCoordinate()) <= range) {
                discovered.add(tile.getCoordinate());
            }
        }
    }

    public void selectTile(HexCoordinate coordinate) {
        if (isBusy() || !discovered.contains(coordinate)) {
            return;
        }
        this.selectedTile = coordinate;
        this.route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile);
    }

    public void moveTo(HexCoordinate coordinate) {
        if (isBusy() || !discovered.contains(coordinate)) {
            return;
        }
        selectTile(coordinate);
        if (route.size() > 1) {
            travelPath = new ArrayList<HexCoordinate>(route);
            travelIndex = 0;
            travelProgress = 0f;
            stopRequested = false;
            traveling = true;
        }
    }

    public boolean isTraveling() {
        return traveling;
    }

    /** Ship finishes the hex it is currently sailing to and then stops. */
    public void stopTravel() {
        if (traveling) {
            stopRequested = true;
        }
    }

    private void updateTravel(float delta) {
        if (!traveling) {
            return;
        }
        travelProgress += delta / SECONDS_PER_HEX;
        while (traveling && travelProgress >= 1f) {
            travelProgress -= 1f;
            travelIndex++;
            lastPosition = playerShip.getPosition();
            playerShip.setPosition(travelPath.get(travelIndex));
            revealAroundShip();
            if (enemyShip != null && playerShip.getPosition().equals(enemyShip.getPosition())) {
                startBattle();
                return;
            }
            if (stopRequested || travelIndex >= travelPath.size() - 1) {
                traveling = false;
                travelProgress = 0f;
                selectedTile = playerShip.getPosition();
                if (stopRequested) {
                    route = new ArrayList<HexCoordinate>();
                }
            }
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

