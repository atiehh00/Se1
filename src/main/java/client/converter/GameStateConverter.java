package client.converter;

import client.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GameStateConverter {

   public static GameState fromServerGameState(messagesbase.messagesfromserver.GameState serverGameState) {
       if (serverGameState == null) {
           return null;
       }
       
       GameMap map = convertMap(serverGameState.getMap());
       List<messagesbase.messagesfromserver.PlayerState> playersList = 
           new ArrayList<>(serverGameState.getPlayers());
       List<PlayerState> players = convertPlayers(playersList);
       
       return new GameState(serverGameState.getGameStateId(), map, players);
   }

   public static messagesbase.messagesfromserver.GameState toServerGameState(GameState ourGameState) {
       throw new UnsupportedOperationException("Converting to server GameState is not supported");
   }

   private static GameMap convertMap(messagesbase.messagesfromserver.FullMap serverMap) {
       if (serverMap == null || serverMap.getMapNodes().isEmpty()) {
           return new GameMap();
       }
       
       List<MapNode> nodes = new ArrayList<>();
       
       for (messagesbase.messagesfromserver.FullMapNode serverNode : serverMap.getMapNodes()) {
           Point position = new Point(serverNode.getX(), serverNode.getY());
           Terrain terrain = TerrainConverter.fromETerrain(serverNode.getTerrain());
           TreasureState treasureState = TreasureStateConverter.fromETreasureState(serverNode.getTreasureState());
           FortState fortState = FortStateConverter.fromEFortState(serverNode.getFortState());
           PlayerPositionState playerPositionState = PlayerPositionStateConverter.fromEPlayerPositionState(serverNode.getPlayerPositionState());
           
           MapNode node = new MapNode(position, terrain, treasureState, fortState, playerPositionState);
           nodes.add(node);
       }
       
       return new GameMap(nodes);
   }
   private static List<PlayerState> convertPlayers(List<messagesbase.messagesfromserver.PlayerState> serverPlayers) {
       List<PlayerState> players = new ArrayList<>();
       
       for (messagesbase.messagesfromserver.PlayerState serverPlayer : serverPlayers) {
           String playerId = serverPlayer.getUniquePlayerID();
           PlayerGameState state = PlayerGameStateConverter.fromEPlayerGameState(serverPlayer.getState());
           boolean hasCollectedTreasure = serverPlayer.hasCollectedTreasure();
           
           players.add(new PlayerState(playerId, state, hasCollectedTreasure));
       }
       
       return players;
   }
}
