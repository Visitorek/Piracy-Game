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
import com.corsair.deluxe.domain.battle.MoveRecord;
import com.corsair.deluxe.domain.battle.MoveType;

import java.util.ArrayList;
import java.util.List;
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
    /**
     * Every thin line of the scene - a wave, a streak of foam, the dark edge of a hull, the seam
     * between the sails, a strand of the net - is at least this many pixels thick. ShapeRenderer runs
     * without MSAA, so a line thinner than a pixel covers no pixel centre and drops out in places; at
     * 400x800 (cell 16.85 px) a net strand of 0.04 cell is 0.67 px and the hull's edge of 0.03 cell is
     * 0.51 px, both under it. On a large screen the cell is big enough that the floor does nothing.
     */
    private static final float MIN_LINE_PX = 1f;
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
    /** Shares of the dial radius: the rim ring. */
    private static final float COMPASS_RIM_RING = 0.055f;
    /** Layout parameters of the GO button under the compass. */
    private static final float GO_HALF_WIDTH_LINES = 2.5f;
    private static final float GO_HALF_HEIGHT_LINES = 1.1f;
    private static final float GO_GAP_LINES = 0.5f;
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
    /** How high the horizon cuts across the board: through the middle of the hulls, as in the sketch. */
    private static final float HORIZON_HEIGHT_CELLS = 2.50f;

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
     * The hull: one piece of timber from the deck down to the waterline, seen at the angle of the
     * sketch. The ship is yawed 15 degrees off its axis, so the long wall facing the board runs away
     * towards the bow and the stern stands beside it. The ships are drawn over the ends of the net, so
     * the strands end on the hull.
     */
    private static final Color HULL_COLOR = color(107, 66, 36);
    /**
     * The stern face is a shade darker than the long wall, so the two faces of the hull read apart
     * even before the dark edge along their corner is drawn.
     */
    private static final float STERN_SHADE = 0.86f;
    private static final Color HULL_STERN_COLOR = shade(HULL_COLOR, STERN_SHADE);
    /**
     * Every edge of the hull carries a dark line, as on the sketch: how thick it is in cells and how
     * dark a shade of the timber it is drawn in. On the smallest screen 0.03 cell is half a pixel, so
     * the line is held up to MIN_LINE_PX - the hatch outline runs through the same hullEdge and gets
     * the floor with it.
     */
    private static final float HULL_EDGE_CELLS = 0.03f;
    private static final float HULL_EDGE_SHADE = 0.45f;
    private static final Color HULL_EDGE_COLOR = shade(HULL_COLOR, HULL_EDGE_SHADE);

    /**
     * The projection. The hull is a box HULL_LENGTH_UNITS long, HULL_BEAM_UNITS wide and
     * HULL_HEIGHT_UNITS tall - one unit being one opening of the net, so the sizes are those of
     * _graphics/ship dimensions.png - looked at from a yaw of VIEW_YAW_DEG off the ship's axis, from
     * the middle of its height and from VIEW_DISTANCE_UNITS units away, which is the view of
     * _graphics/ship view 15deg.png. Everything on a ship - hull, hatches, mast, tap targets - is
     * placed with hullScreenX/hullScreenY below, so it all sits on the same box; the formulas and the
     * drawings behind them are in .meta/battle-screen.md.
     */
    private static final float VIEW_YAW_DEG = 15f;
    private static final float VIEW_DISTANCE_UNITS = 19.5f;
    private static final float HULL_LENGTH_UNITS = 20f;
    private static final float HULL_BEAM_UNITS = 3f;
    private static final float HULL_HEIGHT_UNITS = 7f;
    private static final float SIN_YAW = (float) Math.sin(Math.toRadians(VIEW_YAW_DEG));
    private static final float COS_YAW = (float) Math.cos(Math.toRadians(VIEW_YAW_DEG));

    /** The sail, hung on the mast above the hull. */
    private static final Color SAIL_COLOR = color(196, 186, 158);
    /**
     * The sail carries the same dark line round it as the hull does: the two ships lie so close that
     * their sails overlap, and two panels of one colour would otherwise read as a single sheet. The
     * line goes over the cloth of its own sail, and the sail of the ship drawn second - the enemy -
     * goes over the player's, so the line that separates the two panels is that ship's rear leach.
     */
    private static final float SAIL_EDGE_CELLS = HULL_EDGE_CELLS;
    private static final float SAIL_EDGE_SHADE = HULL_EDGE_SHADE;
    private static final Color SAIL_EDGE_COLOR = shade(SAIL_COLOR, SAIL_EDGE_SHADE);

    /**
     * Thickness of a strand, and of the dark outline that keeps it readable over the sea. The strand
     * itself is never thinner than MIN_LINE_PX, so it cannot drop out on a small screen; the outline is
     * a border round a solid bar rather than a line of its own, and flooring it too would swell the
     * mesh to three pixels of a cell on the smallest screen. It is declared here, above the hatches,
     * because the empty strip above and below a hatch is one rope thick as well.
     */
    private static final float STRAND_THICKNESS = 0.04f;

    /**
     * The hatch column on the wall facing the board: one hatch per row of the board, laid out on the
     * ropes of the net. Going up the column the pattern is a rope, an empty strip as thick as the
     * rope, the hatch, another such strip, the next rope - so a hatch is centred in its row, and one
     * step of the column is exactly one row of the board. The focal length below turns that step into
     * one cell of the net, which is what keeps the hatches in step with the ropes between them.
     */
    private static final float HATCH_COLUMN_UNITS = 0.5f;
    /** How far a hatch runs along the hull: the unit square of the wall, as the sketch gives it. */
    private static final float HATCH_WIDTH_UNITS = 1f;
    /** One row of the board in units of the wall: six units of hull stand for the rows of the board. */
    private static final float HATCH_STEP_UNITS = 6f / BattleState.HEIGHT;
    /** The empty strip above and below a hatch: exactly the thickness of a horizontal rope. */
    private static final float HATCH_GAP_UNITS = HATCH_STEP_UNITS * STRAND_THICKNESS;
    /** The opening itself: the row, less the two strips of rope thickness flanking it. */
    private static final float HATCH_OPEN_UNITS = HATCH_STEP_UNITS - 2f * HATCH_GAP_UNITS;
    private static final int HATCH_COUNT = BattleState.HATCH_ROWS.length;
    /**
     * Where the lowest hatch starts. The column is centred on the horizon, so the strip below the
     * lowest hatch and the one above the highest are the same thickness as the ropes between them.
     */
    private static final float HATCH_MARGIN_UNITS = (HULL_HEIGHT_UNITS
            - ((HATCH_COUNT - 1) * HATCH_STEP_UNITS + HATCH_OPEN_UNITS)) / 2f;
    /** The middle of the column, and how far the view reaches at it: what the focal length is set from. */
    private static final float HATCH_COLUMN_S = HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS / 2f;
    private static final float HATCH_COLUMN_DEPTH = hullDepth(HATCH_COLUMN_S, HULL_BEAM_UNITS);
    private static final float FOCAL_CELLS = HATCH_COLUMN_DEPTH / HATCH_STEP_UNITS;

    /**
     * How far the bow of a hull reaches in towards the net from the middle of the ship, and how far its
     * stern reaches the other way. The two together are the whole spread of the hull on screen, and the
     * bow's reach is what keeps the ship off the board: the projection turns the bow towards the net,
     * so the middle of the ship stands that much further out.
     */
    private static final float HULL_BOW_CELLS = hullX(HULL_LENGTH_UNITS, HULL_BEAM_UNITS);
    private static final float HULL_STERN_CELLS = -hullX(0f, 0f);
    /** How far the rear jamb of the hatch column stands behind the middle of the ship, in board cells. */
    private static final float HATCH_REAR_CELLS = -hullX(HATCH_COLUMN_UNITS, HULL_BEAM_UNITS);
    /**
     * How much of a hatch the eye can see: the bow jamb stands that much further in towards the net
     * than the rear one. It is the width of the doorway on screen, and it is what the lashing of the net
     * is shortened by, so that the ropes stop at the far edge of the doorway instead of crossing it.
     */
    private static final float HATCH_SPAN_CELLS = hullX(HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS, HULL_BEAM_UNITS)
            - hullX(HATCH_COLUMN_UNITS, HULL_BEAM_UNITS);
    /**
     * Where the lashing is made fast: how far the rear jamb of the hatch column stands past the
     * outermost line of the net, in cells. The ship is placed from this - the net has to read as tied
     * at the hatches the units come out of, so the wood ends on the rope rather than the rope being
     * fitted to wherever the wood happens to be.
     */
    private static final float SHIP_STANDOFF_CELLS = 0.5f;
    /**
     * The lashing: how far a horizontal strand of the net runs past the outermost line of the net, in
     * cells. It is the rope left over past the last knot, and it stops on the bow jamb of the hatch, so
     * the rope ends level with the far edge of the doorway and leaves the whole opening free. The rope
     * still crosses the outermost line of the net, so it reads as made fast to the ship.
     */
    private static final float STRAND_LASH_CELLS = SHIP_STANDOFF_CELLS - HATCH_SPAN_CELLS;
    /**
     * The whole ship, deck squares included, stands this far off the net - with the rear jamb of its
     * hatch column SHIP_STANDOFF_CELLS past the outermost line of the net, so the lashings land on the
     * wood beside the doorways. The gap is therefore negative: the hulls are pulled in until they lie
     * over the outermost columns of the board, and the net is drawn over the hulls. The bow's reach
     * towards the net, the place of the hatch on the wall and that stand-off are together the whole
     * distance from the middle of the ship to the outermost line of the net, and this is what is left of
     * it here.
     */
    private static final float SHIP_GAP_CELLS = -(HULL_BOW_CELLS + HATCH_REAR_CELLS + SHIP_STANDOFF_CELLS);
    /**
     * Room beside the net for a whole hull, the strands that reach it and the water beyond its stern:
     * the gap the ship stands off - negative now that it is pulled in over the board - its bow's reach
     * towards the net, its stern's reach away from it, and the air. The board sits this far from the
     * edge of the scene. The air holds what the gap gave up: the ships moved two and three fifths of a
     * cell in towards the net, so the room - and with it the size of the board, the panel and the text -
     * stays exactly where it was.
     */
    private static final float SHIP_AIR_CELLS = 3.4507f;
    private static final float SHIP_ROOM = SHIP_GAP_CELLS + HULL_BOW_CELLS + HULL_STERN_CELLS + SHIP_AIR_CELLS;
    /**
     * The rigging runs from the deck up to the top of the sky band and a little past it, so only its
     * foot shows: the scene mask wipes off everything above the sky, leaving the deck with the bottom
     * of the mast and the lower part of one sail.
     */
    private static final float RIGGING_MIN_SKY = 0.4f;
    /** How far past the top of the sky the rigging reaches, as a share of a cell. */
    private static final float RIGGING_OVERFLOW = 0.5f;
    /** Where the mast stands on the hull, as a share of the hull's length from its stern. */
    private static final float MAST_POSITION = 0.42f;
    /**
     * Where the mast stands across the deck, in ship units: half the beam, that is the middle of the
     * deck's width. Standing on the wall facing the board instead put both masts right beside the
     * neighbouring hull, which read as masts lashed to the wrong ship.
     */
    private static final float MAST_BEAM_UNITS = HULL_BEAM_UNITS / 2f;
    /**
     * Half-width of the sail, as a share of the board's width: the sail is twice this, so it comes out
     * four fifths of the board wide and runs off the edge of the screen on the seaward side. This is
     * the width along the yard, at the head, which is as wide as the sail ever gets.
     */
    private static final float SAIL_HALF_WIDTH = 0.4f;
    /** How much narrower the foot is than the head, as a share of the half-width: the leaches lean in. */
    private static final float SAIL_CLEW_INSET = 0.06f;
    /**
     * How far the two clews - the bottom corners of the sail - sit above the deepest point of the foot,
     * as a share of the half-width. Together with the curve of the foot this gives the sail its belly.
     */
    private static final float SAIL_CLEW_RISE = 0.2f;
    /**
     * The single sail, as shares of the rigging height above the deck: its foot hangs clear of both
     * the deck and the near corner of the board, and its head sits above the sky so the scene mask
     * cuts the sail off flat at the top. Raising the foot costs sail, so this is kept as low as the
     * corner of the board allows: the mast at the middle of the deck's width stands further from the
     * eye than it did on the board-facing wall, so its foot - and the sail hung on it - comes out
     * lower on screen, and 0.25 is what it now takes to keep the foot over the top edge of the board.
     */
    private static final float SAIL_BOTTOM = 0.25f;
    private static final float SAIL_TOP = 0.95f;
    /** How many segments the sagging foot of the sail is cut into. */
    private static final int SAIL_SEGMENTS = 16;
    /** The mast: how thick it is (a heavy pole, not a spar-like stick), and how far up it reaches. */
    private static final float MAST_THICKNESS = 0.176f;
    private static final float MAST_TOP = 1f;

    // --- 8. Net (rope and torn cells) ------------------------------------------------------------

    /** How thick the dark outline round a strand is, in cells. */
    private static final float STRAND_EDGE = 0.012f;
    /**
     * A torn cell: the rope over it is broken, and what is left of it are two stubs, one hanging off
     * each knot of the cell, with a frayed end drooping from each into the gap. Nothing is painted on
     * the cell itself, so where the net lies over a hull the break shows the wood the mesh was torn
     * away from, instead of a bite out of the ship.
     */
    private static final float TORN_STUB_CELLS = 0.12f;
    private static final float TORN_FRAY_CELLS = 0.16f;
    private static final float TORN_FRAY_DROP = 0.7f;
    private static final Color ROPE_COLOR = color(217, 214, 199);
    private static final Color ROPE_SHADE = color(133, 128, 117);

    // --- 9. Hatches, crew and markers ------------------------------------------------------------

    /** A pirate on the net: a dark shadow disc under a solid one. */
    private static final float CREW_SHADOW_RADIUS = 0.38f;
    private static final Color CREW_SHADOW_COLOR = new Color(0f, 0f, 0f, 0.6f);
    private static final float CREW_RADIUS = 0.32f;
    /** A crew pip is a circle of this many segments. */
    private static final int CREW_SEGMENTS = 24;
    /** A destroyed hatch is this flat grey; the rest keep their side's colour. */
    private static final Color HATCH_DISABLED_COLOR = new Color(0.35f, 0.35f, 0.35f, 1f);
    /** The dark line round every hatch, drawn in a deeper shade of the timber of the hull. */
    private static final float HATCH_EDGE_SHADE = 0.35f;
    private static final Color HATCH_EDGE_COLOR = shade(HULL_COLOR, HATCH_EDGE_SHADE);
    /** An open hatch glows gold and pulses between these two brightnesses. */
    private static final Color HATCH_OPEN_COLOR = new Color(0.98f, 0.79f, 0.28f, 1f);
    private static final float HATCH_OPEN_BRIGHTNESS = 0.85f;
    private static final float HATCH_OPEN_PULSE = 0.15f;
    /** A closed hatch sits this dark; a ready one pulses, and the enemy's are always dimmed. */
    private static final float HATCH_CLOSED_BRIGHTNESS = 0.6f;
    private static final float HATCH_ENEMY_BRIGHTNESS = 0.8f;
    /** The tap target of a hatch is widened by this many cells on each side. */
    private static final float HATCH_TAP_REACH = 1f;
    /**
     * Waiting crew are ticked off just outside the hatch: this far out, this far apart. The column
     * hangs from the top edge of its hatch, so it follows the hatch over the hull rather than the row
     * of the board the hatch belongs to.
     */
    private static final float WAITING_MARKER_INSET = 0.36f;
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

    private enum Phase { PLAYER_TURN, ENEMY_TURN, ANIMATING, FINISHED }

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

    private static final float MOVE_ANIMATION_SECONDS = 0.5f;
    private float animTime;
    private final List<CrewAnimation> activeAnimations = new ArrayList<CrewAnimation>();

    private static final class CrewAnimation {
        final BattleSide side;
        final float startX, startY;
        final float endX, endY;
        final int endGridX, endGridY;

        CrewAnimation(BattleSide side, float startX, float startY, float endX, float endY, int endGridX, int endGridY) {
            this.side = side;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.endGridX = endGridX;
            this.endGridY = endGridY;
        }
    }

    private float hatchY(boolean player, int index) {
        float high = hatchLow(index) + HATCH_OPEN_UNITS;
        return hatchCornerY(false, high);
    }

    private void startAnimations(ActionResult result) {
        activeAnimations.clear();
        for (MoveRecord move : result.moves) {
            float startSx, startSy, endSx, endSy;
            int endGridX = move.endX;
            int endGridY = move.endY;

            if (move.type == MoveType.DEPLOY) {
                boolean isPlayer = move.side == BattleSide.PLAYER;
                startSx = hullScreenX(isPlayer, HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS / 2f, HULL_BEAM_UNITS);
                startSy = hullScreenY(HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS / 2f, HULL_BEAM_UNITS, hatchLow(move.startY) + HATCH_OPEN_UNITS / 2f);
            } else {
                startSx = cellLeft(move.startX) + cell / 2f;
                startSy = cellBottom(move.startY) + cell / 2f;
            }

            if (move.type == MoveType.HATCH_HIT) {
                boolean isPlayerHit = move.endX == 0;
                float hx = isPlayerHit ? hatchRight(true) : hatchLeft(false);
                endSx = hx;
                endSy = hatchY(isPlayerHit, BattleState.hatchIndexForRow(move.endY));
            } else {
                endSx = cellLeft(move.endX) + cell / 2f;
                endSy = cellBottom(move.endY) + cell / 2f;
            }

            activeAnimations.add(new CrewAnimation(move.side, startSx, startSy, endSx, endSy, endGridX, endGridY));
        }

        if (!activeAnimations.isEmpty()) {
            phase = Phase.ANIMATING;
            animTime = 0f;
        } else {
            afterAction();
        }
    }

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
    private BattleSide activeSide = BattleSide.PLAYER;
    /** True while a finger that went down on the dial is still dragging the needle. */
    private boolean dragging;
    private float fleeX;
    private float fleeY;
    private float fleeHalfWidth;
    private float fleeHalfHeight;
    private float goX;
    private float goY;
    private float goHalfWidth;
    private float goHalfHeight;

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
        // with room for a whole hull beside the board on each side, and the reserve markers with it. One
        // cell of that box is handed to the side panel, so the board moves left instead of the controls
        // crowding it; the panel is measured from the right edge of the board, so it takes the reserve.
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

        // The GO button sits under the compass.
        goHalfWidth = Math.min(rightWidth / 2f, lineHeight * GO_HALF_WIDTH_LINES);
        goHalfHeight = lineHeight * GO_HALF_HEIGHT_LINES;
        float goGap = lineHeight * GO_GAP_LINES;

        // The compass dial stands in the middle of the sea between the enemy hull and the right margin.
        float enemyFar = enemyHullFar();
        compassX = (enemyFar + width - margin) / 2f;
        goX = compassX;

        float freeHalf = (width - margin - enemyFar) / 2f;
        float boardCenterY = boardY + cell * BattleState.HEIGHT / 2f;
        compassY = boardCenterY + goHalfHeight + goGap / 2f;

        float topAvailable = fleeY - fleeHalfHeight - margin - compassY;
        float bottomAvailable = compassY - (goHalfHeight * 2f + goGap) - (margin + bottomBand);
        compassRadius = Math.min(cell * COMPASS_RADIUS_CELLS, Math.min(freeHalf,
                Math.min(topAvailable, bottomAvailable)));

        goY = compassY - compassRadius - goGap - goHalfHeight;
    }

    @Override
    public void render(float delta) {
        if (leaving) {
            return;
        }
        elapsed += delta;
        phaseTime += delta;
        if (phase == Phase.ANIMATING) {
            animTime += delta;
            if (animTime >= MOVE_ANIMATION_SECONDS) {
                activeAnimations.clear();
                afterAction();
            }
        }
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

        if (Math.abs(touchX - goX) < goHalfWidth && Math.abs(touchY - goY) < goHalfHeight) {
            playerArrow(aimDirection);
            return;
        }

        float dialX = touchX - compassX;
        float dialY = touchY - compassY;
        float dialDistance = (float) Math.sqrt(dialX * dialX + dialY * dialY);
        if (dialDistance <= compassRadius) {
            dragging = true;
            aimDirection = nearestDirection(dialX, dialY);
            return;
        }

        // The tap target is the hatch itself widened by HATCH_TAP_REACH cells on each side, so a hatch
        // stays easy to hit even though the wall shows it narrower than a cell. The hatch slants with
        // the wall, so the box is the bounding box of the four corners: what the eye sees of the hatch
        // is inside it. The boxes of two rows overlap - the reach is wider than the gap between the
        // hatches - so the nearest hatch wins: a tap anywhere on a hatch opens that hatch, and never
        // the one above it.
        int hit = -1;
        float nearest = 0f;
        for (int i = 0; i < BattleState.HATCH_ROWS.length; i++) {
            float reach = cell * HATCH_TAP_REACH;
            float bottom = hatchBottom(i);
            float top = hatchTop(i);
            if (touchX < hatchLeft(true) - reach || touchX > hatchRight(true) + reach
                    || touchY < bottom - reach || touchY > top + reach) {
                continue;
            }
            float distance = Math.abs(touchY - (bottom + top) / 2f);
            if (hit < 0 || distance < nearest) {
                hit = i;
                nearest = distance;
            }
        }
        if (hit >= 0) {
            toggleHatch(hit);
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
        activeSide = BattleSide.PLAYER;
        if (action.isJump()) {
            aimDirection = action.getDirection();
        }
        ActionResult result = state.apply(action);
        if (action.isDeploy()) {
            closeHatches();
        }
        message = "Ty: " + describe(action, result);
        startAnimations(result);
    }

    private void updateEnemy() {
        if (phase != Phase.ENEMY_TURN || phaseTime < ENEMY_THINK_SECONDS) {
            return;
        }
        BattleAction action = ai.chooseAction(state);
        activeSide = BattleSide.ENEMY;
        if (action.isJump()) {
            aimDirection = action.getDirection();
        }
        ActionResult result = state.apply(action);
        message = "Wrog: " + describe(action, result);
        startAnimations(result);
    }

    private void afterAction() {
        outcome = state.getOutcome();
        phaseTime = 0f;
        if (outcome != BattleOutcome.ONGOING) {
            phase = Phase.FINISHED;
            return;
        }
        phase = state.getSideToMove() == BattleSide.PLAYER ? Phase.PLAYER_TURN : Phase.ENEMY_TURN;
        activeSide = phase == Phase.PLAYER_TURN ? BattleSide.PLAYER : BattleSide.ENEMY;
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

    /** -1 for the player, +1 for the enemy: the side of the net a ship stands on. */
    private static float outward(boolean player) {
        return player ? -1f : 1f;
    }

    /**
     * Where the middle of one hull sits, in screen x. The whole ship stands SHIP_GAP_CELLS off the edge
     * of the net, and its bow - the nearest point of the hull, the one the projection turns towards the
     * board - reaches HULL_BOW_CELLS in from the middle of the ship, so the middle, which every hull
     * point is measured from, is that much further out. The gap is negative, so the bow reaches over
     * the board's outermost column and the lashings of the net end on the wood beside the hatches.
     */
    private float hullNear(boolean player) {
        float edge = player ? boardX : cellLeft(BattleState.WIDTH);
        return edge + outward(player) * cell * (SHIP_GAP_CELLS + HULL_BOW_CELLS);
    }

    // --- Hull projection -------------------------------------------------------------------------
    //
    // A point of the hull is given in ship units: s from the stern (0) to the bow (HULL_LENGTH_UNITS),
    // x across the hull from its far side (0) to the side facing the board (HULL_BEAM_UNITS), y up from
    // the waterline (0) to the deck (HULL_HEIGHT_UNITS). The view turns the hull by VIEW_YAW_DEG
    // towards the board, so the wall facing the board is seen at a slant and the stern stands beside it.
    //
    // The helpers below give the place of a point in board cells, measured from the left edge of the
    // board (hullX) and from the horizon (hullY). They are pure geometry: the same point of a ship
    // always lands in the same place of its cell whatever the screen size, and the hull, the hatches,
    // the mast and the tap targets all go through them, so they can never drift apart.

    /** How deep in the view a hull point sits: its distance from the eye, in units. */
    private static float hullDepth(float s, float x) {
        return VIEW_DISTANCE_UNITS + (s - HULL_LENGTH_UNITS / 2f) * COS_YAW
                - (x - HULL_BEAM_UNITS / 2f) * SIN_YAW;
    }

    /** How far across the view a hull point sits, in units to the right of the middle of the ship. */
    private static float hullAcross(float s, float x) {
        return (s - HULL_LENGTH_UNITS / 2f) * SIN_YAW + (x - HULL_BEAM_UNITS / 2f) * COS_YAW;
    }

    /** How far to the right of the middle of the hull's cell a hull point sits, in board cells. */
    private static float hullX(float s, float x) {
        return FOCAL_CELLS * hullAcross(s, x) / hullDepth(s, x);
    }

    /** How far above the horizon a hull point sits, in board cells. */
    private static float hullY(float s, float x, float y) {
        return FOCAL_CELLS * (y - HULL_HEIGHT_UNITS / 2f) / hullDepth(s, x);
    }

    /** The middle of the hulls across the board: the horizon runs through it, as in the sketch. */
    private float hullCentreY() {
        return boardY + cell * BattleState.HEIGHT / 2f;
    }

    /**
     * The screen x of a hull point. The two sides face each other, so the enemy's ship is the player's
     * turned about: the bow of each points at the net, and the stern of each away from it.
     */
    private float hullScreenX(boolean player, float s, float x) {
        return hullNear(player) + cell * (player ? hullX(s, x) : -hullX(s, x));
    }

    /** The screen y of a hull point. */
    private float hullScreenY(float s, float x, float y) {
        return hullCentreY() + cell * hullY(s, x, y);
    }

    /** Where the far end of the enemy's hull, and the whole reserve beside it, begin. */
    private float enemyHullFar() {
        return hullNear(false) + cell * HULL_STERN_CELLS;
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
        drawNeedle();
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
            float thickness = Math.max(MIN_LINE_PX, cell * (WAVE_THICKNESS + WAVE_THICKNESS_GROWTH * t));
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
            shapes.rect(cx - w / 2f, y, w, Math.max(MIN_LINE_PX,
                    cell * GLITTER_THICKNESS * (1f + GLITTER_THICKNESS_GROWTH * t)));
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
            shapes.rect(x, y, w, Math.max(MIN_LINE_PX, cell * FOAM_THICKNESS));
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

        // The ships go down first: they are pulled in until their hatches meet the lashings of the net,
        // so the wood lies over the outermost columns of the board and the net has to go over the wood.
        // The hatches sit on the hulls, as openings in the side each ship shows the enemy, and the net
        // then goes over everything, with the crew on top of it: the units stand on the net, not under.
        drawShips();
        drawHatchColumn(true, pulse);
        drawHatchColumn(false, pulse);
        drawNet();

        for (int x = 0; x < BattleState.WIDTH; x++) {
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                BattleSide occupant = state.getCrewAt(x, y);
                if (occupant == null) {
                    continue;
                }
                boolean skip = false;
                if (phase == Phase.ANIMATING) {
                    for (CrewAnimation anim : activeAnimations) {
                        if (anim.endGridX == x && anim.endGridY == y && (anim.startX != anim.endX || anim.startY != anim.endY)) {
                            skip = true;
                            break;
                        }
                    }
                }
                if (skip) {
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

        if (phase == Phase.ANIMATING) {
            float p = Math.min(1f, animTime / MOVE_ANIMATION_SECONDS);
            float ease = p * p * (3f - 2f * p);
            for (CrewAnimation anim : activeAnimations) {
                float cx = anim.startX + (anim.endX - anim.startX) * ease;
                float cy = anim.startY + (anim.endY - anim.startY) * ease;
                shapes.setColor(CREW_SHADOW_COLOR);
                shapes.circle(cx, cy, cell * CREW_SHADOW_RADIUS, CREW_SEGMENTS);
                shapes.setColor(anim.side == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
                shapes.circle(cx, cy, cell * CREW_RADIUS, CREW_SEGMENTS);
            }
        }
    }

    /**
     * The hatch column of one side, running down the wall that side's hull shows to the board: the five
     * hatches of the net, one per row of the board, placed on the wall by the same projection that draws
     * the hull, so they sit on the wood and stay in step with the rows they open.
     */
    private void drawHatchColumn(boolean player, float pulse) {
        for (int i = 0; i < HATCH_COUNT; i++) {
            drawHatch(player, i, i, pulse);
        }
    }

    /**
     * The hatch of one row of the column: the opening of the wall seen in the projection, with its
     * dark edge. The wall runs away from the eye, so the square is a parallelogram - its sides stay
     * upright (the projection moves a point sideways with its place along the ship only, never with its
     * height), while its top and bottom slant with the wall, exactly as the deck above them does.
     */
    private void hatchQuad(boolean player, int index) {
        float low = hatchLow(index);
        float high = low + HATCH_OPEN_UNITS;
        float rearX = hatchRearX(player);
        float bowX = hatchBowX(player);
        float rearBottom = hatchCornerY(false, low);
        float rearTop = hatchCornerY(false, high);
        float bowBottom = hatchCornerY(true, low);
        float bowTop = hatchCornerY(true, high);
        shapes.triangle(rearX, rearTop, bowX, bowTop, bowX, bowBottom);
        shapes.triangle(rearX, rearTop, bowX, bowBottom, rearX, rearBottom);
        shapes.setColor(HATCH_EDGE_COLOR);
        hullEdge(rearX, rearTop, bowX, bowTop);
        hullEdge(bowX, bowTop, bowX, bowBottom);
        hullEdge(bowX, bowBottom, rearX, rearBottom);
        hullEdge(rearX, rearBottom, rearX, rearTop);
    }

    /**
     * How high the wall stands at one of the two ends of the hatch column, in screen y. The two ends
     * are at different depths, so the same height of wall lands lower at the bow end than at the stern.
     */
    private float hatchCornerY(boolean bow, float units) {
        float s = bow ? HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS : HATCH_COLUMN_UNITS;
        return hullScreenY(s, HULL_BEAM_UNITS, units);
    }

    /** How far up the wall the hatch of one row starts, in ship units. */
    private static float hatchLow(int index) {
        return HATCH_MARGIN_UNITS + (HATCH_COUNT - 1 - index) * HATCH_STEP_UNITS;
    }

    /** The screen x of the two ends of the hatch column: the stern end and the bow end of the wall. */
    private float hatchRearX(boolean player) {
        return hullScreenX(player, HATCH_COLUMN_UNITS, HULL_BEAM_UNITS);
    }

    private float hatchBowX(boolean player) {
        return hullScreenX(player, HATCH_COLUMN_UNITS + HATCH_WIDTH_UNITS, HULL_BEAM_UNITS);
    }

    /** How far the hatch column reaches to the left and to the right on the wall, in screen x. */
    private float hatchLeft(boolean player) {
        return Math.min(hatchRearX(player), hatchBowX(player));
    }

    private float hatchRight(boolean player) {
        return Math.max(hatchRearX(player), hatchBowX(player));
    }

    /**
     * The bounding box of the hatch of one row: the lowest and the highest of its four corners. The
     * stern end stands nearer the eye, so it is taller than the bow end; the box is what the hatch is
     * held to when a tap is judged, since a slanted hatch is no longer a rectangle on screen.
     */
    private float hatchBottom(int index) {
        float low = hatchLow(index);
        return Math.min(hatchCornerY(false, low), hatchCornerY(true, low));
    }

    private float hatchTop(int index) {
        float high = hatchLow(index) + HATCH_OPEN_UNITS;
        return Math.max(hatchCornerY(false, high), hatchCornerY(true, high));
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
     * The two ships the net is strung between. Each one is the wooden box of the sketch, seen at 15
     * degrees off its axis: the long wall facing the net runs away towards the bow, with the stern
     * standing beside it, and the foot of the rigging stands on the middle of the deck's width.
     */
    private void drawShips() {
        drawShip(true);
        drawShip(false);
    }

    private void drawShip(boolean player) {
        // Rigging first: the mast stands on the middle of the deck's width, well behind the near side
        // of the hull, so the hull drawn over it afterwards swallows the foot of the mast and the sail
        // the way it would in a real 15 degree view. Only the part above the deck line stays in sight:
        // a heavy mast running out of the deck towards the top of the sky, carrying one square sail,
        // four fifths of the board wide, whose foot sags between its clews. The scene mask then cuts
        // mast, sail and everything above them off at the top of the sky.
        float mastS = MAST_POSITION * HULL_LENGTH_UNITS;
        float mastX = hullScreenX(player, mastS, MAST_BEAM_UNITS);
        // The mast stands upright, so its foot sits on the deck line at its own place along the hull.
        float mastY = hullScreenY(mastS, MAST_BEAM_UNITS, HULL_HEIGHT_UNITS);
        float sky = Math.max(cell * RIGGING_MIN_SKY, sceneTop - mastY);
        float rig = sky + cell * RIGGING_OVERFLOW;
        float half = cell * BattleState.WIDTH * SAIL_HALF_WIDTH;

        // SAIL_BOTTOM is measured from the foot of the mast, so it has to carry the foot of the sail
        // over the top of the board: the hull only hides the cloth below the deck line - the top edge
        // of the near wall, which swallows the foot of the mast as well - and that line falls away
        // towards the bow, where the foot of the sail hangs out past the hull and over the near corner
        // of the board.
        drawSailPanel(mastX, half, mastY + rig * SAIL_BOTTOM, mastY + rig * SAIL_TOP);

        shapes.setColor(HULL_COLOR);
        shapes.rect(mastX - cell * MAST_THICKNESS / 2f, mastY, cell * MAST_THICKNESS, rig * MAST_TOP);

        // The wall the board sees, from the stern corner to the bow and back under it: the bow is the
        // edge the net is strung from, the stern the far end away from it.
        hullFace(hullScreenX(player, 0f, HULL_BEAM_UNITS), hullScreenY(0f, HULL_BEAM_UNITS, HULL_HEIGHT_UNITS),
                hullScreenX(player, HULL_LENGTH_UNITS, HULL_BEAM_UNITS),
                hullScreenY(HULL_LENGTH_UNITS, HULL_BEAM_UNITS, HULL_HEIGHT_UNITS),
                hullScreenX(player, HULL_LENGTH_UNITS, HULL_BEAM_UNITS),
                hullScreenY(HULL_LENGTH_UNITS, HULL_BEAM_UNITS, 0f),
                hullScreenX(player, 0f, HULL_BEAM_UNITS), hullScreenY(0f, HULL_BEAM_UNITS, 0f), HULL_COLOR);
        // The stern, a shade darker, drawn over the wall so the corner between them stays crisp.
        hullFace(hullScreenX(player, 0f, 0f), hullScreenY(0f, 0f, HULL_HEIGHT_UNITS),
                hullScreenX(player, 0f, HULL_BEAM_UNITS), hullScreenY(0f, HULL_BEAM_UNITS, HULL_HEIGHT_UNITS),
                hullScreenX(player, 0f, HULL_BEAM_UNITS), hullScreenY(0f, HULL_BEAM_UNITS, 0f),
                hullScreenX(player, 0f, 0f), hullScreenY(0f, 0f, 0f), HULL_STERN_COLOR);
    }

    /**
     * One face of the hull: the four corners of a slab of timber, given in order round its edge, filled
     * and then outlined. The outline goes on top of the fill, so the silhouette and the corner between
     * the two walls both get their line.
     */
    private void hullFace(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
            Color shade) {
        shapes.setColor(shade);
        shapes.triangle(x1, y1, x2, y2, x3, y3);
        shapes.triangle(x1, y1, x3, y3, x4, y4);
        shapes.setColor(HULL_EDGE_COLOR);
        hullEdge(x1, y1, x2, y2);
        hullEdge(x2, y2, x3, y3);
        hullEdge(x3, y3, x4, y4);
        hullEdge(x4, y4, x1, y1);
    }

    /**
     * One edge of the outline: a flat-ended bar of the edge's own length, stretched half its width past
     * each end. The two bars that meet at a corner therefore cross and close it, instead of leaving the
     * notch a flat end would.
     */
    private void hullEdge(float x1, float y1, float x2, float y2) {
        bar(x1, y1, x2, y2, Math.max(cell * HULL_EDGE_CELLS, MIN_LINE_PX));
    }

    /** One bar of a dark line of the given thickness, stretched half that past each end. */
    private void bar(float x1, float y1, float x2, float y2, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) {
            return;
        }
        float over = thickness / 2f / length;
        shapes.rectLine(x1 - dx * over, y1 - dy * over, x2 + dx * over, y2 + dy * over, thickness);
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
     *
     * <p>The cloth is drawn first and the dark line round it after, the same way the hull is drawn: the
     * two ships lie so close that their sails overlap, and two panels of one colour with no line between
     * them would read as a single sheet of cloth.
     */
    private void drawSailPanel(float mastX, float halfWidth, float bottom, float top) {
        drawSail(mastX, halfWidth, bottom, top);
        drawSailEdge(mastX, halfWidth, bottom, top);
    }

    /** The cloth of one sail, filled with SAIL_COLOR, in the shape described above. */
    private void drawSail(float mastX, float halfWidth, float bottom, float top) {
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
     * The dark line round the sail: the head, both leaches and the foot. The foot is the same cubic the
     * cloth is filled from, walked in the same steps, so the line lies exactly on the edge of the cloth
     * the whole way round the sag between the clews.
     */
    private void drawSailEdge(float mastX, float halfWidth, float bottom, float top) {
        float clewHalf = halfWidth * (1f - SAIL_CLEW_INSET);
        float clewY = bottom + halfWidth * SAIL_CLEW_RISE;
        float lean = halfWidth - clewHalf;
        float drop = top - clewY;
        float spread = (clewY - bottom) / (0.75f * drop);
        float startX = mastX + clewHalf;
        float endX = mastX - clewHalf;
        float c1x = startX - spread * lean;
        float c1y = clewY - spread * drop;
        float c2x = endX + spread * lean;
        // ShapeRenderer draws with no antialiasing, so a bar narrower than one pixel falls between two
        // pixel centres on some rows and leaves a gap there. This line has to be continuous: it is the
        // only thing separating two sails of one colour. Hence the floor of one pixel.
        float thickness = Math.max(cell * SAIL_EDGE_CELLS, MIN_LINE_PX);

        shapes.setColor(SAIL_EDGE_COLOR);
        bar(mastX - halfWidth, top, mastX + halfWidth, top, thickness);
        bar(mastX + halfWidth, top, startX, clewY, thickness);
        bar(endX, clewY, mastX - halfWidth, top, thickness);
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
            float y = w0 * clewY + w1 * c1y + w2 * c1y + w3 * clewY;
            bar(prevX, prevY, x, y, thickness);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * The boarding net strung between the two hulls: one rope along every grid line, so a mesh opening
     * is exactly one cell wide and a unit stands in the middle of an opening, never on a knot where two
     * strands cross. The openings show the sea and the sky behind them. The horizontal ropes run past
     * the outermost grid lines, over the water and over the wood, and end on the bow jamb of the hatch
     * of their own row, where they are made fast: the net is drawn over the ships, so the lashings stay
     * in sight the whole way from the board to the wood, and the doorway stays clear.
     */
    private void drawNet() {
        float thickness = Math.max(cell * STRAND_THICKNESS, MIN_LINE_PX);
        float reach = cell * STRAND_LASH_CELLS;
        float left = cellLeft(1);
        float right = cellLeft(BattleState.WIDTH - 1);
        float bottom = cellBottom(BattleState.HEIGHT - 1);
        float top = cellBottom(0) + cell;

        for (int line = 0; line <= BattleState.HEIGHT; line++) {
            drawNetLine(line, bottom + line * cell - thickness / 2f, left - reach, right + reach,
                    thickness);
        }
        for (int line = 1; line < BattleState.WIDTH; line++) {
            strand(cellLeft(line) - thickness / 2f, bottom, thickness, top - bottom);
        }
    }

    /**
     * One horizontal rope of the net, run from one lashing to the other, and broken over every torn
     * cell of the row it tops: a tear of the net is the missing piece of this rope, so a hole in a cell
     * that lies over a hull shows the wood the mesh was torn away from, and never a bite out of the
     * ship. The rope tops the row under it, so the line of a row is the net's line of index HEIGHT - y.
     */
    private void drawNetLine(int line, float y, float from, float to, float thickness) {
        int row = BattleState.HEIGHT - line;
        if (row < 0 || row >= BattleState.HEIGHT) {
            strand(from, y, to - from, thickness);
            return;
        }
        float run = from;
        for (int x = 0; x < BattleState.WIDTH; x++) {
            if (!state.isHole(x, row)) {
                continue;
            }
            float cut = cellLeft(x);
            if (cut > run) {
                strand(run, y, cut - run, thickness);
            }
            drawTornKnot(cut, y, thickness, 1f);
            drawTornKnot(cut + cell, y, thickness, -1f);
            run = cut + cell;
        }
        if (to > run) {
            strand(run, y, to - run, thickness);
        }
    }

    /** What a broken rope leaves at one of the two knots of the cell: a stub, and its frayed end. */
    private void drawTornKnot(float knot, float y, float thickness, float towards) {
        float stub = cell * TORN_STUB_CELLS;
        float end = knot + towards * stub;
        strand(towards > 0f ? knot : end, y, stub, thickness);

        float fray = cell * TORN_FRAY_CELLS;
        strandBar(end, y + thickness / 2f, end + towards * fray,
                y + thickness / 2f - fray * TORN_FRAY_DROP, thickness);
    }

    /** One strand, with a darker outline so the mesh stays readable against the bright sea. */
    private void strand(float x, float y, float w, float h) {
        float edge = cell * STRAND_EDGE;
        shapes.setColor(ROPE_SHADE);
        shapes.rect(x - edge, y - edge, w + 2f * edge, h + 2f * edge);
        shapes.setColor(ROPE_COLOR);
        shapes.rect(x, y, w, h);
    }

    /** One strand that does not run straight, outlined the way the straight ones are. */
    private void strandBar(float x1, float y1, float x2, float y2, float thickness) {
        float edge = cell * STRAND_EDGE;
        shapes.setColor(ROPE_SHADE);
        bar(x1, y1, x2, y2, thickness + 2f * edge);
        shapes.setColor(ROPE_COLOR);
        bar(x1, y1, x2, y2, thickness);
    }

    /** Whatever the background shows at this height: the sea below the horizon, the sky above it. */
    private void backdropAt(float y, Color out) {
        if (y < horizonY) {
            seaColorAt(y, out);
        } else {
            skyColorAt(y, out);
        }
    }

    private void drawHatch(boolean player, int y, int index, float pulse) {
        BattleSide owner = player ? BattleSide.PLAYER : BattleSide.ENEMY;
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
        hatchQuad(player, index);

        if (owner == BattleSide.PLAYER && openHatches[index]) {
            drawEntryMarker(cellBottom(y));
        }

        // One small marker per pirate still waiting inside; drawn outside the hatch on the side away
        // from the net, top-aligned to the upper rear corner of its own hatch.
        int waiting = state.getHatchCrew(owner, index);
        if (waiting <= 0) {
            return;
        }
        float out = player ? hatchLeft(player) - cell * WAITING_MARKER_INSET
                : hatchRight(player) + cell * WAITING_MARKER_INSET;
        float high = hatchLow(index) + HATCH_OPEN_UNITS;
        float rearTop = hatchCornerY(false, high);
        float radius = cell * WAITING_MARKER_RADIUS;
        float top = rearTop - radius;
        float step = cell * WAITING_MARKER_STEP;
        shapes.setColor(owner == BattleSide.PLAYER ? PLAYER_COLOR : ENEMY_COLOR);
        for (int i = 0; i < waiting; i++) {
            shapes.circle(out, top - i * step, radius, WAITING_MARKER_SEGMENTS);
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



        drawCompassRose(shade);

        drawNeedle();
        // Small center hub of the needle:
        shapes.setColor(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
        shapes.circle(compassX, compassY, r * 0.08f, MARK_SEGMENTS);

        // The GO button sits under the compass:
        shapes.rect(goX - goHalfWidth, goY - goHalfHeight, goHalfWidth * 2f, goHalfHeight * 2f);

        float fleeShade = phase == Phase.PLAYER_TURN ? 1f : CONTROL_INACTIVE;
        shapes.setColor(FLEE_COLOR.r * fleeShade, FLEE_COLOR.g * fleeShade, FLEE_COLOR.b * fleeShade, 1f);
        shapes.rect(fleeX - fleeHalfWidth, fleeY - fleeHalfHeight, fleeHalfWidth * 2f, fleeHalfHeight * 2f);
    }

    /**
     * Draws nautical compass details: 5-degree tick marks around the rim and an 8-pointed compass rose star.
     */
    private void drawCompassRose(float shade) {
        float r = compassRadius;
        float rimInner = r * (1f - COMPASS_RIM_RING);

        // 1. Ticks every 5 degrees around the rim
        Color tickColor = new Color(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
        shapes.setColor(tickColor);

        for (int deg = 0; deg < 360; deg += 5) {
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            float rInner;
            float thickness;

            if (deg % 90 == 0) {
                // Major cardinal ticks (N, E, S, W)
                rInner = rimInner - r * 0.09f;
                thickness = Math.max(1f, cell * 0.022f);
            } else if (deg % 45 == 0) {
                // Intercardinal ticks (NE, SE, SW, NW)
                rInner = rimInner - r * 0.07f;
                thickness = Math.max(1f, cell * 0.018f);
            } else if (deg % 15 == 0) {
                // 15-degree ticks
                rInner = rimInner - r * 0.05f;
                thickness = Math.max(1f, cell * 0.014f);
            } else {
                // 5-degree minor ticks
                rInner = rimInner - r * 0.03f;
                thickness = Math.max(1f, cell * 0.010f);
            }

            shapes.rectLine(
                    compassX + cos * rInner, compassY + sin * rInner,
                    compassX + cos * rimInner, compassY + sin * rimInner,
                    thickness);
        }

        // 2. Nautical Compass Rose 8-pointed star in dial background
        Color starColorLight = new Color(ARROW_COLOR.r * shade * 0.85f, ARROW_COLOR.g * shade * 0.85f,
                ARROW_COLOR.b * shade * 0.85f, 0.7f);
        Color starColorDark = new Color(ARROW_COLOR.r * shade * 0.50f, ARROW_COLOR.g * shade * 0.50f,
                ARROW_COLOR.b * shade * 0.50f, 0.7f);

        // 4 Primary cardinal points (N, E, S, W)
        float cardinalLen = r * 0.50f;
        float cardinalBase = r * 0.06f;
        int[] cardinalAngles = {0, 90, 180, 270};

        for (int angle : cardinalAngles) {
            float rad = (float) Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            float perpCos = -sin;
            float perpSin = cos;

            float tipX = compassX + cos * cardinalLen;
            float tipY = compassY + sin * cardinalLen;

            float b1X = compassX + perpCos * cardinalBase;
            float b1Y = compassY + perpSin * cardinalBase;
            float b2X = compassX - perpCos * cardinalBase;
            float b2Y = compassY - perpSin * cardinalBase;

            shapes.setColor(starColorLight);
            shapes.triangle(compassX, compassY, tipX, tipY, b1X, b1Y);
            shapes.setColor(starColorDark);
            shapes.triangle(compassX, compassY, tipX, tipY, b2X, b2Y);
        }

        // 4 Secondary intercardinal points (NE, SE, SW, NW)
        float interLen = r * 0.36f;
        float interBase = r * 0.045f;
        int[] interAngles = {45, 135, 225, 315};

        for (int angle : interAngles) {
            float rad = (float) Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            float perpCos = -sin;
            float perpSin = cos;

            float tipX = compassX + cos * interLen;
            float tipY = compassY + sin * interLen;

            float b1X = compassX + perpCos * interBase;
            float b1Y = compassY + perpSin * interBase;
            float b2X = compassX - perpCos * interBase;
            float b2Y = compassY - perpSin * interBase;

            shapes.setColor(starColorLight);
            shapes.triangle(compassX, compassY, tipX, tipY, b1X, b1Y);
            shapes.setColor(starColorDark);
            shapes.triangle(compassX, compassY, tipX, tipY, b2X, b2Y);
        }
    }

    /**
     * The compass needle: single-colored, pivoted at ~1/3 of its length, with a flat rear end,
     * tapering smoothly along its entire length, and ending in a blunt/truncated tip.
     */
    private void drawNeedle() {
        float dirX = aimDirection.getDx(activeSide);
        float dirY = -aimDirection.getDy();
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        dirX /= length;
        dirY /= length;

        float perpX = -dirY;
        float perpY = dirX;

        float r = compassRadius;
        float shade = controlShade();

        float rearLen = r * 0.35f;
        float tipLen = r * 0.90f;

        float wRear = r * 0.06f;
        float wPivot = r * 0.075f;
        float wTip = r * 0.02f;

        Color needleColor = new Color(ARROW_COLOR.r * shade, ARROW_COLOR.g * shade, ARROW_COLOR.b * shade, 1f);
        shapes.setColor(needleColor);

        float cx = compassX;
        float cy = compassY;

        // Rear flat edge points
        float r1x = cx - dirX * rearLen + perpX * wRear;
        float r1y = cy - dirY * rearLen + perpY * wRear;
        float r2x = cx - dirX * rearLen - perpX * wRear;
        float r2y = cy - dirY * rearLen - perpY * wRear;

        // Pivot points
        float m1x = cx + perpX * wPivot;
        float m1y = cy + perpY * wPivot;
        float m2x = cx - perpX * wPivot;
        float m2y = cy - perpY * wPivot;

        // Front truncated tip points
        float t1x = cx + dirX * tipLen + perpX * wTip;
        float t1y = cy + dirY * tipLen + perpY * wTip;
        float t2x = cx + dirX * tipLen - perpX * wTip;
        float t2y = cy + dirY * tipLen - perpY * wTip;

        // Draw 4 triangles comprising the single-colored tapering needle with a flat rear
        shapes.triangle(r1x, r1y, m1x, m1y, r2x, r2y); // Rear body 1
        shapes.triangle(r2x, r2y, m1x, m1y, m2x, m2y); // Rear body 2
        shapes.triangle(m1x, m1y, t1x, t1y, m2x, m2y); // Front body 1
        shapes.triangle(m2x, m2y, t1x, t1y, t2x, t2y); // Front body 2
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
        drawGoLabel();
        drawCompassLabels();
    }

    /**
     * The cardinal and intercardinal letters around the dial. Both are
     * sized from the dial radius rather than from the text scale, so a small dial carries small
     * letters instead of spilling over the hull beside it.
     */
    private void drawCompassLabels() {
        float ring = compassRadius * COMPASS_LETTER_RING;
        float interRing = compassRadius * 0.68f;
        float cos45 = 0.7071068f;

        // Main cardinal directions (N, E, S, W)
        font.getData().setScale(textScale * (compassRadius * COMPASS_LABEL_SIZE) / lineHeight);
        font.setColor(Color.WHITE);
        drawCentered("N", compassX, compassY + ring);
        drawCentered("E", compassX + ring, compassY);
        drawCentered("S", compassX, compassY - ring);
        drawCentered("W", compassX - ring, compassY);

        // Intercardinal directions (NE, SE, SW, NW)
        font.getData().setScale(textScale * (compassRadius * COMPASS_LABEL_SIZE * 0.58f) / lineHeight);
        font.setColor(new Color(0.85f, 0.85f, 0.85f, 1f));
        drawCentered("NE", compassX + interRing * cos45, compassY + interRing * cos45);
        drawCentered("SE", compassX + interRing * cos45, compassY - interRing * cos45);
        drawCentered("SW", compassX - interRing * cos45, compassY - interRing * cos45);
        drawCentered("NW", compassX - interRing * cos45, compassY + interRing * cos45);

        font.getData().setScale(textScale);
    }

    /** Draws "GO!" in the middle of the GO button under the compass. */
    private void drawGoLabel() {
        String label = "GO!";
        float shade = controlShade();
        font.setColor(shade < 1f ? Color.DARK_GRAY : Color.BLACK);
        layout.setText(font, label, font.getColor(), goHalfWidth * 2f, Align.center, false);
        font.draw(batch, layout, goX - layout.width / 2f, goY + layout.height / 2f);
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
