package com.corsair.deluxe.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class HexMap {
    private static final HexCoordinate START = new HexCoordinate(0, 0);
    private static final int START_CLEARANCE = 0;
    private static final float MAIN_LAND_RATIO = 0.18f;
    private static final int MIN_ISLANDS = 4;
    private static final int MAX_EXTRA_ISLANDS = 3;
    private static final int MAX_ISLAND_SIZE = 8;

    private final int radius;
    private final HexTile[][] tiles;
    private final Random random;

    public HexMap(int radius) {
        this(radius, new Random().nextLong());
    }

    public HexMap(int radius, long seed) {
        this.radius = radius;
        this.tiles = new HexTile[radius * 2 + 1][radius * 2 + 1];
        this.random = new Random(seed);
        generate();
    }

    private void generate() {
        Set<HexCoordinate> land = new HashSet<HexCoordinate>();

        int totalTiles = (radius * 2 + 1) * (radius * 2 + 1);
        growLandmass(land, Math.round(totalTiles * MAIN_LAND_RATIO));

        int islands = MIN_ISLANDS + random.nextInt(MAX_EXTRA_ISLANDS + 1);
        for (int i = 0; i < islands; i++) {
            growLandmass(land, 1 + random.nextInt(MAX_ISLAND_SIZE));
        }

        // Guarantee the start hex has a sea route to the map edge
        if (!reachesEdge(findSea(land))) {
            carveChannelFromStart(land);
        }

        // Water the ship cannot reach from the start would be an inland lake, so it becomes land
        Set<HexCoordinate> sea = findSea(land);
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexCoordinate coord = new HexCoordinate(q, r);
                if (!sea.contains(coord)) {
                    land.add(coord);
                }
            }
        }

        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexCoordinate coord = new HexCoordinate(q, r);
                HexTile.Terrain terrain;
                if (land.contains(coord)) {
                    terrain = HexTile.Terrain.ISLAND;
                } else if (touchesLand(coord, land)) {
                    terrain = HexTile.Terrain.SHALLOW;
                } else {
                    terrain = HexTile.Terrain.WATER;
                }
                tiles[normalizeIndex(q)][normalizeIndex(r)] = new HexTile(coord, terrain);
            }
        }
    }

    /** Grows one blob of land that keeps at least one water hex between itself and other landmasses. */
    private void growLandmass(Set<HexCoordinate> land, int targetSize) {
        HexCoordinate seed = null;
        for (int attempt = 0; attempt < 200 && seed == null; attempt++) {
            HexCoordinate candidate = new HexCoordinate(
                    random.nextInt(radius * 2 + 1) - radius, random.nextInt(radius * 2 + 1) - radius);
            if (canTake(candidate, new HashSet<HexCoordinate>(), land)) {
                seed = candidate;
            }
        }
        if (seed == null) {
            return;
        }

        List<HexCoordinate> blobList = new ArrayList<HexCoordinate>();
        Set<HexCoordinate> blob = new HashSet<HexCoordinate>();
        blobList.add(seed);
        blob.add(seed);

        for (int attempt = 0; attempt < targetSize * 40 && blob.size() < targetSize; attempt++) {
            HexCoordinate from = blobList.get(random.nextInt(blobList.size()));
            List<HexCoordinate> neighbors = from.neighbors();
            HexCoordinate next = neighbors.get(random.nextInt(neighbors.size()));
            if (blob.contains(next) || !canTake(next, blob, land)) {
                continue;
            }
            // Prefer hexes touching the blob twice so shapes stay compact
            if (countIn(next, blob) < 2 && random.nextFloat() > 0.35f) {
                continue;
            }
            blob.add(next);
            blobList.add(next);
        }

        land.addAll(blob);
    }

    private boolean canTake(HexCoordinate coord, Set<HexCoordinate> blob, Set<HexCoordinate> land) {
        if (!isInside(coord) || land.contains(coord) || coord.distanceTo(START) <= START_CLEARANCE) {
            return false;
        }
        for (HexCoordinate neighbor : coord.neighbors()) {
            if (land.contains(neighbor) && !blob.contains(neighbor)) {
                return false;
            }
        }
        return true;
    }

    private int countIn(HexCoordinate coord, Set<HexCoordinate> set) {
        int count = 0;
        for (HexCoordinate neighbor : coord.neighbors()) {
            if (set.contains(neighbor)) {
                count++;
            }
        }
        return count;
    }

    private boolean touchesLand(HexCoordinate coord, Set<HexCoordinate> land) {
        return countIn(coord, land) > 0;
    }

    /** Non-land hexes connected to the start hex. */
    private Set<HexCoordinate> findSea(Set<HexCoordinate> land) {
        Set<HexCoordinate> sea = new HashSet<HexCoordinate>();
        Deque<HexCoordinate> queue = new ArrayDeque<HexCoordinate>();
        sea.add(START);
        queue.add(START);
        while (!queue.isEmpty()) {
            HexCoordinate current = queue.poll();
            for (HexCoordinate neighbor : current.neighbors()) {
                if (isInside(neighbor) && !land.contains(neighbor) && sea.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return sea;
    }

    private boolean reachesEdge(Set<HexCoordinate> sea) {
        for (HexCoordinate coord : sea) {
            if (Math.abs(coord.getQ()) == radius || Math.abs(coord.getR()) == radius) {
                return true;
            }
        }
        return false;
    }

    private void carveChannelFromStart(Set<HexCoordinate> land) {
        HexCoordinate current = START;
        while (isInside(current)) {
            land.remove(current);
            current = current.neighbor(HexDirection.E);
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

