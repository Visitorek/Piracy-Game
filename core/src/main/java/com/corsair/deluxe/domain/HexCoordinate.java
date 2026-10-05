package com.corsair.deluxe.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HexCoordinate {
    private final int q;
    private final int r;

    public HexCoordinate(int q, int r) {
        this.q = q;
        this.r = r;
    }

    public int getQ() {
        return q;
    }

    public int getR() {
        return r;
    }

    public HexCoordinate add(HexCoordinate other) {
        return new HexCoordinate(q + other.q, r + other.r);
    }

    public HexCoordinate neighbor(HexDirection direction) {
        return add(direction.toOffset());
    }

    public List<HexCoordinate> neighbors() {
        List<HexCoordinate> result = new ArrayList<HexCoordinate>();
        for (HexDirection direction : HexDirection.values()) {
            result.add(neighbor(direction));
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HexCoordinate that = (HexCoordinate) o;
        return q == that.q && r == that.r;
    }

    @Override
    public int hashCode() {
        return Objects.hash(q, r);
    }

    @Override
    public String toString() {
        return "HexCoordinate{" + "q=" + q + ", r=" + r + '}';
    }
}


