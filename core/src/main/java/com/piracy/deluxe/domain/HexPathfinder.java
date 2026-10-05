package com.piracy.deluxe.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class HexPathfinder {
    private HexPathfinder() {
    }

    public static List<HexCoordinate> findPath(HexMap map, HexCoordinate start, HexCoordinate goal) {
        if (start == null || goal == null || !map.isInside(start) || !map.isInside(goal)) {
            return new ArrayList<HexCoordinate>();
        }

        if (start.equals(goal)) {
            List<HexCoordinate> result = new ArrayList<HexCoordinate>();
            result.add(start);
            return result;
        }

        PriorityQueue<PathNode> openSet = new PriorityQueue<PathNode>(Comparator.comparingInt(node -> node.fScore));
        Map<HexCoordinate, Integer> gScore = new HashMap<HexCoordinate, Integer>();
        Map<HexCoordinate, HexCoordinate> cameFrom = new HashMap<HexCoordinate, HexCoordinate>();

        openSet.add(new PathNode(start, heuristic(start, goal), 0));
        gScore.put(start, 0);

        while (!openSet.isEmpty()) {
            PathNode current = openSet.poll();
            if (current.coordinate.equals(goal)) {
                return reconstructPath(cameFrom, current.coordinate);
            }

            for (HexCoordinate neighbor : current.coordinate.neighbors()) {
                if (!map.isInside(neighbor)) {
                    continue;
                }

                HexTile tile = map.getTile(neighbor);
                if (tile == null) {
                    continue;
                }

                int movementCost = terrainCost(tile.getTerrain());
                int tentativeGScore = gScore.getOrDefault(current.coordinate, Integer.MAX_VALUE) + movementCost;

                if (tentativeGScore < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current.coordinate);
                    gScore.put(neighbor, tentativeGScore);
                    openSet.add(new PathNode(neighbor, tentativeGScore + heuristic(neighbor, goal), tentativeGScore));
                }
            }
        }

        return new ArrayList<HexCoordinate>();
    }

    private static int heuristic(HexCoordinate current, HexCoordinate goal) {
        int dx = Math.abs(current.getQ() - goal.getQ());
        int dy = Math.abs(current.getR() - goal.getR());
        return dx + dy;
    }

    private static int terrainCost(HexTile.Terrain terrain) {
        switch (terrain) {
            case ISLAND:
                return 4;
            case SHALLOW:
                return 2;
            case WATER:
            default:
                return 1;
        }
    }

    private static List<HexCoordinate> reconstructPath(Map<HexCoordinate, HexCoordinate> cameFrom, HexCoordinate current) {
        List<HexCoordinate> path = new ArrayList<HexCoordinate>();
        path.add(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }

        return path;
    }

    private static class PathNode {
        private final HexCoordinate coordinate;
        private final int fScore;
        private final int gScore;

        private PathNode(HexCoordinate coordinate, int fScore, int gScore) {
            this.coordinate = coordinate;
            this.fScore = fScore;
            this.gScore = gScore;
        }
    }
}
