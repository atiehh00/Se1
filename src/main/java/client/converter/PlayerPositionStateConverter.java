package client.converter;

import client.model.PlayerPositionState;
import messagesbase.messagesfromserver.EPlayerPositionState;

public class PlayerPositionStateConverter {
   public static PlayerPositionState fromEPlayerPositionState(EPlayerPositionState ePlayerPositionState) {
       if (ePlayerPositionState == null) {
           return PlayerPositionState.NO_PLAYER;
       }
       
       switch (ePlayerPositionState) {
           case MyPlayerPosition: return PlayerPositionState.MY_PLAYER;
           case EnemyPlayerPosition: return PlayerPositionState.ENEMY_PLAYER;
           case BothPlayerPosition: return PlayerPositionState.BOTH_PLAYERS;
           case NoPlayerPresent: return PlayerPositionState.NO_PLAYER;
           default: return PlayerPositionState.NO_PLAYER;
       }
   }
}
