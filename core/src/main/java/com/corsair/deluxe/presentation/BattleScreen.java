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

    // =============================================================================================
    // BATTLE SCREEN LOOK - PARAMETERS
    //
    // The whole scene is drawn procedurally with ShapeRenderer: no XML layout, no texture atlas, so
    // every size, position and colour of the design is a knob in this block. The knobs are grouped
    // by the element they belong to and each one says what it moves. The same list, as a table with
    // the geometry formulas, the drawing order and recipes for common changes, is in
    // .meta/battle-screen.md.
    //
    // Pipeline: layout() picks a text scale, computeGeometry() turns these numbers into pixels
    // (cell, boardX, boardY, sceneTop, sceneBottom, horizonY, ...), then draw() paints the elements
    // from the back: sea and sky, net, ships, deck squares with hatches, crew, controls, texts.
    //
    // Units: "cells" is a multiple of one net opening, the side of a field. Anything that has to
    // keep its proportions when the board is resized is a fraction of a cell. Screen coordinates are
    // pixels, (0, 0) in the bottom left corner, Y growing upwards.
    // =============================================================================================

    // --- 1. Screen frame and text bands ----------------------------------------------------------

    /** Cells smaller than this make the board unreadable, so the text scale is reduced instead. */
    private static final float MIN_CELL = 26f;
    /** Text scale is 'screen height / this', so the text keeps its size relative to the board. */
    private static final float TEXT_SCALE_HEIGHT = 760f;
    /** The text never shrinks below this scale, even on a screen too small for the board. */
    private static final float MIN_TEXT_SCALE = 0.8f;
    /** Scales tried in order; the first one that keeps the cells at least MIN_CELL big wins. */
    private static final float[] TEXT_SCALE_STEPS = { 2f, 1.6f, 1.3f, 1f };
    /** Gap between two text blocks, as a fraction of the line height. */
    private static final float TEXT_GAP = 0.12f;
    /** Frame margin around the whole screen: at least this many pixels, or this share of the short side. */
    private static final float MARGIN_MIN = 12f;
    private static final float MARGIN_FRACTION = 0.02f;
    /** Half a pixel: the centre of a one-pixel row, used when sampling the sky and the sea ramps. */
    private static final float PIXEL_CENTRE = 0.5f;
    /** Longest variant of the turn line, so the top text band never has to grow. */
    private static final String WORST_TURN_LINE = "Tura " + BattleState.MAX_TURNS + "/" + BattleState.MAX_TURNS
            + " - PORAZKA - dotknij, aby kontynuowac";
    /** Longest realistic battle log entry; it is the text the bottom band is sized for. */
    private static final String LOG_RESERVE =
            "Wrog: wyprowadzenie 5 pir., pokonani: 5, wpadli: 5, zablokowane luki: 5, wycofani: 5";
    /** The bottom band is at least this many lines tall, plus this much air above the log line. */
    private static final float LOG_BAND_LINES = 1.6f;
    private static final float LOG_BAND_PAD = 0.4f;
    /** The column of arrows beside the board takes this share of the width, and this many lines. */
    private static final float ARROW_ZONE_WIDTH = 0.14f;
    private static final float ARROW_ZONE_LINES = 2.4f;
    /** An arrow button is this share of the side panel wide, and this share of the free height tall. */
    private static final float COMPASS_BUTTON_WIDTH = 3.4f;
    private static final float COMPASS_BUTTON_HEIGHT = 0.24f;
    /** The flee button: half its width is this many buttons, but never less than this share of the
     * side panel; half its height is this many buttons, but never less than this many lines. */
    private static final float FLEE_HALF_WIDTH_BUTTONS = 1.5f;
    private static final float FLEE_HALF_WIDTH_OF_PANEL = 0.45f;
    private static final float FLEE_HALF_HEIGHT_BUTTONS = 0.4f;
    private static final float FLEE_HALF_HEIGHT_LINES = 0.75f;
    /** How far under the compass the flee button sits, measured in button sizes. */
    private static final float FLEE_DROP_BUTTONS = 2.2f;
    /** The whole scene stands on this colour, so no gap between the bands ever shows through. */
    private static final Color BACKDROP = color(20, 13, 18);
    /** Each side owns a colour, used for its texts, hatches, crew, waiting markers and pennant. */
    private static final Color PLAYER_COLOR = new Color(0.25f, 0.55f, 1f, 1f);
    private static final Color ENEMY_COLOR = new Color(0.9f, 0.2f, 0.2f, 1f);

    // --- 2. Scene composition (in cells) ---------------------------------------------------------

    /** How far the masts reach above the top deck, and how far the water reaches in front of the hulls. */
    private static final float MAST_BAND = 2f;
    private static final float SEA_DEPTH = 1.30f;
    /** The net between the ships is drawn this much larger than the plain fit would give it. */
    private static final float NET_SCALE = 1.2f;
    /** Half a hull of room beside the board on each side, and how high the horizon cuts across it. */
    private static final float SHIP_ROOM = 1.10f;
    private static final float HORIZON_HEIGHT_CELLS = 2.55f;

    // --- 3. Sky ----------------------------------------------------------------------------------

    /** Sky ramp: hazy and pale along the horizon (t = 0), deep blue overhead (t = 1). */
    private static final float[] SKY_POS = { 0f, 0.07f, 0.20f, 0.42f, 0.70f, 1f };
    private static final float[][] SKY_RGB = {
            { 206, 227, 245 }, { 176, 208, 239 }, { 138, 182, 229 },
            { 100, 152, 216 }, { 66, 115, 194 }, { 38, 76, 152 } };
    /** Rows are one pixel tall with a hair of overlap, so no seam shows between them. */
    private static final float SKY_ROW_HEIGHT = 1.02f;
    /** The haze over the horizon: how high it reaches, how strong it is, how fast it fades. */
    private static final float HAZE_HEIGHT = 0.70f;
    private static final float HAZE_STRENGTH = 0.30f;
    private static final float HAZE_FALLOFF = 1.5f;
    private static final Color HAZE_COLOR = color(238, 245, 253);
    /** A bright line drawn right on the horizon: how thick, and in what colour. */
    private static final float HORIZON_RIM_HEIGHT = 0.028f;
    private static final Color HORIZON_RIM_COLOR = color(176, 205, 231);

    // --- 4. Sun ----------------------------------------------------------------------------------

    /** Where the sun sits: this share of the width across, this high above the horizon. */
    private static final float SUN_X = 0.30f;
    private static final float SUN_HEIGHT = 0.55f;
    /** The glow: this many rings reaching this far out, the outermost all but invisible. */
    private static final int SUN_GLOW_RINGS = 26;
    private static final float SUN_GLOW_RADIUS = 0.14f;
    private static final float SUN_GLOW_ALPHA = 2.4f;
    private static final float SUN_GLOW_ALPHA_GROWTH = 26f;
    private static final Color SUN_COLOR = color(255, 244, 205);
    /** The halo: this many faint rings out to this radius. */
    private static final int SUN_DISC_STEPS = 5;
    private static final float SUN_DISC_RADIUS = 0.40f;
    private static final float SUN_DISC_ALPHA = 62f;
    private static final Color SUN_DISC_COLOR = color(255, 252, 236);
    /** The core is the only fully opaque part of the sun. */
    private static final float SUN_CORE_RADIUS = 0.16f;
    private static final Color SUN_CORE_COLOR = color(255, 253, 240);

    // --- 5. Clouds -------------------------------------------------------------------------------

    /** Clouds in the sky: x and y as shares of the sky, radius in cells, and alpha 0..255. */
    private static final float[][] CLOUD_LAYOUT = {
            { 0.13f, 0.52f, 0.70f, 240f }, { 0.47f, 0.90f, 0.52f, 205f },
            { 0.74f, 0.26f, 0.86f, 235f }, { 0.93f, 0.70f, 0.46f, 190f },
            { 0.60f, 0.62f, 0.30f, 150f } };
    /** Puff layout of a cloud, as offsets and half-sizes relative to the cloud radius. */
    private static final float[][] CLOUD_PUFFS = {
            { -0.72f, -0.02f, 0.50f, 0.30f }, { -0.30f, 0.12f, 0.60f, 0.40f },
            { 0.22f, 0.06f, 0.58f, 0.38f }, { 0.74f, -0.04f, 0.42f, 0.26f } };
    /** A puff is this many rings around a solid core, each ring this much bigger and fainter. */
    private static final int PUFF_RINGS = 2;
    private static final float PUFF_RING_BASE = 0.78f;
    private static final float PUFF_RING_STEP = 0.11f;
    private static final float PUFF_RING_ALPHA = 0.34f;
    /** The lit cap sits this far above the shaded body and is this much smaller than it. */
    private static final float PUFF_LIFT = 0.14f;
    private static final float PUFF_LIT_WIDTH = 0.84f;
    private static final float PUFF_LIT_HEIGHT = 0.78f;
    /** How much of a cloud's alpha the shaded body gets; the lit cap gets all of it. */
    private static final float CLOUD_SHADE_ALPHA = 0.70f;
    /** A puff is a ring of this many segments. */
    private static final int PUFF_SEGMENTS = 28;
    private static final Color CLOUD_LIT = color(252, 253, 255);
    private static final Color CLOUD_BASE = color(178, 196, 224);

    // --- 6. Sea ----------------------------------------------------------------------------------

    /** Sea ramp: a bright band at the horizon (t = 0), deepening towards the viewer (t = 1). */
    private static final float[] SEA_POS = { 0f, 0.11f, 0.24f, 0.40f, 0.58f, 0.79f, 1f };
    private static final float[][] SEA_RGB = {
            { 138, 173, 206 }, { 108, 144, 188 }, { 84, 122, 170 }, { 60, 92, 144 },
            { 44, 68, 116 }, { 32, 50, 92 }, { 22, 35, 68 } };
    /** Rows are one pixel tall with a hair of overlap, so no seam shows between them. */
    private static final float SEA_ROW_HEIGHT = 1.02f;
    /** Wave rows from the horizon down: how many, and how fast they bunch up (an exponent, so a double). */
    private static final int WAVE_ROWS = 96;
    private static final double WAVE_FALLOFF = 1.8;
    /** A wave mark is this thick and this long at the horizon, and this much more close by. */
    private static final float WAVE_THICKNESS = 0.010f;
    private static final float WAVE_THICKNESS_GROWTH = 0.062f;
    private static final float WAVE_SPAN = 0.18f;
    private static final float WAVE_SPAN_GROWTH = 1.90f;
    /** The gap between two marks of a row, and how much wider it gets towards the viewer. */
    private static final float WAVE_GAP = 1.1f;
    private static final float WAVE_GAP_GROWTH = 1.5f;
    /** Crest rows are pale, trough rows dark; both get stronger closer to the viewer. */
    private static final float WAVE_CREST_ALPHA = 34f;
    private static final float WAVE_CREST_ALPHA_GROWTH = 104f;
    private static final float WAVE_TROUGH_ALPHA = 58f;
    private static final float WAVE_TROUGH_ALPHA_GROWTH = 126f;
    private static final Color CREST_COLOR = color(152, 190, 224);
    private static final Color TROUGH_COLOR = color(14, 24, 52);
    /** The sun's path on the water: how many flecks, how they bunch up and how they fade and spread. */
    private static final int GLITTER_COUNT = 190;
    private static final double GLITTER_FALLOFF = 1.7;
    private static final float GLITTER_ALPHA = 168f;
    private static final float GLITTER_FADE = 126f;
    private static final float GLITTER_WIDTH = 0.05f;
    private static final float GLITTER_WIDTH_GROWTH = 0.62f;
    private static final float GLITTER_THICKNESS = 0.011f;
    private static final float GLITTER_THICKNESS_GROWTH = 2.4f;
    private static final float GLITTER_SPREAD = 0.08f;
    private static final float GLITTER_SPREAD_GROWTH = 1.35f;
    /** Loose foam in the foreground, to break the regularity of the wave rows. */
    private static final int FOAM_COUNT = 70;
    private static final float FOAM_DEPTH = 0.72f;
    private static final float FOAM_DEPTH_SPREAD = 0.28f;
    private static final float FOAM_WIDTH = 0.18f;
    private static final float FOAM_WIDTH_SPREAD = 0.55f;
    private static final float FOAM_ALPHA = 40f;
    private static final float FOAM_ALPHA_SPREAD = 70f;
    private static final float FOAM_THICKNESS = 0.018f;
    private static final Color FOAM_COLOR = color(214, 232, 245);
    /** The sea and the sky look the same on every frame, so their scatter is drawn from a fixed seed. */
    private static final long SEA_SEED = 20261007L;

    // --- 7. Ships (hull and rigging) -------------------------------------------------------------

    /**
     * The hull: one piece of timber from the gunwale down past the waterline, and the deck squares the
     * hatches sit in. The ships are drawn over the ends of the net, so the strands end on the hull.
     */
    private static final Color HULL_COLOR = color(107, 66, 36);
    /** The sail, hung on the mast above the hull. */
    private static final Color SAIL_COLOR = color(196, 186, 158);
    /** How wide the hull is at the deck and at the keel, with both sides leaning in by the same amount. */
    private static final float HULL_TOP_CELLS = 2.14f;
    private static final float HULL_KEEL_CELLS = 1.62f;
    /** The deck sits this far above the top of the board, and the keel this far below its bottom. */
    private static final float DECK_LIFT_CELLS = 0.10f;
    private static final float KEEL_DROP_CELLS = 0.50f;
    /** The whole ship, deck squares included, stands this far off the net. */
    private static final float SHIP_GAP_CELLS = 0.50f;
    /** A hull shrinks rather than running into the edge of the screen: this much of a cell stays clear. */
    private static final float SHIP_FIT_MARGIN = 0.05f;
    /** The keel never dips below this height above the bottom of the scene. */
    private static final float KEEL_FLOOR = 0.02f;
    /**
     * The rigging runs from the deck up to the top of the sky band and a little past it, so only its
     * foot shows: the scene mask wipes off everything above the sky, leaving the deck with the bottom
     * of the mast and the lower part of one sail.
     */
    private static final float RIGGING_MIN_SKY = 0.4f;
    /** How far past the top of the sky the rigging reaches, as a share of a cell. */
    private static final float RIGGING_OVERFLOW = 0.5f;
    /** Where the mast stands on the hull, as a share of the hull's width from its net-side edge. */
    private static final float MAST_POSITION = 0.42f;
    /** Half-width of the sail, as a share of the hull's width. */
    private static final float SAIL_HALF_WIDTH = 0.36f;
    /** How far the sail is pulled in at its corners, as a share of its half-width. */
    private static final float SAIL_INSET = 0.2f;
    /**
     * The single sail, as shares of the rigging height above the deck: its foot hangs low over the
     * deck, and its head sits above the sky so the scene mask cuts the sail off flat at the top.
     */
    private static final float SAIL_BOTTOM = 0.05f;
    private static final float SAIL_TOP = 0.95f;
    /** The mast: how thick it is, and how far up the rigging it reaches. */
    private static final float MAST_THICKNESS = 0.044f;
    private static final float MAST_TOP = 1f;

    // --- 8. Net (rope and torn openings) ---------------------------------------------------------

    /** Thickness of a strand, and of the dark outline that keeps it readable over the sea. */
    private static final float STRAND_THICKNESS = 0.04f;
    private static final float STRAND_EDGE = 0.012f;
    /** The horizontal strands cross the gap to the hull and tuck this far under it. */
    private static final float STRAND_TUCK = 0.06f;
    private static final float STRAND_REACH = SHIP_GAP_CELLS + STRAND_TUCK;
    /** A torn opening: its radius, and how many slack strands cross it. */
    private static final float HOLE_RADIUS = 0.38f;
    private static final int HOLE_SPOKES = 6;
    /** Inside the torn rim the backdrop shows through, over this much of the radius. */
    private static final float HOLE_BACKDROP = 0.86f;
    /** The slack strands: how far out they sit, how long and thick they are, and their twist. */
    private static final float HOLE_SPOKE_REACH = 0.8f;
    private static final float HOLE_SPOKE_LENGTH = 0.16f;
    private static final float HOLE_SPOKE_THICKNESS = 0.030f;
    private static final float HOLE_SPOKE_TWIST = 0.5f;
    /** The opening is a circle of this many segments. */
    private static final int HOLE_SEGMENTS = 28;
    private static final Color ROPE_COLOR = color(217, 214, 199);
    private static final Color ROPE_SHADE = color(133, 128, 117);
    /** What shows through a torn opening: the dark gap of a piece missing from the net. */
    private static final Color GAP_COLOR = color(10, 13, 23);

    // --- 9. Hatches, crew and markers ------------------------------------------------------------

    /** A pirate on the net: a dark shadow disc under a solid one. */
    private static final float CREW_SHADOW_RADIUS = 0.38f;
    private static final Color CREW_SHADOW_COLOR = new Color(0f, 0f, 0f, 0.6f);
    private static final float CREW_RADIUS = 0.32f;
    /** A crew pip is a circle of this many segments. */
    private static final int CREW_SEGMENTS = 24;
    /** How much of a cell the hatch plate leaves as a frame around itself. */
    private static final float HATCH_INSET = 0.12f;
    /** A destroyed hatch is this flat grey; the rest keep their side's colour. */
    private static final Color HATCH_DISABLED_COLOR = new Color(0.35f, 0.35f, 0.35f, 1f);
    /** An open hatch glows gold and pulses between these two brightnesses. */
    private static final Color HATCH_OPEN_COLOR = new Color(0.98f, 0.79f, 0.28f, 1f);
    private static final float HATCH_OPEN_BRIGHTNESS = 0.85f;
    private static final float HATCH_OPEN_PULSE = 0.15f;
    /** A closed hatch sits this dark; a ready one pulses, and the enemy's are always dimmed. */
    private static final float HATCH_CLOSED_BRIGHTNESS = 0.6f;
    private static final float HATCH_ENEMY_BRIGHTNESS = 0.8f;
    /** The tap target of a hatch is widened by this many cells on each side. */
    private static final float HATCH_TAP_REACH = 1f;
    /** Waiting crew are ticked off just outside the hatch: this far out, this high, this far apart. */
    private static final float WAITING_MARKER_INSET = 0.36f;
    private static final float WAITING_MARKER_TOP = 0.82f;
    private static final float WAITING_MARKER_STEP = 0.22f;
    private static final float WAITING_MARKER_RADIUS = 0.08f;
    /** A waiting marker is a circle of this many segments. */
    private static final int WAITING_MARKER_SEGMENTS = 12;
    /** The ready-to-release pulse: base, swing and speed (radians per second) of the sine. */
    private static final float HATCH_PULSE_BASE = 0.65f;
    private static final float HATCH_PULSE_SWING = 0.35f;
    private static final float HATCH_PULSE_SPEED = 5f;
    /** The ring showing which mesh opening an open hatch will push its pirate onto. */
    private static final float ENTRY_MARKER_RADIUS = 0.44f;
    private static final float ENTRY_MARKER_INNER = 0.34f;
    /** The entry marker is a circle of this many segments. */
    private static final int ENTRY_MARKER_SEGMENTS = 24;

    // --- 10. Controls ----------------------------------------------------------------------------

    /** An arrow button: the plate behind it, the arrow on it, and how dim it is when it cannot act. */
    private static final float BUTTON_FACE = 0.92f;
    private static final Color BUTTON_FACE_COLOR = new Color(0.2f, 0.2f, 0.28f, 1f);
    private static final float ARROW_RADIUS = 0.3f;
    private static final float ARROW_TAIL = 0.6f;
    private static final float ARROW_WING = 0.7f;
    private static final Color ARROW_COLOR = new Color(0.95f, 0.8f, 0.35f, 1f);
    private static final float CONTROL_INACTIVE = 0.45f;
    /** Padding left and right of "UCIECZKA", as a fraction of the line height. */
    private static final float FLEE_LABEL_PAD = 0.4f;
    /** The flee button is a plain dark red plate with its label on top. */
    private static final Color FLEE_COLOR = new Color(0.45f, 0.12f, 0.12f, 1f);
    // --- end of look parameters ------------------------------------------------------------------

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
        margin = Math.max(MARGIN_MIN, Math.min(w, h) * MARGIN_FRACTION);

        // The battle screen used to run at 'h / 760', which is barely readable on a phone, so the text is
        // drawn twice as large. On very small screens the board would not fit any more, so the multiplier
        // drops until the cells are usable again.
        float[] multipliers = TEXT_SCALE_STEPS;
        for (int i = 0; i < multipliers.length; i++) {
            computeGeometry(Math.max(MIN_TEXT_SCALE, h / TEXT_SCALE_HEIGHT) * multipliers[i]);
            if (cell >= MIN_CELL || i == multipliers.length - 1) {
                break;
            }
        }
    }

    private void computeGeometry(float textScale) {
        this.textScale = textScale;
        font.getData().setScale(textScale);
        lineHeight = font.getLineHeight();
        float gap = lineHeight * TEXT_GAP;

        // Full-width text bands above and below the board; the board and the arrows share the middle.
        // Both bands are sized for the longest text they can ever show, never for the current one.
        textWidth = Math.max(40f, width - 2 * margin);
        topBand = measure(playerLine(), textWidth) + measure(enemyLine(), textWidth)
                + Math.max(measure(turnLine(), textWidth), measure(WORST_TURN_LINE, textWidth))
                + measure(hintLine(), textWidth) + gap * 3f;
        bottomBand = Math.max(lineHeight * LOG_BAND_LINES, measure(LOG_RESERVE, textWidth) + lineHeight * LOG_BAND_PAD);
        float arrowZone = Math.max(width * ARROW_ZONE_WIDTH, lineHeight * ARROW_ZONE_LINES);
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
        horizonY = sceneBottom + cell * (HORIZON_HEIGHT_CELLS + SEA_DEPTH) / netScale;
        skyHeight = Math.max(1f, sceneTop - horizonY);
        seaHeight = Math.max(1f, horizonY - sceneBottom);

        float rightEdge = boardX + boardWidth;
        float rightWidth = width - margin - rightEdge;
        compassX = rightEdge + rightWidth / 2f;
        compassY = boardY + cell * BattleState.HEIGHT / 2f;
        buttonSize = Math.min(rightWidth / COMPASS_BUTTON_WIDTH, usableH * COMPASS_BUTTON_HEIGHT);

        fleeHalfWidth = Math.max(buttonSize * FLEE_HALF_WIDTH_BUTTONS, rightWidth * FLEE_HALF_WIDTH_OF_PANEL);
        fleeHalfHeight = Math.max(buttonSize * FLEE_HALF_HEIGHT_BUTTONS, lineHeight * FLEE_HALF_HEIGHT_LINES);
        fleeX = compassX;
        fleeY = Math.max(boardY + fleeHalfHeight, compassY - buttonSize * FLEE_DROP_BUTTONS);
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

        // The tap target is widened by HATCH_TAP_REACH cells on each side to make hatches easy to hit
        float left = cellLeft(BattleState.hatchColumn(BattleSide.PLAYER));
        for (int i = 0; i < BattleState.HATCH_ROWS.length; i++) {
            float bottom = cellBottom(BattleState.HATCH_ROWS[i]);
            if (touchX >= left - cell * HATCH_TAP_REACH && touchX <= left + cell * (1f + HATCH_TAP_REACH)
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
        drawSceneMask();
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
            shapes.rect(0f, y, width, SKY_ROW_HEIGHT);
        }
        for (float y = horizonY - 1f; y >= sceneBottom; y -= 1f) {
            seaColorAt(y, tint);
            shapes.setColor(tint);
            shapes.rect(0f, y, width, SEA_ROW_HEIGHT);
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
        float hazeHeight = cell * HAZE_HEIGHT;
        for (float y = horizonY; y < horizonY + hazeHeight; y += 1f) {
            float t = (y + PIXEL_CENTRE - horizonY) / hazeHeight;
            float f = HAZE_STRENGTH * (float) Math.pow(Math.max(0f, 1f - t), HAZE_FALLOFF);
            skyColorAt(y, tint);
            shapes.setColor(tint.r + (HAZE_COLOR.r - tint.r) * f,
                    tint.g + (HAZE_COLOR.g - tint.g) * f,
                    tint.b + (HAZE_COLOR.b - tint.b) * f, 1f);
            shapes.rect(0f, y, width, SKY_ROW_HEIGHT);
        }
        shapes.setColor(HORIZON_RIM_COLOR);
        shapes.rect(0f, horizonY - cell * HORIZON_RIM_HEIGHT, width, cell * HORIZON_RIM_HEIGHT);
    }

    /** The sun, low over the water: a broad glow, a halo and a small disc. */
    private void drawSun() {
        float sunX = width * SUN_X;
        float sunY = horizonY + cell * SUN_HEIGHT;
        for (int i = SUN_GLOW_RINGS; i >= 1; i--) {
            float fade = 1f - i / (float) SUN_GLOW_RINGS;
            shapes.setColor(SUN_COLOR.r, SUN_COLOR.g, SUN_COLOR.b,
                    (SUN_GLOW_ALPHA + SUN_GLOW_ALPHA_GROWTH * fade * fade) / 255f);
            shapes.circle(sunX, sunY, cell * SUN_GLOW_RADIUS * i, 32);
        }
        shapes.setColor(SUN_DISC_COLOR.r, SUN_DISC_COLOR.g, SUN_DISC_COLOR.b, SUN_DISC_ALPHA / 255f);
        for (int i = SUN_DISC_STEPS; i >= 1; i--) {
            shapes.circle(sunX, sunY, cell * SUN_DISC_RADIUS * i / SUN_DISC_STEPS, 32);
        }
        shapes.setColor(SUN_CORE_COLOR);
        shapes.circle(sunX, sunY, cell * SUN_CORE_RADIUS, 32);
    }

    private void drawClouds() {
        for (float[] c : CLOUD_LAYOUT) {
            cloud(width * c[0], horizonY + skyHeight * c[1], cell * c[2], c[3] / 255f);
        }
    }

    /** A cluster of puffs, shaded underneath and lit on top. */
    private void cloud(float cx, float cy, float radius, float alpha) {
        for (float[] puff : CLOUD_PUFFS) {
            puff(cx + puff[0] * radius, cy + puff[1] * radius,
                    puff[2] * radius, puff[3] * radius, CLOUD_BASE, alpha * CLOUD_SHADE_ALPHA);
        }
        for (float[] puff : CLOUD_PUFFS) {
            puff(cx + puff[0] * radius, cy + (puff[1] + PUFF_LIFT) * radius,
                    puff[2] * PUFF_LIT_WIDTH * radius, puff[3] * PUFF_LIT_HEIGHT * radius, CLOUD_LIT, alpha);
        }
    }

    /** ring==0 is the solid core, the outer rings fade it out; libGDX has no blur to soften it with. */
    private void puff(float cx, float cy, float rx, float ry, Color color, float alpha) {
        for (int ring = PUFF_RINGS; ring >= 0; ring--) {
            float k = PUFF_RING_BASE + PUFF_RING_STEP * ring;
            shapes.setColor(color.r, color.g, color.b, ring == 0 ? alpha : alpha * PUFF_RING_ALPHA);
            shapes.ellipse(cx - rx * k, cy - ry * k, rx * 2f * k, ry * 2f * k, PUFF_SEGMENTS);
        }
    }

    /** Wave rows, dense and flat at the horizon and long and broken near the bottom of the frame. */
    private void drawWaves() {
        // The last multipliers of every mark are its scatter; they stay here, with the drawing.
        for (int i = 0; i < WAVE_ROWS; i++) {
            float t = (float) Math.pow((i + 0.5f) / WAVE_ROWS, WAVE_FALLOFF);
            float y = horizonY - seaHeight * t;
            float thickness = Math.max(1f, cell * (WAVE_THICKNESS + WAVE_THICKNESS_GROWTH * t));
            float span = cell * (WAVE_SPAN + WAVE_SPAN_GROWTH * t);
            float gap = span * (WAVE_GAP + WAVE_GAP_GROWTH * (1f - t));
            Color color = i % 2 == 0 ? CREST_COLOR : TROUGH_COLOR;
            float alpha = (i % 2 == 0 ? WAVE_CREST_ALPHA + WAVE_CREST_ALPHA_GROWTH * t
                    : WAVE_TROUGH_ALPHA + WAVE_TROUGH_ALPHA_GROWTH * t) / 255f;
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
        for (int i = 0; i < GLITTER_COUNT; i++) {
            float t = (float) Math.pow((i + 0.5f) / GLITTER_COUNT, GLITTER_FALLOFF);
            float y = horizonY - seaHeight * t;
            float alpha = (GLITTER_ALPHA - GLITTER_FADE * t) * (0.35f + 0.65f * seaRandom.nextFloat());
            if (alpha <= 0f) {
                continue;
            }
            float w = cell * (GLITTER_WIDTH + GLITTER_WIDTH_GROWTH * t) * (0.45f + seaRandom.nextFloat());
            float cx = sunX + (seaRandom.nextFloat() * 2f - 1f) * cell * (GLITTER_SPREAD + GLITTER_SPREAD_GROWTH * t);
            shapes.setColor(SUN_COLOR.r, SUN_COLOR.g, SUN_COLOR.b, alpha / 255f);
            shapes.rect(cx - w / 2f, y, w, Math.max(1f, cell * GLITTER_THICKNESS * (1f + GLITTER_THICKNESS_GROWTH * t)));
        }
    }

    /** Loose foam in the foreground, to break the regularity of the wave rows. */
    private void drawFoam() {
        for (int i = 0; i < FOAM_COUNT; i++) {
            float y = horizonY - seaHeight * (FOAM_DEPTH + FOAM_DEPTH_SPREAD * seaRandom.nextFloat());
            float w = cell * (FOAM_WIDTH + FOAM_WIDTH_SPREAD * seaRandom.nextFloat());
            float x = seaRandom.nextFloat() * width;
            shapes.setColor(FOAM_COLOR.r, FOAM_COLOR.g, FOAM_COLOR.b,
                    (FOAM_ALPHA + FOAM_ALPHA_SPREAD * seaRandom.nextFloat()) / 255f);
            shapes.rect(x, y, w, Math.max(1f, cell * FOAM_THICKNESS));
        }
    }

    private void skyColorAt(float y, Color out) {
        ramp(SKY_POS, SKY_RGB, (y + PIXEL_CENTRE - horizonY) / skyHeight, out);
    }

    private void seaColorAt(float y, Color out) {
        ramp(SEA_POS, SEA_RGB, (horizonY - y - PIXEL_CENTRE) / seaHeight, out);
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
        float pulse = HATCH_PULSE_BASE + HATCH_PULSE_SWING * (float) Math.sin(elapsed * HATCH_PULSE_SPEED);

        // The net is the playfield, so it goes down first: the two hull columns drawn over it cover the
        // ends of the strands, right where the rope is lashed to the ship.
        drawNet();
        // The ships go over the net as well, so the strand ends land on the hull; the deck squares then
        // go over the hull, leaving the hull and the rigging visible outside the board.
        drawShips();

        for (int x = 0; x < BattleState.WIDTH; x++) {
            if (x != hatchColumnPlayer && x != hatchColumnEnemy) {
                continue;
            }
            // The deck squares are part of the ship, so they stand off the net with the hull.
            float left = cellLeft(x) + shipShift(x == hatchColumnPlayer ? BattleSide.PLAYER : BattleSide.ENEMY);
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                float bottom = cellBottom(y);
                shapes.setColor(HULL_COLOR);
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
                shapes.setColor(CREW_SHADOW_COLOR);
                shapes.circle(cx, cy, cell * CREW_SHADOW_RADIUS, CREW_SEGMENTS);
                shapes.setColor(occupant == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
                shapes.circle(cx, cy, cell * CREW_RADIUS, CREW_SEGMENTS);
            }
        }
    }

    /**
     * Wipes off everything above the sky band with the backdrop colour. The rigging is drawn tall
     * enough to leave the scene, so this is what cuts the masts and the sails off flat at the top of
     * the sky, just under the text band. The sky itself stops at the same line, so the mask only ever
     * covers bare backdrop.
     */
    private void drawSceneMask() {
        shapes.setColor(BACKDROP);
        shapes.rect(0f, sceneTop, width, Math.max(0f, height - sceneTop));
    }

    /**
     * The two ships the net is strung between. Each one is a wooden hull, symmetric about its own
     * centre, running from just above the top of the board down past its bottom into the water, with
     * the foot of its rigging standing on the deck.
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
        float span = Math.max(cell, Math.min(cell * HULL_TOP_CELLS, room - cell * SHIP_FIT_MARGIN));
        float deckY = boardY + cell * (BattleState.HEIGHT + DECK_LIFT_CELLS);
        // The water in front of the hull is shallow, so the keel stops at the bottom of the scene rather
        // than reaching into the log line underneath it.
        float keelY = Math.max(sceneBottom + cell * KEEL_FLOOR, boardY - cell * KEEL_DROP_CELLS);
        float farDeck = near + out * span;
        // The sides run straight down to the water and then lean in by the same amount on either side,
        // so the hull is symmetric about its own centre.
        float keelInset = span * (1f - HULL_KEEL_CELLS / HULL_TOP_CELLS) / 2f;

        // The hull is a single piece of timber, with neither planking bands nor a waterline stripe to
        // break it up.
        hullBand(near, near, farDeck, farDeck, deckY, boardY, HULL_COLOR);
        hullBand(near, near + out * keelInset, farDeck, farDeck - out * keelInset,
                boardY, keelY, HULL_COLOR);

        // Rigging: only the foot of it is on screen. The mast runs from the deck to the top of the sky
        // and on past it, carrying one square sail whose foot hangs low over the deck; the scene mask
        // then cuts mast, sail and everything above them off at the top of the sky band.
        float sky = Math.max(cell * RIGGING_MIN_SKY, sceneTop - deckY);
        float rig = sky + cell * RIGGING_OVERFLOW;
        float mastX = near + out * span * MAST_POSITION;
        float half = span * SAIL_HALF_WIDTH;

        drawSailPanel(mastX, half, deckY + rig * SAIL_BOTTOM, deckY + rig * SAIL_TOP);

        shapes.setColor(HULL_COLOR);
        shapes.rect(mastX - cell * MAST_THICKNESS / 2f, deckY, cell * MAST_THICKNESS, rig * MAST_TOP);
    }

    /** One slice of the hull: a band between two heights, with both edges given at each height. */
    private void hullBand(float nearTop, float nearBottom, float farTop, float farBottom,
            float yTop, float yBottom, Color shade) {
        shapes.setColor(shade);
        shapes.triangle(nearTop, yTop, farTop, yTop, farBottom, yBottom);
        shapes.triangle(nearTop, yTop, farBottom, yBottom, nearBottom, yBottom);
    }

    /** A square sail hung on the mast, drawn as the trapezoid its left and right halves make. */
    private void drawSailPanel(float mastX, float halfWidth, float bottom, float top) {
        float inset = halfWidth * SAIL_INSET;
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
        shapes.circle(cx, cy, radius, HOLE_SEGMENTS);
        backdropAt(cy, tint);
        shapes.setColor(tint);
        shapes.circle(cx, cy, radius * HOLE_BACKDROP, HOLE_SEGMENTS);

        shapes.setColor(ROPE_COLOR);
        for (int i = 0; i < HOLE_SPOKES; i++) {
            float angle = (float) (i * Math.PI / HOLE_SPOKES) + HOLE_SPOKE_TWIST;
            shapes.rect(cx + (float) Math.cos(angle) * radius * HOLE_SPOKE_REACH,
                    cy + (float) Math.sin(angle) * radius * HOLE_SPOKE_REACH,
                    cell * HOLE_SPOKE_LENGTH, cell * HOLE_SPOKE_THICKNESS);
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
        float inset = cell * HATCH_INSET;
        if (state.isHatchDisabled(owner, index)) {
            shapes.setColor(HATCH_DISABLED_COLOR);
        } else if (owner == BattleSide.PLAYER) {
            if (openHatches[index]) {
                float brightness = HATCH_OPEN_BRIGHTNESS + HATCH_OPEN_PULSE * pulse;
                shapes.setColor(HATCH_OPEN_COLOR.r * brightness, HATCH_OPEN_COLOR.g * brightness,
                        HATCH_OPEN_COLOR.b * brightness, 1f);
            } else {
                boolean ready = phase == Phase.PLAYER_TURN && state.canRelease(BattleSide.PLAYER, index);
                float brightness = ready ? pulse : HATCH_CLOSED_BRIGHTNESS;
                shapes.setColor(PLAYER_COLOR.r * brightness, PLAYER_COLOR.g * brightness, PLAYER_COLOR.b * brightness, 1f);
            }
        } else {
            shapes.setColor(ENEMY_COLOR.r * HATCH_ENEMY_BRIGHTNESS, ENEMY_COLOR.g * HATCH_ENEMY_BRIGHTNESS,
                    ENEMY_COLOR.b * HATCH_ENEMY_BRIGHTNESS, 1f);
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
        float cx = owner == BattleSide.PLAYER ? left - cell * WAITING_MARKER_INSET : left + cell * (1f + WAITING_MARKER_INSET);
        float top = bottom + cell * WAITING_MARKER_TOP;
        float step = cell * WAITING_MARKER_STEP;
        shapes.setColor(owner == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
        for (int i = 0; i < waiting; i++) {
            shapes.circle(cx, top - i * step, cell * WAITING_MARKER_RADIUS, WAITING_MARKER_SEGMENTS);
        }
    }

    /** Shows the mesh opening an open hatch will push its pirate onto. */
    private void drawEntryMarker(float bottom) {
        int entryX = BattleState.entryColumn(BattleSide.PLAYER);
        float ex = cellLeft(entryX) + cell / 2f;
        float ey = bottom + cell / 2f;
        shapes.setColor(HATCH_OPEN_COLOR);
        shapes.circle(ex, ey, cell * ENTRY_MARKER_RADIUS, ENTRY_MARKER_SEGMENTS);
        backdropAt(ey, tint);
        shapes.setColor(tint);
        shapes.circle(ex, ey, cell * ENTRY_MARKER_INNER, ENTRY_MARKER_SEGMENTS);
    }

    private void drawControls() {
        boolean active = phase == Phase.PLAYER_TURN
                && (hasOpenHatches() ? state.canDeploy(BattleSide.PLAYER, openHatches)
                        : state.canJump(BattleSide.PLAYER));
        float shade = active ? 1f : CONTROL_INACTIVE;
        float half = buttonSize / 2f;
        for (JumpDirection direction : JumpDirection.values()) {
            float bx = compassX;
            float by = compassY - direction.getDy() * buttonSize;
            shapes.setColor(BUTTON_FACE_COLOR.r * shade, BUTTON_FACE_COLOR.g * shade,
                    BUTTON_FACE_COLOR.b * shade, 1f);
            shapes.rect(bx - half * BUTTON_FACE, by - half * BUTTON_FACE,
                    buttonSize * BUTTON_FACE, buttonSize * BUTTON_FACE);

            float dirX = direction.getDx(BattleSide.PLAYER);
            float dirY = -direction.getDy();
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            dirX /= length;
            dirY /= length;
            float perpX = -dirY;
            float perpY = dirX;
            float r = buttonSize * ARROW_RADIUS;
            shapes.setColor(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
            shapes.triangle(
                    bx + dirX * r, by + dirY * r,
                    bx - dirX * r * ARROW_TAIL + perpX * r * ARROW_WING,
                    by - dirY * r * ARROW_TAIL + perpY * r * ARROW_WING,
                    bx - dirX * r * ARROW_TAIL - perpX * r * ARROW_WING,
                    by - dirY * r * ARROW_TAIL - perpY * r * ARROW_WING);
        }

        float fleeShade = phase == Phase.PLAYER_TURN ? 1f : CONTROL_INACTIVE;
        shapes.setColor(FLEE_COLOR.r * fleeShade, FLEE_COLOR.g * fleeShade, FLEE_COLOR.b * fleeShade, 1f);
        shapes.rect(fleeX - fleeHalfWidth, fleeY - fleeHalfHeight, fleeHalfWidth * 2f, fleeHalfHeight * 2f);
    }

    private void drawTexts() {
        float gap = lineHeight * TEXT_GAP;
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
        float maxWidth = fleeHalfWidth * 2f - lineHeight * FLEE_LABEL_PAD;
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
