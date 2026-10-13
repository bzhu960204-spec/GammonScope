package com.gammonscope.eval;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GnubgEvalTest {

    private static int[][] startingBoard() {
        int[] a = new int[25];
        int[] b = new int[25];
        for (int[] side : new int[][] {a, b}) {
            side[23] = 2;
            side[12] = 5;
            side[7] = 3;
            side[5] = 5;
        }
        return new int[][] {a, b};
    }

    private GnubgEval engine() {
        try (var in = new org.springframework.core.io.ClassPathResource("gnubg.weights").getInputStream()) {
            return new GnubgEval(Weights.load(in));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void weightsLoad() {
        GnubgEval e = engine();
        float[] out = e.evaluatePosition(startingBoard());
        assertTrue(out[0] > 0.3f && out[0] < 0.7f, "start win prob near 0.5, got " + out[0]);
    }

    @Test
    void openingThreeOneMakesFivePoint() {
        GnubgEval e = engine();
        List<GnubgEval.ScoredMove> moves = e.hint(startingBoard(), 3, 1);
        assertFalse(moves.isEmpty());
        // gnubg's clear best opening play for 3-1 is making the 5-point: 8/5 6/5.
        assertEquals("8/5 6/5", moves.get(0).notation,
                "best 3-1 play should be making the 5-point");
    }
}
