package client.main;

import messagesbase.UniquePlayerIdentifier;
import messagesbase.messagesfromclient.PlayerRegistration;

/**
 * Main client class for the treasure hunt game.
 */
public class MainClient {
    
    /**
     * Main method to start the client.
     * 
     * @param args Command line arguments: gameMode, serverBaseUrl, gameId
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Not enough arguments! Required: gameMode, serverBaseUrl, gameId");
            return;
        }

        String gameMode = args[0];
        String serverBaseUrl = args[1];
        String gameId = args[2];

        System.out.println("Starting client with:");
        System.out.println("GameMode: " + gameMode);
        System.out.println("ServerBaseUrl: " + serverBaseUrl);
        System.out.println("GameId: " + gameId);
        
        // Create network handler
        NetworkHandler networkHandler = new NetworkHandler(serverBaseUrl);
        
        // Register player
        UniquePlayerIdentifier playerId = networkHandler.registerPlayer(
                gameId, 
                "Hamza", 
                "Atieh", 
                "Atiehh00");
        
        if (playerId == null) {
            System.err.println("Failed to register player. Exiting.");
            return;
        }
        
        System.out.println("Successfully registered with Player ID: " + playerId.getUniquePlayerID());
        
        // Create game controller and start the game
        GameController gameController;
        if (gameMode.equalsIgnoreCase("treasurehunt")) {
            gameController = new GameController(networkHandler, gameId, playerId, true);
        } else {
            gameController = new GameController(networkHandler, gameId, playerId);
        }
        
        // Start the game
        gameController.startGame();
    }
}
