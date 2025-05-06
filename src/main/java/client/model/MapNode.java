package client.model;

public class MapNode {
    private final Point position;
    private final Terrain terrain;
    private final TreasureState treasureState;
    private final FortState fortState;
    private final PlayerPositionState playerPositionState;
    
    public MapNode(Point position, Terrain terrain, TreasureState treasureState, 
                  FortState fortState, PlayerPositionState playerPositionState) {
        this.position = position;
        this.terrain = terrain;
        this.treasureState = treasureState;
        this.fortState = fortState;
        this.playerPositionState = playerPositionState;
    }
    
    public Point getPosition() {
        return position;
    }
    
    public Terrain getTerrain() {
        return terrain;
    }
    
    public TreasureState getTreasureState() {
        return treasureState;
    }
    
    public FortState getFortState() {
        return fortState;
    }
    
    public PlayerPositionState getPlayerPositionState() {
        return playerPositionState;
    }
    
    public boolean hasTreasure() {
        return treasureState == TreasureState.MY_TREASURE;
    }
    
    public boolean hasMyFort() {
        return fortState == FortState.MY_FORT;
    }
    
    public boolean hasEnemyFort() {
        return fortState == FortState.ENEMY_FORT;
    }
    
    public boolean hasMyPlayer() {
        return playerPositionState == PlayerPositionState.MY_PLAYER || 
               playerPositionState == PlayerPositionState.BOTH_PLAYERS;
    }
    
    public boolean hasEnemyPlayer() {
        return playerPositionState == PlayerPositionState.ENEMY_PLAYER || 
               playerPositionState == PlayerPositionState.BOTH_PLAYERS;
    }
    
    public boolean hasBothPlayers() {
        return playerPositionState == PlayerPositionState.BOTH_PLAYERS;
    }
    
    public boolean isTraversable() {
        return terrain.isTraversable();
    }
    
    public int getMovementCostTo(MapNode target) {
        return this.terrain.getLeaveCost() + target.terrain.getEnterCost();
    }
}
