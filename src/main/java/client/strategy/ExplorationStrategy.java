package client.strategy;

import client.model.*;
import client.pathfinding.PathFinder;
import client.pathfinding.PathfindingHelper;
import java.util.*;
import java.util.stream.Collectors;

public class ExplorationStrategy {
	private static final int REGION_SIZE = 4;
	private final Random random = new Random();
	private final Set<Point> visitedPositions = new HashSet<>();
	private final Map<String, Integer> regionExplorationCount = new HashMap<>();
	private final List<Point> systematicExplorationPoints = new ArrayList<>();
	private int currentExplorationIndex = 0;
	private boolean hasInitializedExploration = false;

	public enum SplitOrientation {
		VERTICAL, HORIZONTAL
	}

	public enum MapHalf {
		LEFT, RIGHT, TOP, BOTTOM
	}

	private SplitOrientation splitOrientation = null;
	private MapHalf myHalf = null;
	public boolean hasTreasure = false;
	private boolean halfInfoInitialized = false;
	private Point initialPosition = null;

	private ZoneDimension ownZone;
	private ZoneDimension enemyZone;

	public void markVisited(Point position) {
		visitedPositions.add(position);
		markRegionExplored(position);
	}

	public void clearVisited() {
		visitedPositions.clear();
		regionExplorationCount.clear();
	}

	public boolean hasVisited(Point position) {
		return visitedPositions.contains(position);
	}

	public Set<Point> getVisitedPositions() {
		return new HashSet<>(visitedPositions);
	}

	public Optional<MapNode> getNextExplorationTarget(GameMap map, Point currentPosition) {
		return getNextExplorationTarget(map, currentPosition, false);
	}

	public Optional<MapNode> getNextExplorationTarget(GameMap map, Point currentPosition, boolean prioritizeMyHalf) {
		List<MapNode> prioritizedTargets = getPrioritizedExplorationTargets(map, currentPosition, prioritizeMyHalf);
		for (MapNode targetNode : prioritizedTargets) {
			if (!targetNode.getPosition().equals(currentPosition)) {
				return Optional.of(targetNode);
			}
		}
		return Optional.empty();
	}

	private String getRegionKey(Point point) {
		int regionX = point.x / REGION_SIZE;
		int regionY = point.y / REGION_SIZE;
		return regionX + "," + regionY;
	}

	private void markRegionExplored(Point point) {
		String region = getRegionKey(point);
		regionExplorationCount.put(region, regionExplorationCount.getOrDefault(region, 0) + 1);
	}

	public Optional<Direction> getRandomValidDirection(GameMap map, Point position) {
		List<Direction> validDirections = new ArrayList<>();

		for (Direction dir : Direction.values()) {
			Point newPos = dir.move(position);
			Optional<MapNode> nodeOpt = map.getNode(newPos);
			if (nodeOpt.isPresent() && nodeOpt.get().isTraversable()) {
				validDirections.add(dir);
			}
		}

		if (validDirections.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(validDirections.get(random.nextInt(validDirections.size())));
	}

	public Optional<Direction> getSmartRandomDirection(GameMap map, Point position, Set<Point> recentlyVisited) {
		List<Direction> possibleDirections = new ArrayList<>();
		List<Direction> fallbackDirections = new ArrayList<>();

		for (Direction dir : Direction.values()) {
			Point newPos = dir.move(position);
			Optional<MapNode> nodeOpt = map.getNode(newPos);

			if (nodeOpt.isPresent() && nodeOpt.get().isTraversable()) {
				if (!recentlyVisited.contains(newPos)) {
					possibleDirections.add(dir);
					possibleDirections.add(dir);
				} else {
					fallbackDirections.add(dir);
				}
			}
		}

		if (!possibleDirections.isEmpty()) {
			return Optional.of(possibleDirections.get(random.nextInt(possibleDirections.size())));
		} else if (!fallbackDirections.isEmpty()) {
			return Optional.of(fallbackDirections.get(random.nextInt(fallbackDirections.size())));
		}

		return Optional.empty();
	}

	public Optional<MapNode> findBestMountainForVisibility(GameMap map, Point currentPos) {
		MapNode bestMountain = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		boolean hasMountains = false;
		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == Terrain.MOUNTAIN) {
				hasMountains = true;
				break;
			}
		}

		if (!hasMountains) {
			return Optional.empty();
		}

		int mapWidth = map.getMapWidth();
		int mapHeight = map.getMapHeight();

		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() != Terrain.MOUNTAIN) {
				continue;
			}

			Point mountainPos = node.getPosition();
			if (!isInRelevantHalf(mountainPos, map)) {
				continue;
			}
			int distance = currentPos.manhattanDistance(mountainPos);
			double distanceFactor = 15.0 / (distance + 1.0);

			boolean visited = hasVisited(mountainPos);
			double visitedFactor = visited ? 0.3 : 3.0;

			int visibilityRadius = 1;
			int unexploredNeighbors = 0;
			int totalVisibleGrass = 0;

			for (int dx = -visibilityRadius; dx <= visibilityRadius; dx++) {
				for (int dy = -visibilityRadius; dy <= visibilityRadius; dy++) {
					Point neighbor = new Point(mountainPos.x + dx, mountainPos.y + dy);

					if (neighbor.x < 0 || neighbor.x >= mapWidth || neighbor.y < 0 || neighbor.y >= mapHeight) {
						continue;
					}
					Optional<MapNode> neighborNodeOpt = map.getNode(neighbor);
					if (!neighborNodeOpt.isPresent()) {
						continue;
					}

					MapNode neighborNode = neighborNodeOpt.get();
					if (neighborNode.isTraversable()) {
						totalVisibleGrass++;

						if (!hasVisited(neighbor)) {
							unexploredNeighbors++;
						}
					}
				}
			}

			double explorationFactor = unexploredNeighbors * 2.5;
			int distanceToBoundary = Math.min(Math.min(mountainPos.x, mapWidth - 1 - mountainPos.x),
					Math.min(mountainPos.y, mapHeight - 1 - mountainPos.y));

			double boundaryFactor = Math.min(2.0, distanceToBoundary * 0.5);
			int nearbyMountainsCount = 0;
			int checkRadius = 3;

			for (MapNode otherNode : map.getAllNodes()) {
				if (otherNode.getTerrain() == Terrain.MOUNTAIN && !otherNode.getPosition().equals(mountainPos)) {

					int mDist = mountainPos.manhattanDistance(otherNode.getPosition());
					if (mDist <= checkRadius) {
						nearbyMountainsCount++;
					}
				}
			}
			double densityFactor = Math.max(0.5, 3.0 - (nearbyMountainsCount * 0.5));
			double strategicFactor = 0.0;

			if (hasTreasure) {
				Optional<MapNode> enemyFort = map.getEnemyFortPosition();
				if (!enemyFort.isPresent()) {
					if (isInRelevantHalf(mountainPos, map) && !visited) {
						strategicFactor = 3.0;
					}
				}
			} else {
				Optional<MapNode> treasure = map.getTreasurePosition();
				if (!treasure.isPresent()) {
					if (isInRelevantHalf(mountainPos, map) && !visited) {
						strategicFactor = 3.0;
					}
				}
			}

			double score = distanceFactor + explorationFactor + visitedFactor + boundaryFactor + densityFactor
					+ strategicFactor;

			if (score > bestScore) {
				bestScore = score;
				bestMountain = node;
			}
		}

		System.out.println("Best mountain evaluation: "
				+ (bestMountain != null ? bestMountain.getPosition() + " with score " + bestScore : "None found"));

		return Optional.ofNullable(bestMountain);
	}

	public List<MapNode> getPrioritizedExplorationTargets(GameMap map, Point currentPos) {
		return getPrioritizedExplorationTargets(map, currentPos, false);
	}

	public List<MapNode> getPrioritizedExplorationTargets(GameMap map, Point currentPos, boolean prioritizeMyHalf) {
		List<MapNode> targets = new ArrayList<>();

		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == Terrain.MOUNTAIN && !hasVisited(node.getPosition())) {
				if (!prioritizeMyHalf || isInRelevantHalf(node.getPosition(), map)) {
					targets.add(node);
				}
			}
		}

		Set<Point> frontierPoints = new HashSet<>();
		for (MapNode node : map.getAllNodes()) {
			Point pos = node.getPosition();
			if (hasVisited(pos)) {
				for (Direction dir : Direction.values()) {
					Point neighborPos = dir.move(pos);
					Optional<MapNode> neighborOpt = map.getNode(neighborPos);
					if (neighborOpt.isPresent() && neighborOpt.get().isTraversable() && !hasVisited(neighborPos)) {
						if (!prioritizeMyHalf || isInRelevantHalf(neighborPos, map)) {
							frontierPoints.add(neighborPos);
						}
					}
				}
			}
		}
		for (Point point : frontierPoints) {
			Optional<MapNode> nodeOpt = map.getNode(point);
			if (nodeOpt.isPresent()) {
				targets.add(nodeOpt.get());
			}
		}
		for (MapNode node : map.getAllNodes()) {
			if (node.isTraversable() && !hasVisited(node.getPosition()) && !targets.contains(node)) {
				if (!prioritizeMyHalf || isInRelevantHalf(node.getPosition(), map)) {
					targets.add(node);
				}
			}
		}
		targets.sort((a, b) -> {
			int distA = currentPos.manhattanDistance(a.getPosition());
			int distB = currentPos.manhattanDistance(b.getPosition());
			if (distA == distB) {
				return Integer.compare(a.hashCode(), b.hashCode());
			}
			return Integer.compare(distA, distB);
		});

		if (prioritizeMyHalf && targets.isEmpty()) {
			if (!hasTreasure) {
				System.out.println(
						"No targets found in OWN half. Returning empty list (will trigger fallback in MovementStrategy).");
				return Collections.emptyList();
			} else {
				System.out.println("No targets found in ENEMY half after getting treasure. Returning empty list.");
				return Collections.emptyList();
			}
		}

		return targets;
	}

	public void initializeHalfInfo(GameMap map, Point initialPosition) {
		if (halfInfoInitialized)
			return;
		this.initialPosition = initialPosition;
		int width = map.getMapWidth();
		int height = map.getMapHeight();

		if (width > height) {
			splitOrientation = SplitOrientation.VERTICAL;
		} else if (height > width) {
			splitOrientation = SplitOrientation.HORIZONTAL;
		} else {
			splitOrientation = SplitOrientation.HORIZONTAL;
			System.out.println("Map is Square (10x10). Setting split to HORIZONTAL.");
		}
		if (splitOrientation == SplitOrientation.VERTICAL) {
			double midX = (width - 1.0) / 2.0;
			myHalf = (initialPosition.x <= midX) ? MapHalf.LEFT : MapHalf.RIGHT;
			System.out.println("Initializing half: " + myHalf + " (Vertical Split, MidX: " + midX + ")");

			if (myHalf == MapHalf.LEFT) {
				ownZone = new ZoneDimension(0, (int) Math.floor(midX), 0, height - 1);
				enemyZone = new ZoneDimension((int) Math.ceil(midX), width - 1, 0, height - 1);
			} else {
				ownZone = new ZoneDimension((int) Math.ceil(midX), width - 1, 0, height - 1);
				enemyZone = new ZoneDimension(0, (int) Math.floor(midX), 0, height - 1);
			}
		} else {
			double midY = (height - 1.0) / 2.0;
			myHalf = (initialPosition.y <= midY) ? MapHalf.TOP : MapHalf.BOTTOM;
			System.out.println("Initializing half: " + myHalf + " (Horizontal Split, MidY: " + midY + ")");
			if (myHalf == MapHalf.TOP) {
				ownZone = new ZoneDimension(0, width - 1, 0, (int) Math.floor(midY));
				enemyZone = new ZoneDimension(0, width - 1, (int) Math.ceil(midY), height - 1);
			} else {
				ownZone = new ZoneDimension(0, width - 1, (int) Math.ceil(midY), height - 1);
				enemyZone = new ZoneDimension(0, width - 1, 0, (int) Math.floor(midY));
			}
		}

		System.out.println("Own half boundaries: X[" + ownZone.getXMin() + "," + ownZone.getXMax() + "] Y["
				+ ownZone.getYMin() + "," + ownZone.getYMax() + "]");
		System.out.println("Enemy half boundaries: X[" + enemyZone.getXMin() + "," + enemyZone.getXMax() + "] Y["
				+ enemyZone.getYMin() + "," + enemyZone.getYMax() + "]");

		halfInfoInitialized = true;
	}

	public void setHasTreasure(boolean hasTreasure) {
		boolean wasChanged = (this.hasTreasure != hasTreasure);
		this.hasTreasure = hasTreasure;
		if (wasChanged && hasTreasure) {
			System.out.println("===== STRATEGY TRANSITION: TREASURE FOUND =====");
			System.out.println("Switching from OWN half exploration to ENEMY half exploration");
			System.out.println("Enemy half boundaries: X[" + enemyZone.getXMin() + "," + enemyZone.getXMax() + "] Y["
					+ enemyZone.getYMin() + "," + enemyZone.getYMax() + "]");
			Set<Point> visitedCopy = new HashSet<>(visitedPositions);
			int clearedPositions = 0;

			for (Point p : visitedCopy) {
				if (enemyZone.contains(p)) {
					visitedPositions.remove(p);
					if (clearedPositions > 20) {
						break;
					}
				}
			}

			System.out.println(
					"Cleared " + clearedPositions + " visited positions in enemy half to encourage exploration");
		}
	}

	public boolean isInRelevantHalf(Point point, GameMap map) {
		System.out.println("[Debug] isInRelevantHalf called for point: " + point + ", hasTreasure: " + hasTreasure
				+ ", halfInfoInitialized: " + halfInfoInitialized + ", myHalf: " + myHalf + ", ownZone: "
				+ (ownZone != null ? ownZone : "null") + ", enemyZone: " + (enemyZone != null ? enemyZone : "null"));

		if (!halfInfoInitialized || myHalf == null) {
			System.err.println("WARN: Half info not initialized, allowing move.");
			System.out.println("[Debug] isInRelevantHalf returning TRUE (uninitialized)"); // DEBUG LOG
			return true;
		}

		boolean result;
		if (hasTreasure) {
			result = enemyZone.contains(point);

			System.out.println("[Debug] isInRelevantHalf (Treasure Phase) checking enemyZone " + enemyZone
					+ ". Contains(" + point + ")? -> " + result);

		} else {
			result = ownZone.contains(point);

			System.out.println("[Debug] isInRelevantHalf (No Treasure Phase) checking ownZone " + ownZone
					+ ". Contains(" + point + ")? -> " + result);
		}
		return result;
	}

	private Set<Point> getSightRange(MapNode viewpoint, GameMap map) {
		Set<Point> visibleGrass = new HashSet<>();
		Point pos = viewpoint.getPosition();
		Terrain terrain = viewpoint.getTerrain();

		if (terrain == Terrain.MOUNTAIN) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					Point potentialTarget = new Point(pos.x + dx, pos.y + dy);
					Optional<MapNode> targetNodeOpt = map.getNode(potentialTarget);
					if (targetNodeOpt.isPresent() && targetNodeOpt.get().getTerrain() == Terrain.GRASS) {
						visibleGrass.add(potentialTarget);
					}
				}
			}
		} else if (terrain == Terrain.GRASS) {
			visibleGrass.add(pos);
		}
		return visibleGrass;
	}

	private Set<MapNode> identifyPotentialViewpoints(GameMap map) {
		Set<MapNode> viewpoints = new HashSet<>();
		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == Terrain.GRASS || node.getTerrain() == Terrain.MOUNTAIN) {
				viewpoints.add(node);
			}
		}
		return viewpoints;
	}

	private Set<Point> identifyTargetFields(GameMap map) {
		Set<Point> targetFields = new HashSet<>();
		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == Terrain.GRASS) {
				targetFields.add(node.getPosition());
			}
		}
		return targetFields;
	}

	private Set<MapNode> findNecessaryViewpoints(GameMap map) {
		Set<Point> uncoveredFields = identifyTargetFields(map);
		Set<MapNode> potentialViewpoints = identifyPotentialViewpoints(map);
		Set<MapNode> necessaryViewpoints = new HashSet<>();

		while (!uncoveredFields.isEmpty() && !potentialViewpoints.isEmpty()) {
			MapNode bestViewpoint = null;
			Set<Point> bestCoverage = new HashSet<>();
			for (MapNode viewpoint : potentialViewpoints) {
				Set<Point> currentCoverage = getSightRange(viewpoint, map);
				currentCoverage.retainAll(uncoveredFields);

				if (currentCoverage.size() > bestCoverage.size()) {
					bestCoverage = currentCoverage;
					bestViewpoint = viewpoint;
				}
			}

			if (bestViewpoint != null) {
				necessaryViewpoints.add(bestViewpoint);
				uncoveredFields.removeAll(bestCoverage);
				potentialViewpoints.remove(bestViewpoint);
			} else {
				break;
			}
		}

		if (!uncoveredFields.isEmpty()) {
			System.err.println("WARN: Could not cover all grass fields. Remaining: " + uncoveredFields.size());
		}

		return necessaryViewpoints;
	}

	public List<Direction> generateCoveragePath(GameMap map, Point currentPosition, PathFinder pathFinder,
			long timeBudgetMillis) {
		Set<MapNode> necessaryViewpoints = findNecessaryViewpoints(map);
		Set<MapNode> viewpointsToVisit;

		System.out.println("Generating coverage path: hasTreasure=" + this.hasTreasure);

		if (this.hasTreasure) {
			System.out.println("Filtering coverage viewpoints for ENEMY half exploration");
			viewpointsToVisit = necessaryViewpoints.stream().filter(vp -> isInRelevantHalf(vp.getPosition(), map))
					.collect(Collectors.toSet());
			if (viewpointsToVisit.isEmpty() && !necessaryViewpoints.isEmpty()) {
				System.out.println(
						"WARN: No necessary viewpoints found in enemy half, using all necessary viewpoints as fallback.");
				viewpointsToVisit = necessaryViewpoints;
			}
		} else {
			System.out.println("Filtering coverage viewpoints for OWN half exploration");
			viewpointsToVisit = necessaryViewpoints.stream().filter(vp -> isInRelevantHalf(vp.getPosition(), map))
					.collect(Collectors.toSet());
			if (viewpointsToVisit.isEmpty() && !necessaryViewpoints.isEmpty()) {
				System.out.println(
						"WARN: No necessary viewpoints found in own half, using all necessary viewpoints as fallback.");
				viewpointsToVisit = necessaryViewpoints;
			}
		}

		System.out.println("Coverage Path starting with " + viewpointsToVisit.size() + " viewpoints in "
				+ (this.hasTreasure ? "ENEMY" : "OWN") + " half.");

		List<Direction> fullPath = new ArrayList<>();
		Point currentPoint = currentPosition;
		long pathStartTime = System.currentTimeMillis();

		while (!viewpointsToVisit.isEmpty()) {
			long elapsedTime = System.currentTimeMillis() - pathStartTime;
			if (elapsedTime >= timeBudgetMillis) {
				System.out.println("WARN: Time budget exceeded during coverage path generation.");
				break;
			}
			long remainingBudget = timeBudgetMillis - elapsedTime;
			List<MapNode> sortedViewpoints = new ArrayList<>(viewpointsToVisit);
			final Point effectivelyFinalCurrentPoint = currentPoint;
			sortedViewpoints.sort(Comparator.comparingInt(vp -> PathfindingHelper
					.calculateManhattanDistance(effectivelyFinalCurrentPoint, vp.getPosition())));

			MapNode targetViewpoint = null;
			List<Direction> segmentPath = Collections.emptyList();
			for (MapNode potentialTarget : sortedViewpoints) {
				if (potentialTarget.getPosition().equals(currentPoint)) {
					System.out
							.println("DEBUG: Skipping viewpoint target that is the current position: " + currentPoint);
					continue;
				}

				long segmentBudget = Math.max(75, remainingBudget / (viewpointsToVisit.size() + 1)); // Min 75ms or
																										// proportional
				segmentPath = pathFinder.findPath(map, currentPoint, potentialTarget.getPosition(), segmentBudget,
						visitedPositions);

				if (!segmentPath.isEmpty()) {
					targetViewpoint = potentialTarget;
					break;
				} else {
					System.err.println("WARN: Could not find path segment to viewpoint " + potentialTarget.getPosition()
							+ ". Trying next closest...");
				}
			}

			if (targetViewpoint == null) {
				System.err.println(
						"WARN: Could not find path segment to any remaining valid viewpoints. Removing closest and continuing.");
				if (!sortedViewpoints.isEmpty()) {
					MapNode originallyClosest = sortedViewpoints.get(0);
					if (!originallyClosest.getPosition().equals(currentPoint)) {
						viewpointsToVisit.remove(originallyClosest);
					} else if (sortedViewpoints.size() > 1) {
						for (int i = 1; i < sortedViewpoints.size(); i++) {
							MapNode nextClosest = sortedViewpoints.get(i);
							if (!nextClosest.getPosition().equals(currentPoint)) {
								viewpointsToVisit.remove(nextClosest);
								break;
							}
						}
					}
				}
				continue;
			} else {
				fullPath.addAll(segmentPath);
				currentPoint = targetViewpoint.getPosition();
				viewpointsToVisit.remove(targetViewpoint);
			}
		}

		return fullPath;
	}

	public boolean isHalfInfoInitialized() {
		return halfInfoInitialized;
	}

	public SplitOrientation getSplitOrientation() {
		return splitOrientation;
	}

	public MapHalf getMyHalf() {
		return myHalf;
	}

	public List<MapNode> getPrioritizedMountainTargets(GameMap map, Point currentPos) {
		List<MapNode> mountainTargets = new ArrayList<>();

		for (MapNode node : map.getAllNodes()) {
			if (node.getTerrain() == Terrain.MOUNTAIN && !hasVisited(node.getPosition())) {
				if (isInRelevantHalf(node.getPosition(), map)) {
					mountainTargets.add(node);
				}
			}
		}

		mountainTargets.sort((a, b) -> {
			int distA = currentPos.manhattanDistance(a.getPosition());
			int distB = currentPos.manhattanDistance(b.getPosition());
			if (distA == distB) {
				return Integer.compare(a.hashCode(), b.hashCode());
			}
			return Integer.compare(distA, distB);
		});

		return mountainTargets;
	}

	public ZoneDimension getRelevantZone() {
		if (!halfInfoInitialized) {
			return null;
		}

		return hasTreasure ? enemyZone : ownZone;
	}

	public ZoneDimension getOwnZone() {
		return ownZone;
	}

	public ZoneDimension getEnemyZone() {
		return enemyZone;
	}

	public int[] getRelevantHalfBoundaries() {
		if (!halfInfoInitialized) {
			return new int[] { 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE };
		}

		ZoneDimension relevantZone = hasTreasure ? enemyZone : ownZone;
		return new int[] { relevantZone.getXMin(), relevantZone.getXMax(), relevantZone.getYMin(),
				relevantZone.getYMax() };
	}
}
