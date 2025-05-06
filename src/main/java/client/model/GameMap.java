package client.model;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public class GameMap {
    private final Map<Point, MapNode> nodes;
    private int maxX = 0;
    private int maxY = 0;
    
    public GameMap() {
        this.nodes = new HashMap<>();
    }
    
    public GameMap(List<MapNode> nodes) {
        this.nodes = new HashMap<>();
        for (MapNode node : nodes) {
            addNode(node);
        }
    }
    
    public void addNode(MapNode node) {
        Point pos = node.getPosition();
        nodes.put(pos, node);
        maxX = Math.max(maxX, pos.x);
        maxY = Math.max(maxY, pos.y);
    }
    
    public Optional<MapNode> getNode(Point position) {
        return Optional.ofNullable(nodes.get(position));
    }
    
    public Optional<MapNode> getNode(int x, int y) {
        return getNode(new Point(x, y));
    }
    
    public List<MapNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }
    
    public int getMaxX() {
        return maxX;
    }
    
    public int getMaxY() {
        return maxY;
    }
    
    public int getMapWidth() {
        return maxX + 1;
    }

    public int getMapHeight() {
        return maxY + 1;
    }
    
    public Optional<MapNode> getPlayerPosition() {
        for (MapNode node : nodes.values()) {
            if (node.hasMyPlayer()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
    
    public Optional<MapNode> getEnemyPosition() {
        for (MapNode node : nodes.values()) {
            if (node.hasEnemyPlayer()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
    
    public Optional<MapNode> getTreasurePosition() {
        for (MapNode node : nodes.values()) {
            if (node.hasTreasure()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
    
    public Optional<MapNode> getMyFortPosition() {
        for (MapNode node : nodes.values()) {
            if (node.hasMyFort()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
    
    public Optional<MapNode> getEnemyFortPosition() {
        for (MapNode node : nodes.values()) {
            if (node.hasEnemyFort()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
    
    public List<MapNode> getNeighbors(Point position) {
        List<MapNode> neighbors = new ArrayList<>();
        
        for (Direction dir : Direction.values()) {
            Point neighborPos = dir.move(position);
            getNode(neighborPos).ifPresent(neighbors::add);
        }
        
        return neighbors;
    }
    
    public List<MapNode> getTraversableNeighbors(Point position) {
        List<MapNode> neighbors = new ArrayList<>();
        
        for (Direction dir : Direction.values()) {
            Point neighborPos = dir.move(position);
            getNode(neighborPos)
                .filter(MapNode::isTraversable)
                .ifPresent(neighbors::add);
        }
        
        return neighbors;
    }
}
