package client.pathfinding;

import client.model.*;
import java.util.*;

public class PathfindingHelper {
    
    public static int calculateManhattanDistance(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
    
    public static int calculateMovementCost(GameMap map, Point from, Point to, Set<Point> visitedPositions) {
        Optional<MapNode> fromNodeOpt = map.getNode(from);
        Optional<MapNode> toNodeOpt = map.getNode(to);
        
        if (!fromNodeOpt.isPresent() || !toNodeOpt.isPresent()) {
            return Integer.MAX_VALUE;
        }

        MapNode fromNode = fromNodeOpt.get();
        MapNode toNode = toNodeOpt.get();
        
        Terrain fromTerrain = fromNode.getTerrain();
        Terrain toTerrain = toNode.getTerrain();

        int baseCost;
        if (fromTerrain == Terrain.GRASS && toTerrain == Terrain.GRASS) {
            baseCost = 2;
        } else if (fromTerrain == Terrain.MOUNTAIN && toTerrain == Terrain.MOUNTAIN) {
            baseCost = 6;
        } else if (fromTerrain == Terrain.GRASS && toTerrain == Terrain.MOUNTAIN) {
            baseCost = 5;
        } else { 
            baseCost = 5; 
        }

        if (visitedPositions != null && visitedPositions.contains(to)) {
            baseCost += 30;
        }

        return baseCost;
    }

    public static int calculateMovementCost(GameMap map, Point from, Point to, Set<Point> visitedPositions, client.strategy.ExplorationStrategy explorationStrategy) {
        Optional<MapNode> fromNodeOpt = map.getNode(from);
        Optional<MapNode> toNodeOpt = map.getNode(to);
        
        if (!fromNodeOpt.isPresent() || !toNodeOpt.isPresent()) {
            return Integer.MAX_VALUE;
        }

        MapNode fromNode = fromNodeOpt.get();
        MapNode toNode = toNodeOpt.get();
        
        Terrain fromTerrain = fromNode.getTerrain();
        Terrain toTerrain = toNode.getTerrain();

        int baseCost;
        if (fromTerrain == Terrain.GRASS && toTerrain == Terrain.GRASS) {
            baseCost = 2; 
        } else if (fromTerrain == Terrain.MOUNTAIN && toTerrain == Terrain.MOUNTAIN) {
            baseCost = 6;
        } else if (fromTerrain == Terrain.GRASS && toTerrain == Terrain.MOUNTAIN) {
            baseCost = 5;
        } else {
            baseCost = 5; 
        }

        if (visitedPositions != null && visitedPositions.contains(to)) {
             baseCost += 30;
        }

        if (explorationStrategy != null) {
            if (!explorationStrategy.isInRelevantHalf(to, map)) {
                baseCost += 75;
            }
        }

        return baseCost;
    }
    public static List<Point> getValidNeighbors(GameMap map, Point position) {
        List<Point> neighbors = new ArrayList<>();
        
        for (Direction direction : Direction.values()) {
            Point neighborPos = direction.move(position);
            Optional<MapNode> nodeOpt = map.getNode(neighborPos);
            
            if (nodeOpt.isPresent() && nodeOpt.get().isTraversable()) {
                neighbors.add(neighborPos);
            }
        }
        
        return neighbors;
    }
    
    public static Direction getDirection(Point from, Point to) {
        if (to.x > from.x && to.y == from.y) return Direction.RIGHT;
        if (to.x < from.x && to.y == from.y) return Direction.LEFT;
        if (to.y > from.y && to.x == from.x) return Direction.DOWN;
        if (to.y < from.y && to.x == from.x) return Direction.UP;
        return null;
    }
    
    public static Direction[] prioritizeDirections(Point current, Point target) {
        Direction[] directions = new Direction[4];

        boolean horizontalFirst = Math.abs(current.x - target.x) >= Math.abs(current.y - target.y);
        
        if (horizontalFirst) {
            if (current.x < target.x) {
                directions[0] = Direction.RIGHT;
                directions[3] = Direction.LEFT;
            } else {
                directions[0] = Direction.LEFT;
                directions[3] = Direction.RIGHT;
            }
            if (current.y < target.y) {
                directions[1] = Direction.DOWN;
                directions[2] = Direction.UP;
            } else {
                directions[1] = Direction.UP;
                directions[2] = Direction.DOWN;
            }
        } else {
            if (current.y < target.y) {
                directions[0] = Direction.DOWN;
                directions[3] = Direction.UP;
            } else {
                directions[0] = Direction.UP;
                directions[3] = Direction.DOWN;
            }
            if (current.x < target.x) {
                directions[1] = Direction.RIGHT;
                directions[2] = Direction.LEFT;
            } else {
                directions[1] = Direction.LEFT;
                directions[2] = Direction.RIGHT;
            }
        }
        
        return directions;
    }

    public static List<Direction> reconstructPath(AStarNode current) {
        List<Direction> path = new ArrayList<>();
        AStarNode parent = current.getParent();
        
        while (parent != null) {
            Point currentPos = current.getPosition();
            Point parentPos = parent.getPosition();

            int dx = currentPos.x - parentPos.x;
            int dy = currentPos.y - parentPos.y;
            
            if (dx == 1 && dy == 0) {
                path.add(0, Direction.RIGHT);
            } else if (dx == -1 && dy == 0) {
                path.add(0, Direction.LEFT);
            } else if (dx == 0 && dy == 1) {
                path.add(0, Direction.DOWN);
            } else if (dx == 0 && dy == -1) {
                path.add(0, Direction.UP);
            }
            
            current = parent;
            parent = current.getParent();
        }
        
        return path;
    }

    public static double evaluatePathVisibilityBenefit(GameMap map, List<Point> path, Set<Point> visitedPositions) {
        if (path == null || path.isEmpty()) {
            return 0.0;
        }
        
        double visibilityScore = 0.0;
        Set<Point> alreadyCounted = new HashSet<>(); 
        
        for (Point pos : path) {
            Optional<MapNode> nodeOpt = map.getNode(pos);
            if (!nodeOpt.isPresent()) {
                continue;
            }
            
            MapNode node = nodeOpt.get();
            if (node.getTerrain() == Terrain.MOUNTAIN) {
                int visibleNewTiles = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        Point visiblePos = new Point(pos.x + dx, pos.y + dy);
                        if (alreadyCounted.contains(visiblePos)) {
                            continue;
                        }
                        
                        Optional<MapNode> visibleNodeOpt = map.getNode(visiblePos);
                        if (visibleNodeOpt.isPresent() && 
                            visibleNodeOpt.get().isTraversable() && 
                            !visitedPositions.contains(visiblePos)) {
                            
                            visibleNewTiles++;
                            alreadyCounted.add(visiblePos);
                        }
                    }
                }
                
                visibilityScore += visibleNewTiles * 0.5;
            }
        }
        
        return visibilityScore;
    }
} 