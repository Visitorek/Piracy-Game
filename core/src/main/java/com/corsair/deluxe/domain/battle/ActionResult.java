package com.corsair.deluxe.domain.battle;

/** What happened when an action was applied; used for the battle log. */
public final class ActionResult {
    int deployed;
    int killed;
    int fell;
    int retreated;
    int hatchesDisabled;

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
