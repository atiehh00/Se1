package client.converter;

import client.model.PlayerGameState;
import messagesbase.messagesfromserver.EPlayerGameState;

public class PlayerGameStateConverter {
   public static PlayerGameState fromEPlayerGameState(EPlayerGameState ePlayerGameState) {
       if (ePlayerGameState == null) {
           return PlayerGameState.MUST_WAIT;
       }
       
       switch (ePlayerGameState) {
           case MustAct: return PlayerGameState.MUST_ACT;
           case MustWait: return PlayerGameState.MUST_WAIT;
           case Won: return PlayerGameState.WON;
           case Lost: return PlayerGameState.LOST;
           default: return PlayerGameState.MUST_WAIT;
       }
   }
}
