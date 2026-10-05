package com.corsair.deluxe.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HexMap {
    private final int radius;
    private final HexTile[][] tiles;
    private final Random random = new Random();

    public HexMap(int radius) {
        this.radius = radius;
        this.tiles = new HexTile[radius * 2 + 1][radius * 2 + 1];
        generate();
    }

    private void generate() {
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexCoordinate coord = new HexCoordinate(q, r);
                if (Math.abs(q) >= radius - 1 && Math.abs(r) >= radius - 1) {
                    tiles[normalizeIndex(q)][normalizeIndex(r)] = new HexTile(coord, HexTile.Terrain.WATER);
                    continue;
                }

                tiles[normalizeIndex(q)][normalizeIndex(r)] = new HexTile(coord, HexTile.Terrain.WATER);
            }
        }

        placeTerrainPatch(0, 0, 3, HexTile.Terrain.ISLAND);
        placeTerrainPatch(2, 1, 2, HexTile.Terrain.ISLAND);
        placeTerrainPatch(-3, 2, 2, HexTile.Terrain.ISLAND);
        placeTerrainPatch(4, -2, 2, HexTile.Terrain.SHALLOW);
        placeTerrainPatch(-4, -2, 2, HexTile.Terrain.SHALLOW);
    }

    private void placeTerrainPatch(int originQ, int originR, int radiusSize, HexTile.Terrain terrain) {
        for (int q = -radiusSize; q <= radiusSize; q++) {
            for (int r = -radiusSize; r <= radiusSize; r++) {
                if (Math.abs(q) + Math.abs(r) > radiusSize + 1) {
                    continue;
                }
                HexCoordinate coord = new HexCoordinate(originQ + q, originR + r);
                if (isInside(coord)) {
                    HexTile current = getTile(coord);
                    if (current != null && current.getTerrain() == HexTile.Terrain.WATER) {
                        setTile(coord, new HexTile(coord, terrain));
                    }
                }
            }
        }
    }

    public List<HexTile> getTiles() {
        List<HexTile> result = new ArrayList<HexTile>();
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexTile tile = tiles[normalizeIndex(q)][normalizeIndex(r)];
                if (tile != null) {
                    result.add(tile);
                }
            }
        }
        return result;
    }

    public HexTile getTile(HexCoordinate coordinate) {
        if (!isInside(coordinate)) {
            return null;
        }
        return tiles[normalizeIndex(coordinate.getQ())][normalizeIndex(coordinate.getR())];
    }

    public void setTile(HexCoordinate coordinate, HexTile tile) {
        if (isInside(coordinate)) {
            tiles[normalizeIndex(coordinate.getQ())][normalizeIndex(coordinate.getR())] = tile;
        }
    }

    public boolean isInside(HexCoordinate coordinate) {
        int q = coordinate.getQ();
        int r = coordinate.getR();
        return q >= -radius && q <= radius && r >= -radius && r <= radius;
    }

    private int normalizeIndex(int value) {
        return value + radius;
    }
}


