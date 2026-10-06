package com.corsair.deluxe.presentation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.corsair.deluxe.CorsairGame;
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
    private static final int AI_DEPTH = 3;

    private static final Color PLAYER_COLOR = new Color(0.25f, 0.55f, 1f, 1f);
    private static final Color ENEMY_COLOR = new Color(0.9f, 0.2f, 0.2f, 1f);

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
    private final BattleAI ai = new BattleAI(random, AI_DEPTH);
    private final BattleState state;
    private final int initialPlayerUnits;
    /** Hatches the player opened for the next muster; opening and closing them is free. */
    private final boolean[] openHatches = new boolean[BattleState.HATCH_ROWS.length];

    private Phase phase = Phase.PLAYER_TURN;
    private BattleOutcome outcome = BattleOutcome.ONGOING;
    private String message = "Tapnij luk (lewa kolumna) aby go otworzyc, potem strzalka wyprowadza zaloge.";
    private float phaseTime;
    private float elapsed;
    private boolean leaving;

    private float width;
    private float height;
    private float boardX;
    private float boardY;
    private float cell;
    private float compassX;
    private float compassY;
    private float buttonSize;
    private float fleeX;
    private float fleeY;

    public BattleScreen(CorsairGame game, WorldScreen returnScreen, Ship playerShip, Ship enemyShip) {
        this.game = game;
        this.returnScreen = returnScreen;
        this.playerShip = playerShip;
        this.enemyShip = enemyShip;

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
        cell = Math.min(h * 0.85f / BattleState.HEIGHT, w * 0.55f / BattleState.WIDTH);
        float boardWidth = cell * BattleState.WIDTH;
        boardX = (w - boardWidth) / 2f;
        boardY = (h - cell * BattleState.HEIGHT) / 2f;
        font.getData().setScale(Math.max(0.8f, h / 760f));

        float rightEdge = boardX + boardWidth;
        float rightWidth = w - rightEdge;
        compassX = rightEdge + rightWidth / 2f;
        buttonSize = Math.min(rightWidth / 3.4f, h * 0.17f);
        compassY = h * 0.55f;
        fleeX = compassX;
        fleeY = compassY - buttonSize * 2.2f;
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

        if (Math.abs(touchX - fleeX) < buttonSize * 1.5f && Math.abs(touchY - fleeY) < buttonSize * 0.4f) {
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
            message = "Ty: " + describe(action, result) + " (luki zamkniete)";
        } else {
            message = "Ty: " + describe(action, result);
        }
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
            text.append("wyprowadzenie zalogi: ").append(result.getDeployed()).append(" pir.");
        } else {
            text.append("skok: ").append(directionName(action.getDirection()));
        }
        if (result.getKilled() > 0) {
            text.append(", pokonani: ").append(result.getKilled());
        }
        if (result.getFell() > 0) {
            text.append(", wpadli w dziure: ").append(result.getFell());
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

    private void draw() {
        ScreenUtils.clear(0.08f, 0.05f, 0.07f, 1f);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBoard();
        drawControls();
        shapes.end();

        batch.begin();
        drawTexts();
        batch.end();
    }

    private void drawBoard() {
        int hatchColumnPlayer = BattleState.hatchColumn(BattleSide.PLAYER);
        int hatchColumnEnemy = BattleState.hatchColumn(BattleSide.ENEMY);
        float pulse = 0.65f + 0.35f * (float) Math.sin(elapsed * 5f);

        for (int x = 0; x < BattleState.WIDTH; x++) {
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                float left = cellLeft(x);
                float bottom = cellBottom(y);
                boolean hatchColumn = x == hatchColumnPlayer || x == hatchColumnEnemy;

                setTerrainColor(x, y);
                shapes.rect(left, bottom, cell, cell);

                if (hatchColumn) {
                    drawHatch(x, y, left, bottom, pulse);
                }
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

    private void setTerrainColor(int x, int y) {
        boolean hatchColumn = x == BattleState.hatchColumn(BattleSide.PLAYER)
                || x == BattleState.hatchColumn(BattleSide.ENEMY);
        if (hatchColumn) {
            shapes.setColor(0.25f, 0.16f, 0.1f, 1f);
        } else if (state.isHole(x, y)) {
            shapes.setColor(0f, 0f, 0f, 1f);
        } else if ((x + y) % 2 == 0) {
            shapes.setColor(0.67f, 0.5f, 0.3f, 1f);
        } else {
            shapes.setColor(0.6f, 0.43f, 0.25f, 1f);
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
            drawEntryMarker(y, bottom);
        }

        // One small marker per pirate still waiting inside; drawn outside the hatch, from its top down
        int waiting = state.getHatchCrew(owner, index);
        if (waiting <= 0) {
            return;
        }
        float boardRight = boardX + cell * BattleState.WIDTH;
        float cx = owner == BattleSide.PLAYER ? boardX - cell * 0.36f : boardRight + cell * 0.36f;
        float top = bottom + cell * 0.82f;
        float step = cell * 0.22f;
        shapes.setColor(owner == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
        for (int i = 0; i < waiting; i++) {
            shapes.circle(cx, top - i * step, cell * 0.08f, 12);
        }
    }

    /** Shows the net square an open hatch will push its pirate onto. */
    private void drawEntryMarker(int row, float bottom) {
        int entryX = BattleState.entryColumn(BattleSide.PLAYER);
        float ex = cellLeft(entryX) + cell / 2f;
        float ey = bottom + cell / 2f;
        shapes.setColor(0.98f, 0.79f, 0.28f, 1f);
        shapes.circle(ex, ey, cell * 0.44f, 24);
        setTerrainColor(entryX, row);
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
        shapes.rect(fleeX - buttonSize * 1.5f, fleeY - buttonSize * 0.4f, buttonSize * 3f, buttonSize * 0.8f);
    }

    private void drawTexts() {
        float margin = 20f;
        float leftWidth = Math.max(60f, boardX - 2 * margin);
        float y = height - margin;

        font.setColor(PLAYER_COLOR);
        y = drawBlock("TY (" + playerShip.getName() + ")\nrezerwa: " + state.getReserve(BattleSide.PLAYER)
                + "\nna sieci: " + state.getCrewOnNet(BattleSide.PLAYER)
                + "\nluki: " + (BattleState.HATCH_ROWS.length - state.getDisabledHatches(BattleSide.PLAYER))
                + "/" + BattleState.HATCH_ROWS.length, margin, y, leftWidth);

        font.setColor(ENEMY_COLOR);
        y = drawBlock("WROG (" + enemyShip.getName() + ")\nrezerwa: " + state.getReserve(BattleSide.ENEMY)
                + "\nna sieci: " + state.getCrewOnNet(BattleSide.ENEMY)
                + "\nluki: " + (BattleState.HATCH_ROWS.length - state.getDisabledHatches(BattleSide.ENEMY))
                + "/" + BattleState.HATCH_ROWS.length, margin, y - 20f, leftWidth);

        font.setColor(Color.LIGHT_GRAY);
        y = drawBlock("Tura " + (state.getTurnsPlayed() + 1) + "/" + BattleState.MAX_TURNS, margin, y - 20f, leftWidth);

        font.setColor(hasOpenHatches() ? Color.GOLD : Color.LIGHT_GRAY);
        y = drawBlock("otwarte luki: " + openHatchCount() + " (tap = otworz/zamknij)", margin, y - 20f, leftWidth);

        font.setColor(Color.WHITE);
        drawBlock(message, margin, y - 20f, leftWidth);

        float rightX = boardX + cell * BattleState.WIDTH + margin;
        float rightWidth = Math.max(60f, width - rightX - margin);
        font.setColor(Color.GOLD);
        drawBlock(statusText(), rightX, height - margin, rightWidth);

        font.setColor(Color.WHITE);
        drawCentered("UCIECZKA", fleeX, fleeY);
    }

    private String statusText() {
        switch (phase) {
            case PLAYER_TURN: return hasOpenHatches() ? "Twoja tura: wyprowadzenie" : "Twoja tura: skok";
            case ENEMY_TURN: return "Tura wroga...";
            default: return resultText() + "\nDotknij, aby kontynuowac";
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
