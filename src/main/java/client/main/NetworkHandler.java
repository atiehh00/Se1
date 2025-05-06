package client.main;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import messagesbase.UniquePlayerIdentifier;
import messagesbase.messagesfromclient.PlayerRegistration;
import messagesbase.messagesfromclient.PlayerHalfMap;
import messagesbase.messagesfromclient.PlayerMove;
import messagesbase.ResponseEnvelope;
import messagesbase.messagesfromclient.ERequestState;
// Import our model classes instead
import client.model.GameState;
import client.converter.GameStateConverter;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class NetworkHandler {
	private final WebClient baseWebClient;
	private String lastGameStateId = "";
	private final int maxRetries = 2;
	private final long initialRetryDelay = 100;
	private final double retryBackoffFactor = 1.1;
	private final long maxRetryDelay = 2000;
	private final int defaultTimeoutMillis = 500;
	private final Map<String, Integer> errorCounts = new HashMap<>();
	private final Logger logger = Logger.getLogger(NetworkHandler.class.getName());
	private boolean gameEnded = false;

	public NetworkHandler(String serverBaseUrl) {
		try {
			Handler consoleHandler = new ConsoleHandler();
			consoleHandler.setFormatter(new SimpleFormatter() {
				private static final String format = "[%1$tF %1$tT] [%2$s] %3$s %n";

				@Override
				public synchronized String format(LogRecord lr) {
					return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(),
							lr.getMessage());
				}
			});
			logger.addHandler(consoleHandler);
			logger.setLevel(Level.INFO);
		} catch (Exception e) {
			System.err.println("Failed to configure logger: " + e.getMessage());
		}

		logger.info("Initializing NetworkHandler with server URL: " + serverBaseUrl);

		this.baseWebClient = WebClient.builder().baseUrl(serverBaseUrl + "/games")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE).build();
	}

	public UniquePlayerIdentifier registerPlayer(String gameId, String firstName, String lastName, String uAccount) {
		PlayerRegistration playerReg = new PlayerRegistration(firstName, lastName, uAccount);
		logger.info("Attempting to register player: " + firstName + " " + lastName + " (" + uAccount + ")");

		return executeWithRetry("registerPlayer", () -> {
			Mono<ResponseEnvelope<UniquePlayerIdentifier>> webAccess = baseWebClient.method(HttpMethod.POST)
					.uri("/" + gameId + "/players").body(BodyInserters.fromValue(playerReg)).retrieve()
					.bodyToMono(new ParameterizedTypeReference<ResponseEnvelope<UniquePlayerIdentifier>>() {
					});

			ResponseEnvelope<UniquePlayerIdentifier> resultReg = webAccess.block();

			if (resultReg.getState() == ERequestState.Error) {
				throw new ServerErrorException("Registration error: " + resultReg.getExceptionMessage());
			}

			UniquePlayerIdentifier playerId = resultReg.getData().orElse(null);
			if (playerId != null) {
				logger.info("Player registered successfully with ID: " + playerId.getUniquePlayerID());
			} else {
				logger.warning("Player registration returned null ID");
			}

			return playerId;
		});
	}
	
	public messagesbase.messagesfromserver.GameState getGameState(String gameId, UniquePlayerIdentifier playerId) {
		if (gameEnded) {
			logger.info("Game has already ended. Returning null game state.");
			return null;
		}

		return executeWithRetry("getGameState", () -> {
			Mono<ResponseEnvelope<messagesbase.messagesfromserver.GameState>> webAccess = baseWebClient
					.method(HttpMethod.GET).uri("/" + gameId + "/states/" + playerId.getUniquePlayerID()).retrieve()
					.bodyToMono(
							new ParameterizedTypeReference<ResponseEnvelope<messagesbase.messagesfromserver.GameState>>() {
							});

			ResponseEnvelope<messagesbase.messagesfromserver.GameState> resultState = webAccess.block();

			if (resultState.getState() == ERequestState.Error) {
				String errorMsg = resultState.getExceptionMessage();
				if (errorMsg != null && (errorMsg.contains("game has ended") || errorMsg.contains("won or lost"))) {
					gameEnded = true;
					logger.warning("Game has ended: " + errorMsg);
					return null;
				}
				throw new ServerErrorException("Error getting game state: " + errorMsg);
			}

			messagesbase.messagesfromserver.GameState gameState = resultState.getData().orElse(null);
			if (gameState != null) {
				String currentGameStateId = gameState.getGameStateId();
				boolean hasChanged = !currentGameStateId.equals(lastGameStateId);
				lastGameStateId = currentGameStateId;

				if (hasChanged) {
					logger.info("Game state updated (ID: " + currentGameStateId + ")");
				}

				for (messagesbase.messagesfromserver.PlayerState playerState : gameState.getPlayers()) {
					if (playerState.getUniquePlayerID().equals(playerId.getUniquePlayerID())) {
						if (playerState.getState() == messagesbase.messagesfromserver.EPlayerGameState.Won
								|| playerState.getState() == messagesbase.messagesfromserver.EPlayerGameState.Lost) {
							gameEnded = true;
							logger.info("Game has ended. Player has "
									+ (playerState.getState() == messagesbase.messagesfromserver.EPlayerGameState.Won
											? "won"
											: "lost"));
							break;
						}
					}
				}
			} else {
				logger.warning("Received null game state");
			}

			return gameState;
		});
	}
	
	public GameState getGameStateModel(String gameId, UniquePlayerIdentifier playerId) {
		if (gameEnded) {
			logger.info("Game has already ended. Returning null game state model.");
			return null;
		}

		messagesbase.messagesfromserver.GameState serverGameState = getGameState(gameId, playerId);
		if (serverGameState == null) {
			return null;
		}

		return GameStateConverter.fromServerGameState(serverGameState);
	}

	public boolean sendHalfMap(String gameId, PlayerHalfMap halfMap) {
		if (gameEnded) {
			logger.warning("Game has ended. Not sending half map.");
			return false;
		}

		logger.info("Sending half map for player: " + halfMap.getUniquePlayerID());

		return executeWithRetry("sendHalfMap", () -> {
			Mono<ResponseEnvelope<Void>> webAccess = baseWebClient.method(HttpMethod.POST)
					.uri("/" + gameId + "/halfmaps").body(BodyInserters.fromValue(halfMap)).retrieve()
					.bodyToMono(new ParameterizedTypeReference<ResponseEnvelope<Void>>() {
					});

			ResponseEnvelope<Void> result = webAccess.block();

			if (result.getState() == ERequestState.Error) {
				String errorMsg = result.getExceptionMessage();
				if (errorMsg != null && (errorMsg.contains("game has ended") || errorMsg.contains("won or lost"))) {
					gameEnded = true;
					logger.warning("Game has ended. Half map not accepted: " + errorMsg);
					return false;
				}
				throw new ServerErrorException("Error sending half map: " + errorMsg);
			}

			logger.info("Half map sent successfully");
			return true;
		}, false);
	}

	public boolean sendMove(String gameId, PlayerMove move) {
		if (gameEnded) {
			logger.warning("Game has ended. Not sending move: " + move.getMove());
			return false;
		}

		logger.info("Sending move: " + move.getMove() + " for player " + move.getUniquePlayerID());

		return executeWithRetry("sendMove", () -> {
			try {
				Mono<ResponseEnvelope<Void>> webAccess = baseWebClient.method(HttpMethod.POST)
						.uri("/" + gameId + "/moves").body(BodyInserters.fromValue(move)).retrieve()
						.bodyToMono(new ParameterizedTypeReference<ResponseEnvelope<Void>>() {
						});

				ResponseEnvelope<Void> result = webAccess.block();

				if (result.getState() == ERequestState.Error) {
					String errorMsg = result.getExceptionMessage();
					if (errorMsg != null && (errorMsg.contains("game has ended") || errorMsg.contains("won or lost"))) {
						gameEnded = true;
						logger.warning("Game state error: " + errorMsg);
						return false;
					}

					if (errorMsg != null && errorMsg.contains("wasn't the client's turn")) {
						logger.warning("Game state error: " + errorMsg);
						return false;
					}

					throw new ServerErrorException("Error sending move: " + errorMsg);
				}

				logger.info("Move sent successfully!");
				return true;
			} catch (WebClientResponseException e) {
				logger.severe("HTTP error sending move: " + e.getStatusCode() + " - " + e.getMessage());

				String errorMsg = e.getMessage();
				if (errorMsg != null && (errorMsg.contains("game has ended") || errorMsg.contains("won or lost"))) {
					gameEnded = true;
					logger.warning("Game has ended (from HTTP error): " + errorMsg);
				}

				return false;
			}
		}, false);
	}

	public boolean isGameEnded() {
		return gameEnded;
	}

	public void setGameEnded(boolean ended) {
		if (ended && !gameEnded) {
			logger.warning("Game explicitly marked as ended");
		}
		gameEnded = ended;
	}

	private <T> T executeWithRetry(String operationName, Supplier<T> operation) {
		return executeWithRetry(operationName, operation, true);
	}

	private <T> T executeWithRetry(String operationName, Supplier<T> operation, boolean returnNullOnFailure) {
		if (gameEnded && (operationName.equals("sendMove") || operationName.equals("sendHalfMap"))) {
			logger.warning("Game has ended. Not executing " + operationName);
			return null;
		}

		int attempt = 0;
		long retryDelay = 100;

		while (attempt < maxRetries) {
			try {
				T result = operation.get();

				errorCounts.put(operationName, 0);

				return result;
			} catch (Exception e) {
				attempt++;
				String errorMsg = e.getMessage();
				if (errorMsg != null) {
					if (errorMsg.contains("game has ended") || errorMsg.contains("won or lost")
							|| errorMsg.contains("game is over") || errorMsg.contains("game over")
							|| errorMsg.contains("terminated") || errorMsg.contains("violation")) {

						gameEnded = true;
						logger.warning("Game has ended based on error message: " + errorMsg);
						return null;
					}
				}

				int errorCount = errorCounts.getOrDefault(operationName, 0) + 1;
				errorCounts.put(operationName, errorCount);

				logger.severe("Error in " + operationName + " (attempt " + attempt + "/" + maxRetries + "): "
						+ e.getMessage());

				if (attempt >= maxRetries) {
					logger.severe("Max retries reached for " + operationName + ". Giving up.");

					if (errorCount >= 2) {
						logger.warning("Multiple errors detected. Assuming game might be over.");
						gameEnded = true;
					}

					if (!returnNullOnFailure) {
						throw new RuntimeException("Failed to " + operationName + " after " + maxRetries + " attempts",
								e);
					}
					return null;
				}

				try {
					logger.info("Retrying " + operationName + " in " + retryDelay + "ms");
					Thread.sleep(retryDelay);
					retryDelay = Math.min(retryDelay * 2, 500);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					logger.severe("Retry interrupted: " + ie.getMessage());
					if (!returnNullOnFailure) {
						throw new RuntimeException("Retry interrupted", ie);
					}
					return null;
				}
			}
		}

		return null;
	}

	private static class ServerErrorException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ServerErrorException(String message) {
			super(message);
		}
	}
}
