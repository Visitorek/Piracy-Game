package com.corsair.deluxe.domain.battle;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Min/max opponent (alpha-beta) like the original game used; searches a few plies ahead. */
public class BattleAI {
    private static final int WIN_SCORE = 10000;

    private final Random random;
    private final int depth;

    public BattleAI(Random random, int depth) {
        this.random = random;
        this.depth = depth;
    }

    public BattleAction chooseAction(BattleState state) {
        BattleSide side = state.getSideToMove();
        List<BattleAction> actions = state.legalActions(side);
        Collections.shuffle(actions, random);

        BattleAction best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BattleAction action : actions) {
            BattleState next = state.copy();
            next.apply(action);
            int score = search(next, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (side == BattleSide.PLAYER) {
                score = -score;
            }
            if (score > bestScore) {
                bestScore = score;
                best = action;
            }
        }
        return best;
    }

    /** Scores are from the enemy's perspective: the enemy maximises, the player minimises. */
    private int search(BattleState state, int remainingDepth, int alpha, int beta) {
        BattleOutcome outcome = state.getOutcome();
        if (outcome == BattleOutcome.VICTORY) {
            return -WIN_SCORE - remainingDepth;
        }
        if (outcome == BattleOutcome.DEFEAT) {
            return WIN_SCORE + remainingDepth;
        }
        if (outcome == BattleOutcome.DRAW) {
            return 0;
        }
        if (remainingDepth == 0) {
            return evaluate(state);
        }

        BattleSide side = state.getSideToMove();
        boolean maximising = side == BattleSide.ENEMY;
        int best = maximising ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (BattleAction action : state.legalActions(side)) {
            BattleState next = state.copy();
            next.apply(action);
            int score = search(next, remainingDepth - 1, alpha, beta);
            if (maximising) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    private int evaluate(BattleState state) {
        int crewScore = 10 * (state.getTotalCrew(BattleSide.ENEMY) - state.getTotalCrew(BattleSide.PLAYER));
        int hatchScore = 4 * (state.getDisabledHatches(BattleSide.PLAYER) - state.getDisabledHatches(BattleSide.ENEMY));
        // Crew on the net can still act, crew in a hatch is only potential
        int activeScore = 2 * (state.getCrewOnNet(BattleSide.ENEMY) - state.getCrewOnNet(BattleSide.PLAYER));

        // Reward pushing towards the opponent's hatches so the AI does not stall
        int advance = 0;
        for (int x = 0; x < BattleState.WIDTH; x++) {
            for (int y = 0; y < BattleState.HEIGHT; y++) {
                BattleSide occupant = state.getCrewAt(x, y);
                if (occupant == BattleSide.ENEMY) {
                    advance += BattleState.WIDTH - 1 - x;
                } else if (occupant == BattleSide.PLAYER) {
                    advance -= x;
                }
            }
        }
        return crewScore + hatchScore + activeScore + advance;
    }
}
