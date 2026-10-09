package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;
import com.corsair.deluxe.core.GameSettings;
import com.corsair.deluxe.domain.Ship;
import com.corsair.deluxe.domain.battle.ActionResult;
import com.corsair.deluxe.domain.battle.BattleAI;
import com.corsair.deluxe.domain.battle.BattleAction;
import com.corsair.deluxe.domain.battle.BattleOutcome;
import com.corsair.deluxe.domain.battle.BattleResult;
import com.corsair.deluxe.domain.battle.BattleSide;
import com.corsair.deluxe.domain.battle.BattleState;
import com.corsair.deluxe.domain.battle.JumpDirection;

import java.util.Random;

/**
 * Turn-based boarding skirmish in the spirit of Piracy Deluxe: tap the hatches in your column (left)
 * to open them, then press an arrow to muster one pirate out of every open hatch - the hatches close
 * again by themselves - or, with all hatches closed, to make the whole crew on the net jump.
 * Aim: disable the enemy hatches (right column).
 */
public class BattleScreen extends ScreenAdapter {
    private static final float ENEMY_THINK_SECONDS = 0.8f;
    private static final float INPUT_DELAY_SECONDS = 0.6f;
    /** Cells smaller than this make the board unreadable, so the text scale is reduced instead. */
    private static final float MIN_CELL = 26f;
    /** Longest variant of the turn line, so the top text band never has to grow. */
    private static final String WORST_TURN_LINE = "Tura " + BattleState.MAX_TURNS + "/" + BattleState.MAX_TURNS
            + " - PORAZKA - dotknij, aby kontynuowac";
    /** Longest realistic battle log entry; it is the text the bottom band is sized for. */
    private static final String LOG_RESERVE =
            "Wrog: wyprowadzenie 5 pir., pokonani: 5, wpadli: 5, zablokowane luki: 5, wycofani: 5";

    private static final Color PLAYER_COLOR = new Color(0.25f, 0.55f, 1f, 1f);
    private static final Color ENEMY_COLOR = new Color(0.9f, 0.2f, 0.2f, 1f);

    // Sky: hazy and pale along the horizon, deep blue overhead (t = 0 at the horizon, 1 at the top)
    private static final float[] SKY_POS = { 0f, 0.07f, 0.20f, 0.42f, 0.70f, 1f };
    private static final float[][] SKY_RGB = {
            { 206, 227, 245 }, { 176, 208, 239 }, { 138, 182, 229 },
            { 100, 152, 216 }, { 66, 115, 194 }, { 38, 76, 152 } };
    // Sea: a bright band at the horizon, deepening toward the viewer
    private static final float[] SEA_POS = { 0f, 0.11f, 0.24f, 0.40f, 0.58f, 0.79f, 1f };
    private static final float[][] SEA_RGB = {
            { 138, 173, 206 }, { 108, 144, 188 }, { 84, 122, 170 }, { 60, 92, 144 },
            { 44, 68, 116 }, { 32, 50, 92 }, { 22, 35, 68 } };

    private static final Color BACKDROP = color(20, 13, 18);
    private static final Color HAZE_COLOR = color(238, 245, 253);
    private static final Color HORIZON_RIM = color(176, 205, 231);
    private static final Color CREST_COLOR = color(152, 190, 224);
    private static final Color TROUGH_COLOR = color(14, 24, 52);
    private static final Color CLOUD_LIT = color(252, 253, 255);
    private static final Color CLOUD_BASE = color(178, 196, 224);
    private static final Color SUN_COLOR = color(255, 244, 205);
    private static final Color SUN_DISC_COLOR = color(255, 252, 236);
    private static final Color SUN_CORE_COLOR = color(255, 253, 240);
    private static final Color FOAM_COLOR = color(214, 232, 245);
    /** The boarding net: pale rope over the sea, with the dark gap of a torn opening in it. */
    private static final Color ROPE_COLOR = color(217, 214, 199);
    private static final Color ROPE_SHADE = color(133, 128, 117);
    private static final Color GAP_COLOR = color(10, 13, 23);
    /** The column of hull a side's hatches sit in; the ships are drawn over the ends of the net. */
    private static final Color HULL_COLUMN_COLOR = new Color(0.25f, 0.16f, 0.1f, 1f);
    /** The ships themselves: a planked wooden hull, a cream sail, and the hull below the waterline. */
    private static final Color PLANK_TAN = color(200, 152, 84);
    private static final Color PLANK_MID = color(120, 84, 48);
    private static final Color PLANK_LIGHT = color(145, 97, 51);
    private static final Color PLANK_SHADOW = color(60, 35, 16);
    private static final Color SAIL_COLOR = color(196, 186, 158);
    private static final Color KEEL_COLOR = color(30, 44, 82);
    /** Planking bands from the gunwale down to the waterline, with the height each one ends at. */
    private static final Color[] PLANK_BANDS = {
            PLANK_TAN, HULL_COLUMN_COLOR, PLANK_MID, PLANK_LIGHT, PLANK_SHADOW,
            PLANK_MID, PLANK_LIGHT, PLANK_MID, PLANK_LIGHT, HULL_COLUMN_COLOR };
    private static final float[] PLANK_STOPS = {
            0f, 0.05f, 0.17f, 0.29f, 0.38f, 0.43f, 0.53f, 0.63f, 0.73f, 0.82f, 1f };

    /** Where the sun sits across the scene, as a fraction of the width. */
    private static final float SUN_X = 0.30f;
    /** How far the masts reach above the top deck, and how far the water reaches in front of the hulls. */
    private static final float MAST_BAND = 2f;
    private static final float SEA_DEPTH = 1.30f;
    /** The net between the ships is drawn this much larger than the plain fit would give it. */
    private static final float NET_SCALE = 1.2f;
    /** Half a hull of room beside the board on each side, and how high the horizon cuts across it. */
    private static final float SHIP_ROOM = 1.10f;
    private static final float HORIZON_CELLS = 2.55f;
    /** How far the rigging rises above the deck, shrunk if the sky band above the board is shorter. */
    private static final float RIGGING_CELLS = 1.15f;
    /** How wide the hull is at the deck and at the keel, measured from its net-side edge outward. */
    private static final float HULL_TOP_CELLS = 2.14f;
    private static final float HULL_KEEL_CELLS = 1.62f;
    /** The deck sits this far above the top of the board, and the keel this far below its bottom. */
    private static final float DECK_LIFT_CELLS = 0.10f;
    private static final float KEEL_DROP_CELLS = 0.50f;
    /** The whole ship, deck squares included, stands this far off the net. */
    private static final float SHIP_GAP_CELLS = 0.50f;
    /** Thickness of a strand and its dark outline. */
    private static final float STRAND_THICKNESS = 0.04f;
    private static final float STRAND_EDGE = 0.012f;
    /** The horizontal strands cross the gap to the hull and tuck a little way under it. */
    private static final float STRAND_REACH = SHIP_GAP_CELLS + 0.06f;
    /** A torn opening: its radius, and how many slack strands cross it. */
    private static final float HOLE_RADIUS = 0.38f;
    private static final int HOLE_SPOKES = 6;
    /** The sea and the sky look the same on every frame, so their scatter is drawn from a fixed seed. */
    private static final long SEA_SEED = 20261007L;
    /** Puff layout of a cloud, as offsets and half-sizes relative to the cloud radius. */
    private static final float[][] CLOUD_PUFFS = {
            { -0.72f, -0.02f, 0.50f, 0.30f }, { -0.30f, 0.12f, 0.60f, 0.40f },
            { 0.22f, 0.06f, 0.58f, 0.38f }, { 0.74f, -0.04f, 0.42f, 0.26f } };

    private enum Phase { PLAYER_TURN, ENEMY_TURN, FINISHED }

    private final CorsairGame game;
    private final WorldScreen returnScreen;
    private final Ship playerShip;
    private final Ship enemyShip;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Random random = new Random();
    private final BattleAI ai = new BattleAI(random, GameSettings.aiDepth(),
            GameSettings.aiRandomMoveChance());
    private final BattleState state;
    private final int initialPlayerUnits;
    /** Hatches the player opened for the next muster; opening and closing them is free. */
    private final boolean[] openHatches = new boolean[BattleState.HATCH_ROWS.length];

    private Phase phase = Phase.PLAYER_TURN;
    private BattleOutcome outcome = BattleOutcome.ONGOING;
    private String message = "Tapnij luk, aby go otworzyc; strzalka wyprowadza zaloge.";
    private float phaseTime;
    private float elapsed;
    private boolean leaving;

    private float width;
    private float height;
    private float margin;
    private float lineHeight;
    private float textScale;
    private float textWidth;
    private float topBand;
    private float bottomBand;
    private float boardX;
    private float boardY;
    private float cell;
    private float sceneTop;
    private float sceneBottom;
    private float horizonY;
    private float skyHeight = 1f;
    private float seaHeight = 1f;
    /** Scratch colour for the gradient ramps, so a colour is not allocated per drawn row. */
    private final Color tint = new Color();
    private final Random seaRandom = new Random(SEA_SEED);
    private float compassX;
    private float compassY;
    private float buttonSize;
    private float fleeX;
    private float fleeY;
    private float fleeHalfWidth;
    private float fleeHalfHeight;

    public BattleScreen(CorsairGame game, WorldScreen returnScreen, Ship playerShip, Ship enemyShip) {
        this.game = game;
        this.returnScreen = returnScreen;
        this.playerShip = playerShip;
        this.enemyShip = enemyShip;

        // The default font is a small bitmap; it is drawn several times larger here
        font.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);

        int holePairs = Math.min(4, 2 + (100 - playerShip.getHullIntegrity()) / 50);
        this.state = BattleState.create(holePairs, random);
        this.initialPlayerUnits = state.getTotalCrew(BattleSide.PLAYER);
        layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        // The world screen's input handler stays registered otherwise; this screen polls input itself
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        layout(width, height);
    }

    private void layout(float w, float h) {
        width = w;
        height = h;
        camera.setToOrtho(false, w, h);
        margin = Math.max(12f, Math.min(w, h) * 0.02f);

        // The battle screen used to run at 'h / 760', which is barely readable on a phone, so the text is
        // drawn twice as large. On very small screens the board would not fit any more, so the multiplier
        // drops until the cells are usable again.
        float[] multipliers = { 2f, 1.6f, 1.3f, 1f };
        for (int i = 0; i < multipliers.length; i++) {
            computeGeometry(Math.max(0.8f, h / 760f) * multipliers[i]);
            if (cell >= MIN_CELL || i == multipliers.length - 1) {
                break;
            }
        }
    }

    private void computeGeometry(float textScale) {
        this.textScale = textScale;
        font.getData().setScale(textScale);
        lineHeight = font.getLineHeight();
        float gap = lineHeight * 0.12f;

        // Full-width text bands above and below the board; the board and the arrows share the middle.
        // Both bands are sized for the longest text they can ever show, never for the current one.
        textWidth = Math.max(40f, width - 2 * margin);
        topBand = measure(playerLine(), textWidth) + measure(enemyLine(), textWidth)
                + Math.max(measure(turnLine(), textWidth), measure(WORST_TURN_LINE, textWidth))
                + measure(hintLine(), textWidth) + gap * 3f;
        bottomBand = Math.max(lineHeight * 1.6f, measure(LOG_RESERVE, textWidth) + lineHeight * 0.4f);
        float arrowZone = Math.max(width * 0.14f, lineHeight * 2.4f);
        float usableW = width - 2 * margin - arrowZone;
        float usableH = height - 2 * margin - topBand - bottomBand;

        // The scene is the board plus the sky the masts rise into and the water in front of the hulls,
        // with half a hull of room beside the board for the ships and the reserve markers.
        float sceneCells = BattleState.HEIGHT + MAST_BAND + SEA_DEPTH;
        float fit = Math.max(1f, Math.min(usableH / sceneCells, usableW / (BattleState.WIDTH + 2f * SHIP_ROOM)));
        // The net is drawn NET_SCALE larger than that fit. The sky above the masts and the water in front
        // of the hulls give up the room, so the scene still fills the same box between the text bands; a
        // screen too narrow for the wider net keeps the plain fit.
        float netScale = Math.max(1f,
                Math.min(NET_SCALE, usableW / ((BattleState.WIDTH + 2f * SHIP_ROOM) * fit)));
        cell = fit * netScale;
        float bandCells = sceneCells / netScale - BattleState.HEIGHT;
        float mastCells = bandCells * MAST_BAND / (MAST_BAND + SEA_DEPTH);
        float seaCells = bandCells * SEA_DEPTH / (MAST_BAND + SEA_DEPTH);
        float boardWidth = cell * BattleState.WIDTH;
        float sceneWidth = cell * (BattleState.WIDTH + 2f * SHIP_ROOM);
        sceneBottom = margin + bottomBand + (usableH - cell * (BattleState.HEIGHT + bandCells)) / 2f;
        boardX = margin + (usableW - sceneWidth) / 2f + cell * SHIP_ROOM;
        boardY = sceneBottom + cell * seaCells;
        sceneTop = boardY + cell * (BattleState.HEIGHT + mastCells);
        // The horizon stays where it was, so the sky thins out evenly instead of the net rising into it.
        horizonY = sceneBottom + cell * (HORIZON_CELLS + SEA_DEPTH) / netScale;
        skyHeight = Math.max(1f, sceneTop - horizonY);
        seaHeight = Math.max(1f, horizonY - sceneBottom);

        float rightEdge = boardX + boardWidth;
        float rightWidth = width - margin - rightEdge;
        compassX = rightEdge + rightWidth / 2f;
        compassY = boardY + cell * BattleState.HEIGHT / 2f;
        buttonSize = Math.min(rightWidth / 3.4f, usableH * 0.24f);

        fleeHalfWidth = Math.max(buttonSize * 1.5f, rightWidth * 0.45f);
        fleeHalfHeight = Math.max(buttonSize * 0.4f, lineHeight * 0.75f);
        fleeX = compassX;
        fleeY = Math.max(boardY + fleeHalfHeight, compassY - buttonSize * 2.2f);
    }

    @Override
    public void render(float delta) {
        if (leaving) {
            return;
        }
        elapsed += delta;
        phaseTime += delta;
        handleInput();
        updateEnemy();
        if (game.getScreen() == this) {
            draw();
        }
    }

    private void handleInput() {
        if (phase == Phase.FINISHED) {
            boolean confirm = Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
            if (confirm && phaseTime > INPUT_DELAY_SECONDS) {
                leave();
            }
            return;
        }
        if (phase != Phase.PLAYER_TURN) {
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            flee();
            return;
        }
        if (!Gdx.input.justTouched()) {
            return;
        }
        float touchX = Gdx.input.getX() * width / Gdx.graphics.getWidth();
        float touchY = height - Gdx.input.getY() * height / Gdx.graphics.getHeight();

        if (Math.abs(touchX - fleeX) < fleeHalfWidth && Math.abs(touchY - fleeY) < fleeHalfHeight) {
            flee();
            return;
        }

        for (JumpDirection direction : JumpDirection.values()) {
            float buttonX = compassX;
            float buttonY = compassY - direction.getDy() * buttonSize;
            if (Math.abs(touchX - buttonX) < buttonSize / 2f && Math.abs(touchY - buttonY) < buttonSize / 2f) {
                playerArrow(direction);
                return;
            }
        }

        // The tap target is widened by a cell on each side to make hatches easy to hit
        float left = cellLeft(BattleState.hatchColumn(BattleSide.PLAYER));
        for (int i = 0; i < BattleState.HATCH_ROWS.length; i++) {
            float bottom = cellBottom(BattleState.HATCH_ROWS[i]);
            if (touchX >= left - cell && touchX <= left + cell * 2f
                    && touchY >= bottom && touchY <= bottom + cell) {
                toggleHatch(i);
                return;
            }
        }
    }

    /**
     * With at least one hatch open the arrow musters the crew out of every open hatch (the hatches then
     * slam shut again); with all hatches closed it is an ordinary jump of the crew already on the net.
     */
    private void playerArrow(JumpDirection direction) {
        if (hasOpenHatches()) {
            playerAction(BattleAction.deploy(openHatches));
        } else {
            playerAction(BattleAction.jump(direction));
        }
    }

    private void toggleHatch(int hatchIndex) {
        if (state.isHatchDisabled(BattleSide.PLAYER, hatchIndex)) {
            message = "Luk " + (hatchIndex + 1) + " jest zniszczony.";
            return;
        }
        int waiting = state.getHatchCrew(BattleSide.PLAYER, hatchIndex);
        if (waiting <= 0) {
            message = "Luk " + (hatchIndex + 1) + " jest pusty.";
            return;
        }
        openHatches[hatchIndex] = !openHatches[hatchIndex];
        if (!openHatches[hatchIndex]) {
            message = "Luk " + (hatchIndex + 1) + " zamkniety (czeka " + waiting + " pir.).";
        } else if (state.canRelease(BattleSide.PLAYER, hatchIndex)) {
            message = "Luk " + (hatchIndex + 1) + " otwarty - strzalka wyprowadzi zaloge.";
        } else {
            message = "Luk " + (hatchIndex + 1) + " otwarty, ale pole wejsciowe zajete - odsun pirata.";
        }
    }

    private boolean hasOpenHatches() {
        return openHatchCount() > 0;
    }

    private int openHatchCount() {
        int count = 0;
        for (int i = 0; i < openHatches.length; i++) {
            if (openHatches[i]) {
                count++;
            }
        }
        return count;
    }

    /** Mustering the crew slams every hatch shut again, so the next arrow is a jump. */
    private void closeHatches() {
        for (int i = 0; i < openHatches.length; i++) {
            openHatches[i] = false;
        }
    }

    private void playerAction(BattleAction action) {
        if (!state.isLegal(BattleSide.PLAYER, action)) {
            message = action.isDeploy()
                    ? "Zaden z otwartych lukow nie moze teraz wypuscic pirata."
                    : "Brak zalogi na sieci.";
            return;
        }
        ActionResult result = state.apply(action);
        if (action.isDeploy()) {
            closeHatches();
        }
        message = "Ty: " + describe(action, result);
        afterAction();
    }

    private void updateEnemy() {
        if (phase != Phase.ENEMY_TURN || phaseTime < ENEMY_THINK_SECONDS) {
            return;
        }
        BattleAction action = ai.chooseAction(state);
        ActionResult result = state.apply(action);
        message = "Wrog: " + describe(action, result);
        afterAction();
    }

    private void afterAction() {
        outcome = state.getOutcome();
        phaseTime = 0f;
        if (outcome != BattleOutcome.ONGOING) {
            phase = Phase.FINISHED;
            return;
        }
        phase = state.getSideToMove() == BattleSide.PLAYER ? Phase.PLAYER_TURN : Phase.ENEMY_TURN;
    }

    private void flee() {
        outcome = BattleOutcome.FLED;
        phase = Phase.FINISHED;
        phaseTime = 0f;
        message = "Uciekasz z pola bitwy!";
    }

    private String describe(BattleAction action, ActionResult result) {
        StringBuilder text = new StringBuilder();
        if (action.isDeploy()) {
            text.append("wyprowadzenie ").append(result.getDeployed()).append(" pir.");
        } else {
            text.append("skok ").append(directionName(action.getDirection()));
        }
        if (result.getKilled() > 0) {
            text.append(", pokonani: ").append(result.getKilled());
        }
        if (result.getFell() > 0) {
            text.append(", wpadli: ").append(result.getFell());
        }
        if (result.getHatchesDisabled() > 0) {
            text.append(", zablokowane luki: ").append(result.getHatchesDisabled());
        }
        if (result.getRetreated() > 0) {
            text.append(", wycofani: ").append(result.getRetreated());
        }
        return text.toString();
    }

    private String directionName(JumpDirection direction) {
        switch (direction) {
            case FORWARD_UP: return "ukos w gore";
            case FORWARD_DOWN: return "ukos w dol";
            default: return "naprzod";
        }
    }

    private void leave() {
        int lost = Math.max(0, (initialPlayerUnits - state.getTotalCrew(BattleSide.PLAYER)) * BattleState.CREW_PER_UNIT);
        if (outcome == BattleOutcome.DEFEAT) {
            game.openMainMenu();
            return;
        }
        returnScreen.onBattleFinished(new BattleResult(outcome, lost));
        game.setScreen(returnScreen);
        dispose();
    }

    private float cellLeft(int x) {
        return boardX + x * cell;
    }

    private float cellBottom(int y) {
        return boardY + (BattleState.HEIGHT - 1 - y) * cell;
    }

    /**
     * How far a side's ship stands off the net. The deck squares, the hull and the ropes all
     * measure from the same shift, so the gap always reads as part of the ship.
     */
    private float shipShift(BattleSide side) {
        return (side == BattleSide.PLAYER ? -1f : 1f) * cell * SHIP_GAP_CELLS;
    }

    private void draw() {
        ScreenUtils.clear(BACKDROP);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSeaAndSky();
        drawBoard();
        drawControls();
        shapes.end();

        batch.begin();
        drawTexts();
        batch.end();
    }

    /**
     * The sea and the sky, painted under everything else: gradients first, then the air, the sun and its
     * reflections. Everything is expressed in cells so the view keeps its proportions on any screen.
     */
    private void drawSeaAndSky() {
        // One row per pixel. Wider bands step by the whole band delta at once and read as stripes, and
        // fractional bands leave a row out here and there; a fixed seed keeps the scatter still.
        seaRandom.setSeed(SEA_SEED);
        for (float y = horizonY; y < sceneTop; y += 1f) {
            skyColorAt(y, tint);
            shapes.setColor(tint);
            shapes.rect(0f, y, width, 1.02f);
        }
        for (float y = horizonY - 1f; y >= sceneBottom; y -= 1f) {
            seaColorAt(y, tint);
            shapes.setColor(tint);
            shapes.rect(0f, y, width, 1.02f);
        }

        drawHaze();
        drawSun();
        drawClouds();
        drawWaves();
        drawSunGlitter();
        drawFoam();
    }

    /** The haze that hides the far end of the sea, as opaque rows mixed into the sky colour. */
    private void drawHaze() {
        float hazeHeight = cell * 0.70f;
        for (float y = horizonY; y < horizonY + hazeHeight; y += 1f) {
            float t = (y + 0.5f - horizonY) / hazeHeight;
            float f = 0.30f * (float) Math.pow(Math.max(0f, 1f - t), 1.5);
            skyColorAt(y, tint);
            shapes.setColor(tint.r + (HAZE_COLOR.r - tint.r) * f,
                    tint.g + (HAZE_COLOR.g - tint.g) * f,
                    tint.b + (HAZE_COLOR.b - tint.b) * f, 1f);
            shapes.rect(0f, y, width, 1.02f);
        }
        shapes.setColor(HORIZON_RIM);
        shapes.rect(0f, horizonY - cell * 0.028f, width, cell * 0.028f);
    }

    /** The sun, low over the water: a broad glow, a halo and a small disc. */
    private void drawSun() {
        float sunX = width * SUN_X;
        float sunY = horizonY + cell * 0.55f;
        for (int i = 26; i >= 1; i--) {
            float fade = 1f - i / 26f;
            shapes.setColor(SUN_COLOR.r, SUN_COLOR.g, SUN_COLOR.b, (2.4f + 26f * fade * fade) / 255f);
            shapes.circle(sunX, sunY, cell * 0.14f * i, 32);
        }
        shapes.setColor(SUN_DISC_COLOR.r, SUN_DISC_COLOR.g, SUN_DISC_COLOR.b, 62f / 255f);
        for (int i = 5; i >= 1; i--) {
            shapes.circle(sunX, sunY, cell * 0.40f * i / 5f, 32);
        }
        shapes.setColor(SUN_CORE_COLOR);
        shapes.circle(sunX, sunY, cell * 0.16f, 32);
    }

    private void drawClouds() {
        cloud(width * 0.13f, horizonY + skyHeight * 0.52f, cell * 0.70f, 240f / 255f);
        cloud(width * 0.47f, horizonY + skyHeight * 0.90f, cell * 0.52f, 205f / 255f);
        cloud(width * 0.74f, horizonY + skyHeight * 0.26f, cell * 0.86f, 235f / 255f);
        cloud(width * 0.93f, horizonY + skyHeight * 0.70f, cell * 0.46f, 190f / 255f);
        cloud(width * 0.60f, horizonY + skyHeight * 0.62f, cell * 0.30f, 150f / 255f);
    }

    /** A cluster of puffs, shaded underneath and lit on top. */
    private void cloud(float cx, float cy, float radius, float alpha) {
        for (float[] puff : CLOUD_PUFFS) {
            puff(cx + puff[0] * radius, cy + puff[1] * radius,
                    puff[2] * radius, puff[3] * radius, CLOUD_BASE, alpha * 0.70f);
        }
        for (float[] puff : CLOUD_PUFFS) {
            puff(cx + puff[0] * radius, cy + (puff[1] + 0.14f) * radius,
                    puff[2] * 0.84f * radius, puff[3] * 0.78f * radius, CLOUD_LIT, alpha);
        }
    }

    /** ring==0 is the solid core, the outer rings fade it out; libGDX has no blur to soften it with. */
    private void puff(float cx, float cy, float rx, float ry, Color color, float alpha) {
        for (int ring = 2; ring >= 0; ring--) {
            float k = 0.78f + 0.11f * ring;
            shapes.setColor(color.r, color.g, color.b, ring == 0 ? alpha : alpha * 0.34f);
            shapes.ellipse(cx - rx * k, cy - ry * k, rx * 2f * k, ry * 2f * k, 28);
        }
    }

    /** Wave rows, dense and flat at the horizon and long and broken near the bottom of the frame. */
    private void drawWaves() {
        int rows = 96;
        for (int i = 0; i < rows; i++) {
            float t = (float) Math.pow((i + 0.5f) / rows, 1.8);
            float y = horizonY - seaHeight * t;
            float thickness = Math.max(1f, cell * (0.010f + 0.062f * t));
            float span = cell * (0.18f + 1.90f * t);
            float gap = span * (1.1f + 1.5f * (1f - t));
            Color color = i % 2 == 0 ? CREST_COLOR : TROUGH_COLOR;
            float alpha = (i % 2 == 0 ? 34f + 104f * t : 58f + 126f * t) / 255f;
            shapes.setColor(color.r, color.g, color.b, alpha);
            float x = -seaRandom.nextFloat() * gap;
            while (x < width) {
                shapes.rect(x, y, span * (0.55f + 0.80f * seaRandom.nextFloat()), thickness);
                x += span + gap * (0.35f + seaRandom.nextFloat());
            }
        }
    }

    /** The sun's path on the water, widening as it comes closer. */
    private void drawSunGlitter() {
        float sunX = width * SUN_X;
        int count = 190;
        for (int i = 0; i < count; i++) {
            float t = (float) Math.pow((i + 0.5f) / count, 1.7f);
            float y = horizonY - seaHeight * t;
            float alpha = (168f - 126f * t) * (0.35f + 0.65f * seaRandom.nextFloat());
            if (alpha <= 0f) {
                continue;
            }
            float w = cell * (0.05f + 0.62f * t) * (0.45f + seaRandom.nextFloat());
            float cx = sunX + (seaRandom.nextFloat() * 2f - 1f) * cell * (0.08f + 1.35f * t);
            shapes.setColor(SUN_COLOR.r, SUN_COLOR.g, SUN_COLOR.b, alpha / 255f);
            shapes.rect(cx - w / 2f, y, w, Math.max(1f, cell * 0.011f * (1f + 2.4f * t)));
        }
    }

    /** Loose foam in the foreground, to break the regularity of the wave rows. */
    private void drawFoam() {
        for (int i = 0; i < 70; i++) {
            float y = horizonY - seaHeight * (0.72f + 0.28f * seaRandom.nextFloat());
            float w = cell * (0.18f + 0.55f * seaRandom.nextFloat());
            float x = seaRandom.nextFloat() * width;
            shapes.setColor(FOAM_COLOR.r, FOAM_COLOR.g, FOAM_COLOR.b,
                    (40f + 70f * seaRandom.nextFloat()) / 255f);
            shapes.rect(x, y, w, Math.max(1f, cell * 0.018f));
        }
    }

    private void skyColorAt(float y, Color out) {
        ramp(SKY_POS, SKY_RGB, (y + 0.5f - horizonY) / skyHeight, out);
    }

    private void seaColorAt(float y, Color out) {
        ramp(SEA_POS, SEA_RGB, (horizonY - y - 0.5f) / seaHeight, out);
    }

    /** Mixes the two stops a fraction falls between; t == 0 is the horizon end of either ramp. */
    private void ramp(float[] stops, float[][] colors, float t, Color out) {
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        int i = 1;
        while (i < stops.length - 1 && stops[i] < t) {
            i++;
        }
        float span = stops[i] - stops[i - 1];
        float f = span <= 0f ? 0f : (t - stops[i - 1]) / span;
        float[] a = colors[i - 1];
        float[] b = colors[i];
        out.set((a[0] + (b[0] - a[0]) * f) / 255f,
                (a[1] + (b[1] - a[1]) * f) / 255f,
                (a[2] + (b[2] - a[2]) * f) / 255f, 1f);
    }

    private static Color color(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }

    private void drawBoard() {
        int hatchColumnPlayer = BattleState.hatchColumn(BattleSide.PLAYER);
        int hatchColumnEnemy = BattleState.hatchColumn(BattleSide.ENEMY);
        float pulse = 0.65f + 0.35f * (float) Math.sin(elapsed * 5f);

        // The net is the playfield, so it goes down first: the two hull columns drawn over it cover the
        // ends of the strands, right where the rope is lashed to the ship.
        drawNet();
        // The ships go over the net as well, so the strand ends land on the hull; the deck squares then
        // go over the hull, leaving the planking and the rigging visible outside the board.
        drawShips();

        for (int x = 0; x < BattleState.WIDTH; x++) {
            if (x != hatchColumnPlayer && x != hatchColumnEnemy) {
                continue;
            }
            // The deck squares are part of the ship, so they stand off the net with the hull.
            float left = cellLeft(x) + shipShift(x == hatchColumnPlayer ? BattleSide.PLAYER : BattleSide.ENEMY);
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                float bottom = cellBottom(y);
                shapes.setColor(HULL_COLUMN_COLOR);
                shapes.rect(left, bottom, cell, cell);
                drawHatch(x, y, left, bottom, pulse);
            }
        }

        for (int x = 0; x < BattleState.WIDTH; x++) {
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                BattleSide occupant = state.getCrewAt(x, y);
                if (occupant == null) {
                    continue;
                }
                float cx = cellLeft(x) + cell / 2f;
                float cy = cellBottom(y) + cell / 2f;
                shapes.setColor(0f, 0f, 0f, 0.6f);
                shapes.circle(cx, cy, cell * 0.38f, 24);
                shapes.setColor(occupant == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
                shapes.circle(cx, cy, cell * 0.32f, 24);
            }
        }
    }

    /**
     * The two ships the net is strung between. Each one is a planked hull running from just above the
     * top of the board down past its bottom into the water, with the mast, sail and pennant above it.
     */
    private void drawShips() {
        drawShip(true);
        drawShip(false);
    }

    private void drawShip(boolean player) {
        // The ship stands half a cell off the net, so the horizontal strands show as ropes running from
        // the net across to the hull. A narrow screen leaves the ship less room than the sketch asks
        // for, so the hull shrinks rather than running off the edge.
        float edge = player ? cellLeft(1) : cellLeft(BattleState.WIDTH - 1);
        float out = player ? -1f : 1f;
        float near = edge + shipShift(player ? BattleSide.PLAYER : BattleSide.ENEMY);
        float room = player ? near - margin : width - margin - near;
        float span = Math.max(cell, Math.min(cell * HULL_TOP_CELLS, room - cell * 0.05f));
        float deckY = boardY + cell * (BattleState.HEIGHT + DECK_LIFT_CELLS);
        // The water in front of the hull is shallow, so the keel stops at the bottom of the scene rather
        // than reaching into the log line underneath it.
        float keelY = Math.max(sceneBottom + cell * 0.02f, boardY - cell * KEEL_DROP_CELLS);
        float farDeck = near + out * span;
        float farKeel = near + out * span * (HULL_KEEL_CELLS / HULL_TOP_CELLS);
        // The outer side leans inward as it goes down, so a band's far edge depends on its height.
        float slope = (farKeel - farDeck) / (keelY - deckY);

        for (int i = 0; i < PLANK_BANDS.length; i++) {
            float yTop = deckY - (deckY - boardY) * PLANK_STOPS[i];
            float yBottom = deckY - (deckY - boardY) * PLANK_STOPS[i + 1];
            hullBand(near, farDeck + slope * (yTop - deckY), farDeck + slope * (yBottom - deckY),
                    yTop, yBottom, PLANK_BANDS[i]);
        }
        hullBand(near, farDeck + slope * (boardY - deckY), farKeel, boardY, keelY, KEEL_COLOR);

        // Rigging: a mast with a square sail hung on it and the side's pennant at the top. It is sized to
        // the sky left above the deck, so on a short screen the mast shortens instead of reaching the text.
        float sky = Math.max(cell * 0.4f, sceneTop - deckY);
        float rig = Math.min(RIGGING_CELLS, sky / cell * 0.90f) * cell;
        float mastX = near + out * span * 0.42f;
        float half = span * 0.21f;

        drawSailPanel(mastX, half, deckY + rig * 0.06f, deckY + rig * 0.42f);
        drawSailPanel(mastX, half, deckY + rig * 0.46f, deckY + rig * 0.76f);

        // The spar the upper sail hangs from, then the mast splitting both sails, then the pennant.
        shapes.setColor(HULL_COLUMN_COLOR);
        shapes.rect(mastX - half - cell * 0.02f, deckY + rig * 0.40f,
                2f * half + cell * 0.04f, Math.max(1f, rig * 0.09f));
        shapes.rect(mastX - cell * 0.022f, deckY, cell * 0.044f, rig * 0.94f);

        shapes.setColor(player ? PLAYER_COLOR : ENEMY_COLOR);
        shapes.triangle(mastX, deckY + rig, mastX, deckY + rig * 0.78f,
                mastX - out * half, deckY + rig * 0.89f);
    }

    /** One plank of the hull: a band between two heights, with the width of its far edge given. */
    private void hullBand(float near, float farTop, float farBottom, float yTop, float yBottom, Color shade) {
        shapes.setColor(shade);
        shapes.triangle(near, yTop, farTop, yTop, farBottom, yBottom);
        shapes.triangle(near, yTop, farBottom, yBottom, near, yBottom);
    }

    /** A square sail hung on the mast, drawn as the trapezoid its left and right halves make. */
    private void drawSailPanel(float mastX, float halfWidth, float bottom, float top) {
        float inset = halfWidth * 0.2f;
        shapes.setColor(SAIL_COLOR);
        shapes.triangle(mastX - halfWidth, bottom + inset, mastX - halfWidth, top - inset, mastX, top);
        shapes.triangle(mastX - halfWidth, bottom + inset, mastX, top, mastX, bottom);
        shapes.triangle(mastX, bottom, mastX, top, mastX + halfWidth, top - inset);
        shapes.triangle(mastX, bottom, mastX + halfWidth, top - inset, mastX + halfWidth, bottom + inset);
    }

    /**
     * The boarding net strung between the two hulls: one rope along every grid line, so a mesh opening
     * is exactly one cell wide and a unit stands in the middle of an opening, never on a knot where two
     * strands cross. The openings show the sea and the sky behind them. The horizontal ropes run past
     * the outermost grid lines, across the gap to the ships, and tuck under their hulls.
     */
    private void drawNet() {
        float thickness = cell * STRAND_THICKNESS;
        float reach = cell * STRAND_REACH;
        float left = cellLeft(1);
        float right = cellLeft(BattleState.WIDTH - 1);
        float bottom = cellBottom(BattleState.HEIGHT - 1);
        float top = cellBottom(0) + cell;

        for (int line = 0; line <= BattleState.HEIGHT; line++) {
            strand(left - reach, bottom + line * cell - thickness / 2f, right - left + 2f * reach, thickness);
        }
        for (int line = 1; line < BattleState.WIDTH; line++) {
            strand(cellLeft(line) - thickness / 2f, bottom, thickness, top - bottom);
        }

        for (int x = 0; x < BattleState.WIDTH; x++) {
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                if (state.isHole(x, y)) {
                    drawHole(x, y);
                }
            }
        }
    }

    /** One strand, with a darker outline so the mesh stays readable against the bright sea. */
    private void strand(float x, float y, float w, float h) {
        float edge = cell * STRAND_EDGE;
        shapes.setColor(ROPE_SHADE);
        shapes.rect(x - edge, y - edge, w + 2f * edge, h + 2f * edge);
        shapes.setColor(ROPE_COLOR);
        shapes.rect(x, y, w, h);
    }

    /** A torn opening: the backdrop shows through it, ringed by rope and crossed by slack strands. */
    private void drawHole(int x, int y) {
        float cx = cellLeft(x) + cell / 2f;
        float cy = cellBottom(y) + cell / 2f;
        float radius = cell * HOLE_RADIUS;

        shapes.setColor(GAP_COLOR);
        shapes.circle(cx, cy, radius, 28);
        backdropAt(cy, tint);
        shapes.setColor(tint);
        shapes.circle(cx, cy, radius * 0.86f, 28);

        shapes.setColor(ROPE_COLOR);
        for (int i = 0; i < HOLE_SPOKES; i++) {
            float angle = (float) (i * Math.PI / HOLE_SPOKES) + 0.5f;
            shapes.rect(cx + (float) Math.cos(angle) * radius * 0.8f,
                    cy + (float) Math.sin(angle) * radius * 0.8f, cell * 0.16f, cell * 0.030f);
        }
    }

    /** Whatever the background shows at this height: the sea below the horizon, the sky above it. */
    private void backdropAt(float y, Color out) {
        if (y < horizonY) {
            seaColorAt(y, out);
        } else {
            skyColorAt(y, out);
        }
    }

    private void drawHatch(int x, int y, float left, float bottom, float pulse) {
        int index = BattleState.hatchIndexForRow(y);
        if (index < 0) {
            return;
        }
        BattleSide owner = x == 0 ? BattleSide.PLAYER : BattleSide.ENEMY;
        float inset = cell * 0.12f;
        if (state.isHatchDisabled(owner, index)) {
            shapes.setColor(0.35f, 0.35f, 0.35f, 1f);
        } else if (owner == BattleSide.PLAYER) {
            if (openHatches[index]) {
                float brightness = 0.85f + 0.15f * pulse;
                shapes.setColor(0.98f * brightness, 0.79f * brightness, 0.28f * brightness, 1f);
            } else {
                boolean ready = phase == Phase.PLAYER_TURN && state.canRelease(BattleSide.PLAYER, index);
                float brightness = ready ? pulse : 0.6f;
                shapes.setColor(PLAYER_COLOR.r * brightness, PLAYER_COLOR.g * brightness, PLAYER_COLOR.b * brightness, 1f);
            }
        } else {
            shapes.setColor(ENEMY_COLOR.r * 0.8f, ENEMY_COLOR.g * 0.8f, ENEMY_COLOR.b * 0.8f, 1f);
        }
        shapes.rect(left + inset, bottom + inset, cell - 2 * inset, cell - 2 * inset);

        if (owner == BattleSide.PLAYER && openHatches[index]) {
            drawEntryMarker(bottom);
        }

        // One small marker per pirate still waiting inside; drawn outside the hatch, from its top down
        int waiting = state.getHatchCrew(owner, index);
        if (waiting <= 0) {
            return;
        }
        float cx = owner == BattleSide.PLAYER ? left - cell * 0.36f : left + cell * 1.36f;
        float top = bottom + cell * 0.82f;
        float step = cell * 0.22f;
        shapes.setColor(owner == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
        for (int i = 0; i < waiting; i++) {
            shapes.circle(cx, top - i * step, cell * 0.08f, 12);
        }
    }

    /** Shows the mesh opening an open hatch will push its pirate onto. */
    private void drawEntryMarker(float bottom) {
        int entryX = BattleState.entryColumn(BattleSide.PLAYER);
        float ex = cellLeft(entryX) + cell / 2f;
        float ey = bottom + cell / 2f;
        shapes.setColor(0.98f, 0.79f, 0.28f, 1f);
        shapes.circle(ex, ey, cell * 0.44f, 24);
        backdropAt(ey, tint);
        shapes.setColor(tint);
        shapes.circle(ex, ey, cell * 0.34f, 24);
    }

    private void drawControls() {
        boolean active = phase == Phase.PLAYER_TURN
                && (hasOpenHatches() ? state.canDeploy(BattleSide.PLAYER, openHatches)
                        : state.canJump(BattleSide.PLAYER));
        float shade = active ? 1f : 0.45f;
        float half = buttonSize / 2f;
        for (JumpDirection direction : JumpDirection.values()) {
            float bx = compassX;
            float by = compassY - direction.getDy() * buttonSize;
            shapes.setColor(0.2f * shade, 0.2f * shade, 0.28f * shade, 1f);
            shapes.rect(bx - half * 0.92f, by - half * 0.92f, buttonSize * 0.92f, buttonSize * 0.92f);

            float dirX = direction.getDx(BattleSide.PLAYER);
            float dirY = -direction.getDy();
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            dirX /= length;
            dirY /= length;
            float perpX = -dirY;
            float perpY = dirX;
            float r = buttonSize * 0.3f;
            shapes.setColor(0.95f * shade, 0.8f * shade, 0.35f * shade, 1f);
            shapes.triangle(
                    bx + dirX * r, by + dirY * r,
                    bx - dirX * r * 0.6f + perpX * r * 0.7f, by - dirY * r * 0.6f + perpY * r * 0.7f,
                    bx - dirX * r * 0.6f - perpX * r * 0.7f, by - dirY * r * 0.6f - perpY * r * 0.7f);
        }

        float fleeShade = phase == Phase.PLAYER_TURN ? 1f : 0.45f;
        shapes.setColor(0.45f * fleeShade, 0.12f * fleeShade, 0.12f * fleeShade, 1f);
        shapes.rect(fleeX - fleeHalfWidth, fleeY - fleeHalfHeight, fleeHalfWidth * 2f, fleeHalfHeight * 2f);
    }

    private void drawTexts() {
        float gap = lineHeight * 0.12f;
        float y = height - margin;

        font.setColor(PLAYER_COLOR);
        y = drawBlock(playerLine(), margin, y, textWidth);

        font.setColor(ENEMY_COLOR);
        y = drawBlock(enemyLine(), margin, y - gap, textWidth);

        font.setColor(phase == Phase.FINISHED || hasOpenHatches() ? Color.GOLD : Color.LIGHT_GRAY);
        y = drawBlock(turnLine(), margin, y - gap, textWidth);

        drawBlock(hintLine(), margin, y - gap, textWidth);

        font.setColor(Color.WHITE);
        drawBlockBottom(message, margin, textWidth);
        drawFleeLabel();
    }

    private String playerLine() {
        return "TY (" + playerShip.getName() + "): rezerwa " + state.getReserve(BattleSide.PLAYER)
                + ", na sieci " + state.getCrewOnNet(BattleSide.PLAYER)
                + ", luki " + (BattleState.HATCH_ROWS.length - state.getDisabledHatches(BattleSide.PLAYER))
                + "/" + BattleState.HATCH_ROWS.length;
    }

    private String enemyLine() {
        return "WROG (" + enemyShip.getName() + "): rezerwa " + state.getReserve(BattleSide.ENEMY)
                + ", na sieci " + state.getCrewOnNet(BattleSide.ENEMY)
                + ", luki " + (BattleState.HATCH_ROWS.length - state.getDisabledHatches(BattleSide.ENEMY))
                + "/" + BattleState.HATCH_ROWS.length;
    }

    private String turnLine() {
        return "Tura " + (state.getTurnsPlayed() + 1) + "/" + BattleState.MAX_TURNS + " - " + statusText();
    }

    private String hintLine() {
        return "otwarte luki: " + openHatchCount() + " (tap = otworz/zamknij)";
    }

    private String statusText() {
        switch (phase) {
            case PLAYER_TURN: return hasOpenHatches() ? "Twoja tura: wyprowadzenie" : "Twoja tura: skok";
            case ENEMY_TURN: return "Tura wroga...";
            default: return resultText() + " - dotknij, aby kontynuowac";
        }
    }

    private String resultText() {
        switch (outcome) {
            case VICTORY: return "ZWYCIESTWO!";
            case DEFEAT: return "PORAZKA";
            case DRAW: return "REMIS";
            default: return "UCIECZKA";
        }
    }

    private float drawBlock(String text, float x, float y, float targetWidth) {
        layout.setText(font, text, font.getColor(), targetWidth, Align.left, true);
        font.draw(batch, layout, x, y);
        return y - layout.height;
    }

    /** Height the text needs when wrapped into {@code targetWidth}. */
    private float measure(String text, float targetWidth) {
        layout.setText(font, text, Color.WHITE, targetWidth, Align.left, true);
        return layout.height;
    }

    /** Draws the log line so that it grows upwards from just above the bottom edge. */
    private void drawBlockBottom(String text, float x, float targetWidth) {
        layout.setText(font, text, font.getColor(), targetWidth, Align.left, true);
        font.draw(batch, layout, x, margin + Math.min(layout.height, bottomBand));
    }

    /** Draws "UCIECZKA" in the middle of the button, shrinking it only if the button is too narrow. */
    private void drawFleeLabel() {
        layout.setText(font, "UCIECZKA");
        float maxWidth = fleeHalfWidth * 2f - lineHeight * 0.4f;
        if (layout.width > maxWidth && layout.width > 0f) {
            font.getData().setScale(textScale * (maxWidth / layout.width));
            drawCentered("UCIECZKA", fleeX, fleeY);
            font.getData().setScale(textScale);
            return;
        }
        drawCentered("UCIECZKA", fleeX, fleeY);
    }

    private void drawCentered(String text, float centerX, float centerY) {
        layout.setText(font, text);
        font.draw(batch, layout, centerX - layout.width / 2f, centerY + layout.height / 2f);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
