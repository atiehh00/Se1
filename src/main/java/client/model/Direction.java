package client.model;

public enum Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT;
    public Direction getOpposite() {
        switch (this) {
            case UP: return DOWN;
            case DOWN: return UP;
            case LEFT: return RIGHT;
            case RIGHT: return LEFT;
            default: throw new IllegalStateException("Unexpected value: " + this);
        }
    }
    
    public Point move(Point from) {
        switch (this) {
            case UP: return new Point(from.x, from.y - 1);
            case DOWN: return new Point(from.x, from.y + 1);
            case LEFT: return new Point(from.x - 1, from.y);
            case RIGHT: return new Point(from.x + 1, from.y);
            default: return from; 
        }
    }

    public Direction turnClockwise() {
        switch (this) {
            case UP:    return RIGHT;
            case RIGHT: return DOWN;
            case DOWN:  return LEFT;
            case LEFT:  return UP;
            default: throw new IllegalStateException("Unexpected value: " + this);
        }
    }

    public Direction turnCounterClockwise() {
        switch (this) {
            case UP:    return LEFT;
            case LEFT:  return DOWN;
            case DOWN:  return RIGHT;
            case RIGHT: return UP;
            default: throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
