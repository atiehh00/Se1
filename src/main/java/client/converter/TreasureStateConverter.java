package client.converter;

import client.model.TreasureState;
import messagesbase.messagesfromserver.ETreasureState;

public class TreasureStateConverter {
    public static TreasureState fromETreasureState(ETreasureState eTreasureState) {
        if (eTreasureState == null) {
            return TreasureState.NO_TREASURE;
        }
        
        switch (eTreasureState) {
            case MyTreasureIsPresent: return TreasureState.MY_TREASURE;
            case NoOrUnknownTreasureState: return TreasureState.NO_TREASURE;
            default: return TreasureState.UNKNOWN_TREASURE;
        }
    }
}
