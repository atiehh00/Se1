package client.strategy;

import client.model.*;
import client.pathfinding.*;
import java.util.List;
import java.util.Optional;

public interface MovementState {
	Direction handle(MovementContext context, GameState gameState, String playerId);
}

class SearchingForTreasureState implements MovementState {
	@Override
	public Direction handle(MovementContext context, GameState gameState, String playerId) {
		if (gameState.getMap().getTreasurePosition().isPresent()) {
			System.out.println("Treasure located. Switching to moving to treasure state.");
			context.setState(new MovingToTreasureState());
			return context.getState().handle(context, gameState, playerId);
		}

		if (!context.getCurrentPath().isEmpty()) {
			return context.followPath(gameState);
		}
		System.out.println("Calculating exploration path to find treasure...");
		Point currentPos = gameState.getMap().getPlayerPosition().get().getPosition();
		List<Direction> explorationPath = context.calculateExplorationPath(gameState, currentPos);

		if (!explorationPath.isEmpty()) {
			context.setCurrentPath(explorationPath);
			return context.followPath(gameState);
		}
		return context.getRandomMove(gameState);
	}
}

class MovingToTreasureState implements MovementState {
	@Override
	public Direction handle(MovementContext context, GameState gameState, String playerId) {
		if (gameState.hasCollectedTreasure(playerId)) {
			System.out.println("Treasure collected. Switching to moving to fort state.");
			context.setState(new MovingToEnemyFortState());
			return context.getState().handle(context, gameState, playerId);
		}

		if (!gameState.getMap().getTreasurePosition().isPresent() || context.getCurrentPath().isEmpty()) {
			if (!gameState.getMap().getTreasurePosition().isPresent()) {
				System.out.println("Lost sight of treasure. Switching back to searching state.");
				context.setState(new SearchingForTreasureState());
				return context.getState().handle(context, gameState, playerId);
			}

			System.out.println("Calculating path to treasure...");
			Point currentPos = gameState.getMap().getPlayerPosition().get().getPosition();
			Point treasurePos = gameState.getMap().getTreasurePosition().get().getPosition();
			List<Direction> pathToTreasure = context.calculateDirectPath(gameState, currentPos, treasurePos);

			if (!pathToTreasure.isEmpty()) {
				context.setCurrentPath(pathToTreasure);
			} else {
				System.out.println("No direct path to treasure, trying exploration.");
				List<Direction> explorationPath = context.calculateExplorationPath(gameState, currentPos);
				context.setCurrentPath(explorationPath);
			}
		}

		return context.followPath(gameState);
	}
}

class MovingToEnemyFortState implements MovementState {
	@Override
	public Direction handle(MovementContext context, GameState gameState, String playerId) {
		if (!gameState.hasCollectedTreasure(playerId)) {
			System.out.println("Lost treasure. Switching back to searching state.");
			context.setState(new SearchingForTreasureState());
			return context.getState().handle(context, gameState, playerId);
		}

		if (context.getCurrentPath().isEmpty()) {
			if (gameState.getMap().getEnemyFortPosition().isPresent()) {
				System.out.println("Calculating path to enemy fort...");
				Point currentPos = gameState.getMap().getPlayerPosition().get().getPosition();
				Point fortPos = gameState.getMap().getEnemyFortPosition().get().getPosition();
				List<Direction> pathToFort = context.calculateDirectPath(gameState, currentPos, fortPos);

				if (!pathToFort.isEmpty()) {
					context.setCurrentPath(pathToFort);
				} else {
					System.out.println("No direct path to fort, trying exploration.");
					List<Direction> explorationPath = context.calculateExplorationPath(gameState, currentPos);
					context.setCurrentPath(explorationPath);
				}
			} else {
				System.out.println("Enemy fort not visible. Trying to locate it.");
				Point currentPos = gameState.getMap().getPlayerPosition().get().getPosition();
				Optional<Point> predictedFort = context.getPredictedFortPosition();
				if (predictedFort.isPresent()) {
					List<Direction> pathToPredictedFort = context.calculateDirectPath(gameState, currentPos,
							predictedFort.get());
					if (!pathToPredictedFort.isEmpty()) {
						System.out.println("Using predicted fort position: " + predictedFort.get());
						context.setCurrentPath(pathToPredictedFort);
					}
				}
				if (context.getCurrentPath().isEmpty()) {
					List<Direction> explorationPath = context.calculateExplorationPath(gameState, currentPos);
					context.setCurrentPath(explorationPath);
				}
			}
		}

		return context.followPath(gameState);
	}
}