package client.model;

public enum Terrain {
    GRASS,
    MOUNTAIN,
    WATER;
    
    public int getEnterCost() {
        switch (this) {
            case GRASS:
                return 1;
            case MOUNTAIN:
                return 2;
            case WATER:
                return Integer.MAX_VALUE;
            default:
                return 1;
        }
    }
    
    public int getLeaveCost() {
        switch (this) {
            case GRASS:
                return 1;
            case MOUNTAIN:
                return 2;
            default:
                return 1;
        }
    }
    
    public boolean isTraversable() {
        return this != WATER;
    }
}
