package client.strategy;

import client.model.*;
import java.util.*;

public class PathCoverageCalculator {
	private final GameMap gameMap;
	private final Set<Point> visitedPositions;

	public PathCoverageCalculator(GameMap gameMap, Set<Point> visitedPositions) {
		this.gameMap = gameMap;
		this.visitedPositions = new HashSet<>(visitedPositions);
	}

	public PathCoverageStats calculateCoverage(List<Direction> path) {
		if (path.isEmpty()) {
			return new PathCoverageStats(0, 0, 0, Collections.emptySet());
		}

		List<Point> pathPoints = convertDirectionsToPoints(path);

		Set<Point> visiblePositions = calculateVisiblePositions(pathPoints);

		Set<Point> newPositions = new HashSet<>(visiblePositions);
		newPositions.removeAll(visitedPositions);

		int mountainCount = countMountains(pathPoints);

		double explorationScore = calculateExplorationScore(newPositions, mountainCount, path.size());

		return new PathCoverageStats(newPositions.size(), mountainCount, explorationScore, newPositions);
	}

	private List<Point> convertDirectionsToPoints(List<Direction> directions) {
		Optional<MapNode> playerNodeOpt = gameMap.getPlayerPosition();
		if (!playerNodeOpt.isPresent()) {
			return Collections.emptyList();
		}

		Point currentPos = playerNodeOpt.get().getPosition();
		List<Point> points = new ArrayList<>();
		points.add(currentPos);

		for (Direction dir : directions) {
			currentPos = dir.move(currentPos);
			points.add(currentPos);
		}

		return points;
	}

	private Set<Point> calculateVisiblePositions(List<Point> path) {
		Set<Point> visible = new HashSet<>();

		for (Point position : path) {
			visible.add(position);

			Optional<MapNode> nodeOpt = gameMap.getNode(position);
			if (!nodeOpt.isPresent())
				continue;

			int visibilityRange = 1;

			if (nodeOpt.get().getTerrain() == Terrain.MOUNTAIN) {
				visibilityRange = 3;
			}

			for (int dx = -visibilityRange; dx <= visibilityRange; dx++) {
				for (int dy = -visibilityRange; dy <= visibilityRange; dy++) {
					if (Math.abs(dx) + Math.abs(dy) > visibilityRange)
						continue;

					Point visiblePos = new Point(position.x + dx, position.y + dy);
					Optional<MapNode> visibleNodeOpt = gameMap.getNode(visiblePos);

					if (visibleNodeOpt.isPresent() && visibleNodeOpt.get().isTraversable()) {
						visible.add(visiblePos);
					}
				}
			}
		}

		return visible;
	}

	private int countMountains(List<Point> path) {
		int count = 0;

		for (Point position : path) {
			Optional<MapNode> nodeOpt = gameMap.getNode(position);
			if (nodeOpt.isPresent() && nodeOpt.get().getTerrain() == Terrain.MOUNTAIN) {
				count++;
			}
		}

		return count;
	}

	private double calculateExplorationScore(Set<Point> newPositions, int mountainCount, int pathLength) {
		double coverageScore = newPositions.size() * 10.0;

		double mountainScore = mountainCount * 15.0;

		double efficiencyScore = (pathLength > 0) ? coverageScore / pathLength : 0;

		double specialDiscoveryBonus = 0.0;
		for (Point p : newPositions) {
			Optional<MapNode> nodeOpt = gameMap.getNode(p);
			if (nodeOpt.isPresent()) {
				MapNode node = nodeOpt.get();
				if (node.hasTreasure()) {
					specialDiscoveryBonus += 100.0;
				} else if (node.hasEnemyFort()) {
					specialDiscoveryBonus += 200.0;
				}
			}
		}

		return coverageScore + mountainScore + efficiencyScore + specialDiscoveryBonus;
	}

	public static class PathCoverageStats {
		private final int newPositionsCount;
		private final int mountainCount;
		private final double explorationScore;
		private final Set<Point> newPositions;

		public PathCoverageStats(int newPositionsCount, int mountainCount, double explorationScore,
				Set<Point> newPositions) {
			this.newPositionsCount = newPositionsCount;
			this.mountainCount = mountainCount;
			this.explorationScore = explorationScore;
			this.newPositions = newPositions;
		}

		public int getNewPositionsCount() {
			return newPositionsCount;
		}

		public int getMountainCount() {
			return mountainCount;
		}

		public double getExplorationScore() {
			return explorationScore;
		}

		public Set<Point> getNewPositions() {
			return newPositions;
		}

		@Override
		public String toString() {
			return "PathCoverageStats{" + "newPositionsCount=" + newPositionsCount + ", mountainCount=" + mountainCount
					+ ", explorationScore=" + explorationScore + '}';
		}
	}
}