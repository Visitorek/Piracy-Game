package com.corsair.deluxe.domain.battle;

/** Jumps are relative to the side: straight ahead or diagonally ahead (y grows downwards). */
public enum JumpDirection {
    FORWARD_UP(-1),
    FORWARD(0),
    FORWARD_DOWN(1);

    private final int dy;

    JumpDirection(int dy) {
        this.dy = dy;
    }

    public int getDy() {
        return dy;
    }

    public int getDx(BattleSide side) {
        return side == BattleSide.PLAYER ? 1 : -1;
    }
}
