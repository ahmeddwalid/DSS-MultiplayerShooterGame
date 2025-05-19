package main.java.logic;

import main.java.core.*;
import main.java.network.HostServer;
import main.java.util.Constants;
import main.java.util.Direction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles the core game logic on the host side.
 * It processes actions from the EventQueue, updates the GameState,
 * and enforces game rules.
 */
public class GameLogicProcessor {
    private GameState gameState;
    private final EventQueue eventQueue;
    private final HostServer hostServer;

    /**
     * Constructs a GameLogicProcessor.
     * @param gameState The shared game state.
     * @param eventQueue The queue from which to fetch fully acknowledged actions.
     * @param hostServer The HostServer instance, used to trigger game state broadcasts.
     */
    public GameLogicProcessor(GameState gameState, EventQueue eventQueue, HostServer hostServer) {
        this.gameState = gameState;
        this.eventQueue = eventQueue;
        this.hostServer = hostServer;
        if (this.hostServer == null) {
            System.err.println("CRITICAL ERROR: GameLogicProcessor constructor received a null HostServer instance!");
        } else {
            System.out.println("GameLogicProcessor: Constructor - HostServer instance received and set: " + this.hostServer.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this.hostServer)));
        }
    }

    /**
     * Main loop for processing events. Intended to be run in a separate thread.
     */
    public void run() {
        if (this.hostServer == null) {
            System.err.println("CRITICAL ERROR: GameLogicProcessor.run() started, but this.hostServer is null! Cannot proceed with game logic that broadcasts state.");
            // Optionally, you could prevent the loop from starting or throw an exception.
            // For now, it will try to run but fail when broadcasting.
        } else {
            System.out.println("GameLogicProcessor: Run method started. HostServer instance: " + this.hostServer.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this.hostServer)));
        }

        System.out.println("GameLogicProcessor started."); // This was the original log
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Action action = eventQueue.getNextActionToProcess();
                System.out.println("GameLogicProcessor: Processing action: " + action.getDescription());
                boolean stateChanged;
                synchronized (gameState) {
                    stateChanged = processAction(action);
                }

                if (stateChanged) {
                    if (this.hostServer != null) {
                        this.hostServer.broadcastGameState();
                    } else {
                        // This is the error you are seeing.
                        System.err.println("CRITICAL ERROR: hostServer is null in GameLogicProcessor.run()! Cannot broadcast game state.");
                    }
                }
            }
        } catch (InterruptedException e) {
            System.out.println("GameLogicProcessor interrupted.");
            Thread.currentThread().interrupt();
        }
        System.out.println("GameLogicProcessor stopped.");
    }

    private boolean processAction(Action action) {
        Optional<Player> optionalActor = gameState.getPlayer(action.getPlayerId());
        if (optionalActor.isEmpty() || !optionalActor.get().isAlive()) {
            if ("IN_PROGRESS".equals(gameState.getGameStatus())) {
                System.out.println("GameLogicProcessor: Actor " + action.getPlayerId() + " not found or not alive. Action " + action.getActionId() + " skipped.");
            }
            return false;
        }
        Player actor = optionalActor.get();
        boolean changed = false;

        if (action instanceof ShootAction) {
            changed = handleShootAction((ShootAction) action, actor);
        } else if (action instanceof HealAction) {
            changed = handleHealAction((HealAction) action, actor);
        } else if (action instanceof MoveAction) {
            changed = handleMoveAction((MoveAction) action, actor);
        } else {
            System.err.println("GameLogicProcessor: Unknown action type: " + action.getClass().getSimpleName());
            return false;
        }

        if (changed) {
            checkGameOver();
        }
        return changed;
    }

    private boolean handleShootAction(ShootAction shootAction, Player actor) {
        Optional<Player> optionalTarget = gameState.getPlayer(shootAction.getTargetPlayerId());
        if (optionalTarget.isEmpty() || !optionalTarget.get().isAlive()) {
            System.out.println("GameLogicProcessor: Target " + shootAction.getTargetPlayerId() + " for shoot action not found or not alive.");
            return false;
        }
        Player target = optionalTarget.get();

        double distance = gameState.getGrid().getDistance(actor, target);
        if (distance > Constants.MAX_ACTION_DISTANCE) {
            System.out.println("GameLogicProcessor: Shoot action from " + actor.getPlayerId() + " to " + target.getPlayerId() + " rejected. Distance " + String.format("%.2f", distance) + " > " + Constants.MAX_ACTION_DISTANCE);
            return false;
        }

        int oldHealth = target.getHealth();
        target.takeDamage(Constants.SHOOT_DAMAGE);
        gameState.updatePlayer(target);
        System.out.println("GameLogicProcessor: " + actor.getPlayerId() + " SHOT " + target.getPlayerId() + ". " + target.getPlayerId() + " health: " + target.getHealth());

        if (!target.isAlive()) {
            System.out.println("GameLogicProcessor: Player " + target.getPlayerId() + " was defeated by " + actor.getPlayerId() + "!");
        }
        return target.getHealth() != oldHealth || !target.isAlive();
    }

    private boolean handleHealAction(HealAction healAction, Player actor) {
        Optional<Player> optionalTarget = gameState.getPlayer(healAction.getTargetPlayerId());
        if (optionalTarget.isEmpty() || !optionalTarget.get().isAlive()) {
            System.out.println("GameLogicProcessor: Target " + healAction.getTargetPlayerId() + " for heal action not found or not alive.");
            return false;
        }
        Player target = optionalTarget.get();

        double distance = gameState.getGrid().getDistance(actor, target);
        if (distance > Constants.MAX_ACTION_DISTANCE) {
            System.out.println("GameLogicProcessor: Heal action from " + actor.getPlayerId() + " to " + target.getPlayerId() + " rejected. Distance " + String.format("%.2f", distance) + " > " + Constants.MAX_ACTION_DISTANCE);
            return false;
        }
        int oldHealth = target.getHealth();
        target.heal(Constants.HEAL_AMOUNT);
        gameState.updatePlayer(target);
        System.out.println("GameLogicProcessor: " + actor.getPlayerId() + " HEALED " + target.getPlayerId() + ". " + target.getPlayerId() + " health: " + target.getHealth());
        return target.getHealth() != oldHealth;
    }

    private boolean handleMoveAction(MoveAction moveAction, Player actor) {
        Position currentPosition = actor.getPosition();
        Direction direction = moveAction.getDirection();
        Position newPosition = gameState.getGrid().calculateNewPosition(currentPosition, direction);

        if (!gameState.getGrid().isValidPosition(newPosition)) {
            System.out.println("GameLogicProcessor: Move action for " + actor.getPlayerId() + " to " + newPosition + " rejected. Out of bounds.");
            return false;
        }

        actor.setPosition(newPosition);
        gameState.updatePlayer(actor);
        System.out.println("GameLogicProcessor: " + actor.getPlayerId() + " MOVED " + direction + " to " + newPosition);
        return true;
    }

    private void checkGameOver() {
        if (!"IN_PROGRESS".equals(gameState.getGameStatus())) {
            return;
        }

        List<Player> alivePlayers = gameState.getPlayers().values().stream()
                .filter(Player::isAlive)
                .collect(Collectors.toList());

        if (alivePlayers.size() <= 1 && gameState.getPlayers().size() > 1) {
            gameState.setGameStatus("FINISHED");
            if (alivePlayers.size() == 1) {
                gameState.setWinnerPlayerId(alivePlayers.get(0).getPlayerId());
                System.out.println("GameLogicProcessor: Game Over! Winner is " + alivePlayers.get(0).getPlayerId());
            } else {
                System.out.println("GameLogicProcessor: Game Over! No players left alive (Draw).");
                gameState.setWinnerPlayerId("DRAW");
            }
        }
    }

    public void enforceAuthoritativeHealth(String playerId, int clientReportedHealth) {
        // Implicitly handled
    }
}
