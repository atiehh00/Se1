package client.pathfinding;

import client.model.Point;
import java.util.Objects;


public class PathNode implements Comparable<PathNode> {
    final Point point;
    PathNode parent;
    int gScore;
    int fScore;
    
    public PathNode(Point point, PathNode parent, int gScore, int fScore) {
        this.point = point;
        this.parent = parent;
        this.gScore = gScore;
        this.fScore = fScore;
    }
    
    @Override
    public int compareTo(PathNode other) {
        return Integer.compare(this.fScore, other.fScore);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathNode pathNode = (PathNode) o;
        return Objects.equals(point, pathNode.point);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(point);
    }
}
