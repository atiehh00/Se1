package client.main;

import messagesbase.UniquePlayerIdentifier;
import messagesbase.messagesfromclient.PlayerHalfMap;
import messagesbase.messagesfromclient.PlayerMove;
import messagesbase.messagesfromclient.EMove;
import messagesbase.messagesfromclient.ETerrain;

import java.util.Random;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

// Import our model classes
import client.model.*;
import client.converter.*;

public class GameController {
  private final NetworkHandler networkHandler;
  private final String gameId;
  private final UniquePlayerIdentifier playerId;
  private final MapGenerator mapGenerator;
  private final MovementStrategyAdapter movementStrategy;
  private boolean halfMapSent = false;
  private boolean waitingForOpponent = false;
  private int moveCount = 0;
  private Point lastPosition = null;
  private EMove lastAttemptedMove = null;
  private final long pollingInterval = 400;
  private boolean gameOver = false;
  private final long moveBudgetMillis = 1000L; 
  private static final boolean DEBUG = false;
  
 GameController(NetworkHandler networkHandler, String gameId, UniquePlayerIdentifier playerId) {
      this.networkHandler = networkHandler;
      this.gameId = gameId;
      this.playerId = playerId;
      this.mapGenerator = new MapGenerator();
      this.movementStrategy = new MovementStrategyAdapter();
  }
  
  public GameController(NetworkHandler networkHandler, String gameId, UniquePlayerIdentifier playerId, boolean isTreasureHuntMode) {
      this.networkHandler = networkHandler;
      this.gameId = gameId;
      this.playerId = playerId;
      this.mapGenerator = new MapGenerator();
      this.movementStrategy = new MovementStrategyAdapter();
      
  }
  
public void startGame() {
    System.out.println("Starting game...");

    boolean gameRunning = true;
    String lastGameStateId = "";
    int consecutiveErrors = 0;
    int maxConsecutiveErrors = 3;
    
    final int MAX_MOVES_LIMIT = 150;
    
    GameState lastKnownGameState = null; 
    
    while (gameRunning) {
        try {
            Thread.sleep(pollingInterval); 
            
            if (gameOver || networkHandler.isGameEnded()) {
                System.out.println("Game is already over. Exiting game loop.");
                gameOver = true;
                networkHandler.setGameEnded(true);
                gameRunning = false;
                break;
            }
            
            GameState gameState = networkHandler.getGameStateModel(gameId, playerId);
            
            if (gameState != null) {
                lastKnownGameState = gameState;

                if (isGameOver(gameState)) {
                    System.out.println("Game is over. Exiting game loop.");
                    gameOver = true;
                    networkHandler.setGameEnded(true);
                    gameRunning = false;
                    break;
                }
                
                if (!gameState.getGameStateId().equals(lastGameStateId) || !halfMapSent) {
                    lastGameStateId = gameState.getGameStateId();
                    
                    displayGameState(gameState);
                    
                    boolean isOurTurn = gameState.isMyTurn(playerId.getUniquePlayerID());
                    
                    if (isOurTurn) {
                        waitingForOpponent = false;
                        if (gameOver || networkHandler.isGameEnded()) {
                            System.out.println("Game ended while processing. Exiting game loop.");
                            networkHandler.setGameEnded(true);
                            gameRunning = false;
                            break;
                        }

                        if (!halfMapSent) {
                            System.out.println("Generating half map...");
                            long mapStart = System.currentTimeMillis();
                            PlayerHalfMap halfMap = mapGenerator.generateHalfMap(playerId.getUniquePlayerID());
                            long mapDur = System.currentTimeMillis() - mapStart;
                            if (DEBUG) System.out.println("generateHalfMap() took " + mapDur + "ms");
                            
                            if (gameOver || networkHandler.isGameEnded()) {
                                System.out.println("Game ended before sending half map. Exiting game loop.");
                                networkHandler.setGameEnded(true);
                                gameRunning = false;
                                break;
                            }
                            
                            System.out.println("Half map generated, sending to server...");
                            boolean success = networkHandler.sendHalfMap(gameId, halfMap);
                            if (success) {
                                halfMapSent = true;
                                System.out.println("Half map sent successfully!");
                                
                                continue;
                            } else {
                                System.err.println("Failed to send half map. Checking if game ended...");
                                
                                if (networkHandler.isGameEnded()) {
                                    System.out.println("Game appears to be over after half map send failure.");
                                    gameOver = true;
                                    gameRunning = false;
                                    break;
                                }
                            }
                        } else {
                            if (gameOver || networkHandler.isGameEnded()) {
                                System.out.println("Game ended before calculating move. Exiting game loop.");
                                networkHandler.setGameEnded(true);
                                gameRunning = false;
                                break;
                            }
                            
                            Direction nextDirection = getNextMove(gameState);

                            System.out.println("Player position: " + gameState.getMap().getPlayerPosition().map(MapNode::getPosition).orElse(null));
                            System.out.println("Has collected treasure: " + gameState.hasCollectedTreasure(playerId.getUniquePlayerID()));
                            System.out.println("Calculated move: " + nextDirection);
                            
                            if (gameOver || networkHandler.isGameEnded()) {
                                System.out.println("Game ended before sending move. Exiting game loop.");
                                networkHandler.setGameEnded(true);
                                gameRunning = false;
                                break;
                            }
                            
                            EMove serverMove = DirectionConverter.toEMove(nextDirection);
                            

                            PlayerMove move = PlayerMove.of(playerId.getUniquePlayerID(), serverMove);
                            boolean moveSuccess = networkHandler.sendMove(gameId, move);
                            if (moveSuccess) {
                                System.out.println("Move sent successfully!");
                                moveCount++;
                            } else {
                                System.err.println("Failed to send move. Checking if game ended...");
                                
                                if (networkHandler.isGameEnded()) {
                                    System.out.println("Game appears to be over after move send failure.");
                                    gameOver = true;
                                    gameRunning = false;
                                    break;
                                }
                            }
                        }
                    } else {
                        if (!waitingForOpponent) {
                            waitingForOpponent = true;
                            System.out.println("Waiting for opponent to act...");
                        }
                    }
                }
            } else {
                System.err.println("Failed to get game state. Checking if game ended...");
                
                if (networkHandler.isGameEnded()) {
                    System.out.println("Game appears to be over after game state retrieval failure.");
                    gameOver = true;
                    gameRunning = false;
                    break;
                }
                
                consecutiveErrors++;
                if (consecutiveErrors > maxConsecutiveErrors) {
                    System.err.println("Too many consecutive errors. Assuming game has ended.");
                    gameOver = true;
                    networkHandler.setGameEnded(true);
                    gameRunning = false;
                    break;
                }
            }
            if (moveCount >= MAX_MOVES_LIMIT) {
                System.out.println("WARNING: Approaching move limit (" + moveCount + "/" + MAX_MOVES_LIMIT + "). Terminating game loop.");
                break;
            }
        } catch (InterruptedException e) {
            System.err.println("Game loop interrupted: " + e.getMessage());
            gameRunning = false;
        } catch (Exception e) {
            System.err.println("Unexpected error in game loop: " + e.getMessage());
            e.printStackTrace();
            
            String errorMsg = e.getMessage();
            if (errorMsg != null && (
                errorMsg.contains("game has ended") || 
                errorMsg.contains("won or lost") ||
                errorMsg.contains("game is over") ||
                errorMsg.contains("game over"))) {
                
                System.out.println("Game appears to be over based on error message.");
                gameOver = true;
                networkHandler.setGameEnded(true);
                gameRunning = false;
                break;
            }
            
            consecutiveErrors++;
            if (consecutiveErrors > maxConsecutiveErrors) {
                System.err.println("Too many consecutive errors. Assuming game has ended.");
                gameOver = true;
                networkHandler.setGameEnded(true);
                gameRunning = false;
                break;
            }
        }
    }
    
    System.out.println("Game loop ended. Total moves made: " + moveCount);
}

private boolean isGameOver(GameState gameState) {
    for (PlayerState player : gameState.getPlayers()) {
        if (player.getPlayerId().equals(playerId.getUniquePlayerID())) {
            if (player.hasWon()) {
                System.out.println("You won the game! Congratulations!");
                return true;
            } else if (player.hasLost()) {
                System.out.println("You lost the game. Better luck next time!");
                return true;
            }
            break;
        }
    }
    String gameStateId = gameState.getGameStateId().toLowerCase();
    if (gameStateId.contains("gameover") || 
        gameStateId.contains("game_over") ||
        gameStateId.contains("end") ||
        gameStateId.contains("won") ||
        gameStateId.contains("lost") ||
        gameStateId.contains("terminated")) {
        System.out.println("Game appears to be over based on game state ID: " + gameState.getGameStateId());
        return true;
    }
    
    return false;
}

private void displayGameState(GameState gameState) {
    System.out.println("Game State: " + gameState.getGameStateId() + " | Moves: " + moveCount);
    Optional<MapNode> playerPos = gameState.getMap().getPlayerPosition();
    if (playerPos.isPresent()) {
        System.out.println("Position: " + playerPos.get().getPosition());
    }
    Optional<PlayerState> playerState = gameState.getMyPlayerState(playerId.getUniquePlayerID());
    if (playerState.isPresent()) {
        System.out.println("Has treasure: " + playerState.get().hasCollectedTreasure());
    }
}

private static class Point {
    final int x;
    final int y;
    
    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}

private void updateFortBacktracker(GameState gameState) {
    Optional<MapNode> enemyNodeOpt = gameState.getMap().getEnemyPosition();
    if (enemyNodeOpt.isPresent()) {
        client.model.Point enemyPos = enemyNodeOpt.get().getPosition();
        movementStrategy.trackOpponentPosition(enemyPos, gameState.getMap());
    }
}

private Direction getNextMove(GameState gameState) {
    if (DEBUG) System.out.println("Calculating next move...");
    long moveStart = System.currentTimeMillis();
    updateFortBacktracker(gameState);
    Direction nextDirection = movementStrategy.calculateNextMove(gameState, playerId.getUniquePlayerID(), moveBudgetMillis);
    long moveDur = System.currentTimeMillis() - moveStart;
    if (DEBUG) System.out.println("calculateNextMove() took " + moveDur + "ms");
    return nextDirection;
}
}
