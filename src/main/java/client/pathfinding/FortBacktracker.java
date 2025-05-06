package client.pathfinding;

import client.model.*;
import java.util.*;

public class FortBacktracker {
    private final List<Point> opponentPositions = new ArrayList<>();
    private final Map<Point, Integer> fortCandidates = new HashMap<>();
    private Point lastOpponentPosition = null;
    private Optional<Point> predictedFortPosition = Optional.empty();
    private int currentPredictionScore = Integer.MAX_VALUE;
    private Point currentBestCandidate = null;
    private int consecutiveBestCandidateCount = 0;
    private static final int PREDICTION_CONFIDENCE_THRESHOLD = 3; 
    private static final int PREDICTION_SCORE_IMPROVEMENT_THRESHOLD = 4;
    
    private static final int MAX_TRACKING_POSITIONS = 8;
    private static final int FORT_VICINITY_RADIUS = 3; 
    private static final int MAX_POSITIONS_FOR_ANALYSIS = 4; 
    

    public void trackEnemyPosition(Point opponentPosition, GameMap gameMap) {
        if (opponentPosition == null) {
            return;
        }
        
        if (lastOpponentPosition != null && lastOpponentPosition.equals(opponentPosition)) {
            return;
        }
        
        lastOpponentPosition = opponentPosition;
        opponentPositions.add(opponentPosition);
        
        if (opponentPositions.size() > MAX_TRACKING_POSITIONS) {
            opponentPositions.remove(0);
        }
        
        if (opponentPositions.size() >= 5) {
            analyzePossibleFortLocations(gameMap);
        }
    }
    
    public void trackOpponentPosition(Point opponentPosition, GameMap gameMap) {
        trackEnemyPosition(opponentPosition, gameMap);
    }
    
    private void analyzePossibleFortLocations(GameMap gameMap) {
        Point lastPos = opponentPositions.get(opponentPositions.size() - 1);
        
        Set<Point> candidateZone = new HashSet<>();
        
        for (int dx = -FORT_VICINITY_RADIUS; dx <= FORT_VICINITY_RADIUS; dx++) {
            for (int dy = -FORT_VICINITY_RADIUS; dy <= FORT_VICINITY_RADIUS; dy++) {
                Point candidatePos = new Point(lastPos.x + dx, lastPos.y + dy);
                Optional<MapNode> nodeOpt = gameMap.getNode(candidatePos);
                
                if (nodeOpt.isPresent() && nodeOpt.get().isTraversable()) {
                    candidateZone.add(candidatePos);
                }
            }
        }
        
        for (Point candidate : candidateZone) {
            int totalCost = 0;
            int validPaths = 0;
            
            int startIdx = Math.max(0, opponentPositions.size() - MAX_POSITIONS_FOR_ANALYSIS);
            List<Point> relevantOpponentPositions = opponentPositions.subList(startIdx, opponentPositions.size());

            for (Point opponentPos : relevantOpponentPositions) { 
                if (candidate.equals(opponentPos)) {
                    continue;
                }
                
                
                try {
                    PathFinder pathFinder = new PathFinder();
                    List<Direction> path = pathFinder.findDirectPathWithTimeout(gameMap, opponentPos, candidate, 25); // 25ms timeout
                    if (!path.isEmpty()) {
                        totalCost += path.size();
                        validPaths++;
                    } else {
                    }
                } catch (Exception e) {
                   
                    System.err.println("WARN: Exception during FortBacktracker path scoring: " + e.getMessage());
                }
            }
            
            if (validPaths > 0) { 
                int score = totalCost / validPaths;
                
                fortCandidates.put(candidate, score);
            }
        }
        
        Point bestCandidateNow = null;
        int bestScoreNow = Integer.MAX_VALUE;
        
        for (Map.Entry<Point, Integer> entry : fortCandidates.entrySet()) {
            if (entry.getValue() < bestScoreNow) {
                bestScoreNow = entry.getValue();
                bestCandidateNow = entry.getKey();
            }
        }

        if (bestCandidateNow != null) {
            boolean significantImprovement = bestScoreNow < currentPredictionScore - PREDICTION_SCORE_IMPROVEMENT_THRESHOLD;
            boolean sameCandidatePersists = bestCandidateNow.equals(currentBestCandidate);

            if (sameCandidatePersists) {
                consecutiveBestCandidateCount++;
            } else {
                currentBestCandidate = bestCandidateNow;
                consecutiveBestCandidateCount = 1;
            }

           
            if (significantImprovement || consecutiveBestCandidateCount >= PREDICTION_CONFIDENCE_THRESHOLD) {
                if (!predictedFortPosition.isPresent() || !predictedFortPosition.get().equals(bestCandidateNow)) {
                    predictedFortPosition = Optional.of(bestCandidateNow);
                    currentPredictionScore = bestScoreNow;
                    System.out.println("** Fort Prediction Updated: " + bestCandidateNow + 
                                       " (Score: " + bestScoreNow + 
                                       ", Confidence: " + consecutiveBestCandidateCount + 
                                       ", Improved: " + significantImprovement + ") **");
                } else {
                    currentPredictionScore = bestScoreNow;
                    
                }
                if (consecutiveBestCandidateCount >= PREDICTION_CONFIDENCE_THRESHOLD) {
                    consecutiveBestCandidateCount = 0; 
                }
            } else {
                 System.out.println("Fort candidate " + bestCandidateNow + " (Score: " + bestScoreNow + ") not confident/better enough to update prediction (Current: " + predictedFortPosition.orElse(null) + " Score: " + currentPredictionScore + " Confidence: " + consecutiveBestCandidateCount + ")");
            }
        }
        
    }
    
    public Optional<Point> getPredictedFortPosition() {
        return predictedFortPosition;
    }
    
    public void reset() {
        opponentPositions.clear();
        fortCandidates.clear();
        predictedFortPosition = Optional.empty();
        lastOpponentPosition = null;
        currentPredictionScore = Integer.MAX_VALUE;
        currentBestCandidate = null;
        consecutiveBestCandidateCount = 0;
    }
} 