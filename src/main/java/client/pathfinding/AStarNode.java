package client.pathfinding;

import client.model.Point;
import java.util.Objects;

public class AStarNode implements Comparable<AStarNode> {
    private final Point position;
    private AStarNode parent;
    private int gScore; 
    private int hScore; 
    private int fScore; 

    public AStarNode(Point position, AStarNode parent, int gScore, int hScore) {
        this.position = position;
        this.parent = parent;
        this.gScore = gScore;
        this.hScore = hScore;
        this.fScore = gScore + hScore;
    }

    public void update(AStarNode parent, int gScore, int hScore) {
        this.parent = parent;
        this.gScore = gScore;
        this.hScore = hScore;
        this.fScore = gScore + hScore;
    }

    public Point getPosition() {
        return position;
    }

    public AStarNode getParent() {
        return parent;
    }
    
    public int getGScore() {
        return gScore;
    }

    public int getHScore() {
        return hScore;
    }

    public int getFScore() {
        return fScore;
    }

    @Override
    public int compareTo(AStarNode other) {
        return Integer.compare(this.fScore, other.fScore);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AStarNode other = (AStarNode) obj;
        return Objects.equals(position, other.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position);
    }

    @Override
    public String toString() {
        return "AStarNode{position=" + position + ", f=" + fScore + ", g=" + gScore + ", h=" + hScore + "}";
    }
} 