package client.converter;

import client.model.FortState;
import messagesbase.messagesfromserver.EFortState;

public class FortStateConverter {
    public static FortState fromEFortState(EFortState eFortState) {
        if (eFortState == null) {
            return FortState.NO_FORT;
        }
        
        switch (eFortState) {
            case MyFortPresent: return FortState.MY_FORT;
            case EnemyFortPresent: return FortState.ENEMY_FORT;
            case NoOrUnknownFortState: return FortState.NO_FORT;
            default: return FortState.NO_FORT;
        }
    }
}
