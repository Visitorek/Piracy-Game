package com.corsair.deluxe.domain.battle;

/**
 * One turn decision. Opening and closing hatches is free, so the deploy action carries the set of
 * hatches the side wants to open: every open hatch pushes one pirate onto the net and nothing that is
 * already on the net moves. The other decision is the ordinary jump of the whole crew on the net.
 */
public final class BattleAction {
    private final JumpDirection direction;
    private final boolean[] hatches;

    private BattleAction(JumpDirection direction, boolean[] hatches) {
        this.direction = direction;
        this.hatches = hatches;
    }

    public static BattleAction jump(JumpDirection direction) {
        return new BattleAction(direction, null);
    }

    public static BattleAction deploy(boolean[] hatches) {
        return new BattleAction(null, hatches);
    }

    public static BattleAction deploy(int hatchIndex) {
        boolean[] hatches = new boolean[BattleState.HATCH_ROWS.length];
        hatches[hatchIndex] = true;
        return new BattleAction(null, hatches);
    }

    public boolean isDeploy() {
        return hatches != null;
    }

    public boolean isJump() {
        return hatches == null;
    }

    /** Hatches to open; null for a jump. */
    public boolean[] getHatches() {
        return hatches;
    }

    public int getOpenHatchCount() {
        if (hatches == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < hatches.length; i++) {
            if (hatches[i]) {
                count++;
            }
        }
        return count;
    }

    public JumpDirection getDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return isDeploy() ? "deploy " + getOpenHatchCount() + " hatch(es)" : "jump " + direction;
    }
}
