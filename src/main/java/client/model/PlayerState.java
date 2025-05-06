package client.model;

public class PlayerState {
    private final String playerId;
    private final PlayerGameState state;
    private final boolean hasCollectedTreasure;
    
    public PlayerState(String playerId, PlayerGameState state, boolean hasCollectedTreasure) {
        this.playerId = playerId;
        this.state = state;
        this.hasCollectedTreasure = hasCollectedTreasure;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public PlayerGameState getState() {
        return state;
    }
    
    public boolean hasCollectedTreasure() {
        return hasCollectedTreasure;
    }
    
    public boolean isMyTurn() {
        return state == PlayerGameState.MUST_ACT;
    }
    
    public boolean hasWon() {
        return state == PlayerGameState.WON;
    }
    
    public boolean hasLost() {
        return state == PlayerGameState.LOST;
    }
}
