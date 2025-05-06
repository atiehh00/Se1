package client.model;

import java.util.List;
import java.util.Optional;

public class GameState {
    private final String gameStateId;
    private final GameMap map;
    private final List<PlayerState> players;
    
    public GameState(String gameStateId, GameMap map, List<PlayerState> players) {
        this.gameStateId = gameStateId;
        this.map = map;
        this.players = players;
    }
    
    public String getGameStateId() {
        return gameStateId;
    }
    
    public GameMap getMap() {
        return map;
    }
    
    public List<PlayerState> getPlayers() {
        return players;
    }
    
    public Optional<PlayerState> getMyPlayerState(String playerId) {
        return players.stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst();
    }
    
    public boolean hasCollectedTreasure(String playerId) {
        return getMyPlayerState(playerId)
            .map(PlayerState::hasCollectedTreasure)
            .orElse(false);
    }
    
    public boolean isMyTurn(String playerId) {
        return getMyPlayerState(playerId)
            .map(PlayerState::isMyTurn)
            .orElse(false);
    }
    
    public boolean isGameOver() {
        return players.stream()
            .anyMatch(p -> p.hasWon() || p.hasLost());
    }
    
    public boolean hasWon(String playerId) {
        return getMyPlayerState(playerId)
            .map(PlayerState::hasWon)
            .orElse(false);
    }
}
