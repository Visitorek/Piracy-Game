package com.corsair.deluxe.domain.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.corsair.deluxe.core.GameSettings;

/**
 * Turn-based "net" skirmish modelled on Piracy Deluxe.
 *
 * The playfield is a net WIDTH x HEIGHT. The left column holds the player's hatches, the right column
 * the enemy's. A turn is one of two things: muster the crew - every hatch the side opened pushes one
 * pirate onto the net while nothing already on the net moves - or make the whole crew on the net jump
 * one cell in a chosen direction. Opening and closing hatches is free; the net wraps vertically.
 */
public class BattleState {
    /** Playable net is 6 columns x 5 rows; one extra column on each side holds the ships' hatches. */
    public static final int WIDTH = 8;
    public static final int HEIGHT = 5;
    public static final int[] HATCH_ROWS = {0, 1, 2, 3, 4};
    public static final int MAX_TURNS = 80;
    public static final int CREW_PER_UNIT = 3;
    /** Pirates waiting inside every hatch at the start. */
    public static final int PLAYER_PIRATES_PER_HATCH = 2;

    private final boolean[][] holes = new boolean[WIDTH][HEIGHT];
    private final BattleSide[][] crew = new BattleSide[WIDTH][HEIGHT];
    private final boolean[][] hatchDisabled = new boolean[2][HATCH_ROWS.length];
    private final int[][] hatchCrew = new int[2][HATCH_ROWS.length];
    private int turnsPlayed;
    private BattleSide sideToMove = BattleSide.PLAYER;

    private BattleState() {
    }

    /** Creates a fresh net with point-symmetric holes so neither side starts with an advantage. */
    public static BattleState create(int holePairs, Random random) {
        BattleState state = new BattleState();
        for (int s = 0; s < 2; s++) {
            java.util.Arrays.fill(state.hatchCrew[s], piratesPerHatch(BattleSide.values()[s]));
        }

        int placed = 0;
        for (int attempt = 0; attempt < 300 && placed < holePairs; attempt++) {
            int x = 1 + random.nextInt(WIDTH - 2);
            int y = random.nextInt(HEIGHT);
            int mirrorX = WIDTH - 1 - x;
            int mirrorY = HEIGHT - 1 - y;
            boolean centre = x == mirrorX && y == mirrorY;
            boolean blocksEntry = (x == 1 || x == WIDTH - 2) && hatchIndexForRow(y) >= 0;
            if (centre || blocksEntry || state.holes[x][y]) {
                continue;
            }
            state.holes[x][y] = true;
            state.holes[mirrorX][mirrorY] = true;
            placed++;
        }
        return state;
    }

    public static int piratesPerHatch(BattleSide side) {
        return side == BattleSide.PLAYER
                ? PLAYER_PIRATES_PER_HATCH
                : GameSettings.enemyPiratesPerHatch();
    }

    public BattleState copy() {
        BattleState copy = new BattleState();
        for (int x = 0; x < WIDTH; x++) {
            System.arraycopy(holes[x], 0, copy.holes[x], 0, HEIGHT);
            System.arraycopy(crew[x], 0, copy.crew[x], 0, HEIGHT);
        }
        for (int s = 0; s < 2; s++) {
            System.arraycopy(hatchDisabled[s], 0, copy.hatchDisabled[s], 0, HATCH_ROWS.length);
            System.arraycopy(hatchCrew[s], 0, copy.hatchCrew[s], 0, HATCH_ROWS.length);
        }
        copy.turnsPlayed = turnsPlayed;
        copy.sideToMove = sideToMove;
        return copy;
    }

    public static int hatchIndexForRow(int row) {
        for (int i = 0; i < HATCH_ROWS.length; i++) {
            if (HATCH_ROWS[i] == row) {
                return i;
            }
        }
        return -1;
    }

    public static int hatchColumn(BattleSide side) {
        return side == BattleSide.PLAYER ? 0 : WIDTH - 1;
    }

    /** Net column a pirate steps onto when it leaves its hatch. */
    public static int entryColumn(BattleSide side) {
        return side == BattleSide.PLAYER ? 1 : WIDTH - 2;
    }

    public BattleSide getSideToMove() {
        return sideToMove;
    }

    public int getTurnsPlayed() {
        return turnsPlayed;
    }

    public boolean isHole(int x, int y) {
        return holes[x][y];
    }

    public BattleSide getCrewAt(int x, int y) {
        return crew[x][y];
    }

    public boolean isHatchDisabled(BattleSide side, int hatchIndex) {
        return hatchDisabled[side.ordinal()][hatchIndex];
    }

    /** Pirates still waiting inside the given hatch. */
    public int getHatchCrew(BattleSide side, int hatchIndex) {
        return hatchCrew[side.ordinal()][hatchIndex];
    }

    public int getReserve(BattleSide side) {
        int total = 0;
        for (int count : hatchCrew[side.ordinal()]) {
            total += count;
        }
        return total;
    }

    public int getCrewOnNet(BattleSide side) {
        int count = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (crew[x][y] == side) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getTotalCrew(BattleSide side) {
        return getReserve(side) + getCrewOnNet(side);
    }

    public int getDisabledHatches(BattleSide side) {
        int count = 0;
        for (boolean disabled : hatchDisabled[side.ordinal()]) {
            if (disabled) {
                count++;
            }
        }
        return count;
    }

    /** True when the hatch can push one pirate onto the net right now. */
    public boolean canRelease(BattleSide side, int hatchIndex) {
        if (hatchIndex < 0 || hatchIndex >= HATCH_ROWS.length) {
            return false;
        }
        return getHatchCrew(side, hatchIndex) > 0
                && !isHatchDisabled(side, hatchIndex)
                && crew[entryColumn(side)][HATCH_ROWS[hatchIndex]] != side;
    }

    /** Mask of the hatches that would push a pirate out if the crew were mustered right now. */
    public boolean[] releasableHatchMask(BattleSide side) {
        boolean[] mask = new boolean[HATCH_ROWS.length];
        for (int i = 0; i < HATCH_ROWS.length; i++) {
            mask[i] = canRelease(side, i);
        }
        return mask;
    }

    /** True when at least one of the opened hatches can actually push a pirate onto the net. */
    public boolean canDeploy(BattleSide side, boolean[] hatches) {
        if (hatches == null) {
            return false;
        }
        for (int i = 0; i < hatches.length && i < HATCH_ROWS.length; i++) {
            if (hatches[i] && canRelease(side, i)) {
                return true;
            }
        }
        return false;
    }

    public boolean canJump(BattleSide side) {
        return getCrewOnNet(side) > 0;
    }

    public List<BattleAction> legalActions(BattleSide side) {
        List<BattleAction> actions = new ArrayList<BattleAction>();
        if (canJump(side)) {
            for (JumpDirection direction : JumpDirection.values()) {
                actions.add(BattleAction.jump(direction));
            }
        }
        boolean[] mask = releasableHatchMask(side);
        boolean any = false;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                any = true;
                actions.add(BattleAction.deploy(i));
            }
        }
        if (any) {
            actions.add(BattleAction.deploy(mask));
        }
        return actions;
    }

    public boolean isLegal(BattleSide side, BattleAction action) {
        if (action.isDeploy()) {
            return canDeploy(side, action.getHatches());
        }
        return canJump(side);
    }

    /** Applies the action for the side to move and passes the turn to the opponent. */
    public ActionResult apply(BattleAction action) {
        ActionResult result = new ActionResult();
        if (action.isDeploy()) {
            applyDeploy(sideToMove, action.getHatches(), result);
        } else {
            applyJump(sideToMove, action.getDirection(), result);
        }
        turnsPlayed++;
        sideToMove = sideToMove.opponent();
        return result;
    }

    /**
     * Pushes one pirate out of every opened hatch that is ready. Nothing already on the net moves:
     * mustering the crew is the whole action.
     */
    private void applyDeploy(BattleSide side, boolean[] hatches, ActionResult result) {
        int x = entryColumn(side);
        for (int i = 0; i < HATCH_ROWS.length && i < hatches.length; i++) {
            if (!hatches[i] || !canRelease(side, i)) {
                continue;
            }
            int y = HATCH_ROWS[i];
            if (crew[x][y] != null) {
                result.killed++;
            }
            crew[x][y] = side;
            hatchCrew[side.ordinal()][i]--;
            result.deployed++;
        }
    }

    private void applyJump(BattleSide side, final JumpDirection direction, ActionResult result) {
        List<int[]> positions = new ArrayList<int[]>();
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (crew[x][y] == side) {
                    positions.add(new int[]{x, y});
                }
            }
        }

        // The crew member leading the jump moves first so followers are not blocked by their own side
        Collections.sort(positions, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                int dotA = a[0] * direction.getDx(side) + a[1] * direction.getDy();
                int dotB = b[0] * direction.getDx(side) + b[1] * direction.getDy();
                return Integer.compare(dotB, dotA);
            }
        });

        for (int[] position : positions) {
            moveCrewMember(side, position[0], position[1], direction, result);
        }
    }

    private void moveCrewMember(BattleSide side, int x, int y, JumpDirection direction, ActionResult result) {
        int targetX = x + direction.getDx(side);
        int targetY = ((y + direction.getDy()) % HEIGHT + HEIGHT) % HEIGHT;

        if (targetX == 0 || targetX == WIDTH - 1) {
            // A pirate reaching the enemy hull always goes for the hatch straight in front of it,
            // no matter which jump was ordered (a diagonal jump still aims at its own row).
            BattleSide owner = targetX == 0 ? BattleSide.PLAYER : BattleSide.ENEMY;
            if (owner == side) {
                return;
            }
            crew[x][y] = null;
            int hatchIndex = hatchIndexForRow(y);
            if (hatchIndex >= 0 && !hatchDisabled[owner.ordinal()][hatchIndex]) {
                hatchDisabled[owner.ordinal()][hatchIndex] = true;
                result.hatchesDisabled++;
            }
            return;
        }

        if (holes[targetX][targetY]) {
            crew[x][y] = null;
            result.fell++;
            return;
        }
        if (crew[targetX][targetY] == side) {
            return;
        }
        if (crew[targetX][targetY] != null) {
            result.killed++;
        }
        crew[targetX][targetY] = side;
        crew[x][y] = null;
    }

    private boolean isDefeated(BattleSide side) {
        if (getCrewOnNet(side) > 0) {
            return false;
        }
        for (int i = 0; i < HATCH_ROWS.length; i++) {
            if (getHatchCrew(side, i) > 0 && !isHatchDisabled(side, i)) {
                return false;
            }
        }
        return true;
    }

    /** Outcome from the player's point of view. */
    public BattleOutcome getOutcome() {
        boolean playerDefeated = isDefeated(BattleSide.PLAYER);
        boolean enemyDefeated = isDefeated(BattleSide.ENEMY);
        if (playerDefeated && enemyDefeated) {
            return BattleOutcome.DRAW;
        }
        if (playerDefeated) {
            return BattleOutcome.DEFEAT;
        }
        if (enemyDefeated) {
            return BattleOutcome.VICTORY;
        }
        if (turnsPlayed >= MAX_TURNS) {
            int difference = getTotalCrew(BattleSide.PLAYER) - getTotalCrew(BattleSide.ENEMY);
            if (difference > 0) {
                return BattleOutcome.VICTORY;
            }
            return difference < 0 ? BattleOutcome.DEFEAT : BattleOutcome.DRAW;
        }
        return BattleOutcome.ONGOING;
    }
}
