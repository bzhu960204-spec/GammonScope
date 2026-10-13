package com.gammonscope.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Loads the gnubg weights once at startup and exposes a move-hint API backed by
 * {@link GnubgEval}. Hints are 0-ply cubeless (money) evaluations.
 */
@Service
public class EvalEngine {

    private final GnubgEval eval;

    public EvalEngine() {
        this.eval = new GnubgEval(loadWeights());
    }

    private static Weights loadWeights() {
        try (InputStream in = new ClassPathResource("gnubg.weights").getInputStream()) {
            return Weights.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load gnubg.weights from classpath", e);
        }
    }

    /**
     * @param checkers [2][25] checker counts, each player's own perspective
     * @param onRoll   which player (0/1) is to move
     * @param dice0    first die (1-6)
     * @param dice1    second die (1-6)
     */
    public List<GnubgEval.ScoredMove> hint(int[][] checkers, int onRoll, int dice0, int dice1) {
        int[][] board = new int[2][];
        board[1] = checkers[onRoll].clone();
        board[0] = checkers[1 - onRoll].clone();
        return eval.hint(board, dice0, dice1);
    }
}
