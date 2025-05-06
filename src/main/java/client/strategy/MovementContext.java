package client.strategy;

import client.model.*;
import client.pathfinding.*;
import java.util.*;

public class MovementContext {
	private MovementState state;
	private final PathFinder pathFinder;
	private final ExplorationStrategy explorationStrategy;
	private final FortBacktracker fortBacktracker;
	private final Random random = new Random();

	private List<Direction> currentPath = new ArrayList<>();
	private final LinkedHashSet<Point> recentlyVisited = new LinkedHashSet<>();
	private final Map<Point, Integer> failedMoveAttempts = new HashMap<>();
	private final Set<Point> unreachablePositions = new HashSet<>();

	private Point lastPosition = null;
	private Direction lastAttemptedDirection = null;
	private long cycleDetectionTimestamp = 0;
	private static final int CYCLE_DETECTION_COOLDOWN_MS = 5000;
	private static final int MAX_RECENT_POSITIONS = 15;

	public MovementContext() {
		this.state = new SearchingForTreasureState();
		this.pathFinder = new PathFinder();
		this.explorationStrategy = new ExplorationStrategy();
		this.fortBacktracker = new FortBacktracker();
	}

	public Direction calculateNextMove(GameState gameState, String playerId, long timeBudgetMillis) {
		long startTime = System.currentTimeMillis();

		try {
			Optional<MapNode> playerNodeOpt = gameState.getMap().getPlayerPosition();
			if (!playerNodeOpt.isPresent()) {
				return getRandomDirection();
			}

			Optional<MapNode> enemyNodeOpt = gameState.getMap().getEnemyPosition();
			if (enemyNodeOpt.isPresent()) {
				Point enemyPos = enemyNodeOpt.get().getPosition();
				fortBacktracker.trackOpponentPosition(enemyPos, gameState.getMap());
			}

			MapNode playerNode = playerNodeOpt.get();
			Point currentPos = playerNode.getPosition();
			trackRecentlyVisited(currentPos);

			if (System.currentTimeMillis() - cycleDetectionTimestamp > CYCLE_DETECTION_COOLDOWN_MS) {
				if (detectCycle()) {
					cycleDetectionTimestamp = System.currentTimeMillis();
					System.out.println("Cycle detected! Breaking path and using different strategy.");
					currentPath.clear();
					Direction randomMove = startRandomExploration(gameState, currentPos);
					lastAttemptedDirection = randomMove;
					return randomMove;
				}
			}

			if (lastPosition == null) {
				lastPosition = currentPos;
			} else if (!lastPosition.equals(currentPos)) {
				failedMoveAttempts.remove(lastPosition);
				lastPosition = currentPos;
			} else if (lastAttemptedDirection != null) {
				failedMoveAttempts.put(currentPos, failedMoveAttempts.getOrDefault(currentPos, 0) + 1);
				if (failedMoveAttempts.getOrDefault(currentPos, 0) >= 3) {
					System.out.println(
							"Too many failed attempts at " + currentPos + ", marking as potentially unreachable");
					unreachablePositions.add(currentPos);
					currentPath.clear();
				}
			}
			return state.handle(this, gameState, playerId);

		} catch (Exception e) {
			System.err.println("Error in movement strategy: " + e.getMessage());
			e.printStackTrace();
			return getRandomDirection();
		}
	}

	public Direction followPath(GameState gameState) {
		if (currentPath.isEmpty()) {
			return getRandomMove(gameState);
		}

		Direction nextDirection = currentPath.remove(0);
		lastAttemptedDirection = nextDirection;
		return nextDirection;
	}

	public List<Direction> calculateDirectPath(GameState gameState, Point start, Point target) {
		return pathFinder.findPath(gameState.getMap(), start, target, 1000, explorationStrategy.getVisitedPositions());
	}

	public List<Direction> calculateExplorationPath(GameState gameState, Point currentPos) {

		Optional<MapNode> explorationTarget = explorationStrategy.getNextExplorationTarget(gameState.getMap(),
				currentPos);

		if (explorationTarget.isPresent()) {
			Point targetPos = explorationTarget.get().getPosition();
			return pathFinder.findPath(gameState.getMap(), currentPos, targetPos, 800,
					explorationStrategy.getVisitedPositions());
		}

		List<Direction> randomMove = new ArrayList<>();
		randomMove.add(getOptimizedRandomMove(gameState, currentPos));
		return randomMove;
	}

	public Direction getRandomMove(GameState gameState) {
		Optional<MapNode> playerNodeOpt = gameState.getMap().getPlayerPosition();
		if (!playerNodeOpt.isPresent()) {
			return getRandomDirection();
		}

		Point currentPos = playerNodeOpt.get().getPosition();
		return getOptimizedRandomMove(gameState, currentPos);
	}

	private Direction getOptimizedRandomMove(GameState gameState, Point currentPos) {

		Optional<Direction> randomDirection = explorationStrategy.getSmartRandomDirection(gameState.getMap(),
				currentPos, recentlyVisited);

		if (randomDirection.isPresent()) {
			return randomDirection.get();
		}

		Optional<Direction> anyRandomDirection = explorationStrategy.getRandomValidDirection(gameState.getMap(),
				currentPos);

		if (anyRandomDirection.isPresent()) {
			return anyRandomDirection.get();
		}
		return getRandomDirection();
	}

	private Direction getRandomDirection() {
		Direction[] directions = Direction.values();
		return directions[random.nextInt(directions.length)];
	}

	private Direction startRandomExploration(GameState gameState, Point currentPos) {
		currentPath.clear();

		List<Direction> explorationMoves = new ArrayList<>();

		Set<Point> visitedDuringPlanning = new HashSet<>(recentlyVisited);

		for (int i = 0; i < 3; i++) {
			Direction bestMove = null;
			double bestScore = Double.NEGATIVE_INFINITY;

			for (Direction dir : Direction.values()) {
				Point newPos = dir.move(currentPos);
				Optional<MapNode> nodeOpt = gameState.getMap().getNode(newPos);

				if (nodeOpt.isPresent() && nodeOpt.get().isTraversable() && !visitedDuringPlanning.contains(newPos)
						&& !unreachablePositions.contains(newPos)) {

					double score = 10.0;

					if (!explorationStrategy.hasVisited(newPos)) {
						score += 5.0;
					}

					if (nodeOpt.get().getTerrain() == Terrain.MOUNTAIN) {
						score += 3.0;
					}

					score += random.nextDouble() * 2.0;

					if (score > bestScore) {
						bestScore = score;
						bestMove = dir;
					}
				}
			}

			if (bestMove != null) {
				explorationMoves.add(bestMove);
				Point nextPos = bestMove.move(currentPos);
				visitedDuringPlanning.add(nextPos);
				currentPos = nextPos;
			} else {
				break;
			}
		}

		if (!explorationMoves.isEmpty()) {
			currentPath.addAll(explorationMoves.subList(1, explorationMoves.size()));
			return explorationMoves.get(0);
		}

		return getRandomDirection();
	}

	private boolean detectCycle() {
		if (recentlyVisited.size() < 6) {
			return false;
		}

		Point[] positions = recentlyVisited.toArray(new Point[0]);
		int posCount = positions.length;

		if (posCount >= 4 && positions[posCount - 1].equals(positions[posCount - 3])
				&& positions[posCount - 2].equals(positions[posCount - 4])) {
			System.out.println("Detected cycle of length 2");
			return true;
		}

		if (posCount >= 6 && positions[posCount - 1].equals(positions[posCount - 4])
				&& positions[posCount - 2].equals(positions[posCount - 5])
				&& positions[posCount - 3].equals(positions[posCount - 6])) {
			System.out.println("Detected cycle of length 3");
			return true;
		}

		Map<Point, Integer> visitCounts = new HashMap<>();
		for (Point p : positions) {
			visitCounts.put(p, visitCounts.getOrDefault(p, 0) + 1);
		}

		if (visitCounts.size() <= 3 && recentlyVisited.size() >= 8) {
			int highVisitPositions = 0;
			for (int count : visitCounts.values()) {
				if (count >= 3) {
					highVisitPositions++;
				}
			}

			if (highVisitPositions >= 1) {
				System.out.println("Detected confinement to small area - visiting the same " + visitCounts.size()
						+ " positions repeatedly");
				return true;
			}
		}

		return false;
	}

	private void trackRecentlyVisited(Point position) {
		recentlyVisited.add(position);
		if (recentlyVisited.size() > MAX_RECENT_POSITIONS) {
			Iterator<Point> it = recentlyVisited.iterator();
			it.next();
			it.remove();
		}
	}

	public Optional<Point> getPredictedFortPosition() {
		return fortBacktracker.getPredictedFortPosition();
	}

	public MovementState getState() {
		return state;
	}

	public void setState(MovementState state) {
		this.state = state;
	}

	public List<Direction> getCurrentPath() {
		return currentPath;
	}

	public void setCurrentPath(List<Direction> path) {
		this.currentPath = new ArrayList<>(path);
	}

	public void clearPath() {
		this.currentPath.clear();
	}
}