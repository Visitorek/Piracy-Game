package com.corsair.deluxe.domain.battle;

import java.util.ArrayList;
import java.util.List;

/** What happened when an action was applied; used for the battle log. */
public final class ActionResult {
    int deployed;
    int killed;
    int fell;
    int retreated;
    int hatchesDisabled;
    public final List<MoveRecord> moves = new ArrayList<MoveRecord>();

    /** Pirates pushed out of their hatches onto the net by this action. */
    public int getDeployed() {
        return deployed;
    }

    public int getKilled() {
        return killed;
    }

    public int getFell() {
        return fell;
    }

    public int getRetreated() {
        return retreated;
    }

    public int getHatchesDisabled() {
        return hatchesDisabled;
    }
}
