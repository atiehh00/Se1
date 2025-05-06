package client.converter;

import client.model.Terrain;
import messagesbase.messagesfromclient.ETerrain;

public class TerrainConverter {
    public static ETerrain toETerrain(Terrain terrain) {
        if (terrain == null) {
            return null;
        }
        
        switch (terrain) {
            case GRASS: return ETerrain.Grass;
            case MOUNTAIN: return ETerrain.Mountain;
            case WATER: return ETerrain.Water;
            default: return null;
        }
    }
    public static Terrain fromETerrain(ETerrain eTerrain) {
        if (eTerrain == null) {
            return null;
        }
        
        switch (eTerrain) {
            case Grass: return Terrain.GRASS;
            case Mountain: return Terrain.MOUNTAIN;
            case Water: return Terrain.WATER;
            default: return null;
        }
    }
    public static Terrain fromString(String terrainString) {
        if (terrainString == null) {
            return null;
        }
        
        switch (terrainString.toLowerCase()) {
            case "grass": return Terrain.GRASS;
            case "mountain": return Terrain.MOUNTAIN;
            case "water": return Terrain.WATER;
            default: return Terrain.GRASS;
        }
    }
}
