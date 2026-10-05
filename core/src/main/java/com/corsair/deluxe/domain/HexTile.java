package com.corsair.deluxe.domain;

public class HexTile {
    public enum Terrain {
        WATER,
        SHALLOW,
        ISLAND
    }

    private final HexCoordinate coordinate;
    private final Terrain terrain;

    public HexTile(HexCoordinate coordinate, Terrain terrain) {
        this.coordinate = coordinate;
        this.terrain = terrain;
    }

    public HexCoordinate getCoordinate() {
        return coordinate;
    }

    public Terrain getTerrain() {
        return terrain;
    }
}


