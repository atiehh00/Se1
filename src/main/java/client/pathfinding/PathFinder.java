package client.pathfinding;

import client.model.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import client.strategy.ExplorationStrategy;

public class PathFinder {
    private static final int MAX_PATH_LENGTH = 100;
    private static final int MAX_ITERATIONS = 5000;
    private static final int TIME_CHECK_INTERVAL = 50;
    
    private static final boolean DEBUG = false;

    public List<Direction> findPath(GameMap map, Point start, Point target, long timeBudgetMillis, Set<Point> visitedPositions, ExplorationStrategy explorationStrategy) {
        long startTime = System.currentTimeMillis();
        
        if (start.equals(target)) {
            return new ArrayList<>();
        }
        
        List<Direction> directPath = findDirectPath(map, start, target);
        if (!directPath.isEmpty()) {
            return limitPathLength(directPath);
        }
        
        int manhattanDistance = PathfindingHelper.calculateManhattanDistance(start, target);
        
        if (manhattanDistance > 10) {
            return getDirectionalPath(map, start, target);
        }

        int[] boundaries = null;
        if (explorationStrategy != null && explorationStrategy.isHalfInfoInitialized()) {
            boundaries = explorationStrategy.getRelevantHalfBoundaries();
        }

        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        Map<Point, AStarNode> allNodes = new HashMap<>();
        Set<Point> closedSet = new HashSet<>();
        
        AStarNode startNode = new AStarNode(start, null, 0, heuristic(start, target));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int iterations = 0;
        
        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;

            if (iterations % TIME_CHECK_INTERVAL == 0) {
                if (DEBUG) System.out.println("WARN: Pathfinding timed out after " + (System.currentTimeMillis() - startTime) + "ms");
                if (!allNodes.isEmpty()) {
                    List<Direction> partialPath = getPartialPath(allNodes, target, start);
                    if (DEBUG) System.out.println("Returning partial path of length " + partialPath.size());
                    return partialPath;
                }
                return Collections.emptyList();
            }
            
            AStarNode current = openSet.poll();
            
            closedSet.add(current.getPosition());
            if (current.getPosition().equals(target)) {
                return reconstructPath(current);
            }
            
            List<Point> neighbors = PathfindingHelper.getValidNeighbors(map, current.getPosition());
            
            for (Point neighborPoint : neighbors) {
                if (closedSet.contains(neighborPoint)) {
                    continue;
                }
                
                int moveCost = PathfindingHelper.calculateMovementCost(map, current.getPosition(), neighborPoint, visitedPositions, explorationStrategy);
                if (moveCost == Integer.MAX_VALUE) {
                    continue;
                }
                
                int tentativeGScore = current.getGScore() + moveCost;

                AStarNode neighborAStarNode = allNodes.get(neighborPoint);
                if (neighborAStarNode == null) {
                    neighborAStarNode = new AStarNode(
                        neighborPoint, 
                        current, 
                        tentativeGScore, 
                        heuristic(neighborPoint, target)
                    );
                    allNodes.put(neighborPoint, neighborAStarNode);
                    openSet.add(neighborAStarNode);
                } 
                else if (tentativeGScore < neighborAStarNode.getGScore()) {
                    openSet.remove(neighborAStarNode);

                    neighborAStarNode.update(current, tentativeGScore, heuristic(neighborPoint, target));

                    openSet.add(neighborAStarNode);
                }
            }
        }

        if (DEBUG) System.out.println("A* failed/timed out. Trying BFS fallback...");
        long remainingTimeForBFS = Math.max(20, timeBudgetMillis - (System.currentTimeMillis() - startTime)); // Min 20ms for BFS
        List<Direction> bfsPath = findDirectPathWithTimeout(map, start, target, remainingTimeForBFS);
        if (!bfsPath.isEmpty()) {
            if (DEBUG) System.out.println("BFS fallback found a path.");
            return limitPathLength(bfsPath);
        }

        if (DEBUG) System.out.println("BFS fallback also failed or timed out.");

        System.out.println("WARN: A* and BFS pathfinding failed. Falling back to single directional step.");
        return getDirectionalPath(map, start, target); 
    }
    
    private List<Direction> tryDirectPath(GameMap map, Point start, Point target) {
        List<Direction> path = new ArrayList<>();
        
        Point current = new Point(start.x, start.y);

        while (current.x != target.x) {
            Direction dir = current.x < target.x ? Direction.RIGHT : Direction.LEFT;
            Point next = dir.move(current);

            Optional<MapNode> nextNode = map.getNode(next);
            if (!nextNode.isPresent() || !nextNode.get().isTraversable()) {
                return new ArrayList<>(); 
            }
            
            path.add(dir);
            current = next;
        }

        while (current.y != target.y) {
            Direction dir = current.y < target.y ? Direction.DOWN : Direction.UP;
            Point next = dir.move(current);
            Optional<MapNode> nextNode = map.getNode(next);
            if (!nextNode.isPresent() || !nextNode.get().isTraversable()) {
                return new ArrayList<>();
            }
            
            path.add(dir);
            current = next;
        }
        
        return path;
    }
    
    private List<Direction> reconstructPath(AStarNode targetNode) {
        List<Direction> path = new ArrayList<>();
        AStarNode current = targetNode;
        AStarNode parent = current.getParent();
        
        while (parent != null) {
            Direction dir = PathfindingHelper.getDirection(parent.getPosition(), current.getPosition());
            if (dir != null) {
                path.add(dir);
            }
            current = parent;
            parent = current.getParent();
        }
        Collections.reverse(path);
        return limitPathLength(path);
    }
    
    private List<Direction> getPartialPath(Map<Point, AStarNode> allNodes, Point target, Point start) {
        AStarNode closest = findClosestToTarget(allNodes.values(), target);
        if (closest != null && !closest.getPosition().equals(start)) {
            return reconstructPath(closest);
        }
        return new ArrayList<>();
    }
    
    private AStarNode findClosestToTarget(Collection<AStarNode> nodes, Point target) {
        AStarNode closest = null;
        int closestDistance = Integer.MAX_VALUE;
        
        for (AStarNode node : nodes) {
            int distance = heuristic(node.getPosition(), target);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node;
            }
        }
        
        return closest;
    }
    
    private int heuristic(Point a, Point b) {
        return PathfindingHelper.calculateManhattanDistance(a, b);
    }

    private List<Direction> limitPathLength(List<Direction> path) {
        if (path.size() <= MAX_PATH_LENGTH) {
            return path;
        }
        return path.subList(0, MAX_PATH_LENGTH);
    }

    public List<Direction> getDirectionalPath(GameMap map, Point start, Point target) {
        List<Direction> path = new ArrayList<>();
        int dx = target.x - start.x;
        int dy = target.y - start.y;
        
        boolean prioritizeX = Math.abs(dx) >= Math.abs(dy);
        
        if (prioritizeX) {
            Direction xDir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
            for (int i = 0; i < Math.abs(dx); i++) {
                path.add(xDir);
            }
            
            Direction yDir = dy > 0 ? Direction.DOWN : Direction.UP;
            for (int i = 0; i < Math.abs(dy); i++) {
                path.add(yDir);
            }
        } else {
            Direction yDir = dy > 0 ? Direction.DOWN : Direction.UP;
            for (int i = 0; i < Math.abs(dy); i++) {
                path.add(yDir);
            }
            
            Direction xDir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
            for (int i = 0; i < Math.abs(dx); i++) {
                path.add(xDir);
            }
        }
        
        return path;
    }

    private boolean isValidDirection(GameMap map, Point position, Direction direction) {
        Point newPos = direction.move(position);
        Optional<MapNode> nodeOpt = map.getNode(newPos);
        return nodeOpt.isPresent() && nodeOpt.get().isTraversable();
    }

    public List<Direction> findDirectPath(GameMap map, Point start, Point target) {
        if (map == null || start == null || target == null) {
            return Collections.emptyList();
        }
        
        if (DEBUG) System.out.println("Calculating direct path from " + start + " to " + target);
        
        Optional<MapNode> startNodeOpt = map.getNode(start);
        Optional<MapNode> targetNodeOpt = map.getNode(target);
        
        if (!startNodeOpt.isPresent() || !targetNodeOpt.isPresent()) {
            return Collections.emptyList();
        }
        
        if (!targetNodeOpt.get().isTraversable()) {
            return Collections.emptyList();
        }
        
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();
        Map<Point, Point> parentMap = new HashMap<>();
        Map<Point, Integer> distanceMap = new HashMap<>();
        
        queue.add(start);
        visited.add(start);
        distanceMap.put(start, 0);
        
        boolean pathFound = false;
        
        while (!queue.isEmpty() && !pathFound) {
            Point current = queue.poll();
            int currentDistance = distanceMap.get(current);
           
            if (current.equals(target)) {
                pathFound = true;
                break;
            }
          
            for (Direction dir : Direction.values()) {
                Point next = dir.move(current);
                if (visited.contains(next)) {
                    continue;
                }
                
                Optional<MapNode> nextNodeOpt = map.getNode(next);
                if (!nextNodeOpt.isPresent() || !nextNodeOpt.get().isTraversable()) {
                    continue;
                }
               
                MapNode nextNode = nextNodeOpt.get();
                if (nextNode.getTerrain() == Terrain.GRASS) {
                    visited.add(next);
                    parentMap.put(next, current);
                    distanceMap.put(next, currentDistance + 1);
                    
                    if (next.equals(target)) {
                        pathFound = true;
                        break;
                    }
                    
                    queue.add(next);
                }
            }

            if (!pathFound) {
                for (Direction dir : Direction.values()) {
                    Point next = dir.move(current);
                    if (visited.contains(next)) {
                        continue;
                    }
                    
                    Optional<MapNode> nextNodeOpt = map.getNode(next);
                    if (!nextNodeOpt.isPresent() || !nextNodeOpt.get().isTraversable()) {
                        continue;
                    }

                    MapNode nextNode = nextNodeOpt.get();
                    if (nextNode.getTerrain() == Terrain.MOUNTAIN) {
                        visited.add(next);
                        parentMap.put(next, current);
                        distanceMap.put(next, currentDistance + 1);
                        
                        if (next.equals(target)) {
                            pathFound = true;
                            break;
                        }
                        
                        queue.add(next);
                    }
                }
            }

            if (visited.size() > MAX_PATH_LENGTH * 2) {
                break;
            }
        }

        if (!parentMap.containsKey(target)) {
            if (DEBUG) System.out.println("No direct path found");
            return Collections.emptyList();
        }

        List<Direction> path = new ArrayList<>();
        Point current = target;
        
        while (!current.equals(start)) {
            Point parent = parentMap.get(current);
            
            int dx = current.x - parent.x;
            int dy = current.y - parent.y;
            
            if (dx == 1) path.add(0, Direction.RIGHT);
            else if (dx == -1) path.add(0, Direction.LEFT);
            else if (dy == 1) path.add(0, Direction.DOWN);
            else if (dy == -1) path.add(0, Direction.UP);
            
            current = parent;
        }
        
        if (DEBUG) System.out.println("Direct path found with length: " + path.size());
        return path;
    }

    public List<Direction> findDirectPathWithTimeout(GameMap map, Point start, Point goal, long timeBudgetMillis) {
        long startTime = System.currentTimeMillis();
        Queue<Point> queue = new LinkedList<>();
        Set<Point> visited = new HashSet<>();
        Map<Point, Direction> cameFromDirection = new HashMap<>();

        if (!map.getNode(start).isPresent() || !map.getNode(goal).isPresent() ||
            !map.getNode(start).get().isTraversable() || !map.getNode(goal).get().isTraversable()) {
            return Collections.emptyList();
        }

        queue.add(start);
        visited.add(start);

        int iterations = 0;
        while (!queue.isEmpty()) {
            iterations++;
            if (iterations % TIME_CHECK_INTERVAL == 0) {
                if (DEBUG) System.out.println("WARN: BFS pathfinding timed out after " + (System.currentTimeMillis() - startTime) + "ms");
                return Collections.emptyList();
            }

            Point current = queue.poll();

            if (current.equals(goal)) {
                return reconstructDirectPath(cameFromDirection, start, goal);
            }

            for (Direction direction : Direction.values()) {
                Point neighborPos = direction.move(current);
                Optional<MapNode> neighborNodeOpt = map.getNode(neighborPos);

                if (neighborNodeOpt.isPresent() && 
                    neighborNodeOpt.get().isTraversable() && 
                    !visited.contains(neighborPos)) {
                    
                    visited.add(neighborPos);
                    cameFromDirection.put(neighborPos, direction); 
                    queue.add(neighborPos);
                }
            }
        }

        return Collections.emptyList(); 
    }

    
    private List<Direction> reconstructDirectPath(Map<Point, Direction> cameFromDirection, Point start, Point goal) {
        LinkedList<Direction> path = new LinkedList<>();
        Point current = goal;
        
        while (!current.equals(start)) {
            Direction dir = cameFromDirection.get(current);
            if (dir == null) {
                return Collections.emptyList(); 
            }
            path.addFirst(dir);
            current = dir.getOpposite().move(current); 
        }
        
        return path;
    }
    public List<Direction> findPath(GameMap map, Point start, Point target, long timeBudgetMillis, Set<Point> visitedPositions) {
        return findPath(map, start, target, timeBudgetMillis, visitedPositions, null);
    }
}
