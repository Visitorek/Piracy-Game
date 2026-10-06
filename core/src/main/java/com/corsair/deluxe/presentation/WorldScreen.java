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
import com.badlogic.gdx.math.MathUtils;
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
    /** Where the hull currently points, in degrees; follows the course and eases into turns. */
    private float shipHeadingDeg;
    private boolean stopRequested;
    private float lineThickness;
    private static final int LOOK_AROUND_TURNS = 3;
    private static final int LOOK_AROUND_BONUS_RANGE = 1;
    private boolean lookingAround;
    private float lookElapsed;
    private final Set<HexCoordinate> discovered = new HashSet<HexCoordinate>();
    private String hudMessage;
    private String hudMessageDetail;
    private float hudMessageElapsed;
    private static final float HUD_MESSAGE_SECONDS = 2.2f;
    private static final float HUD_MESSAGE_FADE_SECONDS = 0.6f;
    private static final Color HUD_MESSAGE_PANEL_COLOR = new Color(0.07f, 0.09f, 0.14f, 0.88f);
    private static final Color HUD_MESSAGE_TEXT_COLOR = new Color(0.97f, 0.87f, 0.62f, 1f);
    private static final Color HUD_MESSAGE_DETAIL_COLOR = new Color(0.88f, 0.88f, 0.86f, 1f);

    private static final float TILE_RADIUS = 0.70f;
    private static final float TILE_ALPHA_VISIBLE = 0.85f;
    private static final float TILE_ALPHA_FOGGED = 0.30f;
    private static final float TILE_FILL_ALPHA_VISIBLE = 0.80f;
    private static final float TILE_FILL_ALPHA_FOGGED = 0.22f;
    /**
     * How far a border stops short of the edge of its cell. Two neighbours each leave this much, so a
     * thin line of background runs between their borders and single hexes stay readable instead of
     * the borders melting into one another.
     */
    private static final float HEX_SEPARATOR = 0.01f;
    /**
     * Every hex border is the same band, be it a seam between two tiles of the same terrain or the
     * contact line between two different ones - only the colour tells them apart. The band is the
     * rim between a tile's fill and the edge of its cell, so neighbouring tiles always meet the same
     * way. Being world-space, borders no longer change thickness with the camera zoom either.
     */
    private static final float BORDER_WIDTH = GameConfig.HEX_SPACING - TILE_RADIUS - HEX_SEPARATOR;
    private static final Color BACKGROUND_COLOR = new Color(0.45f, 0.45f, 0.45f, 1f);
    private static final Color CATEGORY_ISLAND = new Color(0.45f, 0.52f, 0.30f, 1f);
    private static final Color CATEGORY_SHALLOW = new Color(0.32f, 0.70f, 0.74f, 1f);
    private static final Color CATEGORY_WATER = new Color(0.13f, 0.27f, 0.46f, 1f);
    /** Land hemmed in by land on every side is overgrown jungle: dark green. */
    private static final Color DENSE_VEGETATION_COLOR = new Color(0.15f, 0.30f, 0.14f, 1f);
    /** Land meets the shallows: a strip of yellow sand. */
    private static final Color SAND_COLOR = new Color(0.94f, 0.85f, 0.46f, 1f);
    /**
     * Land drops straight into the deep: a rocky cliff. Land is always ringed by shallows, so this
     * only shows up if generation ever lets the two touch.
     */
    private static final Color CLIFF_COLOR = new Color(0.38f, 0.31f, 0.25f, 1f);
    /** Shallows meet the deep: a line of pale surf. */
    private static final Color REEF_COLOR = new Color(0.66f, 0.84f, 0.88f, 1f);
    /** Our ship's hex keeps its normal terrain colours and is ringed in red to stand out. */
    private static final Color SHIP_FRAME_COLOR = new Color(0.82f, 0.13f, 0.13f, 0.95f);
    /**
     * Width of the ring: exactly one tile border, so the ring replaces the border of the ship's tile
     * and never spills onto its neighbours.
     */
    private static final float SHIP_FRAME_WIDTH = BORDER_WIDTH;
    /**
     * Palette for the ship model, lifted from the launcher icon and the splash screen: navy hull,
     * pale planking, off-white canvas with the icon's navy bands, and a gold pennant that echoes the
     * game's HUD. The deck covers most of the hull, so the ship still reads light on dark water.
     */
    private static final ShipStyle PLAYER_SHIP_STYLE = new ShipStyle(
            new Color(0.23f, 0.24f, 0.44f, 1f),
            new Color(0.93f, 0.86f, 0.66f, 1f),
            new Color(0.12f, 0.13f, 0.29f, 1f),
            new Color(0.95f, 0.95f, 0.90f, 1f),
            new Color(0.40f, 0.27f, 0.15f, 1f),
            new Color(0.95f, 0.80f, 0.36f, 1f));
    /** The same silhouette burnt black with blood-red canvas, so friend and foe stay apart. */
    private static final ShipStyle ENEMY_SHIP_STYLE = new ShipStyle(
            new Color(0.15f, 0.14f, 0.15f, 1f),
            new Color(0.36f, 0.32f, 0.28f, 1f),
            new Color(0.45f, 0.11f, 0.11f, 1f),
            new Color(0.26f, 0.24f, 0.24f, 1f),
            new Color(0.20f, 0.17f, 0.14f, 1f),
            new Color(0.88f, 0.20f, 0.20f, 1f));
    /**
     * Hull seen from above: pointed bow at +x, transom stern at -x. Every part of the model stays
     * within the inradius of the hex outline (0.606), so the red ring is never covered.
     */
    private static final float[] SHIP_HULL = {
            0.52f, 0.00f,
            0.28f, 0.13f,
            0.06f, 0.18f,
            -0.24f, 0.185f,
            -0.40f, 0.15f,
            -0.48f, 0.078f,
            -0.48f, -0.078f,
            -0.40f, -0.15f,
            -0.24f, -0.185f,
            0.06f, -0.18f,
            0.28f, -0.13f};
    /** Planking inside the bulwark. */
    private static final float[] SHIP_DECK = {
            0.45f, 0.00f,
            0.24f, 0.08f,
            0.05f, 0.112f,
            -0.21f, 0.115f,
            -0.35f, 0.093f,
            -0.42f, 0.048f,
            -0.42f, -0.048f,
            -0.35f, -0.093f,
            -0.21f, -0.115f,
            0.05f, -0.112f,
            0.24f, -0.08f};
    /** Raised quarterdeck over the stern. */
    private static final float[] SHIP_CASTLE = {-0.38f, 0.082f, -0.20f, 0.11f, -0.20f, -0.11f, -0.38f, -0.082f};
    /** Canvas spread on the yards, perpendicular to the keel, each with a stripe across it. */
    private static final float[] SHIP_MAIN_SAIL = {0.05f, 0.205f, 0.19f, 0.205f, 0.19f, -0.205f, 0.05f, -0.205f};
    private static final float[] SHIP_MAIN_STRIPE = {0.105f, 0.205f, 0.135f, 0.205f, 0.135f, -0.205f, 0.105f, -0.205f};
    private static final float[] SHIP_MIZZEN_SAIL = {-0.17f, 0.16f, -0.03f, 0.16f, -0.03f, -0.16f, -0.17f, -0.16f};
    private static final float[] SHIP_MIZZEN_STRIPE = {-0.12f, 0.16f, -0.08f, 0.16f, -0.08f, -0.16f, -0.12f, -0.16f};
    /** Jib sheeted forward of the foremast. */
    private static final float[] SHIP_JIB = {0.30f, 0.135f, 0.30f, -0.135f, 0.54f, 0.00f};
    /** Pennant streaming off the stern. */
    private static final float[] SHIP_PENNANT = {-0.46f, 0.045f, -0.46f, -0.045f, -0.55f, 0.00f};
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

    /**
     * Re-arms alpha blending. libGDX' {@code SpriteBatch.end()} switches GL blending off, so any
     * frame that ends with text drawing would otherwise paint the next frame's fogged tiles at full
     * brightness - the map relies on alpha for everything that is out of sight.
     */
    private void enableAlphaBlending() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void render(float delta) {
        handleInput(delta);
        updateTravel(delta);
        updateLookAround(delta);
        updateHudMessage(delta);
        cameraManager.update();

        ScreenUtils.clear(BACKGROUND_COLOR);

        enableAlphaBlending();
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

            boolean shipTile = playerShip.getPosition().equals(tile.getCoordinate());
            if (debug) {
                drawDebugTile(tile, centerX, centerY);
            } else {
                drawPaintedTile(tile, centerX, centerY);
            }
            if (!shipTile && selectedTile != null && selectedTile.equals(tile.getCoordinate())) {
                shapeRenderer.setColor(1f, 0.98f, 0.7f, 0.8f);
                drawHexOutline(centerX, centerY, TILE_RADIUS);
            }
        }

        // The ship's hex is painted like every other tile; this ring is what sets it apart. It fills
        // the border of that hex, so the tile still reads as a single hexagon with a red rim.
        Vector2 shipTilePixel = HexMath.hexToPixel(playerShip.getPosition(), GameConfig.HEX_SPACING);
        shapeRenderer.setColor(SHIP_FRAME_COLOR);
        drawHexRing(6f + shipTilePixel.x, 4.2f + shipTilePixel.y, TILE_RADIUS,
                TILE_RADIUS + SHIP_FRAME_WIDTH);

        // Both ships sit on top of that ring, well inside its inner edge.
        Vector2 shipPixel = new Vector2(shipTilePixel);
        if (traveling) {
            Vector2 nextPixel = HexMath.hexToPixel(travelPath.get(travelIndex + 1), GameConfig.HEX_SPACING);
            Vector2 step = new Vector2(nextPixel).sub(shipTilePixel);
            if (!step.isZero()) {
                float course = MathUtils.atan2Deg(step.y, step.x);
                shipHeadingDeg = MathUtils.lerpAngleDeg(shipHeadingDeg, course, Math.min(1f, delta * 8f));
            }
            shipPixel = new Vector2(shipTilePixel).lerp(nextPixel, travelProgress);
        }
        float shipWorldX = 6f + shipPixel.x;
        float shipWorldY = 4.2f + shipPixel.y;

        if (enemyShip != null && discovered.contains(enemyShip.getPosition())) {
            Vector2 enemyPixel = HexMath.hexToPixel(enemyShip.getPosition(), GameConfig.HEX_SPACING);
            float enemyWorldX = 6f + enemyPixel.x;
            float enemyWorldY = 4.2f + enemyPixel.y;
            // A raider: it keeps its bow pointed at us wherever we are.
            float enemyHeading = MathUtils.atan2Deg(shipWorldY - enemyWorldY, shipWorldX - enemyWorldX);
            drawShip(enemyWorldX, enemyWorldY, enemyHeading, ENEMY_SHIP_STYLE);
        }

        drawShip(shipWorldX, shipWorldY, shipHeadingDeg, PLAYER_SHIP_STYLE);

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
        drawHudMessage();
    }

    /** Shows a short, self-expiring note in the top centre of the screen. */
    private void showHudMessage(String message, String detail) {
        this.hudMessage = message;
        this.hudMessageDetail = detail;
        this.hudMessageElapsed = 0f;
    }

    private void updateHudMessage(float delta) {
        if (hudMessage == null) {
            return;
        }
        hudMessageElapsed += delta;
        if (hudMessageElapsed >= HUD_MESSAGE_SECONDS) {
            hudMessage = null;
            hudMessageDetail = null;
        }
    }

    private void drawHudMessage() {
        if (hudMessage == null) {
            return;
        }
        float density = Gdx.graphics.getDensity();
        float alpha = Math.min(1f, (HUD_MESSAGE_SECONDS - hudMessageElapsed) / HUD_MESSAGE_FADE_SECONDS);
        alpha = Math.max(0f, alpha);

        float baseScale = font.getData().scaleX;
        font.getData().setScale(baseScale * 1.15f);
        glyphLayout.setText(font, hudMessage);
        float titleWidth = glyphLayout.width;
        float titleHeight = glyphLayout.height;

        boolean hasDetail = hudMessageDetail != null;
        float detailWidth = 0f;
        float detailHeight = 0f;
        if (hasDetail) {
            font.getData().setScale(baseScale * 0.75f);
            glyphLayout.setText(font, hudMessageDetail);
            detailWidth = glyphLayout.width;
            detailHeight = glyphLayout.height;
        }
        font.getData().setScale(baseScale);

        float padding = 22f * density;
        float gap = hasDetail ? 10f * density : 0f;
        float panelWidth = Math.max(titleWidth, detailWidth) + padding * 2f;
        float panelHeight = titleHeight + gap + detailHeight + padding * 2f;
        float centerX = Gdx.graphics.getWidth() / 2f;
        float panelTop = Gdx.graphics.getHeight() - 28f * density;
        float panelLeft = centerX - panelWidth / 2f;
        float panelBottom = panelTop - panelHeight;

        enableAlphaBlending();
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HUD_MESSAGE_PANEL_COLOR.r, HUD_MESSAGE_PANEL_COLOR.g,
                HUD_MESSAGE_PANEL_COLOR.b, HUD_MESSAGE_PANEL_COLOR.a * alpha);
        shapeRenderer.rect(panelLeft, panelBottom, panelWidth, panelHeight);
        shapeRenderer.setColor(0.85f, 0.7f, 0.35f, 0.9f * alpha);
        shapeRenderer.rect(panelLeft, panelBottom, panelWidth, Math.max(2f, 3f * density));
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin();
        font.getData().setScale(baseScale * 1.15f);
        font.setColor(HUD_MESSAGE_TEXT_COLOR.r, HUD_MESSAGE_TEXT_COLOR.g,
                HUD_MESSAGE_TEXT_COLOR.b, HUD_MESSAGE_TEXT_COLOR.a * alpha);
        font.draw(spriteBatch, hudMessage, centerX - titleWidth / 2f, panelTop - padding);
        if (hasDetail) {
            font.getData().setScale(baseScale * 0.75f);
            font.setColor(HUD_MESSAGE_DETAIL_COLOR.r, HUD_MESSAGE_DETAIL_COLOR.g,
                    HUD_MESSAGE_DETAIL_COLOR.b, HUD_MESSAGE_DETAIL_COLOR.a * alpha);
            font.draw(spriteBatch, hudMessageDetail, centerX - detailWidth / 2f,
                    panelTop - padding - titleHeight - gap);
        }
        font.getData().setScale(baseScale);
        font.setColor(Color.WHITE);
        spriteBatch.end();

        // The batch just switched blending off - the map needs it again on the next frame.
        enableAlphaBlending();
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
        enableAlphaBlending();
    }

    // TEMPORARY: debug button that jumps straight into a battle
    private float debugButtonCenterY() {
        return buttonCenterY() - buttonRadius() * 2.5f;
    }

    private void drawDebugBattleButton() {
        float cx = buttonCenterX();
        float cy = debugButtonCenterY();
        float radius = buttonRadius() * 0.7f;

        enableAlphaBlending();
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
        enableAlphaBlending();
    }

    private float buttonRadius() {
        return 36f * Gdx.graphics.getDensity();
    }

    private float menuButtonRadius() {
        return buttonRadius() * 0.9f;
    }

    /** Exit sits at the bottom of the right-hand button column, out of the way of the spyglass. */
    private float menuButtonCenterY() {
        return menuButtonRadius() + 24f * Gdx.graphics.getDensity();
    }

    /** Leaves the world map and shows the native main menu (same as the system back button). */
    private void drawMenuButton() {
        float cx = buttonCenterX();
        float cy = menuButtonCenterY();
        float radius = menuButtonRadius();
        float u = radius;

        enableAlphaBlending();
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

        enableAlphaBlending();
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

    /** Paints a solid hexagonal ring between two radii, in the colour the caller has already set. */
    private void drawHexRing(float centerX, float centerY, float innerRadius, float outerRadius) {
        float[] inner = buildHexVertices(centerX, centerY, innerRadius);
        float[] outer = buildHexVertices(centerX, centerY, outerRadius);
        for (int i = 0; i < 6; i++) {
            int next = (i + 1) % 6;
            float innerFromX = inner[i * 2];
            float innerFromY = inner[i * 2 + 1];
            float innerToX = inner[next * 2];
            float innerToY = inner[next * 2 + 1];
            float outerFromX = outer[i * 2];
            float outerFromY = outer[i * 2 + 1];
            float outerToX = outer[next * 2];
            float outerToY = outer[next * 2 + 1];
            shapeRenderer.triangle(innerFromX, innerFromY, innerToX, innerToY, outerFromX, outerFromY);
            shapeRenderer.triangle(outerFromX, outerFromY, innerToX, innerToY, outerToX, outerToY);
        }
    }

    /**
     * Paints the border of one edge of a hex: the wedge of the rim between the tile's fill and the
     * edge of its cell. Neighbouring tiles own the rim on their own side, so two of these wedges meet
     * exactly on the cell boundary and cover the rim between the tiles without a gap or an overlap.
     */
    private void drawHexEdge(float centerX, float centerY, int edgeIndex) {
        float[] inner = buildHexVertices(centerX, centerY, TILE_RADIUS);
        float[] outer = buildHexVertices(centerX, centerY, TILE_RADIUS + BORDER_WIDTH);
        int from = edgeIndex * 2;
        int to = ((edgeIndex + 1) % 6) * 2;

        shapeRenderer.triangle(inner[from], inner[from + 1], inner[to], inner[to + 1],
                outer[to], outer[to + 1]);
        shapeRenderer.triangle(outer[to], outer[to + 1], outer[from], outer[from + 1],
                inner[from], inner[from + 1]);
    }

    /**
     * Draws the ship model seen from above: dark hull, planked deck, canvas spread on the yards, the
     * masts with a bowsprit and jib, and a pennant streaming off the stern. The whole model fits
     * inside the hex outline, so the red ring around our own tile stays visible.
     */
    private void drawShip(float centerX, float centerY, float headingDeg, ShipStyle style) {
        shapeRenderer.translate(centerX, centerY, 0f);
        shapeRenderer.rotate(0f, 0f, 1f, headingDeg);

        shapeRenderer.setColor(style.hull);
        fillPolygon(SHIP_HULL);
        shapeRenderer.setColor(style.deck);
        fillPolygon(SHIP_DECK);
        // The quarterdeck is planked with the same wood as the rest of the deck - no navy patch at
        // the stern - and it also covers the bit of hull the deck outline leaves bare there.
        fillPolygon(SHIP_CASTLE);

        shapeRenderer.setColor(style.canvas);
        fillPolygon(SHIP_MAIN_SAIL);
        fillPolygon(SHIP_MIZZEN_SAIL);
        fillPolygon(SHIP_JIB);
        shapeRenderer.setColor(style.trim);
        fillPolygon(SHIP_MAIN_STRIPE);
        fillPolygon(SHIP_MIZZEN_STRIPE);

        shapeRenderer.setColor(style.mast);
        shapeRenderer.rectLine(0.34f, 0f, 0.56f, 0f, 0.04f);
        shapeRenderer.circle(0.30f, 0f, 0.024f, 12);
        shapeRenderer.circle(0.12f, 0f, 0.032f, 12);
        shapeRenderer.circle(-0.10f, 0f, 0.028f, 12);

        shapeRenderer.setColor(style.flag);
        fillPolygon(SHIP_PENNANT);

        shapeRenderer.identity();
    }

    /** Paints a convex polygon given as x/y pairs, as a triangle fan around its own centroid. */
    private void fillPolygon(float[] points) {
        int count = points.length / 2;
        float centerX = 0f;
        float centerY = 0f;
        for (int i = 0; i < count; i++) {
            centerX += points[i * 2];
            centerY += points[i * 2 + 1];
        }
        centerX /= count;
        centerY /= count;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            shapeRenderer.triangle(centerX, centerY,
                    points[i * 2], points[i * 2 + 1],
                    points[next * 2], points[next * 2 + 1]);
        }
    }

    /** Debug view: flat terrain colours, exactly as the map has always been drawn. */
    private void drawDebugTile(HexTile tile, float centerX, float centerY) {
        Color color = debugColorFor(tile.getTerrain());
        float alpha = isVisible(tile.getCoordinate()) ? 0.6f : 0.25f;
        shapeRenderer.setColor(color.r, color.g, color.b, alpha);
        drawHexOutline(centerX, centerY, TILE_RADIUS);
    }

    /**
     * Normal view: the hex is filled with its terrain category colour and its border is painted by
     * what lies on the other side of each edge - the tile's own category colour when the neighbour
     * shares its terrain, otherwise the colour of the transition between the two (sand, cliff, reef).
     * Both tiles paint their half of a shared edge, so the border of a seam always matches and the
     * tiles meet exactly, with no background showing between them.
     */
    private void drawPaintedTile(HexTile tile, float centerX, float centerY) {
        boolean visible = isVisible(tile.getCoordinate());
        boolean jungle = isJungle(tile);
        Color fill = jungle ? DENSE_VEGETATION_COLOR : categoryColorFor(tile.getTerrain());
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
            boolean sharedTerrain = neighborTerrain == tile.getTerrain();
            Color color;
            if (!sharedTerrain) {
                color = transitionColorFor(tile.getTerrain(), neighborTerrain);
            } else if (jungle && isJungle(neighbor)) {
                color = DENSE_VEGETATION_COLOR;
            } else {
                color = categoryColorFor(tile.getTerrain());
            }
            shapeRenderer.setColor(color.r, color.g, color.b, edgeAlpha);
            drawHexEdge(centerX, centerY, EDGE_INDEX[direction.ordinal()]);
        }
    }

    /** True when this land tile is hemmed in by land on all six sides, i.e. jungle. */
    private boolean isJungle(HexTile tile) {
        if (tile == null || tile.getTerrain() != HexTile.Terrain.ISLAND) {
            return false;
        }
        for (HexDirection direction : HexDirection.values()) {
            HexTile neighbor = map.getTile(tile.getCoordinate().neighbor(direction));
            if (neighbor == null || neighbor.getTerrain() != HexTile.Terrain.ISLAND) {
                return false;
            }
        }
        return true;
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
        if (isBlockedMove(target)) {
            return;
        }
        selectedTile = target;
        route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile, GameSettings.isDebugMode());
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

    /**
     * Ships sail, they do not walk: land is out of bounds unless debug mode is on, which lets the
     * player beach the ship anywhere for testing. A refused move warns the player on the HUD.
     */
    private boolean isBlockedMove(HexCoordinate coordinate) {
        if (isBusy() || !discovered.contains(coordinate)) {
            return true;
        }
        if (canSailTo(coordinate)) {
            return false;
        }
        showHudMessage("Mielizna!", "Statek nie wplynie na lad");
        return true;
    }

    private boolean canSailTo(HexCoordinate coordinate) {
        if (GameSettings.isDebugMode()) {
            return true;
        }
        HexTile tile = map.getTile(coordinate);
        return tile != null && tile.getTerrain() != HexTile.Terrain.ISLAND;
    }

    public void selectTile(HexCoordinate coordinate) {
        if (isBlockedMove(coordinate)) {
            return;
        }
        this.selectedTile = coordinate;
        this.route = HexPathfinder.findPath(map, playerShip.getPosition(), selectedTile,
                GameSettings.isDebugMode());
    }

    public void moveTo(HexCoordinate coordinate) {
        if (isBlockedMove(coordinate)) {
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

    /** Colours of one ship model: hull, deck planking, navy trim, canvas, spars and pennant. */
    private static final class ShipStyle {
        private final Color hull;
        private final Color deck;
        private final Color trim;
        private final Color canvas;
        private final Color mast;
        private final Color flag;

        private ShipStyle(Color hull, Color deck, Color trim, Color canvas, Color mast, Color flag) {
            this.hull = hull;
            this.deck = deck;
            this.trim = trim;
            this.canvas = canvas;
            this.mast = mast;
            this.flag = flag;
        }
    }
}

