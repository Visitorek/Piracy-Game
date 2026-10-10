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
 * to open them, then tap the middle of the compass to muster one pirate out of every open hatch - the
 * hatches close again by themselves - or, with all hatches closed, to make the whole crew on the net
 * jump. The compass needle points the way; tapping the dial sets it.
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
    /** The column of controls beside the board takes this share of the width, and this many lines. */
    private static final float ARROW_ZONE_WIDTH = 0.14f;
    private static final float ARROW_ZONE_LINES = 2.4f;
    /** The side panel takes this many cells of the scene box, so the controls stop crowding the board. */
    private static final float PANEL_RESERVE_CELLS = 1f;
    /** The compass dial's radius is this many cells, capped by the strip of sea it stands in. */
    private static final float COMPASS_RADIUS_CELLS = 1.6f;
    /** Shares of the dial radius: the rim ring and the GO button in the middle. */
    private static final float COMPASS_RIM_RING = 0.055f;
    private static final float COMPASS_GO_RADIUS = 0.34f;
    /** Each direction arrow sits outside the rim: a short stem off the dial's edge and a head, so the
     * whole compass reaches this many dial radii from the centre. The stem starts on the rim itself,
     * so no sliver of sea opens up between the dial and the arrows. */
    private static final float COMPASS_ARROW_INNER = 1f;
    private static final float COMPASS_ARROW_HEAD = 1.10f;
    private static final float COMPASS_ARROW_TIP = 1.28f;
    /** Half the width of an arrow's stem, and of the base of its head. */
    private static final float COMPASS_ARROW_STEM = 0.16f;
    private static final float COMPASS_ARROW_WING = 0.21f;
    /** The needle is a flat ended bar: it reaches this far, and it is exactly as wide as an arrow's
     * stem, so the bar and the arrow beyond the rim read as one line. */
    private static final float NEEDLE_LENGTH = 0.86f;
    private static final float NEEDLE_HALF_WIDTH = COMPASS_ARROW_STEM;
    /** The cardinal letters sit this far out, and every compass label is this tall a share of the
     * dial radius, so the letters keep their place on the dial at any size. */
    private static final float COMPASS_LETTER_RING = 0.78f;
    private static final float COMPASS_LABEL_SIZE = 0.28f;
    /** How many triangles the dial, and the round GO plate, are drawn with. */
    private static final int DIAL_SEGMENTS = 48;
    private static final int MARK_SEGMENTS = 12;
    /** The flee button: half its width and half its height are this many text lines. It sits in the
     * top right corner of the scene, so it never grows wider than the side panel beside the board. */
    private static final float FLEE_HALF_WIDTH_LINES = 3f;
    private static final float FLEE_HALF_HEIGHT_LINES = 1.1f;
    /** The air between the top text band and the flee button, measured in text lines. */
    private static final float FLEE_CORNER_GAP_LINES = 0.5f;
    /** The whole scene stands on this colour, so no gap between the bands ever shows through. */
    private static final Color BACKDROP = color(20, 13, 18);
    /** Each side owns a colour, used for its texts, hatches, crew and waiting markers. */
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
     * The hull: one piece of timber from the gunwale down past the waterline, split into the two walls
     * the view shows of it. The ships are drawn over the ends of the net, so the strands end on the hull.
     */
    private static final Color HULL_COLOR = color(107, 66, 36);
    /**
     * The stern wall is a shade darker than the side, so the corner where the two walls meet reads
     * without an outline down the hull.
     */
    private static final float STERN_SHADE = 0.86f;
    private static final Color HULL_STERN_COLOR = shade(HULL_COLOR, STERN_SHADE);
    /** How much of the hull's width is the stern; the side facing the enemy takes the rest. */
    private static final float STERN_SHARE = 0.5f;
    /** How far the deck and the waterline fall away from the stern to the bow, as a share of a cell. */
    private static final float DECK_LEAN_CELLS = 0.25f;
    private static final float WATERLINE_LEAN_CELLS = 0.05f;
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
    /**
     * Half-width of the sail, as a share of the hull's width: the sail is twice this, so it comes out
     * three hulls wide and runs off the edge of the screen on the seaward side. This is the width along
     * the yard, at the head, which is as wide as the sail ever gets.
     */
    private static final float SAIL_HALF_WIDTH = 1.5f;
    /** How much narrower the foot is than the head, as a share of the half-width: the leaches lean in. */
    private static final float SAIL_CLEW_INSET = 0.06f;
    /**
     * How far the two clews - the bottom corners of the sail - sit above the deepest point of the foot,
     * as a share of the half-width. Together with the curve of the foot this gives the sail its belly.
     */
    private static final float SAIL_CLEW_RISE = 0.2f;
    /**
     * The single sail, as shares of the rigging height above the deck: its foot hangs well clear of
     * the deck, and its head sits above the sky so the scene mask cuts the sail off flat at the top.
     */
    private static final float SAIL_BOTTOM = 0.15f;
    private static final float SAIL_TOP = 0.95f;
    /** How many segments the sagging foot of the sail is cut into. */
    private static final int SAIL_SEGMENTS = 16;
    /** The mast: how thick it is (a heavy pole, not a spar-like stick), and how far up it reaches. */
    private static final float MAST_THICKNESS = 0.176f;
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
    /**
     * A hatch sits on the side the hull shows to the enemy, and is placed from that wall: this share of
     * the wall's width at most, pushed this far back from the corner towards the stern.
     */
    private static final float HATCH_WALL_SHARE = 0.7f;
    private static final float HATCH_REAR_INSET = 0.12f;
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

    /** The compass: the dial face, and the rim, marks and needle drawn on it. */
    private static final Color BUTTON_FACE_COLOR = new Color(0.2f, 0.2f, 0.28f, 1f);
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
    private String message = "Tapnij luk, aby go otworzyc; kompas wyprowadza zaloge.";
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
    private float compassRadius;
    /** Where the compass needle points: the move that the GO button in the middle will make. */
    private JumpDirection aimDirection = JumpDirection.FORWARD;
    /** True while a finger that went down on the dial is still dragging the needle. */
    private boolean dragging;
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
        // with half a hull of room beside the board for the ships and the reserve markers. One cell of
        // that box is handed to the side panel, so the board moves left instead of the controls crowding
        // it; the panel is measured from the right edge of the board, so it takes the whole reserve.
        float sceneCells = BattleState.HEIGHT + MAST_BAND + SEA_DEPTH;
        float span = BattleState.WIDTH + 2f * SHIP_ROOM;
        float boxW = usableW * span / (span + PANEL_RESERVE_CELLS);
        float fit = Math.max(1f, Math.min(usableH / sceneCells, boxW / span));
        // The net is drawn NET_SCALE larger than that fit. The sky above the masts and the water in front
        // of the hulls give up the room, so the scene still fills the same box between the text bands; a
        // screen too narrow for the wider net keeps the plain fit.
        float netScale = Math.max(1f, Math.min(NET_SCALE, boxW / (span * fit)));
        // Where the board already sits at the readability floor, paying for the panel with cell size
        // would force the text scale down a step, so there the reserve is given back instead.
        if (fit * netScale < MIN_CELL) {
            boxW = usableW;
            fit = Math.max(1f, Math.min(usableH / sceneCells, boxW / span));
            netScale = Math.max(1f, Math.min(NET_SCALE, boxW / (span * fit)));
        }
        cell = fit * netScale;
        float bandCells = sceneCells / netScale - BattleState.HEIGHT;
        float mastCells = bandCells * MAST_BAND / (MAST_BAND + SEA_DEPTH);
        float seaCells = bandCells * SEA_DEPTH / (MAST_BAND + SEA_DEPTH);
        float boardWidth = cell * BattleState.WIDTH;
        float sceneWidth = cell * span;
        sceneBottom = margin + bottomBand + (usableH - cell * (BattleState.HEIGHT + bandCells)) / 2f;
        boardX = margin + (boxW - sceneWidth) / 2f + cell * SHIP_ROOM;
        boardY = sceneBottom + cell * seaCells;
        sceneTop = boardY + cell * (BattleState.HEIGHT + mastCells);
        // The horizon stays where it was, so the sky thins out evenly instead of the net rising into it.
        horizonY = sceneBottom + cell * (HORIZON_HEIGHT_CELLS + SEA_DEPTH) / netScale;
        skyHeight = Math.max(1f, sceneTop - horizonY);
        seaHeight = Math.max(1f, horizonY - sceneBottom);

        float rightEdge = boardX + boardWidth;
        float rightWidth = width - margin - rightEdge;

        // The flee button is a small plate hugging the label, pinned in the top right corner of the
        // scene, just under the text band; the panel beside the board caps how wide it may grow.
        fleeHalfWidth = Math.min(rightWidth / 2f, lineHeight * FLEE_HALF_WIDTH_LINES);
        fleeHalfHeight = lineHeight * FLEE_HALF_HEIGHT_LINES;
        fleeX = width - margin - fleeHalfWidth;
        fleeY = height - margin - topBand - lineHeight * FLEE_CORNER_GAP_LINES - fleeHalfHeight;

        // The compass dial stands in the middle of the sea between the enemy hull and the right
        // margin, so it never sits over a ship, and it is as big as that strip of sea and the room
        // left between the top text band and the flee button allow. The strip has to hold the whole
        // compass, arrows included, hence the division by the reach of an arrow.
        float enemyFar = hullNear(false) + hullSpan(false);
        compassX = (enemyFar + width - margin) / 2f;
        compassY = boardY + cell * BattleState.HEIGHT / 2f;
        float freeHalf = (width - margin - enemyFar) / 2f;
        compassRadius = Math.min(cell * COMPASS_RADIUS_CELLS, Math.min(freeHalf,
                Math.min(fleeY - fleeHalfHeight - compassY, compassY - (margin + topBand)))
                / COMPASS_ARROW_TIP);
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
        if (!Gdx.input.isTouched()) {
            dragging = false;
            return;
        }
        float touchX = Gdx.input.getX() * width / Gdx.graphics.getWidth();
        float touchY = height - Gdx.input.getY() * height / Gdx.graphics.getHeight();

        // Dragging the needle only aims it: the move itself is made by the GO button in the middle.
        if (dragging && !Gdx.input.justTouched()) {
            aimDirection = nearestDirection(touchX - compassX, touchY - compassY);
            return;
        }
        if (!Gdx.input.justTouched()) {
            return;
        }

        if (Math.abs(touchX - fleeX) < fleeHalfWidth && Math.abs(touchY - fleeY) < fleeHalfHeight) {
            flee();
            return;
        }

        float dialX = touchX - compassX;
        float dialY = touchY - compassY;
        float dialDistance = (float) Math.sqrt(dialX * dialX + dialY * dialY);
        if (dialDistance <= compassRadius * COMPASS_GO_RADIUS) {
            playerArrow(aimDirection);
            return;
        }
        if (dialDistance <= compassRadius) {
            dragging = true;
            aimDirection = nearestDirection(dialX, dialY);
            return;
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
     * Snaps a tap inside the dial to one of the three moves the crew may make. Screen y grows upwards
     * while JumpDirection.dy grows downwards, so a positive angle here is a move up the screen.
     */
    private JumpDirection nearestDirection(float dx, float dy) {
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (degrees > 22.5f) {
            return JumpDirection.FORWARD_UP;
        }
        if (degrees < -22.5f) {
            return JumpDirection.FORWARD_DOWN;
        }
        return JumpDirection.FORWARD;
    }

    /**
     * With at least one hatch open the compass musters the crew out of every open hatch (the hatches then
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
            message = "Luk " + (hatchIndex + 1) + " otwarty - kompas wyprowadzi zaloge.";
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

    /** Mustering the crew slams every hatch shut again, so the next use of the compass is a jump. */
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

    /**
     * Where the hull of one side starts: half a cell off the net, on the edge cell of the board.
     */
    private float hullNear(boolean player) {
        float edge = player ? cellLeft(1) : cellLeft(BattleState.WIDTH - 1);
        return edge + shipShift(player ? BattleSide.PLAYER : BattleSide.ENEMY);
    }

    /**
     * How wide that hull is. A narrow screen leaves it less room than the sketch asks for, so the hull
     * shrinks rather than running off the edge.
     */
    private float hullSpan(boolean player) {
        float near = hullNear(player);
        float room = player ? near - margin : width - margin - near;
        return Math.max(cell, Math.min(cell * HULL_TOP_CELLS, room - cell * SHIP_FIT_MARGIN));
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

        // The needle crosses the cardinal letters, so the part of it past the GO button is painted a
        // second time, on top of them. The button and its label sit inside that radius and stay clear.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float needleShade = controlShade();
        shapes.setColor(ARROW_COLOR.r * needleShade, ARROW_COLOR.g * needleShade,
                ARROW_COLOR.b * needleShade, 1f);
        drawNeedle(COMPASS_GO_RADIUS);
        shapes.end();
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
        float pulse = HATCH_PULSE_BASE + HATCH_PULSE_SWING * (float) Math.sin(elapsed * HATCH_PULSE_SPEED);

        // The net is the playfield, so it goes down first: the two hulls drawn over it cover the ends of
        // the strands, right where the rope is lashed to the ship. The hatches then go on the hulls, so
        // they read as openings in the side the ship shows the enemy rather than as squares on the net.
        drawNet();
        drawShips();
        drawHatchColumn(true, pulse);
        drawHatchColumn(false, pulse);

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
     * The five hatches of one side, stacked down the rear of the side its hull shows to the enemy. That
     * side is the near half of the hull - the half facing the net - so the hatches are placed from its
     * wall: as wide as a share of it, and pushed back against the corner it makes with the stern.
     */
    private void drawHatchColumn(boolean player, float pulse) {
        float span = hullSpan(player);
        float wall = span * (1f - STERN_SHARE);
        float size = Math.min(wall * HATCH_WALL_SHARE, cell * (1f - 2f * HATCH_INSET));
        float out = player ? -1f : 1f;
        float corner = hullNear(player) + out * wall;
        float rear = corner - out * wall * HATCH_REAR_INSET;
        float hatchX = player ? rear : rear - size;
        BattleSide owner = player ? BattleSide.PLAYER : BattleSide.ENEMY;
        for (int y = 0; y < BattleState.HEIGHT; y++) {
            drawHatch(owner, y, hatchX, cellBottom(y) + (cell - size) / 2f, size, pulse);
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
     * The two ships the net is strung between. Each one is a wooden hull seen a little from the side and
     * from above, so it shows two walls - the side facing the net and the stern away from it - running
     * from just above the top of the board down past its bottom into the water, with the foot of its
     * rigging standing on the deck.
     */
    private void drawShips() {
        drawShip(true);
        drawShip(false);
    }

    private void drawShip(boolean player) {
        float near = hullNear(player);
        float out = player ? -1f : 1f;
        float span = hullSpan(player);
        float deckY = boardY + cell * (BattleState.HEIGHT + DECK_LIFT_CELLS);
        // The water in front of the hull is shallow, so the keel stops at the bottom of the scene rather
        // than reaching into the log line underneath it.
        float keelY = Math.max(sceneBottom + cell * KEEL_FLOOR, boardY - cell * KEEL_DROP_CELLS);
        // The hull is read from the stern to the bow, the bow being the edge the net is strung from. The
        // stern is the far half of it, the side facing the net the near half, and the deck and the
        // waterline both fall away towards the bow, so the stern stands taller than the bow.
        float far = near + out * span;
        float side = span * (1f - STERN_SHARE);
        float xCorner = near + out * side;
        float leanDeck = cell * DECK_LEAN_CELLS;
        float leanWater = cell * WATERLINE_LEAN_CELLS;
        float deckCorner = deckY - leanDeck * STERN_SHARE;
        float deckBow = deckY - leanDeck;
        float waterCorner = boardY - leanWater * STERN_SHARE;
        float waterBow = boardY - leanWater;
        float keelCorner = keelY - leanWater * STERN_SHARE;
        float keelBow = keelY - leanWater;
        // The keel is narrower than the deck at both ends of the whole hull, so the inset is spread
        // along its length rather than dropped in at the corner.
        float keelInset = span * (1f - HULL_KEEL_CELLS / HULL_TOP_CELLS) / 2f;
        float keelOut = far - out * keelInset;
        float keelMid = keelOut - out * (span - 2f * keelInset) * STERN_SHARE;
        float keelIn = near + out * keelInset;

        // The side the enemy sees, from the corner to the bow: the hull above the water, then the part
        // of it the water hides.
        hullQuad(xCorner, deckCorner, near, deckBow, near, waterBow, xCorner, waterCorner, HULL_COLOR);
        hullQuad(xCorner, waterCorner, near, waterBow, keelIn, keelBow, keelMid, keelCorner, HULL_COLOR);
        // The stern, a shade darker, drawn over the side so the corner between them stays crisp.
        hullQuad(far, deckY, xCorner, deckCorner, xCorner, waterCorner, far, boardY, HULL_STERN_COLOR);
        hullQuad(far, boardY, xCorner, waterCorner, keelMid, keelCorner, keelOut, keelY,
                HULL_STERN_COLOR);

        // Rigging: only the foot of it is on screen. A heavy mast runs from the deck to the top of the
        // sky and on past it, carrying one square sail, three hulls wide, whose foot hangs clear of the
        // deck and sags between its clews; the scene mask then cuts mast, sail and everything above them
        // off at the top of the sky.
        float mastX = near + out * span * MAST_POSITION;
        // The mast stands upright, so its foot sits on the deck line at its own place along the hull.
        float mastY = deckY - leanDeck * (1f - MAST_POSITION);
        float sky = Math.max(cell * RIGGING_MIN_SKY, sceneTop - mastY);
        float rig = sky + cell * RIGGING_OVERFLOW;
        float half = span * SAIL_HALF_WIDTH;

        drawSailPanel(mastX, half, mastY + rig * SAIL_BOTTOM, mastY + rig * SAIL_TOP);

        shapes.setColor(HULL_COLOR);
        shapes.rect(mastX - cell * MAST_THICKNESS / 2f, mastY, cell * MAST_THICKNESS, rig * MAST_TOP);
    }

    /** One wall of the hull: the four corners of a slab of timber, given in order round its edge. */
    private void hullQuad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
            Color shade) {
        shapes.setColor(shade);
        shapes.triangle(x1, y1, x2, y2, x3, y3);
        shapes.triangle(x1, y1, x3, y3, x4, y4);
    }

    /** A colour darkened (or lightened) by a factor, for the walls of one hull. */
    private static Color shade(Color base, float factor) {
        return new Color(base.r * factor, base.g * factor, base.b * factor, 1f);
    }

    /**
     * One square sail hung on the mast: a straight head along the yard, two leaches leaning in to the
     * clews, and a foot sagging between them, so the cloth reads as a sail filled with wind rather than
     * a flat panel. The foot is a cubic curve that leaves each clew along its own leach, which both
     * rounds the clews off and leaves no corner where the foot meets a leach.
     */
    private void drawSailPanel(float mastX, float halfWidth, float bottom, float top) {
        float clewHalf = halfWidth * (1f - SAIL_CLEW_INSET);
        float clewY = bottom + halfWidth * SAIL_CLEW_RISE;
        // The foot runs from one clew to the other, and with this control-point spread it bottoms out at
        // exactly `bottom` halfway across, while the curve stays symmetric about the mast.
        float lean = halfWidth - clewHalf;
        float drop = top - clewY;
        float spread = (clewY - bottom) / (0.75f * drop);
        float startX = mastX + clewHalf;
        float endX = mastX - clewHalf;
        float c1x = startX - spread * lean;
        float c1y = clewY - spread * drop;
        float c2x = endX + spread * lean;
        float c2y = clewY - spread * drop;

        shapes.setColor(SAIL_COLOR);
        // A fan struck from the middle of the head, where the mast will cover the seam. The sail is
        // convex, so the fan fills it exactly: the head itself is a line, and the triangles that carry
        // the two corners under the yard sit either side of the ones that carry the foot.
        shapes.triangle(mastX, top, mastX + halfWidth, top, startX, clewY);
        float prevX = startX;
        float prevY = clewY;
        for (int i = 1; i <= SAIL_SEGMENTS; i++) {
            float t = i / (float) SAIL_SEGMENTS;
            float u = 1f - t;
            float w0 = u * u * u;
            float w1 = 3f * u * u * t;
            float w2 = 3f * u * t * t;
            float w3 = t * t * t;
            float x = w0 * startX + w1 * c1x + w2 * c2x + w3 * endX;
            float y = w0 * clewY + w1 * c1y + w2 * c2y + w3 * clewY;
            shapes.triangle(mastX, top, prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
        shapes.triangle(mastX, top, prevX, prevY, mastX - halfWidth, top);
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

    private void drawHatch(BattleSide owner, int y, float hatchX, float hatchY, float size, float pulse) {
        int index = BattleState.hatchIndexForRow(y);
        if (index < 0) {
            return;
        }
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
        shapes.rect(hatchX, hatchY, size, size);

        if (owner == BattleSide.PLAYER && openHatches[index]) {
            drawEntryMarker(cellBottom(y));
        }

        // One small marker per pirate still waiting inside; drawn outside the hatch, from the top of its
        // row down, so the markers stay put however the hatch is placed on the hull.
        int waiting = state.getHatchCrew(owner, index);
        if (waiting <= 0) {
            return;
        }
        float cx = owner == BattleSide.PLAYER ? hatchX - cell * WAITING_MARKER_INSET
                : hatchX + size + cell * WAITING_MARKER_INSET;
        float top = cellBottom(y) + cell * WAITING_MARKER_TOP;
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

    /** The brightness of the steering controls: dimmed whenever the player may not steer. */
    private float controlShade() {
        boolean active = phase == Phase.PLAYER_TURN
                && (hasOpenHatches() ? state.canDeploy(BattleSide.PLAYER, openHatches)
                        : state.canJump(BattleSide.PLAYER));
        return active ? 1f : CONTROL_INACTIVE;
    }

    private void drawControls() {
        float shade = controlShade();
        float r = compassRadius;
        shapes.setColor(BUTTON_FACE_COLOR.r * shade, BUTTON_FACE_COLOR.g * shade,
                BUTTON_FACE_COLOR.b * shade, 1f);
        shapes.circle(compassX, compassY, r, DIAL_SEGMENTS);
        shapes.setColor(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
        shapes.circle(compassX, compassY, r, DIAL_SEGMENTS);
        shapes.setColor(BUTTON_FACE_COLOR.r * shade, BUTTON_FACE_COLOR.g * shade,
                BUTTON_FACE_COLOR.b * shade, 1f);
        shapes.circle(compassX, compassY, r * (1f - COMPASS_RIM_RING), DIAL_SEGMENTS);

        // The three arrows outside the rim are exactly the three moves the needle may point at. They
        // stay off the dial, so the cardinal letters drawn inside it keep the dial to themselves.
        shapes.setColor(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
        for (JumpDirection direction : JumpDirection.values()) {
            float markX = direction.getDx(BattleSide.PLAYER);
            float markY = -direction.getDy();
            float length = (float) Math.sqrt(markX * markX + markY * markY);
            markX /= length;
            markY /= length;
            float perpX = -markY;
            float perpY = markX;
            shapes.rectLine(
                    compassX + markX * r * COMPASS_ARROW_INNER, compassY + markY * r * COMPASS_ARROW_INNER,
                    compassX + markX * r * COMPASS_ARROW_HEAD, compassY + markY * r * COMPASS_ARROW_HEAD,
                    r * COMPASS_ARROW_STEM * 2f);
            shapes.triangle(
                    compassX + markX * r * COMPASS_ARROW_TIP, compassY + markY * r * COMPASS_ARROW_TIP,
                    compassX + markX * r * COMPASS_ARROW_HEAD + perpX * r * COMPASS_ARROW_WING,
                    compassY + markY * r * COMPASS_ARROW_HEAD + perpY * r * COMPASS_ARROW_WING,
                    compassX + markX * r * COMPASS_ARROW_HEAD - perpX * r * COMPASS_ARROW_WING,
                    compassY + markY * r * COMPASS_ARROW_HEAD - perpY * r * COMPASS_ARROW_WING);
        }

        drawNeedle(0f);
        // The GO button sits over the foot of the needle and is the whole tap target for the move.
        shapes.circle(compassX, compassY, r * COMPASS_GO_RADIUS, MARK_SEGMENTS);

        float fleeShade = phase == Phase.PLAYER_TURN ? 1f : CONTROL_INACTIVE;
        shapes.setColor(FLEE_COLOR.r * fleeShade, FLEE_COLOR.g * fleeShade, FLEE_COLOR.b * fleeShade, 1f);
        shapes.rect(fleeX - fleeHalfWidth, fleeY - fleeHalfHeight, fleeHalfWidth * 2f, fleeHalfHeight * 2f);
    }

    /**
     * The needle, a bar of one width from {@code fromShare} of the dial radius out to its tip, so it
     * ends flat instead of in a point. It is drawn twice per frame: with the dial, and once more after
     * the cardinal letters, which it crosses.
     */
    private void drawNeedle(float fromShare) {
        float dirX = aimDirection.getDx(BattleSide.PLAYER);
        float dirY = -aimDirection.getDy();
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        dirX /= length;
        dirY /= length;
        shapes.rectLine(
                compassX + dirX * compassRadius * fromShare, compassY + dirY * compassRadius * fromShare,
                compassX + dirX * compassRadius * NEEDLE_LENGTH, compassY + dirY * compassRadius * NEEDLE_LENGTH,
                compassRadius * NEEDLE_HALF_WIDTH * 2f);
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
        drawCompassLabels();
    }

    /**
     * The cardinal letters around the dial and the GO label on the button in the middle. Both are
     * sized from the dial radius rather than from the text scale, so a small dial carries small
     * letters instead of spilling over the hull beside it.
     */
    private void drawCompassLabels() {
        float ring = compassRadius * COMPASS_LETTER_RING;
        font.getData().setScale(textScale * (compassRadius * COMPASS_LABEL_SIZE) / lineHeight);
        font.setColor(Color.WHITE);
        drawCentered("N", compassX, compassY + ring);
        drawCentered("E", compassX + ring, compassY);
        drawCentered("S", compassX, compassY - ring);
        drawCentered("W", compassX - ring, compassY);
        font.setColor(Color.BLACK);
        drawCentered("GO!", compassX, compassY);
        font.getData().setScale(textScale);
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
