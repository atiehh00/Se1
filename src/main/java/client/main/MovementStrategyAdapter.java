package client.main;

import client.model.*;
import client.strategy.MovementStrategy;
import client.converter.*;
import messagesbase.messagesfromclient.PlayerMove;

public class MovementStrategyAdapter {
    private final MovementStrategy movementStrategy;
    
    public MovementStrategyAdapter() {
        this.movementStrategy = new MovementStrategy();
    }
    
    public Direction calculateNextMove(client.model.GameState gameState, String playerId, Long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        

        logGameState(gameState, playerId);
        
        Direction direction = movementStrategy.calculateNextMove(gameState, playerId, timeoutMillis);
        
        System.out.println("Calculated move: " + direction + " (took " + 
                          (System.currentTimeMillis() - startTime) + "ms)");
        
        return direction;
    }

    private void logGameState(client.model.GameState gameState, String playerId) {
        gameState.getMap().getPlayerPosition().ifPresent(node -> {
            System.out.println("Player position: " + node.getPosition());
        });
        
        gameState.getMyPlayerState(playerId).ifPresent(player -> {
            System.out.println("Has collected treasure: " + player.hasCollectedTreasure());
        });
        
        gameState.getMap().getTreasurePosition().ifPresent(node -> {
            System.out.println("Treasure position: " + node.getPosition());
        });
        
        gameState.getMap().getEnemyFortPosition().ifPresent(node -> {
            System.out.println("Enemy fort position: " + node.getPosition());
        });
    }

    public void resetPath() {
        movementStrategy.resetPath();
    }

    public void trackOpponentPosition(client.model.Point enemyPos, client.model.GameMap gameMap) {
        movementStrategy.trackOpponentPosition(enemyPos, gameMap);
    }
}
