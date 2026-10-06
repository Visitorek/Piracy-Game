package com.corsair.deluxe.domain.battle;

public enum BattleSide {
    PLAYER,
    ENEMY;

    public BattleSide opponent() {
        return this == PLAYER ? ENEMY : PLAYER;
    }
}
