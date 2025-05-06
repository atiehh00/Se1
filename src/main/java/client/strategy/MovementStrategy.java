package client.strategy;

import client.model.*;
import client.pathfinding.PathFinder;
import client.pathfinding.FortBacktracker;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MovementStrategy {
	private static final boolean DEBUG = false;

	private final Random random = new Random();
	private final PathFinder pathFinder;
	private final ExplorationStrategy explorationStrategy;
	private final FortBacktracker fortBacktracker;
	private final Set<Point> unreachablePositions = new HashSet<>();
	private final LinkedHashSet<Point> recentlyVisited = new LinkedHashSet<>();
	private final Map<Point, Integer> failedMoveAttempts = new HashMap<>();

	private List<Direction> currentPath = new ArrayList<>();
	private Point lastPosition = null;
	private Direction lastAttemptedDirection = null;
	private Direction currentOngoingMove = null;
	private int remainingActionsForMove = 0;
	private Point targetPosition = null;
	private int stuckCounter = 0;
	private int consecutiveRandomMoves = 0;
	private boolean exploringRandomly = false;
	private int randomExplorationMoves = 0;

	private static final int MAX_RECENT_POSITIONS = 10;
	private static final int MAX_STUCK_COUNT = 2;
	private static final int MAX_CONSECUTIVE_RANDOM = 3;
	private static final int MAX_RANDOM_EXPLORATION = 5;
	private static final int MAX_FAILED_ATTEMPTS = 2;
	private long lastDrasticUnstuckTime = 0;
	private static final long DRASTIC_UNSTUCK_COOLDOWN_MS = 5000;

	private final MovementContext movementContext = new MovementContext();

	private int currentGameRound = 0;

	public MovementStrategy() {
		this.pathFinder = new PathFinder();
		this.explorationStrategy = new ExplorationStrategy();
		this.fortBacktracker = new FortBacktracker();
	}

	public Direction calculateNextMove(GameState gameState, String playerId, Long timeoutMillis) {
		long startTime = System.currentTimeMillis();
		long timeBudget = timeoutMillis != null ? timeoutMillis - 50 : 950;
		Optional<MapNode> targetNodeOpt = Optional.empty();
		long pathBudget = 0;

		try {
			trackOpponent(gameState);

			Optional<MapNode> playerNodeOpt = gameState.getMap().getPlayerPosition();
			if (!playerNodeOpt.isPresent()) {
				return getRandomDirection();
			}

			MapNode playerNode = playerNodeOpt.get();
			Point currentPos = playerNode.getPosition();
			boolean hadTreasureBefore = explorationStrategy.hasTreasure;
			boolean hasTreasureNow = gameState.hasCollectedTreasure(playerId);

			if (!explorationStrategy.isHalfInfoInitialized()) {
				Optional<MapNode> myFortOpt = gameState.getMap().getMyFortPosition();
				if (myFortOpt.isPresent() && myFortOpt.get().getPosition().equals(currentPos)) {
					System.out.println("Player is at Fort position. Initializing half info...");
					explorationStrategy.initializeHalfInfo(gameState.getMap(), currentPos);
				} else if (currentGameRound > 0) {
					System.out.println("Player not at fort yet (Current: " + currentPos + ", Fort: "
							+ (myFortOpt.isPresent() ? myFortOpt.get().getPosition().toString() : "Unknown")
							+ "). Delaying half initialization.");
				} else {
				}
			}
			explorationStrategy.setHasTreasure(hasTreasureNow);

			if (!hadTreasureBefore && hasTreasureNow) {
				System.out.println("===== PHASE TRANSITION: TREASURE ACQUIRED =====");
				System.out.println("Switching to enemy fort targeting strategy");
				currentPath.clear();
			}

			Direction multiActionMove = handleMultiActionMove(gameState, currentPos);
			if (multiActionMove != null) {
				return multiActionMove;
			}

			explorationStrategy.markVisited(currentPos);

			Optional<MapNode> enhancedVisibility = checkMountainVisibility(gameState, currentPos);

			Optional<MapNode> highPriorityTargetOpt = checkForHighPriorityTarget(gameState, playerId);
			if (highPriorityTargetOpt.isPresent()) {
				Point targetPos = highPriorityTargetOpt.get().getPosition();
				System.out.println("HIGH PRIORITY TARGET DETECTED: " + highPriorityTargetOpt.get().getTerrain() + " at "
						+ targetPos + ". Calculating path...");
				pathBudget = Math.max(300, timeBudget - (System.currentTimeMillis() - startTime));
				System.out.println("Allocated path budget (High Prio / Initial Target): " + pathBudget + "ms");

				currentPath = pathFinder.findPath(gameState.getMap(), currentPos, targetPos, pathBudget,
						explorationStrategy.getVisitedPositions(), explorationStrategy);

				if (!currentPath.isEmpty()) {
					System.out.println("Path found to high-priority target.");
					Direction nextDirectionHP = currentPath.remove(0);
					if (isValidMove(gameState.getMap(), currentPos, nextDirectionHP)) {
						lastAttemptedDirection = nextDirectionHP;
						int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, nextDirectionHP);
						if (requiredActions > 1) {
							startMultiActionMove(nextDirectionHP, requiredActions, currentPos);
						}
						if (!isValidMove(gameState.getMap(), currentPos, nextDirectionHP)) {
							System.out.println("WARN: High-priority path move (" + nextDirectionHP
									+ ") became invalid! Falling back.");
							return explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
									.orElse(Direction.UP);
						}
						return nextDirectionHP;
					} else {
						System.out.println("WARN: First step of high-priority path invalid!");
						currentPath.clear();
					}
				} else {
					System.out.println("Path failed for high-priority target, attempting directional step...");
					List<Direction> directionalPath = pathFinder.getDirectionalPath(gameState.getMap(), currentPos,
							targetPos);
					if (!directionalPath.isEmpty()) {
						Direction singleStep = directionalPath.get(0);
						if (isValidMove(gameState.getMap(), currentPos, singleStep)) {
							System.out.println("Using directional step towards high-priority target: " + singleStep);
							lastAttemptedDirection = singleStep;
							int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, singleStep);
							if (requiredActions > 1) {
								startMultiActionMove(singleStep, requiredActions, currentPos);
							}
							if (!isValidMove(gameState.getMap(), currentPos, singleStep)) {
								System.out.println("WARN: High-priority directional step (" + singleStep
										+ ") became invalid! Falling back.");
								return explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
										.orElse(Direction.UP);
							}
							return singleStep;
						} else {
							System.out.println("Directional step " + singleStep + " is invalid.");
						}
					} else {
						System.out.println("Directional step calculation failed for high-priority target.");
					}
				}
				System.out.println("WARN: Could not path or step towards high-priority target " + targetPos
						+ ". Continuing with normal logic...");
			}

			trackRecentlyVisited(currentPos);

			if (detectCycle()) {
				if (DEBUG)
					System.out.println("Detected movement cycle. Breaking cycle with random exploration.");
				currentPath.clear();
				return getOptimizedExplorationMove(gameState, currentPos, playerId,
						timeBudget - (System.currentTimeMillis() - startTime));
			}
			if (lastPosition != null && lastPosition.equals(currentPos)) {
				Direction stuckMove = handleSamePositionStuck(gameState, currentPos, playerId);
				if (stuckMove != null) {
					return stuckMove;
				}
			} else {
				handleSuccessfulMove(currentPos);
			}

			if (!currentPath.isEmpty()) {
				Direction potentialNextDirection = currentPath.get(0);

				if (isValidMove(gameState.getMap(), currentPos, potentialNextDirection)) {
					Direction nextDirection = currentPath.remove(0);
					lastAttemptedDirection = nextDirection;

					int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, nextDirection);
					if (requiredActions > 1) {
						startMultiActionMove(nextDirection, requiredActions, currentPos);
					}
					if (!isValidMove(gameState.getMap(), currentPos, nextDirection)) {
						System.out.println(
								"WARN: Path follow move (" + nextDirection + ") became invalid! Falling back.");
						return explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
								.orElse(Direction.UP);
					}
					return nextDirection;
				} else {
					System.out.println("WARN: Next step in current path (" + potentialNextDirection
							+ ") is invalid. Clearing path.");
					currentPath.clear();
				}
			}

			if (enhancedVisibility.isPresent()) {

				if (DEBUG)
					System.out.println("Enhanced visibility detected target. Prioritizing direct path.");

				Point targetPos = enhancedVisibility.get().getPosition();
				long directPathBudget = Math.max(300, timeBudget - (System.currentTimeMillis() - startTime));
				System.out.println("Allocated path budget (Mountain Vis): " + directPathBudget + "ms");
				List<Direction> directPath = pathFinder.findPath(gameState.getMap(), currentPos, targetPos,
						directPathBudget, explorationStrategy.getVisitedPositions(), explorationStrategy);

				if (!directPath.isEmpty()) {
					currentPath = directPath;
					System.out.println("Found direct path via mountain visibility to: " + targetPos + ". Length: "
							+ currentPath.size());
				}
			}

			if (!highPriorityTargetOpt.isPresent() && currentPath.isEmpty()) {
				System.out.println("No high-priority target, determining next best target...");
				targetNodeOpt = determineExplorationOrPredictionTarget(gameState, playerId, currentPos);
				if (targetNodeOpt.isPresent()) {
					Point targetPos = targetNodeOpt.get().getPosition();
					pathBudget = Math.max(300, timeBudget - (System.currentTimeMillis() - startTime)); // Increased min
																										// budget to
																										// 300ms
					System.out.println("Allocated path budget (Determined Target): " + pathBudget + "ms");
					currentPath = pathFinder.findPath(gameState.getMap(), currentPos, targetPos, pathBudget,
							explorationStrategy.getVisitedPositions(), explorationStrategy);

					if (currentPath.isEmpty()) {
						System.out.println("WARN: Pathfinding failed for determined target: " + targetPos);
					}
				}
			}

			if (targetNodeOpt.isPresent()) {
				System.out.println("Determined Target: " + targetNodeOpt.get().getPosition() + " (Type: "
						+ targetNodeOpt.get().getTerrain() + ")");
			} else if (currentPath.isEmpty()) {
				System.out.println("No specific target found, entering exploration mode.");
			}

			long timeAfterPathfinding = System.currentTimeMillis();
			if (pathBudget > 0) {
				System.out
						.println("Pathfinding took: " + (timeAfterPathfinding - (startTime + (timeBudget - pathBudget)))
								+ " ms. Path length: " + currentPath.size());
			}
			System.out.println("DEBUG: Before final path step. Path size: " + currentPath.size()); // Added logging
			if (currentPath.isEmpty()) {
				System.out.println("Path is empty before final step. Falling back to exploration move.");
				Direction finalMove = getOptimizedExplorationMove(gameState, currentPos, playerId,
						timeBudget - (timeAfterPathfinding - startTime));
				lastAttemptedDirection = finalMove;
				if (!isValidMove(gameState.getMap(), currentPos, finalMove)) {
					System.out.println(
							"WARN: Fallback exploration move (" + finalMove + ") is invalid! Falling back AGAIN.");
					finalMove = explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
							.orElseGet(() -> {
								System.err.println("CRITICAL: No valid moves found, even random! Defaulting to DOWN.");
								return Direction.DOWN;
							});
					lastAttemptedDirection = finalMove;
				}
				return finalMove;
			}

			Direction nextDirection = currentPath.remove(0);
			System.out.println("DEBUG: After removing first step. Path size now: " + currentPath.size()); // Added
																											// logging

			if (isValidMove(gameState.getMap(), currentPos, nextDirection)) {
				lastAttemptedDirection = nextDirection;

				int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, nextDirection);
				if (requiredActions > 1) {
					startMultiActionMove(nextDirection, requiredActions, currentPos);
				}

				if (!isValidMove(gameState.getMap(), currentPos, nextDirection)) {
					System.out.println("WARN: First step of calculated path (" + nextDirection
							+ ") is invalid. Falling back to exploration.");
					currentPath.clear();
					Direction finalMove = getOptimizedExplorationMove(gameState, currentPos, playerId,
							timeBudget - (timeAfterPathfinding - startTime));
					if (!isValidMove(gameState.getMap(), currentPos, finalMove)) {
						System.out.println(
								"WARN: Fallback exploration move (" + finalMove + ") is invalid! Falling back AGAIN.");
						finalMove = explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
								.orElseGet(() -> {
									System.err.println(
											"CRITICAL: No valid moves found, even random! Defaulting to DOWN.");
									return Direction.DOWN;
								});
					}
					lastAttemptedDirection = finalMove;
					return finalMove;
				}
				return nextDirection;
			}

			System.out.println("WARN: First step of calculated path (" + nextDirection
					+ ") is invalid. Falling back to exploration.");
			currentPath.clear();
			Direction finalMove = getOptimizedExplorationMove(gameState, currentPos, playerId,
					timeBudget - (timeAfterPathfinding - startTime));
			if (!isValidMove(gameState.getMap(), currentPos, finalMove)) {
				System.out
						.println("WARN: Fallback exploration move (" + finalMove + ") is invalid! Falling back AGAIN.");
				finalMove = explorationStrategy.getRandomValidDirection(gameState.getMap(), currentPos)
						.orElseGet(() -> {
							System.err.println("CRITICAL: No valid moves found, even random! Defaulting to DOWN.");
							return Direction.DOWN;
						});
			}
			lastAttemptedDirection = finalMove;
			return finalMove;

		} catch (Exception e) {
			System.err.println("Error in move calculation: " + e.getMessage());
			e.printStackTrace();
			System.err.println("Exception occurred, falling back to default direction: DOWN");
			lastAttemptedDirection = Direction.DOWN;
			return lastAttemptedDirection;
		}
	}

	private Direction getSimpleMove(GameState gameState, Point currentPos) {
		if (gameState == null || currentPos == null) {
			return getRandomDirection();
		}
		if (lastAttemptedDirection != null) {
			if (isValidMove(gameState.getMap(), currentPos, lastAttemptedDirection)) {
				return lastAttemptedDirection;
			}
		}
		for (Direction dir : Direction.values()) {
			if (isValidMove(gameState.getMap(), currentPos, dir)) {
				return dir;
			}
		}
		return getRandomDirection();
	}

	private Optional<MapNode> determineExplorationOrPredictionTarget(GameState gameState, String playerId,
			Point currentPos) {
		boolean hasTreasure = explorationStrategy.hasTreasure;

		if (hasTreasure) {
			Optional<MapNode> knownFort = gameState.getMap().getEnemyFortPosition();
			if (knownFort.isPresent()) {
				System.out.println("Targeting KNOWN ENEMY FORT at: " + knownFort.get().getPosition());
				return knownFort;
			}

			Optional<Point> predictedFort = fortBacktracker.getPredictedFortPosition();
			if (predictedFort.isPresent()) {
				Optional<MapNode> predictedFortNodeOpt = gameState.getMap().getNode(predictedFort.get());
				if (predictedFortNodeOpt.isPresent() && predictedFortNodeOpt.get().isTraversable()) {
					System.out.println("Targeting PREDICTED ENEMY FORT at: " + predictedFort.get());

					if (explorationStrategy.isInRelevantHalf(predictedFort.get(), gameState.getMap())) {
						System.out.println("Predicted fort is in the ENEMY half (good)");
					} else {
						System.out.println("WARN: Predicted fort is NOT in the expected enemy half!");
					}

					return predictedFortNodeOpt;
				}
			}

			System.out.println("No known/predicted fort. Exploring ENEMY half...");

			List<MapNode> mountainTargets = explorationStrategy.getPrioritizedMountainTargets(gameState.getMap(),
					currentPos);
			if (!mountainTargets.isEmpty()) {
				MapNode bestMountain = mountainTargets.get(0);
				System.out.println(
						"Targeting mountain at " + bestMountain.getPosition() + " for visibility in ENEMY half");
				return Optional.of(bestMountain);
			}

			System.out.println("No suitable mountains found. Getting general exploration target in ENEMY half...");
			return explorationStrategy.getNextExplorationTarget(gameState.getMap(), currentPos, true);

		} else {
			Optional<MapNode> knownTreasure = gameState.getMap().getTreasurePosition();
			if (knownTreasure.isPresent()) {
				System.out.println("Targeting KNOWN TREASURE at: " + knownTreasure.get().getPosition());
				return knownTreasure;
			}

			System.out.println("No visible treasure. Exploring OWN half...");
			List<MapNode> mountainTargets = explorationStrategy.getPrioritizedMountainTargets(gameState.getMap(),
					currentPos);
			if (!mountainTargets.isEmpty()) {
				MapNode bestMountain = mountainTargets.get(0);
				System.out
						.println("Targeting mountain at " + bestMountain.getPosition() + " for visibility in OWN half");
				return Optional.of(bestMountain);
			}

			System.out.println("No suitable mountains found. Getting general exploration target in OWN half...");
			return explorationStrategy.getNextExplorationTarget(gameState.getMap(), currentPos, true);
		}
	}

	private Optional<MapNode> findBestMountainForVisibility(GameMap map) {
		Optional<MapNode> playerPos = map.getPlayerPosition();
		if (!playerPos.isPresent()) {
			return Optional.empty();
		}

		Point currentPos = playerPos.get().getPosition();
		MapNode bestMountain = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() != Terrain.MOUNTAIN) {
				continue;
			}

			Point mountainPos = node.getPosition();

			int distance = currentPos.manhattanDistance(mountainPos);
			double distanceFactor = 10.0 / (distance + 1.0);

			boolean visited = explorationStrategy.hasVisited(mountainPos);
			double visitedFactor = visited ? 0.5 : 2.0;

			double score = distanceFactor + visitedFactor;

			if (!explorationStrategy.isInRelevantHalf(mountainPos, map)) {
				score -= 100.0;
			}

			if (score > bestScore) {
				bestScore = score;
				bestMountain = node;
			}
		}

		return (bestMountain != null && bestScore > 0) ? Optional.of(bestMountain) : Optional.empty();
	}

	private Direction getExplorationMove(GameState gameState, Point currentPos, String playerId,
			long timeBudgetMillis) {
		long startTime = System.currentTimeMillis();

		if (DEBUG)
			System.out.println("Generating coverage path...");

		currentPath = explorationStrategy.generateCoveragePath(gameState.getMap(), currentPos, pathFinder,
				timeBudgetMillis);

		long timeAfterCoveragePath = System.currentTimeMillis();
		if (DEBUG)
			System.out.println("Coverage path generation took " + (timeAfterCoveragePath - startTime)
					+ " ms, found path length: " + currentPath.size());

		if (!currentPath.isEmpty()) {
			Direction nextDirection = currentPath.remove(0);

			if (isValidMove(gameState.getMap(), currentPos, nextDirection)) {
				lastAttemptedDirection = nextDirection;
				int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, nextDirection);
				if (requiredActions > 1) {
					startMultiActionMove(nextDirection, requiredActions, currentPos);
				}
				return nextDirection;
			} else {
				if (DEBUG)
					System.out.println("WARN: First step of coverage path invalid. Clearing path.");
				currentPath.clear();
			}
		} else {
			if (DEBUG)
				System.out.println("WARN: Coverage path generation returned empty path.");
		}

		if (DEBUG)
			System.out.println("Falling back to optimized random move.");
		return getOptimizedRandomMove(gameState, currentPos, playerId);
	}

	private Direction getOptimizedRandomMove(GameState gameState, Point currentPos, String playerId) {
		List<Direction> directions = new ArrayList<>(Arrays.asList(Direction.values()));
		if (gameState.hasCollectedTreasure(playerId) && gameState.getMap().getEnemyFortPosition().isPresent()) {

			Point fortPos = gameState.getMap().getEnemyFortPosition().get().getPosition();
			Collections.sort(directions, (d1, d2) -> {
				Point p1 = d1.move(currentPos);
				Point p2 = d2.move(currentPos);
				return Integer.compare(p1.manhattanDistance(fortPos), p2.manhattanDistance(fortPos));
			});

			if (random.nextDouble() < 0.3) {
				Collections.shuffle(directions, random);
			}
		} else if (gameState.getMap().getTreasurePosition().isPresent()) {
			Point treasurePos = gameState.getMap().getTreasurePosition().get().getPosition();
			Collections.sort(directions, (d1, d2) -> {
				Point p1 = d1.move(currentPos);
				Point p2 = d2.move(currentPos);
				return Integer.compare(p1.manhattanDistance(treasurePos), p2.manhattanDistance(treasurePos));
			});
			if (random.nextDouble() < 0.3) {
				Collections.shuffle(directions, random);
			}
		} else {
			Collections.shuffle(directions, random);
		}

		Direction bestDirection = null;
		double bestScore = -Double.MAX_VALUE;

		Map<Point, Integer> visitCounts = new HashMap<>();
		for (Point p : recentlyVisited) {
			visitCounts.put(p, visitCounts.getOrDefault(p, 0) + 1);
		}

		for (Direction dir : directions) {
			if (isValidMove(gameState.getMap(), currentPos, dir)) {
				Point targetPos = dir.move(currentPos);

				double score = 0;

				if (!explorationStrategy.isInRelevantHalf(targetPos, gameState.getMap())) {
					score -= 100.0;
					if (explorationStrategy.hasTreasure) {
						System.out.println(
								"DEBUG: Penalizing move " + dir + " for returning to own half after treasure.");
					} else {
						System.out.println(
								"DEBUG: Penalizing move " + dir + " for crossing to enemy half before treasure.");
					}
				}

				if (explorationStrategy.hasVisited(targetPos)) {
					score -= 30.0;
				}

				int recentVisits = visitCounts.getOrDefault(targetPos, 0);
				if (recentVisits > 0) {
					score -= Math.pow(5.0, recentVisits);
				}

				int failCount = failedMoveAttempts.getOrDefault(targetPos, 0);
				score -= failCount * 10.0;

				if (dir == lastAttemptedDirection) {
					score -= 5.0;
				}
				Optional<MapNode> targetNodeOpt = gameState.getMap().getNode(targetPos);
				if (targetNodeOpt.isPresent() && targetNodeOpt.get().getTerrain() == Terrain.MOUNTAIN) {
					score += 15.0;
				}

				if (gameState.hasCollectedTreasure(playerId) && gameState.getMap().getEnemyFortPosition().isPresent()) {

					Point fortPos = gameState.getMap().getEnemyFortPosition().get().getPosition();
					int currentDist = currentPos.manhattanDistance(fortPos);
					int newDist = targetPos.manhattanDistance(fortPos);

					if (newDist < currentDist) {
						score += 25.0;
					} else if (newDist > currentDist) {
						score -= 15.0;
					}
				} else if (gameState.getMap().getTreasurePosition().isPresent()) {
					Point treasurePos = gameState.getMap().getTreasurePosition().get().getPosition();
					int currentDist = currentPos.manhattanDistance(treasurePos);
					int newDist = targetPos.manhattanDistance(treasurePos);

					if (newDist < currentDist) {
						score += 25.0;
					} else if (newDist > currentDist) {
						score -= 15.0;
					}
				}

				Point targetPoint = null;
				if (gameState.hasCollectedTreasure(playerId)) {
					targetPoint = gameState.getMap().getEnemyFortPosition().map(MapNode::getPosition)
							.orElse(calculateEnemyHalfCenter(gameState.getMap()));
				} else {
					targetPoint = gameState.getMap().getTreasurePosition().map(MapNode::getPosition)
							.orElse(calculateOwnHalfCenter(gameState.getMap(), currentPos));
				}

				if (targetPoint != null) {
					int currentDistToTarget = currentPos.manhattanDistance(targetPoint);
					int newDistToTarget = targetPos.manhattanDistance(targetPoint);
					if (newDistToTarget < currentDistToTarget) {
						score += 20.0;
					} else if (newDistToTarget > currentDistToTarget) {
						score -= 10.0;
					}
				} else {
					boolean targetIsInRelevantHalf = explorationStrategy.isInRelevantHalf(targetPos,
							gameState.getMap());
					if (targetIsInRelevantHalf) {
						score += 10.0;
					}
				}

				score += random.nextDouble() * 0.5;

				if (score > bestScore) {
					bestScore = score;
					bestDirection = dir;
				}
			}
		}

		if (bestDirection == null) {
			return getRandomDirection();
		}

		lastAttemptedDirection = bestDirection;
		int requiredActions = calculateRequiredActions(gameState.getMap(), currentPos, bestDirection);
		if (requiredActions > 1) {
			startMultiActionMove(bestDirection, requiredActions, currentPos);
		}

		return bestDirection;
	}

	private Direction getRandomDirection() {
		System.err.println("ERROR: Unsafe getRandomDirection() called!");
		Direction[] allDirections = Direction.values();
		return allDirections[random.nextInt(allDirections.length)];
	}

	private Direction handleMultiActionMove(GameState gameState, Point currentPos) {
		if (currentOngoingMove != null && remainingActionsForMove > 0) {
			if (gameState.isGameOver()) {
				resetMultiActionMove();
				return null;
			}

			if (targetPosition != null && targetPosition.equals(currentPos)) {
				resetMultiActionMove();
				return null;
			}

			remainingActionsForMove--;

			if (isValidMove(gameState.getMap(), currentPos, currentOngoingMove)) {
				if (remainingActionsForMove <= 0) {
					Direction moveToReturn = currentOngoingMove;
					resetMultiActionMove();
					return moveToReturn;
				} else {
					return currentOngoingMove;
				}
			} else {
				resetMultiActionMove();

				Optional<Direction> alternativeDirection = explorationStrategy
						.getRandomValidDirection(gameState.getMap(), currentPos);

				if (alternativeDirection.isPresent()) {
					return alternativeDirection.get();
				}
			}
		}

		return null;
	}

	private void resetMultiActionMove() {
		currentOngoingMove = null;
		remainingActionsForMove = 0;
		targetPosition = null;
	}

	private void startMultiActionMove(Direction direction, int requiredActions, Point currentPos) {
		currentOngoingMove = direction;
		remainingActionsForMove = requiredActions - 1;
		targetPosition = direction.move(currentPos);
	}

	private Direction handleSamePositionStuck(GameState gameState, Point currentPos, String playerId) {
		if (lastAttemptedDirection != null) {
			failedMoveAttempts.put(currentPos, failedMoveAttempts.getOrDefault(currentPos, 0) + 1);
			if (failedMoveAttempts.getOrDefault(currentPos, 0) >= MAX_FAILED_ATTEMPTS) {
				unreachablePositions.add(currentPos);
				Point failedTarget = lastAttemptedDirection.move(currentPos);
				unreachablePositions.add(failedTarget);
				System.out.println("Marking positions " + currentPos + " and " + failedTarget
						+ " as unreachable after multiple failed attempts at " + currentPos);
				currentPath.clear();
			}
		}

		stuckCounter++;
		if (stuckCounter >= MAX_STUCK_COUNT) {
			System.out.println("STUCK: Counter reached " + stuckCounter + " at " + currentPos + ". Last attempted: "
					+ lastAttemptedDirection);
			currentPath.clear();
			stuckCounter = 0;
			consecutiveRandomMoves++;

			if (consecutiveRandomMoves > MAX_CONSECUTIVE_RANDOM
					&& (System.currentTimeMillis() - lastDrasticUnstuckTime > DRASTIC_UNSTUCK_COOLDOWN_MS)) {
				lastDrasticUnstuckTime = System.currentTimeMillis();
				return handleDrasticUnstuck(gameState, currentPos, playerId);
			}

			boolean hasTreasure = gameState.hasCollectedTreasure(playerId);
			Optional<MapNode> fortNodeOpt = gameState.getMap().getEnemyFortPosition();

			if (lastAttemptedDirection != null) {
				System.out.println("Stuck: Trying perpendicular moves to " + lastAttemptedDirection);
				Direction[] perpendicularMoves = { lastAttemptedDirection.turnClockwise(),
						lastAttemptedDirection.turnCounterClockwise() };
				Collections.shuffle(Arrays.asList(perpendicularMoves), random);

				for (Direction perpDir : perpendicularMoves) {
					if (isValidMove(gameState.getMap(), currentPos, perpDir)) {
						Point nextPos = perpDir.move(currentPos);
						if (!recentlyVisited.contains(nextPos) || !explorationStrategy.hasVisited(nextPos)) {
							System.out.println("Stuck: Found promising perpendicular move: " + perpDir);
							lastAttemptedDirection = perpDir;
							return perpDir;
						}
					}
				}
				for (Direction perpDir : perpendicularMoves) {
					if (isValidMove(gameState.getMap(), currentPos, perpDir)) {
						System.out.println("Stuck: Found valid (but possibly visited) perpendicular move: " + perpDir);
						lastAttemptedDirection = perpDir;
						return perpDir;
					}
				}
				System.out.println("Stuck: Perpendicular moves not helpful or invalid.");
			}

			System.out.println("Stuck: Falling back to optimized random move.");
			return getOptimizedRandomMove(gameState, currentPos, playerId);
		}

		return null;
	}

	private Direction handleDrasticUnstuck(GameState gameState, Point currentPos, String playerId) {
		System.out.println("DRASTIC UNSTUCK: Triggered at " + currentPos + ". Clearing path & attempting recovery.");
		consecutiveRandomMoves = 0;
		currentPath.clear();

		System.out.println("DRASTIC: Clearing recent history and unreachable points.");
		recentlyVisited.clear();
		unreachablePositions.clear();
		failedMoveAttempts.clear();

		if (random.nextDouble() < 0.1) {
			System.out.println("DRASTIC: Performing complete exploration reset");
			explorationStrategy.clearVisited();
		} else {
			Set<Point> visitedPositions = explorationStrategy.getVisitedPositions();
			Set<Point> toRemove = new HashSet<>();
			int clearRadius = 3;
			for (Point p : visitedPositions) {
				if (p.manhattanDistance(currentPos) <= clearRadius) {
					toRemove.add(p);
				}
			}
			for (Point p : toRemove) {
				visitedPositions.remove(p);
			}
			System.out
					.println("DRASTIC: Cleared " + toRemove.size() + " visited positions within radius " + clearRadius);
		}

		boolean hasTreasure = gameState.hasCollectedTreasure(playerId);
		Optional<MapNode> fortNodeOpt = gameState.getMap().getEnemyFortPosition();
		Optional<MapNode> treasureNodeOpt = gameState.getMap().getTreasurePosition();
		Point targetPos = null;
		String targetType = "exploration";
		Optional<MapNode> nearbyMountain = findNearestUnvisitedMountain(gameState.getMap(), currentPos);
		if (nearbyMountain.isPresent()
				&& explorationStrategy.isInRelevantHalf(nearbyMountain.get().getPosition(), gameState.getMap())) {
			targetPos = nearbyMountain.get().getPosition();
			targetType = "Nearby Mountain for visibility";
		} else if (hasTreasure && fortNodeOpt.isPresent()) {
			targetPos = fortNodeOpt.get().getPosition();
			targetType = "Fort";
		} else if (treasureNodeOpt.isPresent()) {
			targetPos = treasureNodeOpt.get().getPosition();
			targetType = "Treasure";
		} else {
			List<MapNode> prioritizedTargets = explorationStrategy.getPrioritizedExplorationTargets(gameState.getMap(),
					currentPos, true);

			if (!prioritizedTargets.isEmpty()) {
				targetPos = prioritizedTargets.get(0).getPosition();
				targetType = "Prioritized unvisited in " + (explorationStrategy.hasTreasure ? "ENEMY" : "OWN")
						+ " half";
			} else {
				targetPos = calculateCenterPoint(explorationStrategy.getVisitedPositions());
				targetType = "Away from explored center";
			}
		}
		System.out
				.println("Drastic Unstuck: Target type: " + targetType + (targetPos != null ? " at " + targetPos : ""));

		if (targetPos != null) {
			List<Direction> quickPath = pathFinder.findPath(gameState.getMap(), currentPos, targetPos, 50,
					explorationStrategy.getVisitedPositions());

			if (!quickPath.isEmpty()) {
				Direction pathDir = quickPath.get(0);
				if (isValidMove(gameState.getMap(), currentPos, pathDir)) {
					System.out.println("Drastic Unstuck: Found valid path step toward " + targetType);
					lastAttemptedDirection = pathDir;
					return pathDir;
				}
			}
		}

		List<Direction> bestMoves = new ArrayList<>();
		List<Direction> goodMoves = new ArrayList<>();
		List<Direction> okMoves = new ArrayList<>();

		for (Direction dir : Direction.values()) {
			Point nextPos = dir.move(currentPos);
			if (isValidMove(gameState.getMap(), currentPos, dir)) {
				boolean leadsToTarget = false;
				if (targetPos != null) {
					if (targetType.equals("Fort") || targetType.equals("Treasure")
							|| targetType.startsWith("Prioritized") || targetType.startsWith("Nearby")) {
						leadsToTarget = nextPos.manhattanDistance(targetPos) < currentPos.manhattanDistance(targetPos);
					} else {
						if (targetPos != null) {
							leadsToTarget = nextPos.manhattanDistance(targetPos) > currentPos
									.manhattanDistance(targetPos);
						}
					}
				}
				boolean leadsToUnvisited = !explorationStrategy.hasVisited(nextPos);
				boolean leadsToRelevantHalf = explorationStrategy.isInRelevantHalf(nextPos, gameState.getMap());
				Optional<MapNode> nextNode = gameState.getMap().getNode(nextPos);
				boolean leadsToMountain = nextNode.isPresent()
						&& nextNode.get().getTerrain() == client.model.Terrain.MOUNTAIN;

				if ((leadsToTarget && leadsToUnvisited && leadsToRelevantHalf)
						|| (leadsToMountain && leadsToUnvisited && leadsToRelevantHalf)) {
					bestMoves.add(dir);
				} else if ((leadsToTarget && leadsToRelevantHalf) || (leadsToUnvisited && leadsToRelevantHalf)
						|| (leadsToMountain)) {
					goodMoves.add(dir);
				} else {
					okMoves.add(dir);
				}
			}
		}

		Collections.shuffle(bestMoves, random);
		Collections.shuffle(goodMoves, random);
		Collections.shuffle(okMoves, random);

		if (!bestMoves.isEmpty()) {
			System.out.println("Drastic Unstuck: Found 'best' move: " + bestMoves.get(0));
			lastAttemptedDirection = bestMoves.get(0);
			return bestMoves.get(0);
		}
		if (!goodMoves.isEmpty()) {
			System.out.println("Drastic Unstuck: Found 'good' move: " + goodMoves.get(0));
			lastAttemptedDirection = goodMoves.get(0);
			return goodMoves.get(0);
		}
		if (lastAttemptedDirection != null) {
			Direction oppositeDirection = lastAttemptedDirection.getOpposite();
			if (okMoves.contains(oppositeDirection)) {
				System.out.println("Drastic Unstuck: Trying opposite of last failed move: " + oppositeDirection);
				lastAttemptedDirection = oppositeDirection;
				return oppositeDirection;
			}
		}
		if (!okMoves.isEmpty()) {
			System.out.println("Drastic Unstuck: Found 'ok' move: " + okMoves.get(0));
			lastAttemptedDirection = okMoves.get(0);
			return okMoves.get(0);
		}
		Direction randomDirection = getRandomDirection();
		System.out.println("Drastic Unstuck: Last resort: Using random direction: " + randomDirection);
		lastAttemptedDirection = randomDirection;
		return randomDirection;
	}

	private Optional<MapNode> findNearestUnvisitedMountain(GameMap map, Point currentPos) {
		MapNode bestMountain = null;
		int bestDistance = Integer.MAX_VALUE;

		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == client.model.Terrain.MOUNTAIN
					&& !explorationStrategy.hasVisited(node.getPosition())) {

				int distance = currentPos.manhattanDistance(node.getPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					bestMountain = node;
				}
			}
		}

		return Optional.ofNullable(bestMountain);
	}

	public void updateGameRound() {
		currentGameRound++;
	}

	public boolean isInRandomOpponentPhase() {
		return currentGameRound <= 16;
	}

	private boolean detectCycle() {
		if (recentlyVisited.size() < 4) {
			return false;
		}

		Point[] positions = recentlyVisited.toArray(new Point[0]);
		int posCount = positions.length;

		if (posCount >= 4 && positions[posCount - 1].equals(positions[posCount - 3])
				&& positions[posCount - 2].equals(positions[posCount - 4])) {
			System.out.println(
					"Detected cycle of length 2: " + positions[posCount - 2] + " <-> " + positions[posCount - 1]);
			return true;
		}

		if (posCount >= 6 && positions[posCount - 1].equals(positions[posCount - 4])
				&& positions[posCount - 2].equals(positions[posCount - 5])
				&& positions[posCount - 3].equals(positions[posCount - 6])) {
			System.out.println("Detected cycle of length 3");
			return true;
		}

		if (posCount >= 8 && positions[posCount - 1].equals(positions[posCount - 5])
				&& positions[posCount - 2].equals(positions[posCount - 6])
				&& positions[posCount - 3].equals(positions[posCount - 7])
				&& positions[posCount - 4].equals(positions[posCount - 8])) {
			System.out.println("Detected cycle of length 4");
			return true;
		}

		Map<Point, Integer> visitCounts = new HashMap<>();
		int checkLastN = Math.min(posCount, 8);
		for (int i = posCount - checkLastN; i < posCount; i++) {
			Point p = positions[i];
			visitCounts.put(p, visitCounts.getOrDefault(p, 0) + 1);
		}

		int uniquePositionsInRecent = visitCounts.size();
		if (uniquePositionsInRecent <= 2 && checkLastN >= 4) {
			System.out.println("Detected confinement: Visiting " + uniquePositionsInRecent
					+ " unique positions in last " + checkLastN + " moves.");
			return true;
		}
		if (uniquePositionsInRecent <= 3 && checkLastN >= 6) {
			System.out.println("Detected confinement: Visiting " + uniquePositionsInRecent
					+ " unique positions in last " + checkLastN + " moves.");
			return true;
		}

		Point currentPos = positions[posCount - 1];
		if (visitCounts.getOrDefault(currentPos, 0) >= 3 && checkLastN >= 4) {
			System.out.println("Detected multiple recent visits to current position: " + currentPos);
			return true;
		}

		return false;
	}

	private void trackRecentlyVisited(Point currentPos) {
		recentlyVisited.add(currentPos);
		if (recentlyVisited.size() > MAX_RECENT_POSITIONS) {
			Iterator<Point> iterator = recentlyVisited.iterator();
			iterator.next();
			iterator.remove();
		}
	}

	private void handleSuccessfulMove(Point currentPos) {
		stuckCounter = 0;
		failedMoveAttempts.remove(currentPos);
		lastPosition = currentPos;
	}

	private boolean isValidMove(GameMap map, Point position, Direction direction) {
		Point newPos = direction.move(position);
		if (unreachablePositions.contains(newPos)) {
			return false;
		}
		Optional<MapNode> nodeOpt = map.getNode(newPos);
		return nodeOpt.isPresent() && nodeOpt.get().isTraversable();
	}

	private int calculateRequiredActions(GameMap map, Point position, Direction direction) {
		Optional<MapNode> currentNodeOpt = map.getNode(position);
		if (!currentNodeOpt.isPresent()) {
			return 1;
		}

		Point targetPos = direction.move(position);
		Optional<MapNode> targetNodeOpt = map.getNode(targetPos);
		if (!targetNodeOpt.isPresent()) {
			return 1;
		}

		MapNode currentNode = currentNodeOpt.get();
		MapNode targetNode = targetNodeOpt.get();

		return currentNode.getMovementCostTo(targetNode);
	}

	public void resetPath() {
		currentPath.clear();
		resetMultiActionMove();
	}

	private Optional<MapNode> checkMountainVisibility(GameState gameState, Point currentPos) {
		Optional<MapNode> currentNodeOpt = gameState.getMap().getNode(currentPos);
		if (!currentNodeOpt.isPresent()) {
			return Optional.empty();
		}

		MapNode currentNode = currentNodeOpt.get();
		if (currentNode.getTerrain() != Terrain.MOUNTAIN) {
			return Optional.empty();
		}

		System.out.println("Player is on a mountain. Checking extended visibility...");
		int visibilityRange = 3;
		for (MapNode node : gameState.getMap().getAllNodes()) {
			Point nodePos = node.getPosition();

			if (Math.abs(nodePos.x - currentPos.x) > visibilityRange
					|| Math.abs(nodePos.y - currentPos.y) > visibilityRange) {
				continue;
			}

			explorationStrategy.markVisited(nodePos);

			if (node.hasTreasure()) {
				System.out.println("MOUNTAIN VISIBILITY: Found treasure at " + nodePos);
				return Optional.of(node);
			} else if (node.hasEnemyFort()) {
				System.out.println("MOUNTAIN VISIBILITY: Found enemy fort at " + nodePos);
				return Optional.of(node);
			}
		}

		return Optional.empty();
	}

	private Direction getOptimizedExplorationMove(GameState gameState, Point currentPos, String playerId,
			long timeBudgetMillis) {
		return getExplorationMove(gameState, currentPos, playerId, timeBudgetMillis);
	}

	public void trackOpponentPosition(Point opponentPos, GameMap gameMap) {
		if (isInRandomOpponentPhase()) {
			System.out.println("IGNORING random opponent position during first 16 rounds: " + opponentPos);
			return;
		}

		fortBacktracker.trackOpponentPosition(opponentPos, gameMap);
	}

	private void trackOpponent(GameState gameState) {
		Optional<MapNode> enemyNodeOpt = gameState.getMap().getEnemyPosition();
		if (enemyNodeOpt.isPresent()) {
			Point enemyPos = enemyNodeOpt.get().getPosition();

			if (isInRandomOpponentPhase()) {
				System.out.println(
						"Random opponent phase (round " + currentGameRound + "). Ignoring opponent at: " + enemyPos);
				return;
			}
			fortBacktracker.trackOpponentPosition(enemyPos, gameState.getMap());
			updateGameRound();
		}
	}

	private Point calculateCenterPoint(Set<Point> points) {
		if (points == null || points.isEmpty()) {
			return null;
		}

		double sumX = 0;
		double sumY = 0;

		for (Point p : points) {
			sumX += p.x;
			sumY += p.y;
		}

		int centerX = (int) Math.round(sumX / points.size());
		int centerY = (int) Math.round(sumY / points.size());

		return new Point(centerX, centerY);
	}

	private Optional<MapNode> checkForHighPriorityTarget(GameState gameState, String playerId) {
		if (gameState.hasCollectedTreasure(playerId)) {
			Optional<MapNode> enemyFort = gameState.getMap().getEnemyFortPosition();
			if (enemyFort.isPresent()) {
				return enemyFort;
			}
		} else {
			Optional<MapNode> treasure = gameState.getMap().getTreasurePosition();
			if (treasure.isPresent()) {
				return treasure;
			}
		}
		return Optional.empty();
	}

	private Point calculateOwnHalfCenter(GameMap map, Point currentPos) {
		if (!explorationStrategy.isHalfInfoInitialized())
			return currentPos; // Fallback

		ZoneDimension ownZone = explorationStrategy.getOwnZone();

		int centerX = (ownZone.getXMin() + ownZone.getXMax()) / 2;
		int centerY = (ownZone.getYMin() + ownZone.getYMax()) / 2;

		return new Point(centerX, centerY);
	}

	private Point calculateEnemyHalfCenter(GameMap map) {
		if (!explorationStrategy.isHalfInfoInitialized())
			return new Point(map.getMapWidth() / 2, map.getMapHeight() / 2); // Fallback to map center

		ZoneDimension enemyZone = explorationStrategy.getEnemyZone();
		int centerX = (enemyZone.getXMin() + enemyZone.getXMax()) / 2;
		int centerY = (enemyZone.getYMin() + enemyZone.getYMax()) / 2;

		return new Point(centerX, centerY);
	}
}
