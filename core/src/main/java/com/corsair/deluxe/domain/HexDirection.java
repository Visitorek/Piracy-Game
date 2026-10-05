package com.corsair.deluxe.domain;

public enum HexDirection {
    NE(1, -1),
    E(1, 0),
    SE(0, 1),
    SW(-1, 1),
    W(-1, 0),
    NW(0, -1);

    private final int q;
    private final int r;

    HexDirection(int q, int r) {
        this.q = q;
        this.r = r;
    }

    public HexCoordinate toOffset() {
        return new HexCoordinate(q, r);
    }
}


