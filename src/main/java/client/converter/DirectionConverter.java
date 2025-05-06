package client.converter;

import client.model.Direction;
import messagesbase.messagesfromclient.EMove;


public class DirectionConverter {
    public static EMove toEMove(Direction direction) {
        if (direction == null) {
            return null;
        }
        
        switch (direction) {
            case UP: return EMove.Up;
            case DOWN: return EMove.Down;
            case LEFT: return EMove.Left;
            case RIGHT: return EMove.Right;
            default: return null; 
        }
    }    
    public static Direction fromEMove(EMove eMove) {
        if (eMove == null) {
            return null;
        }
        
        switch (eMove) {
            case Up: return Direction.UP;
            case Down: return Direction.DOWN;
            case Left: return Direction.LEFT;
            case Right: return Direction.RIGHT;
            default: return null; 
        }
    }
}
