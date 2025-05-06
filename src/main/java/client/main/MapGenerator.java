package client.main;

import messagesbase.messagesfromclient.PlayerHalfMap;
import messagesbase.messagesfromclient.PlayerHalfMapNode;
import messagesbase.messagesfromclient.ETerrain;

import java.util.*;

public class MapGenerator {
  private final Random random = new Random();
  
  private static final int HALF_MAP_WIDTH = 10;
  private static final int HALF_MAP_HEIGHT = 5;
  private static final int TOTAL_FIELDS = HALF_MAP_WIDTH * HALF_MAP_HEIGHT;
  
  private static final int MIN_WATER = 7;
  private static final int MIN_GRASS = 24;
  private static final int MIN_MOUNTAIN = 5;
  private static final double EDGE_TRAVERSABLE_PERCENTAGE = 0.51;
  
  
  private static final boolean DEBUG = false;

  public PlayerHalfMap generateHalfMap(String playerId) {
    int maxAttempts = 10;
    int attempts = 0;
    
    while (attempts < maxAttempts) {
        attempts++;
        
        ETerrain[][] terrainMap = new ETerrain[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
        boolean[][] fortMap = new boolean[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
        
        for (int x = 0; x < HALF_MAP_WIDTH; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
                terrainMap[x][y] = ETerrain.Grass;
            }
        }
        
        addTerrainFeatures(terrainMap);
        
        placeFort(terrainMap, fortMap);
        
        if (verifyAllFieldsReachable(terrainMap) && validateEdgeWaterCount(terrainMap)) {
            List<PlayerHalfMapNode> nodes = new ArrayList<>();
            for (int x = 0; x < HALF_MAP_WIDTH; x++) {
                for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
                    nodes.add(new PlayerHalfMapNode(x, y, fortMap[x][y], terrainMap[x][y]));
                }
            }
            return new PlayerHalfMap(playerId, nodes);
        }
        
        System.out.println("Map generation attempt " + attempts + " failed validation, retrying...");
    }
    

    System.out.println("All map generation attempts failed, creating emergency map");
    return createSafeEmergencyMap(playerId);
}

private boolean validateEdgeWaterCount(ETerrain[][] terrainMap) {
    int rightEdgeWater = 0;
    for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
        if (terrainMap[HALF_MAP_WIDTH - 1][y] == ETerrain.Water) {
            rightEdgeWater++;
        }
    }
    if (rightEdgeWater > 2) {
        System.out.println("Too much water on right edge: " + rightEdgeWater);
        return false;
    }

    int leftEdgeWater = 0;
    for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
        if (terrainMap[0][y] == ETerrain.Water) {
            leftEdgeWater++;
        }
    }
    if (leftEdgeWater > 2) {
        System.out.println("Too much water on left edge: " + leftEdgeWater);
        return false;
    }

    int topEdgeWater = 0;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        if (terrainMap[x][0] == ETerrain.Water) {
            topEdgeWater++;
        }
    }
    if (topEdgeWater > 5) {
        System.out.println("Too much water on top edge: " + topEdgeWater);
        return false;
    }

    int bottomEdgeWater = 0;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        if (terrainMap[x][HALF_MAP_HEIGHT - 1] == ETerrain.Water) {
            bottomEdgeWater++;
        }
    }
    if (bottomEdgeWater > 5) {
        System.out.println("Too much water on bottom edge: " + bottomEdgeWater);
        return false;
    }

    return true;
}

private PlayerHalfMap createSafeEmergencyMap(String playerId) {
    ETerrain[][] terrainMap = new ETerrain[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
    boolean[][] fortMap = new boolean[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
    
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            terrainMap[x][y] = ETerrain.Grass;
        }
    }
    
    terrainMap[4][2] = ETerrain.Water;
    terrainMap[5][2] = ETerrain.Water;
    terrainMap[6][2] = ETerrain.Water;
    terrainMap[4][3] = ETerrain.Water;
    terrainMap[5][3] = ETerrain.Water;
    terrainMap[6][3] = ETerrain.Water;
    terrainMap[5][1] = ETerrain.Water;
    
    terrainMap[2][0] = ETerrain.Mountain;
    terrainMap[2][1] = ETerrain.Mountain;
    terrainMap[2][2] = ETerrain.Mountain;
    terrainMap[3][0] = ETerrain.Mountain;
    terrainMap[3][1] = ETerrain.Mountain;
    
    fortMap[1][2] = true;
    
    List<PlayerHalfMapNode> nodes = new ArrayList<>();
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            nodes.add(new PlayerHalfMapNode(x, y, fortMap[x][y], terrainMap[x][y]));
        }
    }
    
    return new PlayerHalfMap(playerId, nodes);
}


private void addTerrainFeatures(ETerrain[][] terrainMap) {
    int waterCount = 0;
    while (waterCount < MIN_WATER) {
        int x = 2 + random.nextInt(HALF_MAP_WIDTH - 4); 
        int y = 1 + random.nextInt(HALF_MAP_HEIGHT - 2); 
        if (terrainMap[x][y] == ETerrain.Grass) {
            terrainMap[x][y] = ETerrain.Water;
            waterCount++;
        }
    }
    
    int mountainCount = 0;
    while (mountainCount < MIN_MOUNTAIN) {
        int x = random.nextInt(HALF_MAP_WIDTH);
        int y = random.nextInt(HALF_MAP_HEIGHT);
        if (terrainMap[x][y] == ETerrain.Grass) {
            terrainMap[x][y] = ETerrain.Mountain;
            mountainCount++;
        }
    }
}

private void generatePatternA(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Generating Pattern A: Lake in center with mountains on edges");
    int centerX = HALF_MAP_WIDTH / 2;
    int centerY = HALF_MAP_HEIGHT / 2;
    int lakeSize = 3 + random.nextInt(2);
    
    int waterCount = 0;
    for (int x = centerX - lakeSize/2; x <= centerX + lakeSize/2; x++) {
        for (int y = centerY - lakeSize/2; y <= centerY + lakeSize/2; y++) {
            if (x >= 0 && x < HALF_MAP_WIDTH && y >= 0 && y < HALF_MAP_HEIGHT) {
                if (random.nextDouble() < 0.7) {
                    terrainMap[x][y] = ETerrain.Water;
                    waterCount++;
                }
            }
        }
    }
    
    int mountainCount = 0;
    for (int x = 0; x < HALF_MAP_WIDTH && mountainCount < MIN_MOUNTAIN; x += 2) {
        terrainMap[x][0] = ETerrain.Mountain;
        mountainCount++;
    }
    if (mountainCount < MIN_MOUNTAIN) {
        for (int y = 1; y < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN; y += 2) {
            terrainMap[0][y] = ETerrain.Mountain;
            mountainCount++;
        }
    }
    int fortX = HALF_MAP_WIDTH - 2;
    int fortY = 1;
    fortMap[fortX][fortY] = true;
    
    if (DEBUG) System.out.println("Pattern A generated with " + waterCount + " water and " + mountainCount + " mountains");
}

private void generatePatternB(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Generating Pattern B: River across map with mountains in corners");
    
    boolean horizontal = random.nextBoolean();
    int riverPos = 1 + random.nextInt(HALF_MAP_HEIGHT - 2);
    
    int waterCount = 0;
    if (horizontal) {
        for (int x = 0; x < HALF_MAP_WIDTH; x++) {
            int actualY = riverPos;
            if (random.nextDouble() < 0.3) {
                actualY += (random.nextBoolean() ? 1 : -1);
                if (actualY < 0) actualY = 0;
                if (actualY >= HALF_MAP_HEIGHT) actualY = HALF_MAP_HEIGHT - 1;
            }
            
            terrainMap[x][actualY] = ETerrain.Water;
            waterCount++;
            if (random.nextDouble() < 0.4 && actualY > 0) {
                terrainMap[x][actualY - 1] = ETerrain.Water;
                waterCount++;
            }
        }
    } else {
        riverPos = 1 + random.nextInt(HALF_MAP_WIDTH - 2); 
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            int actualX = riverPos;
            if (random.nextDouble() < 0.3) {
                actualX += (random.nextBoolean() ? 1 : -1);
                if (actualX < 0) actualX = 0;
                if (actualX >= HALF_MAP_WIDTH) actualX = HALF_MAP_WIDTH - 1;
            }
            
            terrainMap[actualX][y] = ETerrain.Water;
            waterCount++;
            
            if (random.nextDouble() < 0.4 && actualX > 0) {
                terrainMap[actualX - 1][y] = ETerrain.Water;
                waterCount++;
            }
        }
    }
    int mountainCount = 0;
    for (int i = 0; i < 2 && mountainCount < MIN_MOUNTAIN; i++) {
        for (int j = 0; j < 2 && mountainCount < MIN_MOUNTAIN; j++) {
            if (terrainMap[i][j] != ETerrain.Water) {
                terrainMap[i][j] = ETerrain.Mountain;
                mountainCount++;
            }
        }
    }
    for (int i = HALF_MAP_WIDTH - 2; i < HALF_MAP_WIDTH && mountainCount < MIN_MOUNTAIN; i++) {
        for (int j = HALF_MAP_HEIGHT - 2; j < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN; j++) {
            if (terrainMap[i][j] != ETerrain.Water) {
                terrainMap[i][j] = ETerrain.Mountain;
                mountainCount++;
            }
        }
    }
    while (mountainCount < MIN_MOUNTAIN) {
        int x = random.nextInt(HALF_MAP_WIDTH);
        int y = random.nextInt(HALF_MAP_HEIGHT);
        if (terrainMap[x][y] != ETerrain.Water && terrainMap[x][y] != ETerrain.Mountain) {
            terrainMap[x][y] = ETerrain.Mountain;
            mountainCount++;
        }
    }
    int fortX, fortY;
    do {
        fortX = 2 + random.nextInt(HALF_MAP_WIDTH - 4);
        fortY = 2 + random.nextInt(HALF_MAP_HEIGHT - 4);
    } while (terrainMap[fortX][fortY] != ETerrain.Grass);
    
    fortMap[fortX][fortY] = true;
    
    if (DEBUG) System.out.println("Pattern B generated with " + waterCount + " water and " + mountainCount + " mountains");
}

private void generatePatternC(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Generating Pattern C: Multiple small lakes with mountain range");
    int lakeCount = 2 + random.nextInt(2);
    int waterCount = 0;
    
    for (int lake = 0; lake < lakeCount; lake++) {
        int lakeX = 1 + random.nextInt(HALF_MAP_WIDTH - 2);
        int lakeY = 1 + random.nextInt(HALF_MAP_HEIGHT - 2);
        int lakeSize = 1 + random.nextInt(2);
        
        for (int x = lakeX - lakeSize; x <= lakeX + lakeSize; x++) {
            for (int y = lakeY - lakeSize; y <= lakeY + lakeSize; y++) {
                if (x >= 0 && x < HALF_MAP_WIDTH && y >= 0 && y < HALF_MAP_HEIGHT) {
                    if (random.nextDouble() < 0.7) {
                        terrainMap[x][y] = ETerrain.Water;
                        waterCount++;
                    }
                }
            }
        }
    }
    int mountainCount = 0;
    int startX = random.nextBoolean() ? 0 : HALF_MAP_WIDTH - 1;
    int startY = random.nextBoolean() ? 0 : HALF_MAP_HEIGHT - 1;
    int dirX = startX == 0 ? 1 : -1;
    int dirY = startY == 0 ? 1 : -1;
    
    int x = startX;
    int y = startY;
    
    while (x >= 0 && x < HALF_MAP_WIDTH && y >= 0 && y < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN) {
        if (terrainMap[x][y] != ETerrain.Water) {
            terrainMap[x][y] = ETerrain.Mountain;
            mountainCount++;
        }
        if (random.nextDouble() < 0.7) {
            x += dirX;
        }
        if (random.nextDouble() < 0.7) {
            y += dirY;
        }
        
        if (x < 0 || x >= HALF_MAP_WIDTH || y < 0 || y >= HALF_MAP_HEIGHT) {
            break;
        }
    }
    
    while (mountainCount < MIN_MOUNTAIN) {
        x = random.nextInt(HALF_MAP_WIDTH);
        y = random.nextInt(HALF_MAP_HEIGHT);
        if (terrainMap[x][y] != ETerrain.Water && terrainMap[x][y] != ETerrain.Mountain) {
            terrainMap[x][y] = ETerrain.Mountain;
            mountainCount++;
        }
    }
    int fortX, fortY;
    do {
        fortX = 1 + random.nextInt(HALF_MAP_WIDTH - 2);
        fortY = 1 + random.nextInt(HALF_MAP_HEIGHT - 2);
    } while (terrainMap[fortX][fortY] != ETerrain.Grass);
    
    fortMap[fortX][fortY] = true;
    
    if (DEBUG) System.out.println("Pattern C generated with " + waterCount + " water and " + mountainCount + " mountains");
}

private void generatePatternD(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Generating Pattern D: Water on one side, mountains on the other");
    
    boolean waterOnLeft = random.nextBoolean();
    
    int waterCount = 0;
    int mountainCount = 0;
    
    if (waterOnLeft) {
        for (int x = 0; x < HALF_MAP_WIDTH / 2; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
                if (random.nextDouble() < 0.6) {
                    terrainMap[x][y] = ETerrain.Water;
                    waterCount++;
                }
            }
        }
        
        for (int x = HALF_MAP_WIDTH / 2; x < HALF_MAP_WIDTH && mountainCount < MIN_MOUNTAIN; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN; y++) {
                if (random.nextDouble() < 0.4) {
                    terrainMap[x][y] = ETerrain.Mountain;
                    mountainCount++;
                }
            }
        }
        int fortX, fortY;
        do {
            fortX = HALF_MAP_WIDTH / 2 + random.nextInt(HALF_MAP_WIDTH / 2);
            fortY = random.nextInt(HALF_MAP_HEIGHT);
        } while (terrainMap[fortX][fortY] != ETerrain.Grass);
        
        fortMap[fortX][fortY] = true;
    } else {
        for (int x = HALF_MAP_WIDTH / 2; x < HALF_MAP_WIDTH; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
                if (random.nextDouble() < 0.6) {
                    terrainMap[x][y] = ETerrain.Water;
                    waterCount++;
                }
            }
        }
        
        for (int x = 0; x < HALF_MAP_WIDTH / 2 && mountainCount < MIN_MOUNTAIN; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN; y++) {
                if (random.nextDouble() < 0.4) {
                    terrainMap[x][y] = ETerrain.Mountain;
                    mountainCount++;
                }
            }
        }
        
        int fortX, fortY;
        do {
            fortX = random.nextInt(HALF_MAP_WIDTH / 2);
            fortY = random.nextInt(HALF_MAP_HEIGHT);
        } while (terrainMap[fortX][fortY] != ETerrain.Grass);
        
        fortMap[fortX][fortY] = true;
    }
    
    if (waterCount < MIN_WATER) {
        int side = waterOnLeft ? 0 : HALF_MAP_WIDTH / 2;
        for (int x = side; x < side + HALF_MAP_WIDTH / 2 && waterCount < MIN_WATER; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT && waterCount < MIN_WATER; y++) {
                if (terrainMap[x][y] != ETerrain.Water && !fortMap[x][y]) {
                    terrainMap[x][y] = ETerrain.Water;
                    waterCount++;
                }
            }
        }
    }

    if (mountainCount < MIN_MOUNTAIN) {
        int side = waterOnLeft ? HALF_MAP_WIDTH / 2 : 0;
        for (int x = side; x < side + HALF_MAP_WIDTH / 2 && mountainCount < MIN_MOUNTAIN; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT && mountainCount < MIN_MOUNTAIN; y++) {
                if (terrainMap[x][y] != ETerrain.Water && terrainMap[x][y] != ETerrain.Mountain && !fortMap[x][y]) {
                    terrainMap[x][y] = ETerrain.Mountain;
                    mountainCount++;
                }
            }
        }
    }
    
    if (DEBUG) System.out.println("Pattern D generated with " + waterCount + " water and " + mountainCount + " mountains");
}

private void generatePatternE(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Generating Pattern E: Scattered terrain with a central feature");
    
    int featureType = random.nextInt(3);
    int centerX = HALF_MAP_WIDTH / 2;
    int centerY = HALF_MAP_HEIGHT / 2;

    if (featureType == 0) {
        if (DEBUG) System.out.println("Central feature: Lake");
        int lakeSize = 1 + random.nextInt(2);
        
        for (int x = centerX - lakeSize; x <= centerX + lakeSize; x++) {
            for (int y = centerY - lakeSize; y <= centerY + lakeSize; y++) {
                if (x >= 0 && x < HALF_MAP_WIDTH && y >= 0 && y < HALF_MAP_HEIGHT) {
                    terrainMap[x][y] = ETerrain.Water;
                }
            }
        }
    } else if (featureType == 1) {
        if (DEBUG) System.out.println("Central feature: Mountain cluster");
        int clusterSize = 1 + random.nextInt(2);
        
        for (int x = centerX - clusterSize; x <= centerX + clusterSize; x++) {
            for (int y = centerY - clusterSize; y <= centerY + clusterSize; y++) {
                if (x >= 0 && x < HALF_MAP_WIDTH && y >= 0 && y < HALF_MAP_HEIGHT) {
                    if (random.nextDouble() < 0.7) {
                        terrainMap[x][y] = ETerrain.Mountain;
                    }
                }
            }
        }
    } else {
        if (DEBUG) System.out.println("Central feature: Fort");
        fortMap[centerX][centerY] = true;
    }
    int waterCount = 0;
    while (waterCount < MIN_WATER) {
        int clusterX = random.nextInt(HALF_MAP_WIDTH);
        int clusterY = random.nextInt(HALF_MAP_HEIGHT);
        
        if (fortMap[clusterX][clusterY]) continue;
        
        terrainMap[clusterX][clusterY] = ETerrain.Water;
        waterCount++;
        
        for (int i = 0; i < 2 && waterCount < MIN_WATER; i++) {
            int dx = random.nextInt(3) - 1; // -1, 0, or 1
            int dy = random.nextInt(3) - 1; // -1, 0, or 1
            
            int newX = clusterX + dx;
            int newY = clusterY + dy;
            
            if (newX >= 0 && newX < HALF_MAP_WIDTH && newY >= 0 && newY < HALF_MAP_HEIGHT) {
                if (!fortMap[newX][newY]) {
                    terrainMap[newX][newY] = ETerrain.Water;
                    waterCount++;
                }
            }
        }
    }

    int mountainCount = countTerrain(terrainMap, ETerrain.Mountain);
    while (mountainCount < MIN_MOUNTAIN) {
        int x = random.nextInt(HALF_MAP_WIDTH);
        int y = random.nextInt(HALF_MAP_HEIGHT);
        
        if (terrainMap[x][y] != ETerrain.Water && terrainMap[x][y] != ETerrain.Mountain && !fortMap[x][y]) {
            terrainMap[x][y] = ETerrain.Mountain;
            mountainCount++;
        }
    }
    
    boolean fortPlaced = false;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (fortMap[x][y]) {
                fortPlaced = true;
                break;
            }
        }
        if (fortPlaced) break;
    }
    
    if (!fortPlaced) {
        int fortX, fortY;
        do {
            fortX = random.nextInt(HALF_MAP_WIDTH);
            fortY = random.nextInt(HALF_MAP_HEIGHT);
        } while (terrainMap[fortX][fortY] != ETerrain.Grass);
        
        fortMap[fortX][fortY] = true;
    }
    
    if (DEBUG) System.out.println("Pattern E generated with " + waterCount + " water and " + mountainCount + " mountains");
}

/**
 * Adds random variation to the map to make it more unique.
 */
private void addRandomVariation(ETerrain[][] terrainMap, boolean[][] fortMap) {
    if (DEBUG) System.out.println("Adding random variation to make map more unique");
    
    int changes = 3 + random.nextInt(5); 
    for (int i = 0; i < changes; i++) {
        int x = random.nextInt(HALF_MAP_WIDTH);
        int y = random.nextInt(HALF_MAP_HEIGHT);
        
        if (fortMap[x][y]) continue;
        
        double chance = random.nextDouble();
        if (chance < 0.4 && terrainMap[x][y] != ETerrain.Water) {
            terrainMap[x][y] = ETerrain.Water;
        } else if (chance < 0.8 && terrainMap[x][y] != ETerrain.Mountain) {
            terrainMap[x][y] = ETerrain.Mountain;
        } else {
            terrainMap[x][y] = ETerrain.Grass;
        }
    }
    
    if (!verifyAllFieldsReachable(terrainMap)) {
        System.out.println("Random variation created connectivity issues. Fixing...");
        fixConnectivity(terrainMap);
    }
    
    int waterCount = countTerrain(terrainMap, ETerrain.Water);
    int mountainCount = countTerrain(terrainMap, ETerrain.Mountain);
    
    if (waterCount < MIN_WATER) {
        addMoreWater(terrainMap, fortMap, MIN_WATER - waterCount);
    }
    
    if (mountainCount < MIN_MOUNTAIN) {
        forceAddMountains(terrainMap, fortMap, MIN_MOUNTAIN - mountainCount);
    }
}

private void fixConnectivity(ETerrain[][] terrainMap) {
    List<Point> nonWaterFields = new ArrayList<>();
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (terrainMap[x][y] != ETerrain.Water) {
                nonWaterFields.add(new Point(x, y));
            }
        }
    }
    
    if (nonWaterFields.isEmpty()) {
        System.err.println("ERROR: No non-water fields found!");
        return;
    }

    Point start = nonWaterFields.get(0);
    boolean[][] visited = new boolean[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
    Queue<Point> queue = new LinkedList<>();
    queue.add(start);
    visited[start.x][start.y] = true;

    Set<Point> reachableFields = new HashSet<>();
    reachableFields.add(start);
    
    while (!queue.isEmpty()) {
        Point current = queue.poll();
        
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] dir : directions) {
            int newX = current.x + dir[0];
            int newY = current.y + dir[1];
            
            if (newX >= 0 && newX < HALF_MAP_WIDTH && newY >= 0 && newY < HALF_MAP_HEIGHT) {
                if (terrainMap[newX][newY] != ETerrain.Water && !visited[newX][newY]) {
                    Point newPoint = new Point(newX, newY);
                    visited[newX][newY] = true;
                    queue.add(newPoint);
                    reachableFields.add(newPoint);
                }
            }
        }
    }
    List<Point> unreachableFields = new ArrayList<>();
    for (Point field : nonWaterFields) {
        if (!reachableFields.contains(field)) {
            unreachableFields.add(field);
        }
    }
    
    System.out.println("Found " + unreachableFields.size() + " unreachable fields");
    for (Point unreachable : unreachableFields) {
        Point closest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (Point reachable : reachableFields) {
            int distance = Math.abs(unreachable.x - reachable.x) + Math.abs(unreachable.y - reachable.y);
            if (distance < minDistance) {
                minDistance = distance;
                closest = reachable;
            }
        }
        
        if (closest != null) {
            createPath(terrainMap, unreachable, closest);
            reachableFields.add(unreachable);
        }
    }
}

private void createPath(ETerrain[][] terrainMap, Point from, Point to) {
    int x = from.x;
    int y = from.y;
    
    while (x != to.x) {
        x += (x < to.x) ? 1 : -1;
        if (terrainMap[x][y] == ETerrain.Water) {
            terrainMap[x][y] = ETerrain.Grass;
            System.out.println("Converting water to grass at (" + x + "," + y + ") to create path");
        }
    }
    
    while (y != to.y) {
        y += (y < to.y) ? 1 : -1;
        if (terrainMap[x][y] == ETerrain.Water) {
            terrainMap[x][y] = ETerrain.Grass;
            System.out.println("Converting water to grass at (" + x + "," + y + ") to create path");
        }
    }
}

private void forceAddWater(ETerrain[][] terrainMap, boolean[][] fortMap, int countToAdd) {
    System.out.println("EMERGENCY: Force adding " + countToAdd + " water fields");
    int added = 0;
    for (int y = 1; y < HALF_MAP_HEIGHT - 1 && added < countToAdd; y++) {
        for (int x = 1; x < HALF_MAP_WIDTH - 1 && added < countToAdd; x++) {
            if (fortMap[x][y] || terrainMap[x][y] == ETerrain.Water) {
                continue;
            }
            
            terrainMap[x][y] = ETerrain.Water;
            added++;
        }
    }
    
    for (int y = 0; y < HALF_MAP_HEIGHT && added < countToAdd; y++) {
        for (int x = 0; x < HALF_MAP_WIDTH && added < countToAdd; x++) {
            if (fortMap[x][y] || terrainMap[x][y] == ETerrain.Water) {
                continue;
            }
            
            terrainMap[x][y] = ETerrain.Water;
            added++;
        }
    }
    
    System.out.println("Force added " + added + " water fields");
}

private void forceAddMountains(ETerrain[][] terrainMap, boolean[][] fortMap, int countToAdd) {
    System.out.println("EMERGENCY: Force adding " + countToAdd + " mountain fields");
    int added = 0;
    
    for (int y = 0; y < HALF_MAP_HEIGHT && added < countToAdd; y++) {
        for (int x = 0; x < HALF_MAP_WIDTH && added < countToAdd; x++) {
            if (fortMap[x][y] || terrainMap[x][y] == ETerrain.Water || terrainMap[x][y] == ETerrain.Mountain) {
                continue;
            }
            
            terrainMap[x][y] = ETerrain.Mountain;
            added++;
        }
    }
    
    if (added < countToAdd) {
        for (int y = 0; y < HALF_MAP_HEIGHT && added < countToAdd; y++) {
            for (int x = 0; x < HALF_MAP_WIDTH && added < countToAdd; x++) {
                if (fortMap[x][y] || terrainMap[x][y] == ETerrain.Mountain) {
                    continue;
                }

                terrainMap[x][y] = ETerrain.Mountain;
                added++;
            }
        }
    }
    
    System.out.println("Force added " + added + " mountain fields");
}

private int countTerrain(ETerrain[][] terrainMap, ETerrain terrain) {
    int count = 0;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (terrainMap[x][y] == terrain) {
                count++;
            }
        }
    }
    return count;
}

private void addMoreWater(ETerrain[][] terrainMap, boolean[][] fortMap, int countToAdd) {
    int added = 0;
    int attempts = 0;
    int maxAttempts = 100;
    
    while (added < countToAdd && attempts < maxAttempts) {
        attempts++;
        
        int startX = 1 + random.nextInt(HALF_MAP_WIDTH - 2);
        int startY = 1 + random.nextInt(HALF_MAP_HEIGHT - 2);
        
        if (fortMap[startX][startY] || terrainMap[startX][startY] == ETerrain.Water) {
            continue;
        }
        
        terrainMap[startX][startY] = ETerrain.Water;
        added++;
        
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] dir : directions) {
            if (added >= countToAdd) break;
            
            int newX = startX + dir[0];
            int newY = startY + dir[1];
            
            if (newX >= 1 && newX < HALF_MAP_WIDTH - 1 && newY >= 1 && newY < HALF_MAP_HEIGHT - 1) {
                if (fortMap[newX][newY] || terrainMap[newX][newY] == ETerrain.Water) {
                    continue;
                }
                
                terrainMap[newX][newY] = ETerrain.Water;
                added++;
            }
        }
    }
    
    for (int y = 0; y < HALF_MAP_HEIGHT && added < countToAdd; y++) {
        for (int x = 0; x < HALF_MAP_WIDTH && added < countToAdd; x++) {
            if (fortMap[x][y] || terrainMap[x][y] == ETerrain.Water) {
                continue;
            }
            
            terrainMap[x][y] = ETerrain.Water;
            added++;
        }
    }
    
    System.out.println("Added " + added + " water fields out of " + countToAdd + " needed");
}

private void placeFort(ETerrain[][] terrainMap, boolean[][] fortMap) {
    boolean fortPlaced = false;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (fortMap[x][y]) {
                fortPlaced = true;
                break;
            }
        }
        if (fortPlaced) break;
    }
    
    if (fortPlaced) {
        return;
    }
    
    List<Point> grassFields = new ArrayList<>();
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (terrainMap[x][y] == ETerrain.Grass) {
                grassFields.add(new Point(x, y));
            }
        }
    }
    
    if (!grassFields.isEmpty()) {
        Point fortLocation = grassFields.get(random.nextInt(grassFields.size()));
        fortMap[fortLocation.x][fortLocation.y] = true;
        System.out.println("Fort placed at position (" + fortLocation.x + "," + fortLocation.y + ")");
    } else {
        System.err.println("ERROR: No grass fields available for fort placement!");
        for (int x = 0; x < HALF_MAP_WIDTH; x++) {
            for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
                if (terrainMap[x][y] == ETerrain.Water) {
                    terrainMap[x][y] = ETerrain.Grass;
                    fortMap[x][y] = true;
                    System.out.println("EMERGENCY: Converted water to grass at (" + x + "," + y + ") and placed fort");
                    return;
                }
            }
        }
    }
}
private void ensureEdgesTraversable(ETerrain[][] terrainMap) {
    ensureEdgeTraversable(terrainMap, 0, HALF_MAP_WIDTH - 1, 0, 0, true);
    
    ensureEdgeTraversable(terrainMap, 0, HALF_MAP_WIDTH - 1, HALF_MAP_HEIGHT - 1, HALF_MAP_HEIGHT - 1, true);
    
    ensureEdgeTraversable(terrainMap, 0, 0, 0, HALF_MAP_HEIGHT - 1, false);
    
    ensureEdgeTraversable(terrainMap, HALF_MAP_WIDTH - 1, HALF_MAP_WIDTH - 1, 0, HALF_MAP_HEIGHT - 1, false);
}


private void ensureEdgeTraversable(ETerrain[][] terrainMap, int startX, int endX, int startY, int endY, boolean horizontal) {
    int length = horizontal ? (endX - startX + 1) : (endY - startY + 1);
    int minTraversable = (int) Math.ceil(length * EDGE_TRAVERSABLE_PERCENTAGE);
    
    int traversableCount = 0;
    
    if (horizontal) {
        for (int x = startX; x <= endX; x++) {
            if (terrainMap[x][startY] != ETerrain.Water) {
                traversableCount++;
            }
        }

        if (traversableCount < minTraversable) {
            int toConvert = minTraversable - traversableCount;
            System.out.println("Converting " + toConvert + " water cells to grass on horizontal edge at y=" + startY);
            
            for (int x = startX; x <= endX && toConvert > 0; x++) {
                if (terrainMap[x][startY] == ETerrain.Water) {
                    terrainMap[x][startY] = ETerrain.Grass;
                    toConvert--;
                }
            }
        }
    } else {
        for (int y = startY; y <= endY; y++) {
            if (terrainMap[startX][y] != ETerrain.Water) {
                traversableCount++;
            }
        }
        if (traversableCount < minTraversable) {
            int toConvert = minTraversable - traversableCount;
            System.out.println("Converting " + toConvert + " water cells to grass on vertical edge at x=" + startX);
            
            for (int y = startY; y <= endY && toConvert > 0; y++) {
                if (terrainMap[startX][y] == ETerrain.Water) {
                    terrainMap[startX][y] = ETerrain.Grass;
                    toConvert--;
                }
            }
        }
    }
}

private boolean verifyAllFieldsReachable(ETerrain[][] terrainMap) {
    Point start = null;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (terrainMap[x][y] != ETerrain.Water) {
                start = new Point(x, y);
                break;
            }
        }
        if (start != null) break;
    }
    
    if (start == null) return false;

    int totalNonWaterFields = 0;
    for (int x = 0; x < HALF_MAP_WIDTH; x++) {
        for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
            if (terrainMap[x][y] != ETerrain.Water) {
                totalNonWaterFields++;
            }
        }
    }
    
    boolean[][] visited = new boolean[HALF_MAP_WIDTH][HALF_MAP_HEIGHT];
    Queue<Point> queue = new LinkedList<>();
    queue.add(start);
    visited[start.x][start.y] = true;
    int reachableFields = 1; 
    
    while (!queue.isEmpty()) {
        Point current = queue.poll();
        
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] dir : directions) {
            int newX = current.x + dir[0];
            int newY = current.y + dir[1];
            
            if (newX >= 0 && newX < HALF_MAP_WIDTH && newY >= 0 && newY < HALF_MAP_HEIGHT) {
                if (terrainMap[newX][newY] != ETerrain.Water && !visited[newX][newY]) {
                    visited[newX][newY] = true;
                    queue.add(new Point(newX, newY));
                    reachableFields++;
                }
            }
        }
    }
    
    boolean allReachable = reachableFields == totalNonWaterFields;
    if (!allReachable) {
        System.out.println("Map connectivity check failed: Only " + reachableFields + 
                          " out of " + totalNonWaterFields + " non-water fields are reachable");
    }
    return allReachable;
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
}

private void printColoredMap(ETerrain[][] terrainMap, boolean[][] fortMap) {
   System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
   System.out.println("║                        GENERATED HALF MAP                         ║");
   System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
   
   System.out.println("Legend: \u001B[32m.\u001B[0m = Grass, \u001B[34m~\u001B[0m = Water, \u001B[33m^\u001B[0m = Mountain, \u001B[31mF\u001B[0m = Fort");
   System.out.print("   ");
   for (int x = 0; x < HALF_MAP_WIDTH; x++) {
       System.out.print("─");
   }
   System.out.println();
   
   System.out.print("   ");
   for (int x = 0; x < HALF_MAP_WIDTH; x++) {
       System.out.print(x % 10);
   }
   System.out.println();
   
   for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
       System.out.print(y % 10 + " │");
       
       for (int x = 0; x < HALF_MAP_WIDTH; x++) {
           if (fortMap[x][y]) {
               System.out.print("\u001B[41m\u001B[30mF\u001B[0m");
           } else if (terrainMap[x][y] == ETerrain.Grass) {
               System.out.print("\u001B[32m.\u001B[0m");
           } else if (terrainMap[x][y] == ETerrain.Water) {
               System.out.print("\u001B[34m~\u001B[0m");
           } else if (terrainMap[x][y] == ETerrain.Mountain) {
               System.out.print("\u001B[33m^\u001B[0m");
           }
       }
       
       System.out.println("│");
   }
   System.out.print("   ");
   for (int x = 0; x < HALF_MAP_WIDTH; x++) {
       System.out.print("─");
   }
   System.out.println();
   
   int grassCount = 0, waterCount = 0, mountainCount = 0;
   for (int x = 0; x < HALF_MAP_WIDTH; x++) {
       for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
           if (terrainMap[x][y] == ETerrain.Grass) {
               grassCount++;
           } else if (terrainMap[x][y] == ETerrain.Water) {
               waterCount++;
           } else if (terrainMap[x][y] == ETerrain.Mountain) {
               mountainCount++;
           }
       }
   }
   
   System.out.println("\n╭───────────────────────────────────────────────────────────────╮");
   System.out.println("│ \u001B[1m📊 MAP STATISTICS\u001B[0m                                            │");
   System.out.println("╰───────────────────────────────────────────────────────────────╯");
   
   System.out.println("  \u001B[32m🌿 Grass: " + grassCount + " (" + 
                    String.format("%.1f", grassCount * 100.0 / TOTAL_FIELDS) + "%)\u001B[0m");
   System.out.println("  \u001B[34m💧 Water: " + waterCount + " (" + 
                    String.format("%.1f", waterCount * 100.0 / TOTAL_FIELDS) + "%)\u001B[0m");
   System.out.println("  \u001B[33m⛰️ Mountains: " + mountainCount + " (" + 
                    String.format("%.1f", mountainCount * 100.0 / TOTAL_FIELDS) + "%)\u001B[0m");
   
   Point fortPosition = null;
   for (int x = 0; x < HALF_MAP_WIDTH; x++) {
       for (int y = 0; y < HALF_MAP_HEIGHT; y++) {
           if (fortMap[x][y]) {
               fortPosition = new Point(x, y);
               break;
           }
       }
       if (fortPosition != null) break;
   }
   
   if (fortPosition != null) {
       System.out.println("  \u001B[31m🏰 Fort position: (" + fortPosition.x + "," + fortPosition.y + ")\u001B[0m");
   }
}

}
