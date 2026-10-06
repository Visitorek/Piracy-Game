package com.corsair.deluxe.domain.battle;

public final class BattleResult {
    private final BattleOutcome outcome;
    private final int playerCrewLost;

    public BattleResult(BattleOutcome outcome, int playerCrewLost) {
        this.outcome = outcome;
        this.playerCrewLost = playerCrewLost;
    }

    public BattleOutcome getOutcome() {
        return outcome;
    }

    public int getPlayerCrewLost() {
        return playerCrewLost;
    }
}
